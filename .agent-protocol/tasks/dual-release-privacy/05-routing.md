# dual-release-privacy — 路由与换挡记录

## 初始路由

- mode：triad
- assurance：high
- route_revision：7
- 角色映射：controller=codex；executor=dsh；reviewer=claude；acceptor=codex
- 选择理由：由旧 CURRENT.md 继承；随后发现生成器回报与落盘不一致，未开工。

## 换挡记录

### route_revision 2 — 纠正错误指针

- 事实：`agent-protocol task ... -TaskClass high -Activate` 回报 Activated=True/high，但 `CURRENT.md` 未切换且新任务仍为 draft/standard；紧随其后的换挡误写入已冻结 AI 任务。
- 处置：controller 恢复 AI 任务路由文件原文，人工把 CURRENT 指向本任务；不把误写路由当作 AI 新结论。

### route_revision 3 — triad

- changed_at：2026-09-19T22:19:56+08:00
- old_mode：codex-claude
- new_mode：triad
- reason：AI 实验功能保持封存；新任务仅调整双版本发布，按高风险发布配置由 Codex 主控验收、DSH 执行、Claude 只读复审。
- 角色映射：controller=codex；executor=dsh；reviewer=claude；acceptor=codex

### 复审后扩围 — releaseS beta 更新识别

- Claude 首轮复审确认 CI 矩阵改动本身正确，同时指出 `AppVariant.isBeta()` 未包含 `BETA_RELEASES`；controller 读取消费端后确认这会让 releaseS 默认走 `/releases/latest`，随后又按 `BETA_RELEASES` 过滤而取不到更新。
- 扩围只增加 `AppReleaseInfo.kt` 与一个新的 JVM 回归测试；先复现、后修正。AI 功能继续封存，发布/提交/推送仍禁止。

### route_revision 4 — 产品身份纠偏与 A/B 阶段重冻结

- changed_at：2026-09-20T14:16:47+08:00
- mode/角色不变：triad；controller=codex、executor=dsh、reviewer=claude、acceptor=codex。
- 原因：用户确认旧自用安装身份是 `io.legado.app.tjf.release`，必须由 private 版本原地覆盖保留数据；新 normal 版本使用基础包名 `io.legado.app.tjf`；`.releaseS` 不属于正式发布身份。
- 当前只授权 A/B：controller 重冻结范围，executor 只改 `AppReleaseInfoTest.kt` 与 `10-execution.md`，留下可复现红测；生产源码、Gradle、Manifest、CI 与资源文件只读。
- 旧两轮 reviewer 与旧 acceptance 全部 superseded，但保留原文和 rounds 审计记录；新一轮复审从未来 C 阶段重新计数。
## Route revision 5

- changed_at: 2026-09-20T15:48:22+08:00
- old_mode: triad
- new_mode: claude-triad
- reason: 用户要求将 dual-release-privacy 迁移到 Claude 主导；保持 high 风险档，不降级保证：Claude=controller+acceptor，DSH=executor，Codex=独立 reviewer；下一阶段先执行 C+D，E/F 仍需另行授权。
- new_controller: claude
- new_executor: dsh
- new_reviewer: codex
- new_acceptor: claude
## Route revision 6

- changed_at: 2026-09-20T15:51:52+08:00
- old_mode: claude-triad
- new_mode: dsh-claude
- reason: Codex 当前额度不可用；用户明确要求改用 Claude 与 DSH。任务当前阶段风险档由 high 降为 standard：DSH=controller+executor+acceptor，Claude=独立 reviewer；C+D 可继续，E/F、提交、推送、发布仍需另行授权。
- new_controller: dsh
- new_executor: dsh
- new_reviewer: claude
- new_acceptor: dsh
## Route revision 7

- changed_at: 2026-09-21T09:38:24+08:00
- old_mode: dsh-claude
- new_mode: triad
- reason: 用户于 2026-09-21 明确要求 Codex 对已完成 A-D 开展发布级验收；恢复 Codex controller+acceptor、DSH executor、Claude reviewer 的 high 路由，实际提交/推送/发布仍未授权
- new_controller: codex
- new_executor: dsh
- new_reviewer: claude
- new_acceptor: codex