<#
.SYNOPSIS
统一构建脚本：编译 + 单测 + 打包，并动态定位带时间戳的 APK。

.DESCRIPTION
本机构建环境要点（复盘踩坑记录）：
  1. GRADLE_USER_HOME 固定为 D:\gradle_home（wrapper 锁文件/构建缓存位置）；
  2. 编译任务为 :app:compileAppDebugKotlin（不存在 compileLegadoDebugKotlin）；
  3. 调试包 applicationId 为 io.legado.app.debug；
  4. APK 文件名带构建时间戳（legado_app_*.apk），安装/拷贝必须动态取最新文件；
  5. 单测任务为 :app:testAppDebugUnitTest。

.USAGE
  powershell -ExecutionPolicy Bypass -File .\build.ps1
  powershell -ExecutionPolicy Bypass -File .\build.ps1 -NoTest
  powershell -ExecutionPolicy Bypass -File .\build.ps1 -Install
#>
param(
    [switch]$NoTest,
    [switch]$Install
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

if (-not $env:GRADLE_USER_HOME) {
    $env:GRADLE_USER_HOME = "D:\gradle_home"
}

function Invoke-Gradle([string[]]$GradleArgs) {
    & (Join-Path $root 'gradlew.bat') -p $root @GradleArgs
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

Write-Host "[1/3] 编译 Kotlin"
Invoke-Gradle @(':app:compileAppDebugKotlin', '--console=plain')

if (-not $NoTest) {
    Write-Host "[2/3] JVM 单测"
    Invoke-Gradle @(':app:testAppDebugUnitTest', '--console=plain')
} else {
    Write-Host "[2/3] 跳过单测（-NoTest）"
}

Write-Host "[3/3] 打包 assembleDebug"
Invoke-Gradle @(':app:assembleDebug', '--console=plain')

$apk = Get-ChildItem -Path (Join-Path $root 'app\build\outputs\apk\app\debug\*.apk') -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $apk) {
    Write-Error "未找到 APK 输出：app\build\outputs\apk\app\debug\*.apk"
    exit 1
}
Write-Host ("APK: " + $apk.FullName)

if ($Install) {
    Write-Host "安装到设备：adb install -r ..."
    & adb install -r $apk.FullName
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
    Write-Host "安装完成"
}
