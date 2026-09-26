# book-source-p1-diagnostics — 被取代说明（非 close）

- 状态：`rework`（2026-09-23 acceptor 判定「本轮 P1 不通过，不进入 P2」）。此后本目录**未再修改**，没有 `40-archive.json`，也**不在** `.agent-protocol/ARCHIVE.md`——该索引由 `agent-protocol close` 按各任务 `40-archive.json` 重建，手工加行会被下次重建冲掉，所以本说明写在任务目录内。
- P1 的后续轮次以新任务继续：`book-source-p1-rework-r3`（2026-09-24，`provisional`）与 `book-source-p1-device`（2026-09-24，`accepted`）。**本目录的结论只到 2026-09-23 的 rework 为止**；其中 NOT RUN 与收窄口径已被上述两个任务覆盖/收窄，读结论请以 `../book-source-p1-device/30-acceptance.md` 为准。
- `agent-protocol role-verify book-source-p1-diagnostics` → **FAIL（1 项）**：
  `15-executor-raw.md -> 10-execution.md` 报「顶层轮次不是最新一轮：round_id=executor-r001, expected=executor-r002」。
  根因是结构性而非业务性的：本轮 executor 证据不是 runner 封套写出（该事实已在 `../book-source-p1-rework-r3/findings.json` F-0006 登记），同一份回执此后又被整轮复制进 `rounds/executor-r001/`，于是顶层 r001 与已归档 r001 并存；校验器按 `maxArchived+1` 期望顶层为 r002，必然失配。
  **证据哈希本身 PASS**：`rounds/executor-r001/15-executor-raw.md -> 10-execution.md`、`15-reviewer-raw.md -> 20-review.md` 与 `rounds/reviewer-r001/*` 三项一致。
- 处置：不改写任何角色原文（`README.md` 数据边界），也不为凑 PASS 重排/复制目录。本说明与《书源与解析优化计划书》§7 P1「收尾记录」、《维护计划书》待办池 2026-09-26 块共同构成披露。
