# ai-stage-e-anchor-controlled — 路由与换挡记录

## 初始路由

- mode：dsh-claude
- assurance：standard
- route_revision：1
- 角色映射：controller=dsh；executor=dsh；reviewer=claude；acceptor=dsh
- 选择理由：由 CURRENT.md 继承（mode=dsh-claude，route_revision=1）

## 换挡记录

每次追加：时间、旧模式、新模式、原因、最后已验证状态、未完成项、新 controller。不得删除旧记录。

### 2026-09-19 暂停（用户指示：优先处理亮度反馈）

- 状态：执行报告 `10-execution.md` 已完成、冻结探针 **PASS**；**未拉 reviewer、未写 30-acceptance、未 close**。
- **核心问题状态：无法判定（待复核）**。生产分块 600 下：既有观测 6 次 `ANCHOR ambiguous + changed chunk=1`（含修复构建，见验收中的构建同一性字段）；本任务另有一次尝试，其后状态由『未处理』变为『已就绪』，但**该次是否真的发出请求、消耗几块，均未获独立证明**（缺『清缓存后触发前』的同身份读数）。因此**不得**把它当作『一次通过』与既有失败对比，也**不得**据此断言『间歇性』或『已修复』。
- **可安全承接的事实**：①代码侧修复已复审通过并提交（`6a7a2143a`）；②失败方向为整章失败关闭、保留原文（仅在 `chunk=1` 场景有直接证据）；③恢复时的首选假设见《维护计划书》待办池首块。
- **严禁承接的表述**：『已确证间歇性失败』『修复在生产配置下已验证有效/无效』——两者都缺决定性读数。
- 本任务已消耗请求：**6 块**（自用户授权起合计 7 块）。
- 设备侧：分块仍为 600、超时 120、recordLog 关闭，无残留改动。
- 恢复步骤：拉起 reviewer → 写 `30-acceptance.md` → `close ai-stage-e-anchor-controlled`。
- 未闭合缺口：成功路径无固定字段（`ai-ok`/`ai-protocol` 不存在）；『模型未给上下文』与『组合仍不唯一』无法区分；D2 未跑。