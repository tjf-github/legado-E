# AI 正文净化阶段 A——DSH 协作实施与 Codex 验收报告

> 状态：`accepted`
> 日期：2026-09-03
> 分支：`codex/manga-import`
> 提交/推送/发布：均未执行

## 1. 结论

《AI正文净化功能计划书.md》的阶段 A 离线基座已完成并通过 Codex 独立验收：已固化纯文本入口、整章保护与分块、局部编辑硬校验、缓存身份、Fake Provider 串行/单飞/取消/失败熔断契约。本阶段完全离线，未接入真实 HTTP、API Key、Keystore、文件缓存、`ReadBook` 或 UI，不会发送书籍正文。

## 2. DSH 实际交付与纠偏

- 目标：`deepseek-official / deepseek-v4-flash-vision-exp`，DSH `0.1.1-rc.2`。
- 首个会话 `session-129e3aee-de75-401c-8d74-519b55a37ce0` 接收完整阶段 A 任务；长时间进行分析但未及时落盘，Codex 纠偏后仍未收敛，因此取消。
- 第二个会话 `session-a534ea28-e399-4576-8bc5-0be19d4672f6` 缩小为分块器任务，仍只分析未落盘，后取消。
- 首个会话在取消交界实际留下 `AiProtocol.kt` 和 `AiPlainText.kt`。Codex 检查实际内容后保留；其余生产代码、测试、修错和验收由 Codex 完成。
- 本报告不声称 DSH 完成了未实际交付的分块、校验、状态机或测试。

DSH 外发范围被限定为功能书、阶段 A 任务书和相关 Kotlin 代码事实；未发送 `look.ps1`、凭据、签名材料、真实书源、用户正文或缓存。

## 3. 改动文件

### 生产代码

- `app/src/main/java/io/legado/app/help/ai/AiProtocol.kt`：Provider 和局部编辑契约类型（DSH 草案）。
- `app/src/main/java/io/legado/app/help/ai/AiPlainText.kt`：统一纯文本判定（DSH 草案）。
- `app/src/main/java/io/legado/app/help/ai/AiTextChunker.kt`：URL/数值整章哨兵化、code point 分块、边界优先级、独立尾部上下文和严格恢复。
- `app/src/main/java/io/legado/app/help/ai/AiOutputValidator.kt`：局部编辑范围/锚定/类型/哨兵/长度校验与逆序应用。
- `app/src/main/java/io/legado/app/help/ai/AiCacheKey.kt`：带长度帧的确定性 SHA-256 缓存身份，类型上排除 API Key。
- `app/src/main/java/io/legado/app/help/ai/AiChapterProcessor.kt`：全局串行、同键单飞、取消传播和整章失败回退的离线编排。

### JVM 测试

- `AiPlainTextTest.kt`
- `AiTextChunkerTest.kt`
- `AiOutputValidatorTest.kt`
- `AiCacheKeyTest.kt`
- `AiChapterProcessorTest.kt`

共 16 项新增测试，覆盖功能书的固定正反例。

## 4. 验证证据

1. 首次聚焦测试：12 项中 10 通过、2 失败。失败均是测试夹具偏差：本地书需包含 `BookType.local` 类型位；9-code-point 窗口不能要求越界包含第二个换行。修正夹具后未放宽生产契约。
2. 聚焦测试：
   - `\.\gradlew.bat :app:testAppDebugUnitTest --tests 'io.legado.app.help.ai.*'`
   - 结果：`BUILD SUCCESSFUL`，16/16 通过。
3. Kotlin 编译门槛：
   - `\.\gradlew.bat :app:compileAppDebugKotlin`
   - 结果：`BUILD SUCCESSFUL in 1m 7s`。
4. 全量 JVM 单测门槛：
   - `\.\gradlew.bat :app:testAppDebugUnitTest`
   - 结果：15 个套件、105 项测试，0 failures、0 errors、0 skipped；`BUILD SUCCESSFUL in 12s`。
5. `git diff --check`：通过；仅显示用户既有文档的 LF/CRLF 提示，无空白错误。
6. 敏感字面检查：新生产目录不包含 API Key、Authorization、Bearer、签名文件或凭据路径。

Gradle 初次在受限沙箱内因 `D:\gradle_home` 锁文件拒绝访问失败；按项目规则获得对该缓存的授权后重跑成功。这是环境权限记录，不是代码失败。

## 5. 未实施与下一门禁

以下属于阶段 B～E，本报告不宣称已完成：

- Chat Completions HTTP Provider、响应体上限、跨主机重定向认证头限制与错误脱敏；
- Android Keystore 密钥包装、禁止备份存储、失效清理和备份/日志/异常泄漏验收；
- 独立原子文件缓存及按章/书/全部清理联动；
- `ReadBook.contentLoadFinish*` 共享挂起函数、原文/AI 显示源、设置页、逐书开关、本设备+服务地址外发授权和阅读页状态；
- 真机与 lint 发布前验收。

下一步必须从阶段 B 开始，先完成可审计的 `AiKeyStore` 与备份隔离门禁，再让真实 Provider 获得读取密钥和发送正文的能力。
