# 角色与隔离规则

## controller

- 单写 `CURRENT.md`；
- 冻结目标、非目标、文件白名单、外发边界和完成条件；
- 发生换挡时递增 `route_revision`；
- 不得用“代理说完成”替代真实证据。

## executor

- 单写 `10-execution.md`；
- 先记录复现或基线，再做最小改动；
- 列出实际读取、修改、命令、结果、偏差和 `NOT RUN`；
- 默认不得提交、推送、发布或扩大授权。

## reviewer

- 单写 `20-review.md`；
- 默认只读；
- findings 必须包含严重级别、证据、反例和最小建议；
- 不得自行写 `accepted`。

## acceptor

- 单写 `30-acceptance.md`；
- 核对实际 diff、测试、可见行为和路由记录；
- 明确采纳、驳回或修正 reviewer 意见；
- 最终判定只能是 `accepted / rework / blocked / provisional`。

## 由 runner 拉起时的「单写」偏离

上面各角色的「单写」指的是**内容作者唯一**，不是**落盘进程唯一**。当角色由 `agent-protocol role`
拉起的后台子代理承担时：

- `executor` / `acceptor`：子代理有项目写权限，**仍然自己写**自己的证据文件。runner 只记录运行前的
  mtime，运行后校验文件确实前进了，否则判定「子代理没有写出证据」并失败——不代写、不补写。
- `reviewer`：子代理以**真只读**姿态运行，只把复审报告打到 stdout；runner 把 stdout **原样转录**为
  `20-review.md`，并把该文件的 sha256 记进执行回执。内容作者仍然是 reviewer，runner 只是中立的落盘器。

这是对 `## reviewer` 一条的**有意识偏离**，理由是：允许写、只靠提示词要求「只写 20-review.md」的隔离，
远弱于内核级/工具级的真只读；而转录留下的 sha256 让「事后改动复审结论」变成可检出。
用 `agent-protocol role-verify <task-id>` 重算比对即可发现漂移。

这条偏离**不放松任何其它规则**：reviewer 依然默认只读、依然不得自行写 `accepted`，
依然受上面各节全部约束。dsh 没有任何只读强制能力，因此默认不得担任 reviewer。



- `controller + acceptor`：允许。
- `controller + reviewer + acceptor`：双代理模式允许，但 reviewer 必须与 executor 是不同运行实例。
- `controller + executor + acceptor`：双代理模式允许，但必须存在不同运行实例的 reviewer，且 acceptor 在复审后重新核对。
- 所有角色同一实例：仅 `single`，状态上限 `provisional`。

