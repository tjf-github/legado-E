<#
.SYNOPSIS
监视长时间运行的构建任务，检测"卡死"状态并提醒。

.DESCRIPTION
判断是否卡住，主要看两个信号：
  1. CPU 是否还在被消耗（真正在编译的进程 CPU 会持续增长）
  2. 日志文件是否还在更新（如果指定了日志文件）

两种用法：
  A. 包住构建命令运行（推荐，构建结束后自动结束监视）
     powershell -ExecutionPolicy Bypass -File .\watch-build.ps1 `
         -Command "set GRADLE_USER_HOME=D:\vsproject\legado-E\.gradle_home && .\gradlew.bat :app:compileAppDebugKotlin --console=plain > build.log 2>&1" `
         -LogPath .\build.log -MaxMinutes 20

  B. 附加到已经在运行的进程（需要知道进程 PID）
     powershell -ExecutionPolicy Bypass -File .\watch-build.ps1 -ProcessId 12345

.PARAMETER Command
要运行并监视的命令（交给 cmd.exe 执行，可见窗口显示输出）。

.PARAMETER ProcessId
附加模式：要监视的进程 PID。

.PARAMETER ProcessName
附加模式：按进程名查找（取最新启动的一个）。

.PARAMETER CpuProcessName
Command 模式下检查 CPU 的进程名，默认 java（Gradle 编译用）。

.PARAMETER LogPath
构建日志文件路径；超过 StaleMinutes 未更新视为停滞。

.PARAMETER SampleSeconds
每次检查的采样间隔（秒）。

.PARAMETER CpuIdleSeconds
一个采样窗口内 CPU 消耗低于该值（秒）视为空闲。

.PARAMETER StaleMinutes
日志超过该分钟数未更新视为停滞。

.PARAMETER StuckSamples
连续多少次"空闲且日志停滞"后判定为卡死。

.PARAMETER MaxMinutes
总运行时间超过该分钟数时发出提醒（0 = 不检查）。

.PARAMETER KillOnStuck
判定卡死后自动终止进程树（默认只提醒，不自动杀）。

.PARAMETER Quiet
只在状态变化或报警时输出。

.PARAMETER NoBuildWindow
构建命令不在新窗口显示（后台运行时用），输出仍按命令里的重定向写入日志。
#>

param(
    [string]$Command = "",
    [int]$ProcessId = 0,
    [string]$ProcessName = "java",
    [string]$CpuProcessName = "java",
    [string]$LogPath = "",
    [int]$SampleSeconds = 30,
    [double]$CpuIdleSeconds = 0.1,
    [int]$StaleMinutes = 5,
    [int]$StuckSamples = 3,
    [int]$MaxMinutes = 0,
    [switch]$KillOnStuck,
    [switch]$Quiet,
    [switch]$NoBuildWindow
)

$ErrorActionPreference = "Stop"

function Write-Status([string]$Text, [string]$Color = "Gray") {
    if (-not $Quiet) {
        Write-Host ("[{0}] {1}" -f (Get-Date -Format "HH:mm:ss"), $Text) -ForegroundColor $Color
    }
}

function Alert([string]$Text) {
    Write-Host ""
    Write-Host ("[{0}] 警告：{1}" -f (Get-Date -Format "HH:mm:ss"), $Text) -ForegroundColor Red
    [System.Media.SystemSounds]::Exclamation.Play()
}

function Get-AggregateCpu([string]$Name) {
    $total = 0.0
    Get-Process -Name $Name -ErrorAction SilentlyContinue | ForEach-Object { $total += $_.CPU }
    return $total
}

