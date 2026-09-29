# <task-id> — 最终验收

状态：`review`

- acceptor 运行实例：
- mode / assurance / route_revision：
- 实际 diff：
- 执行证据核对：
- 复审 findings 逐项处理：
- 独立复测：
- 可见行为：
- NOT RUN：

## 最终判定

`accepted / rework / blocked / provisional`

- product_state：`ready / rework / blocked`
- assurance_state：`complete / partial / failed`
- 理由：
- 唯一下一步：
- 提交 / 推送 / 发布状态：

本轮回执在报告写完后才生成；本轮回执落盘后的 `role-verify` 由 controller 执行，acceptor 在此只记 `NOT RUN（回执待落盘）`。`rework` 原文不因 controller 后续 provisional 收尾而改写。
