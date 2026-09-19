# tri-agent-protocol-test-20260910 — DSH 调度报告

状态：`review`

## 调用拓扑与证据

当前宿主的两个自定义 preset 各只启用一个子代理，因此没有伪造“单会话双调用”，而是使用两个全新 DSH 父会话分别验证：

- Codex 腿：页面工具行 `subagent_codex · Codex protocol review leg`，runId `8f666fae-137a-4d13-b9e0-23207260cc89`，DSH 父 nonce `DSH_PARENT_CODEX_LEG_ACK_20260910`，用时 1 分 10 秒。
- Claude 腿：页面工具行 `subagent_claude_code · Claude child protocol review`，runId `b27dfebe-8fef-497f-8127-2f9fcf391dc6`，DSH 父 nonce `DSH_PARENT_CLAUDE_LEG_ACK_20260910`，用时 22 秒。
- 两腿均只读取 `00-task.md`；传给子代理的提示只含角色、相对文件名、输出格式和禁止事项；未嵌入正文，未修改文件，未运行命令或测试。

Codex 子代理的逐字结果独立保存在 `15-codex-child-review.md`；本文件只记录 DSH 调度与汇总，避免父代理报告成为第二份可被改写的“原文”。

## DSH 汇总判断

- “入口层只放稳定指针、证据层存任务材料”可执行，但仅靠文字分层不能自动阻止迁移污染或并发覆盖。
- 两个子代理都在只收到文件名与边界的情况下返回了相关实质意见，证明文件邮箱路径可用。
- 单一 DSH 父会话同时暴露两个工具本次 `NOT RUN`；这不影响两个独立通道验收，但不得当作单会话调度证据。
- DSH 结论只到 `review`，未声称 `accepted`。
