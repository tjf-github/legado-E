# book-source-p1-diagnostics — 路由与换挡记录

## 初始路由

- mode：dsh-claude
- assurance：standard
- route_revision：8
- 角色映射：controller=dsh；executor=dsh；reviewer=claude；acceptor=dsh
- 选择理由：由 CURRENT.md 继承（mode=triad，route_revision=7）

## 换挡记录

每次追加：时间、旧模式、新模式、原因、最后已验证状态、未完成项、新 controller。不得删除旧记录。
## Route revision 8

- changed_at: 2026-09-24T22:12:58+08:00
- old_mode: triad
- new_mode: dsh-claude
- reason: 人类于 2026-09-24 在 DSH 窗口直接指派 P1 第三轮返工。按 MODES.md 入口规则，mode.controller 必须等于人类启动的 agent 品牌（本次为 DSH），原 triad/route_revision 7 的 Codex controller 本机没有运行实例，无法拉起 reviewer 与完成验收；Claude 仍为独立只读 reviewer。原始 P1 任务证据目录保持只读，不改其任何角色原文。
- new_controller: dsh
- new_executor: dsh
- new_reviewer: claude
- new_acceptor: dsh