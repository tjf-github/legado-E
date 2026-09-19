# ai-stage-e-device — 路由与换挡记录

## 初始路由

- mode：dsh-claude
- assurance：standard
- route_revision：1
- 角色映射：controller=dsh；executor=dsh；reviewer=claude；acceptor=dsh
- 选择理由：由 CURRENT.md 继承（mode=dsh-claude，route_revision=1）

## 换挡记录

每次追加：时间、旧模式、新模式、原因、最后已验证状态、未完成项、新 controller。不得删除旧记录。

### 2026-09-19 授权追加（**非换挡**：mode 与 route_revision 均不变）

- 时间：2026-09-19，真机复测进行中。
- 触发：执行到第 2 个样本后，**人类当场追加指定第三个测试样本**（"我已换到 948 章，这一章最开始有重复标题可以作为测试"），使实际处理样本数为 3，超出任务书「1–2 个极短章」的原范围。
- 范围与上限：目的地与模型**不变**；**真实请求上限 3 次不变**（已确证 3 块，另 1 次计数未当场读取）；证据仍只记固定脱敏字段。
- 同日 controller 补充授权（已写入 `00-task.md`）：为把请求数压进上限，临时把分块上限 600 → 6000；处理完成后**已还原为 600 并复核**。
- 最后已验证状态：构建、安装（保留数据）、临时打包改动还原、探针冻结命令均 PASS；三个样本结果见 `10-execution.md`。
- 未完成项：**锚点路径未被证明**（`recordLog` 关闭，无固定字段证据）；D2 发布矩阵未跑。
- controller 不变（dsh）；route_revision 不变（1）。

