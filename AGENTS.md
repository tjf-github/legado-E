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

- 编译/单测门槛：改动后必须 `.\gradlew.bat :app:compileAppDebugKotlin` 与 `:app:testAppDebugUnitTest` 通过再提交（flavor 是 `app`，任务名不是 legado）；**发版前**再补跑 `:app:lintAppDebug`（CI `Test Build` 实际跑 test + lint + assembleRelease，别只跑 compile+test 就发版）。
- 跑 Gradle 前 `$env:GRADLE_USER_HOME = "D:\gradle_home"`。
- 数据库不加表不加列；本地 TXT/EPUB 导入与阅读路径零侵入。
- 提交信息用 conventional commits（`fix/feat/docs/chore/test`）；PowerShell 下中文提交信息用 `git commit -F <临时文件>`，避免引号/尖括号把参数拆散。

## 本地专属文件（勿删、勿提交）

`app/release.keystore`、`keystore.properties`、`keystore-base64.txt`、`build.log` 被 .gitignore，是本地构建/签名材料；任何 `git clean` 之前先备份它们。

## 文档索引

- `发布推送流程书.md` —— 发布/推送全流程 + 踩坑记录（遇到发布问题首选）；
- `双平台开发环境流程书.md` —— Windows + WSL 双端环境事实、本地改动（-Xmx3g/skip-worktree）、网络踩坑、同步约定；
- `维护计划书-后续修复与增强.md` —— 当前待办与已知问题（**活文档**，新反馈都记这里）；
- `优化预处理计划书.md` —— 稳定基线后的技术优化候选、测量和排期，不替代维护计划；
- `AI正文净化功能计划书.md` —— AI 正文后处理的第一版范围、安全边界、DSH 审查与开工门禁（功能书已完成，尚未实施）；
- `复盘总结.md` —— 各期成果、手测记录与提交清单；
- `第N期计划书*.md`、`构建优化书.md` —— 一~五期功能设计与构建方案（**历史归档，已完成，不再维护**）。

## 多 agent 协作约定

- 动任何模块前，先读对应计划书与该模块代码注释；
- 改 bug 先写复现测试（JVM 单测在 `app/src/test`），后修代码，全量单测通过再提交；
- 功能改动只提交到 `codex/manga-import`；发布动作只发生在 main 合并环节（走流程书 §4）；
- **接「发布/构建/CI」类任务前**，先读《发布推送流程书.md》并核对 `.github/workflows/*.yml` 的 `on:` 触发条件（发版是 `push main` 自动触发，不是 `workflow_dispatch`），别凭 workflow 文件名或第一印象下结论；
- **多窗口共享同一 git 仓库**：关键 git 操作前先 `git status -sb` + `git log --oneline -3` 核对最新状态（别的窗口可能已推进 HEAD，别拿旧状态决策）。
- **对话框回调绑定 Activity**：`GroupSelectDialog`/`GroupManageDialog` 等 CallBack 取 `activity as? CallBack`，Fragment 内（如书架分组页）无法直达，需内联自建弹窗（示例见 `BooksFragment.selectGroup`）。
