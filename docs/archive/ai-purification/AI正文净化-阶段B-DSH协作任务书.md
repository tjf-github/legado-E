# AI 正文净化阶段 B——DSH 协作任务书

> 状态：`accepted`（DSH 两轮审查 + 最终确认，Codex 独立实现与验收）
> 日期：2026-09-05
> 基线：`49c802ec6 feat(ai): 建立正文净化阶段 A 离线基座`
> 实施协作：DSH（`deepseek-official / deepseek-v4-pro`）+ Codex
> 验收者：Codex（独立检查实际 diff、反例、备份边界和全量回归）

## 1. 目标

按《AI正文净化功能计划书.md》的“阶段 B：Provider 与安全配置”，在不接阅读页、不发送真实书籍正文的前提下，完成可测试的 Chat Completions Provider、安全配置与密钥存储基座。

## 2. 必须交付

1. `OpenAiCompatibleProvider`：只支持 Chat Completions；固定系统提示与结构化局部编辑协议；正文请求从调用方显式传入 API Key，不从普通配置读取。
2. Provider 安全边界：默认仅 HTTPS；HTTP 只允许经上层显式批准的 loopback/局域网地址；禁止把 `Authorization` 带到跨主机重定向；超时可配置；响应体有硬上限；错误只返回稳定分类和脱敏消息。
3. `testConnection`：只发送代码内固定测试文本，不能接收或转发书籍正文；复用与正式请求相同的 URL、重定向、大小上限和脱敏规则。
4. `AiKeyStore`：API 23+ 使用 Android Keystore 的 AES/GCM 主密钥，密文只写 `noBackupFilesDir`；密钥失效、认证失败、密文损坏或解密失败时删除不可用密文并返回“需要重新录入”，绝不回退明文。API 21–22 明确返回不支持并保持 AI 关闭。
5. 非敏感 `AiTextConfig`：不包含 API Key，持久化载体不得让调用方误把 Key 塞入普通 Preferences；配置变更产生新 revision/fingerprint，供阶段 C/D 取消旧任务与使旧缓存失效。
6. 测试：URL 策略、错误分类、脱敏、响应上限、固定测试连接载荷、跨主机重定向不泄漏认证头、配置 fingerprint 不含 Key、密钥存储状态机（用可注入 fake crypto/file backend 做 JVM 测试）。

## 3. 完成门禁

- 测试连接载荷中不存在任何调用方正文；
- 测试密钥字面量不出现在普通配置、异常文本、日志字段、缓存身份/元数据或仓库内生成的备份/导出结构中；
- Keystore 失效/密文损坏后密文被清除、功能保持关闭并要求重录；
- Provider 对认证失败、限流、响应过大、截断、协议错误、网络/超时做稳定分类；
- `git diff --check`、AI 聚焦 JVM 测试、`:app:compileAppDebugKotlin`、`:app:testAppDebugUnitTest` 通过。

## 4. 范围与保护

- 允许修改 `app/src/main/java/io/legado/app/help/ai/` 与对应 JVM/Android 测试；必要时最小增加网络测试依赖或 Android backup XML；
- 允许新增本阶段实施报告，并同步功能书/维护计划/`AGENTS.md` 的状态；
- 不接 `ReadBook`、`ChapterProvider` 或任何 UI，不实现阶段 C 文件缓存/分块扩展，不新增数据库表/列；
- 不读取或修改 `app/release.keystore`、`keystore.properties`、`keystore-base64.txt`、`look.ps1`、真实凭据、真实书源/正文/缓存；
- 不提交、不推送、不发布、不切换分支。

## 5. DSH 返回协议

DSH 先做阻塞性反例审查，重点核对 Android 21–22、OkHttp 重定向头、Gson 协议解析、响应体限额、Key/备份边界和可测性；返回最多 8 个必须修正项及最小实现建议。DSH 的结论只能是 `review`，不能自行标记 `accepted`。