# 取得要监视的进程
$target = $null
if ($Command) {
    $psi = [System.Diagnostics.ProcessStartInfo]::new("cmd.exe", "/c " + $Command)
    if ($NoBuildWindow) {
        $psi.UseShellExecute = $false
        $psi.CreateNoWindow = $true
    } else {
        $psi.UseShellExecute = $true
    }
    $target = [System.Diagnostics.Process]::Start($psi)
    Write-Status ("已启动构建命令 (PID {0})" -f $target.Id) -Color Green
} else {
    if ($ProcessId -gt 0) {
        try {
            $target = Get-Process -Id $ProcessId -ErrorAction Stop
        } catch {
            Write-Host ("找不到进程 PID {0}，请确认它还在运行。" -f $ProcessId) -ForegroundColor Yellow
            exit 1
        }
    } else {
        $target = Get-Process -Name $ProcessName -ErrorAction SilentlyContinue |
            Sort-Object StartTime -Descending | Select-Object -First 1
        if (-not $target) {
            Write-Host ("没有找到进程：{0}。如果任务还没开始，请先启动它，或改用 -Command 方式。" -f $ProcessName) -ForegroundColor Yellow
            exit 1
        }
    }
    Write-Status ("已附加到进程 {0} (PID {1})" -f $target.ProcessName, $target.Id) -Color Green
}

if ($LogPath) {
    Write-Status ("日志文件：{0}（{1} 分钟未更新视为停滞）" -f $LogPath, $StaleMinutes)
}
Write-Status ("判定规则：CPU 本窗口少于 {0}s 且日志停滞，连续 {1} 次即报警" -f $CpuIdleSeconds, $StuckSamples)

$startTime = Get-Date
$cpuPrev = if ($Command) { Get-AggregateCpu $CpuProcessName } else { $target.CPU }
$idleCount = 0
$stuckReported = $false
$maxWarned = $false

while ($true) {
    Start-Sleep -Seconds $SampleSeconds

    # 目标进程是否已结束
    $exited = $false
    try {
        $target.Refresh()
        $exited = $target.HasExited
    } catch {
        $exited = $true
    }
    if ($exited) {
        Write-Status ("进程已结束（PID {0}），监视结束。" -f $target.Id) -Color Green
        exit 0
    }

    # CPU 增量
    $cpuNow = if ($Command) { Get-AggregateCpu $CpuProcessName } else { $target.CPU }
    $cpuDelta = [Math]::Max(0.0, $cpuNow - $cpuPrev)
    $cpuPrev = $cpuNow
    $cpuIdle = $cpuDelta -lt $CpuIdleSeconds

    # 日志新鲜度
    $logFresh = $true
    if ($LogPath) {
        if (Test-Path -LiteralPath $LogPath) {
            $logFresh = ((Get-Date) - (Get-Item -LiteralPath $LogPath).LastWriteTime).TotalMinutes -lt $StaleMinutes
        } else {
            $logFresh = $false
        }
    }

    if ($LogPath) {
        $suspicious = $cpuIdle -and (-not $logFresh)
    } else {
        # 没有日志文件时，仅凭 CPU 空闲来判定
        $suspicious = $cpuIdle
    }
    if ($suspicious) { $idleCount++ } else { $idleCount = 0 }

    $elapsedMin = [math]::Round(((Get-Date) - $startTime).TotalMinutes, 1)
    $cpuTxt = ("CPU 本窗口 {0:N2}s" -f $cpuDelta)
    $logTxt = if ($LogPath) { if ($logFresh) { "日志正常" } else { "日志停滞" } } else { "无日志监控" }
    Write-Status ("已运行 {0} 分钟 | {1} | {2} | 连续可疑 {3}/{4}" -f $elapsedMin, $cpuTxt, $logTxt, $idleCount, $StuckSamples)

    if ($idleCount -ge $StuckSamples) {
        if (-not $stuckReported) {
            $stuckReported = $true
            Alert ("检测到疑似卡死：进程还活着，但 CPU 几乎无消耗（{0}），日志也未更新（{1} 分钟），连续确认了 {2} 次。" -f $cpuTxt, $StaleMinutes, $StuckSamples)
            if ($KillOnStuck) {
                Alert "正在终止进程树..."
                & taskkill.exe /PID $target.Id /T /F
            } else {
                Alert "未自动终止（可用 -KillOnStuck 开启）。你可以手动去停止，或按 Ctrl+C 结束监视。"
            }
        }
    } else {
        $stuckReported = $false
    }

    if ($MaxMinutes -gt 0 -and $elapsedMin -ge $MaxMinutes -and -not $maxWarned) {
        $maxWarned = $true
        Alert ("已运行超过 {0} 分钟仍未结束。CPU/日志看起来正常，可能只是比较慢，建议留意一下。" -f $MaxMinutes)
    }
}
