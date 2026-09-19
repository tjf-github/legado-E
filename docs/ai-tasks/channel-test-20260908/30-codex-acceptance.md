# channel-test-20260908 — Codex 独立验收

状态：`accepted`（仅协作通道）

## 实测结果

- Claude Code `2.1.260` 在隔离目录中以 restricted + Read-only 方式成功读取任务文件，返回正确标题及完整第二意见；完整复审无 permission denial。
- 控制提示只包含角色、`00-task.md` 和交付要求，没有嵌入源码或任务正文。
- 尝试让 restricted Claude直接写文件时，Windows连续返回 `EPERM`；该路径失败关闭，没有产生项目改动。
- 因此正式通道采用“Claude只读共享文件、结果经 CLI 返回、Codex宿主写回”的结构，不为了直接写回而开放仓库权限。
- Claude复审中的 Unicode、重叠锚点、完整保护区间、offset交叉校验和上下文限长具有实际价值；noop/null字段策略仍由 Codex在后续 B1 任务中决定。

## 完整性边界

- 本次未修改生产源码或测试；
- 未执行 Gradle、真机、提交、推送或发布；
- AI正文净化阶段 E 仍为 `review / NOT READY`；
- 本判定只证明协作通道可用，不证明 Claude可替代 DSH执行或 Codex验收。

## 固化结论

采用 `docs/ai-tasks/<task-id>/00/10/20/30` 四文件协议。DSH负责有限实现和证据，Claude负责只读第二意见，Codex负责范围控制、宿主写回及唯一最终验收。

