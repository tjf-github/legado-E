# AI 正文净化阶段 A——DSH 协作任务书

> 状态：`accepted`（DSH 交付协议/判定草案，Codex 补齐实现并独立验收）
> 日期：2026-09-03
> 实施者：DSH（`deepseek-official / deepseek-v4-flash-vision-exp`）+ Codex
> 验收者：Codex（独立检查实际 diff、反例和全量回归）

## 1. 目标

按《AI正文净化功能计划书.md》的“阶段 A：契约与反例测试”建立一个离线、可单测、不改变现有阅读行为的实现基座。先用失败样本和 JVM 单测固化契约，再做最小实现使本阶段测试通过。

## 2. 允许范围

- 新增 `app/src/main/java/io/legado/app/help/ai/` 下的离线核心类；
- 新增 `app/src/test/java/io/legado/app/help/ai/` 下的 JVM 测试和脱敏固定语料；
- 如测试构建确实需要，可最小修改与测试直接相关的 Gradle 配置，但必须在报告中单独说明；
- 新增 `AI正文净化-阶段A-DSH实施报告.md`，记录改动、命令、测试数、偏差、未验证项和风险。

## 3. 本阶段必须固化的契约

1. `isAiPlainText` 使用唯一实现：拒绝本地书、图片书、`<img>`、`<usehtml>` 和真实 HTML 标签；允许 `a < b > c`、`1 < 2`、孤立尖括号和普通纯文本。
2. 整章先对 URL、连续数字/数值做不可猜测、可逆、仅内存存在的哨兵化，再分块；哨兵不得被硬切。
3. 正文块严格覆盖哨兵化输入且互不重叠；拼接恢复后与原输入按 Unicode code point 完全一致。`context_only` 独立、只读、不属于任何正文块，上限 200 code point。
4. 验证局部编辑列表：`chunk_id`、结束原因、128 项上限、偏移边界、顺序/不重叠、`original` 锚定、`kind`、哨兵不可触碰/构造、文本编辑非空/核心字符等长/单项不超 12，空白和标点编辑不得包含汉字、字母或数字。
5. 从后向前应用经验证编辑，恢复哨兵后再校验保护值的数量、值与顺序。
6. 缓存身份生成是纯函数，包含功能书 7.2 的所有身份项，不得包含 API Key。
7. 用 Fake Provider 验证串行、同键单飞、取消、任一块失败后停止后续调用、只有整章 `Completed` 才可作为缓存候选；本阶段可用内存仓库，不实现真实文件缓存。

## 4. 保护项与排除项

- 不修改 `WebBook`、`ReadBook`、`ContentProcessor`、`ChapterProvider`、`BookHelp` 和任何 UI；
- 不实现真实 HTTP Provider、API Key/Keystore、Preferences、文件缓存、数据库或备份改动；
- 不读取或修改 `app/release.keystore`、`keystore.properties`、`keystore-base64.txt`、`look.ps1`、用户凭据、真实书源/正文/缓存；
- 不修改《AI正文净化功能计划书.md》、`AGENTS.md`、维护/优化计划书和用户现有未提交改动；
- 不提交、不推送、不发布，不切换分支。

## 5. 必须的验证与返回包

- 先运行新增的聚焦 JVM 测试，再运行 `:app:compileAppDebugKotlin` 和 `:app:testAppDebugUnitTest`；
- Gradle 前设置 `GRADLE_USER_HOME=D:\gradle_home`；
- 执行 `git diff --check`，报告实际改动文件、测试命令/数量/结果、未验证项、偏差、风险和建议下一步；
- 完成后只把任务状态交付为 `review`，不得自行声称 `accepted`。
