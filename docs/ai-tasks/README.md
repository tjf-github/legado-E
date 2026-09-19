# Codex / DSH / Claude 协作协议

状态：`archived`（历史归档，2026-09-18 起）。本目录保留 triad 时期（Codex = controller + 最终验收）的协作协议与实测证据，**不再作为任务入口**；现役协作入口是项目根目录的 `.agent-protocol/`（见 `.agent-protocol/README.md`、`CURRENT.md`、`MODES.md`，当前 mode=`dsh-claude`）。具体功能计划、发布流程和 `AGENTS.md` 的硬性规则仍优先适用。

## 1. 固定角色

- **Codex**：确定架构、拆分边界、写任务书、检查真实 diff，并作出唯一的 `accepted / rework / blocked` 判定。
- **DSH（DeepSeek Harness）**：在任务书授权范围内实现或验证，记录改动和证据；最多把任务推进到 `review`，不得自行宣布 `accepted`。
- **Claude / DeepSeek**：作为独立第二意见，重点查遗漏、反例、安全和协议边界；默认只读，不与 DSH 并行修改生产文件，也不得自行宣布 `accepted`。

三者不是可互换的执行者。任何模型的 `ready`、连接成功或绿灯报告都不能替代 Codex 独立验收。

### Codex 身份不得混淆

- **Codex 控制端**：发起当前项目任务、核对工作树与可见证据、写最终验收。
- **DSH 内 Codex 子代理**：一次性执行或审查实例，只能返回本次委托结果；即使名称同为 Codex，也没有最终验收权。

DSH 可使用一个同时暴露两个子代理的 preset，也可因宿主配置限制分成两个 DSH 会话分别调用。两种拓扑都必须保留各自工具结果和 runId；分会话测试不得伪称“同一父会话双调度”。

## 2. 每项任务的文件

从 `_template/` 复制一个任务目录：

```text
docs/ai-tasks/<task-id>/
├── 00-task.md               # Codex：边界、授权、完成条件
├── 10-dsh-report.md         # DSH：实施与验证证据
├── 15-codex-child-review.md # 可选：DSH 内 Codex 子代理原始结果
├── 20-claude-review.md      # Claude：第二意见
└── 30-codex-acceptance.md   # Codex：独立验收与下一步
```

一份文件同一时刻只由对应角色写。需要纠正另一角色的事实时，在自己的文件里追加说明，不覆盖原始报告。

`CURRENT.md` 是唯一当前任务指针，由 Codex 控制端单写。其最小字段为 `task_id / evidence_dir / state / owner`；任务关闭后必须改为 `idle` 并保留 `last_closed_task`，防止旧任务指针漂移成新任务上下文。

## 3. 状态机

常规实现任务：

```text
draft -> ready-for-dsh -> review -> accepted
                              `-> rework -> review
```

纯审查任务可从 `draft` 进入 `ready-for-claude-review`，随后仍只能到 `review`，最后由 Codex决定。未运行必须写 `NOT RUN`，不能按通过处理。

## 4. 同一工作区交接

协作材料通过任务目录交接，不由用户在不同窗口复制粘贴：

1. Codex只把已批准的文件、事实摘要和验收条件写入 `00-task.md`。
2. DSH读取任务书并把结果写入 `10-dsh-report.md`。
3. Claude以受限、只读方式读取任务目录；调用命令只传任务文件名和角色指令，不把源码拼进命令参数或 stdin。
4. Claude的结构化结果由 Codex原样写入 `20-claude-review.md`。当前 Claude Desktop 内置 CLI 的 restricted 模式直接写工作区会触发 Windows `EPERM`，因此采用宿主写回，而不开放仓库写权限。
5. Codex对照实际代码、测试、日志和可见行为，在 `30-codex-acceptance.md` 记录采纳、驳回、修正及最终状态。

每个角色的原始证据采用独立文件。宿主因权限限制代写时必须逐字保留模型结果，并同时记录工具名、runId、调用拓扑与未运行项；父代理只能新增自己的汇总，不能覆写子代理原文。未调用 DSH 内 Codex 子代理时不必创建 `15-codex-child-review.md`，不得用空文件暗示已调用。

## 5. 数据与权限边界

“不经管道传项目数据”不等于“不向外部模型发送数据”。Claude或 DSH 读取到的任务文件仍会由对应外部服务处理，因此每次任务书必须明确目的地、模型、文件白名单和数据范围。

默认禁止提供：

- API Key、Token、Cookie、认证头和真实服务凭据；
- `app/release.keystore`、`keystore.properties`、`keystore-base64.txt`；
- 真实书源、章节正文、用户缓存、账户或费用数据；
- 未脱敏日志、响应体、异常 message、URL、书名或私人数据；
- 与任务无关的工作区文件。

默认禁止外部执行者提交、推送、切分支、合并、发布、卸载应用、清数据或扩大外发范围。任何例外必须写入当次 `00-task.md`。

## 6. 验收底线

- 关键 Git 操作前重新执行 `git status -sb` 与 `git log --oneline -3`。
- Bug修复先写可复现反例，再改生产代码。
- 改动后按 `AGENTS.md` 运行 compile 和全量 JVM 单测；发布前补 lint 和发布流程要求的门禁。
- DSH/Claude报告只作线索；Codex必须检查真实 diff、反例、命令结果和需要的真机/界面证据。
- 一个阶段未完成验收、清理和同步前，不启动下一阶段。

本协议的通道实测证据见 `channel-test-20260908/`。

双子代理三方协议实测见 `tri-agent-protocol-test-20260910/`；可迁移步骤见 `MIGRATION.md`。
