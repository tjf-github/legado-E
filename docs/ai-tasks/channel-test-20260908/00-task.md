# channel-test-20260908 — Claude 同工作区通道测试

状态：`accepted`（仅通道能力，不代表任何产品功能验收）

## 目标

验证 Codex无需用户中转，也无需把项目内容拼入命令或 stdin，即可让 Claude从同一工作区读取一份限定任务书并返回可供 Codex独立审查的结果。

## 授权与隔离

- Claude只读取隔离邮箱中的合成任务书，不读取项目源码。
- 不提供真实章节、书名、URL、密钥、签名材料、日志或账户数据。
- Claude无项目写权限，不运行命令、测试、提交、推送或发布。
- 测试模型通过现有 Claude Desktop / CC Switch DeepSeek 路由调用。

## 合成审查题

评估一个 Kotlin 局部编辑校验器为 `ambiguous + changed` 增加短前后文精确锚点的最小测试契约，保持 Unicode code-point、哨兵保护、排序/重叠校验和整章失败关闭。

## 完成条件

- [x] Claude受限读取任务书成功；
- [x] 调用参数不含任务正文或源码；
- [x] 返回包含反例和测试建议的实质复审；
- [x] Codex独立判断哪些意见可采纳；
- [x] Git tracked 源码不被 Claude修改。

