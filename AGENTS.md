# AGENTS.md — legado-E 项目协作须知

本文件由 DeepSeek Harness 自动注入到工作目录为 `D:\vsproject\legado-E` 的每个会话开头（多 agent 协作共读）。
详细操作步骤见《发布推送流程书.md》；本文件只写"必须先知道"的东西。

## 项目定位

- legado 阅读 App 的独立 fork（origin: `tjf-github/legado-E`，upstream: `Luoyacheng/legado-E`），Kotlin/Android。
- 近期主线：本地漫画导入（文件夹/相册）、目录章节增删/拆分/合并、书架分类、构建与 CI 优化。
- 历史与待办见仓库内各期计划书与《维护计划书-后续修复与增强.md》。

## 分支与发布（最重要）

- 开发分支：`codex/manga-import`；发布分支：`main`。
- `git push origin main` 触发 GitHub Actions `Test Build`，自动构建并发布 **beta 预发布**（tag `beta`）；App 内更新检查读 `releases/tags/beta`。
- 发正式版（prerelease:false）：先在 `app/src/main/assets/updateLog.md` 顶部加 `**20xx/xx/xx**` 更新条目，再合并推送。
- 发布合并：`main` 先 `merge --ff-only origin/main`，再 `git merge codex/manga-import -X theirs`（冲突一律取 codex 侧），推送前本地全量单测通过。完整步骤见《发布推送流程书.md》§4。

## 硬性规则

- 编译/单测门槛：改动后必须 `.\gradlew.bat :app:compileAppDebugKotlin` 与 `:app:testAppDebugUnitTest` 通过再提交（flavor 是 `app`，任务名不是 legado）。
- 跑 Gradle 前 `$env:GRADLE_USER_HOME = "D:\gradle_home"`。
- 数据库不加表不加列；本地 TXT/EPUB 导入与阅读路径零侵入。
- 提交信息用 conventional commits（`fix/feat/docs/chore/test`）；PowerShell 下中文提交信息用 `git commit -F <临时文件>`，避免引号/尖括号把参数拆散。

## 沙箱与提权（本机特有，别的 agent 直接照做）

默认 workspace-write 沙箱会拦截三类操作，需带 `sandbox_permissions: danger-full-access` 重试一次：

1. 工作区内**文件删除/unlink**（`git checkout` 切分支、`git clean` 报 `unable to unlink ... Invalid argument`）；
2. Gradle 写 `D:\gradle_home`（wrapper 报 `zip.lck 拒绝访问`）；
3. git 走 HTTPS 推送/拉取（凭据库被拦，报 `SEC_E_NO_CREDENTIALS`）。**本机 SChannel 后端不可用**：提权后若仍报 schannel 错，加 `-c http.sslBackend=openssl`，如 `git -c http.sslBackend=openssl push origin main`。

另：git 的正常信息写在 stderr，PowerShell 会显示 `NativeCommandError` + `exit code 1`，**属假失败**——看到 `xxxx..yyyy  branch -> branch` 就是成功，不要只看 exit code。

## 本地专属文件（勿删、勿提交）

`app/release.keystore`、`keystore.properties`、`keystore-base64.txt`、`build.log` 被 .gitignore，是本地构建/签名材料；任何 `git clean` 之前先备份它们。

## 文档索引

- `发布推送流程书.md` —— 发布/推送全流程 + 踩坑记录（遇到发布问题首选）；
- `维护计划书-后续修复与增强.md` —— 当前待办与已知问题（**活文档**，新反馈都记这里）；
- `复盘总结.md` —— 各期成果、手测记录与提交清单；
- `第N期计划书*.md`、`构建优化书.md` —— 一~五期功能设计与构建方案（**历史归档，已完成，不再维护**）。

## 多 agent 协作约定

- 动任何模块前，先读对应计划书与该模块代码注释；
- 改 bug 先写复现测试（JVM 单测在 `app/src/test`），后修代码，全量单测通过再提交；
- 功能改动只提交到 `codex/manga-import`；发布动作只发生在 main 合并环节（走流程书 §4）；
- **接「发布/构建/CI」类任务前**，先读《发布推送流程书.md》并核对 `.github/workflows/*.yml` 的 `on:` 触发条件（发版是 `push main` 自动触发，不是 `workflow_dispatch`），别凭 workflow 文件名或第一印象下结论；
- **多窗口共享同一 git 仓库**：关键 git 操作前先 `git status -sb` + `git log --oneline -3` 核对最新状态（别的窗口可能已推进 HEAD，别拿旧状态决策）。
