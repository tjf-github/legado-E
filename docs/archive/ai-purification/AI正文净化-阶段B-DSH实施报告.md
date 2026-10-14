# AI 正文净化阶段 B——DSH 协作实施与 Codex 验收报告

> 状态：`accepted`
> 日期：2026-09-05
> 分支：`codex/manga-import`
> 基线：`49c802ec6 feat(ai): 建立正文净化阶段 A 离线基座`
> 推送/发布：未执行

## 1. 结论

阶段 B 已完成代码级验收：实现 Chat Completions Provider、安全端点与重定向策略、响应体硬限额、严格协议解析、固定连接测试、稳定脱敏错误、无 Key 普通配置、Android Keystore 密钥包装及配置失效信号。实现未接阅读页/UI，未调用真实 AI 服务，也未读取或发送真实密钥、书源、正文或缓存。

## 2. DSH 协作记录

- 目标：本机 DSH，`deepseek-official / deepseek-v4-pro`，reasoning `high`；会话 `session-fdd023af-295f-4fda-b455-d3acbb6cd047`。
- 外发范围：阶段 B 任务书/功能书、阶段 A AI 契约、AI 新增源码与测试，以及现有 HTTP/备份边界；排除签名文件、凭据、真实书源/正文/缓存、费用选择和发布权限。
- 首轮 DSH 找到 8 类阻塞风险：共享 trust-all HTTP 客户端、API 21–22 Keystore 门槛、响应先整块读入、Gson 缺字段放行、动态异常泄漏、测试连接可注入正文、Key/双备份路径、JVM 注入缝缺失。
- Codex 按实际仓库逐项实现并补反例；第二轮 DSH 对当前文件复审返回 `ready`，8 项全部 `resolved`，`remaining_blockers=[]`。
- DSH 只作审查，没有修改文件、运行 Gradle、提交、推送或发布；最终 `accepted` 由 Codex 独立给出。

## 3. 主要实现

### Provider 与网络安全

- `OpenAiCompatibleProvider` 固定 Chat Completions、`temperature=0`、输出预算和版本化局部编辑提示；`testConnection(config, apiKey)` 没有正文参数，只能发送代码内固定文本。
- `AiEndpointPolicy` 默认仅 HTTPS；HTTP 只有显式批准的 loopback/RFC 私网字面地址可用，拒绝 URL 用户信息、查询串和 fragment。
- `OkHttpAiTransport` 使用全新安全默认客户端，不复用项目的宽松 TLS 客户端；关闭自动重定向/重试，只允许同源 307/308 手工重发，跨来源及 301/302/303 在第二跳前拒绝。
- Content-Length 先验超限会拒绝；无/伪造 Content-Length 时仍按流累计，超过上限立即停止。所有 `InterruptedIOException` 归类为 timeout。
- HTTP/网络/协议错误只暴露固定分类与固定消息，不携带动态 URL、头、响应体、正文或 Key。

### 配置与密钥

- `AiTextConfig`/`AiProviderConfig` 类型上不含 API Key；专用 no-backup 配置文件使用 `AtomicFile`，变更产生 generation + SHA-256 fingerprint 和失效回调。
- `AiApiKey` 不是 data class，构造受限，`toString` 固定脱敏，明文只在构造认证头/加密的最短作用域取用。
- API 23+ 使用 Android Keystore AES/GCM；密文带版本/长度帧并仅写 `noBackupFilesDir/ai_text/api_key.bin`。API 21–22 返回 Unsupported，不产生密文。
- 密钥缺失、失效、密文损坏或解密失败会删除密文与别名并触发必传的 `onKeyUnavailable`；`AiSecurityGate` 只有在总开关开启且 Key Available 时允许运行。
- AI 请求、编辑、处理结果、HTTP 请求/响应等承载正文的对象都覆盖 `toString`，避免调试插值泄漏正文。

## 4. 测试与验证

- 聚焦命令：`.\gradlew.bat :app:testAppDebugUnitTest --tests 'io.legado.app.help.ai.*'`；最终 32 项通过。
- 编译门禁：`.\gradlew.bat :app:compileAppDebugKotlin`；`BUILD SUCCESSFUL in 47s`。
- 全量 JVM 门禁：`.\gradlew.bat :app:testAppDebugUnitTest`；19 套件、121 项，0 failures、0 errors、0 skipped，`BUILD SUCCESSFUL in 12s`。
- `git diff --check`：无空白错误，仅现有 Git LF/CRLF 提示。
- 覆盖反例：公网 HTTP、含凭据/查询的 URL、跨源重定向、改变 POST 语义的重定向、响应超限、401 脱敏、finish reason、缺字段/null/未知 kind、固定连接载荷、配置失效、密文损坏、API 21–22 不落盘、任意 Provider 异常不透传。

## 5. 边界与下一步

- 本阶段未实现阶段 C 的文件缓存/整章状态机扩展，未实现阶段 D 的阅读链路/UI/逐书授权，也未运行真实 Provider。
- 真机 Android Keystore 轮换/失效和实际 backup zip 全文扫描属于阶段 E 的真机验收；当前已从结构上隔离两条备份路径：Key 不进入 SharedPreferences/数据库，密文只在 `noBackupFilesDir`，仓库手工 Backup 的白名单不包含该目录。
- 唯一下一步：阶段 C“分块、校验与缓存”。
