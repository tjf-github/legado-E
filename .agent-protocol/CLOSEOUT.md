# 收尾与清理规则

- 状态：`active`
- 适用时点：任何 `agent-protocol close` 之后；任何阶段或工作包验收之后。
- 本文件回答一件事：**跑完之后，仓库怎么保持干净、状态怎么保持可续、经验怎么不占注入层预算。**

## 1. 信息分层与预算

| 层 | 位置 | 放什么 | 不放什么 |
|---|---|---|---|
| 注入层 | 项目 `AGENTS.md`（建议 ≤60 行） | 每个新窗口开工必知的可执行规则与索引 | 历史、复盘、逐次交接记录 |
| 状态层 | `.agent-protocol/CURRENT.md`（`close` 自动写）+ 项目自己的状态文件 | 当前任务/模式/角色映射；产品与阶段进度、唯一下一动作 | 已闭环任务的逐条状态 |
| 导航层 | 项目 `README.md` 与项目地图 | 读什么、目录在哪、怎样才算完成 | 进度事实（指向状态层） |
| 证据层 | `.agent-protocol/tasks/**`、阶段报告目录、决策记录 | 执行/复审/验收原文、冻结探针、回执 | 临时副本、草稿 |
| 归档层 | Git 历史 + 归档摘要 | 恢复命令 `git show <commit>:<path>` 与压缩理由 | —— |

规则：**只有「每个新窗口都必须知道的一行」才升到注入层**；其余新经验进复盘、流程书或归档摘要。

每轮的角色上下文由 runner 生成（`.agent-protocol/tasks/<任务>/13-<角色>-context.json`）：历史长报告默认不进上下文，需要历史时读 `.agent-protocol/ARCHIVE.md` 的一行索引与对应任务的 `40-archive.json`。

## 2. 任务 `close` 之后的清单

1. **指针与双状态**：`CURRENT.md` 的 `state` / `last_closed_task` 由 `close` 写；最终状态取自独立 `30-acceptance.md`，或受限 provisional 收尾时的 `35-controller-closeout.md`。`accepted` 必须是 `ready + complete`；产品已好但证据未闭合应如实写 `ready + partial`，不要混成“代码仍需返工”。
   - acceptor 的报告与回执由 runner 顺序生成；acceptor 在写自己的报告时无法检查尚不存在的本轮回执。`role-verify` 是 controller 在回执落盘后执行的收尾检查，`close` 也会重跑。acceptor 应检查当时已有的证据，并把尚未到时点的检查写作 `NOT RUN（回执待落盘）`，不得因此反复重启同一角色。
   - 独立 acceptor 判 `rework` / `blocked` 时原文必须保留。若产品门禁已过、剩余是可明确登记的保证证据债务，controller 可另写 `35-controller-closeout.md`：单独一行 `` `provisional` ``，再逐行填写 `product_state` 为 `ready`、`assurance_state` 为 `partial`、`acceptor_sha256` 为 `30-acceptance.md` 原始字节 SHA-256、`unresolved_findings` 为发现 ID 列表，以及具体的 `next_action` 和 `rationale`；字段行格式与 `30-acceptance.md` 相同。`close` 要求独立 acceptor 成功回执、严格 `role-verify` 与稳定业务快照；机器归档同时保留 acceptor 原判和 controller 处置。此路**只能关闭为 provisional**，不能宣称独立验收 accepted。
   - 复审冻结的是业务输入：Git 工作树、ignored 正式文件、协议稳定文件、当前任务书/门禁清单/执行报告。`tasks/` 中的回执、探针、发现、检查点和归档是运行中生成的输出，不能整体塞入前后相等的输入快照；它们各用角色回执、文件哈希、`role-verify` 与归档摘要核对。不同摘要先核对定义域，不把 Git 分量、复合探针值和历史版本值直接相比。
2. **进度**：项目状态文件只写产品/阶段事实与唯一下一动作，不枚举已闭环任务状态。
3. **证据**：`agent-protocol role-verify <task-id>` 的顶层当前轮全 PASS；历史失败轮可显示 `SKIP`，升级前缺少新封套但哈希一致的归档轮只能显示 `LEGACY`（`legacy-hash-only`），不能把它读成新封套同等级证据；任务目录六件套齐备。
4. **归档**：`close` 已把 `40-archive.md` / `40-archive.json`、`.agent-protocol/ARCHIVE.md` 一行索引与 `CURRENT.md` 作为可回滚事务发布；机器归档记录各角色顶层轮次、校验结果与 runner 写范围结论 `write_scope_check`（`pass` / `violations` / `not-configured`），重复 `close` 不产生重复索引。`accepted` 而 `write_scope_check` 有 violations 时 `close` 直接阻塞；`snapshot_exclude` 未覆盖的集合只保留路径与规则摘要，须在披露里说明其内容不在保证范围内。长报告留在任务目录，不搬进新任务的上下文。检查索引指向的文件确实存在。
5. **披露**：换挡、降档、角色重叠、越界、`NOT RUN` 必须写进 `05-routing.md` / `30-acceptance.md`，不能只留在聊天里。
6. **清理**：按第 3 节分类执行；删除必须有任务书白名单或用户当次授权。
7. **索引**：本次新增的文档在 `AGENTS.md` 或 `README.md` 里各加一行索引。
8. **提交**：提交、推送、发布是三次独立授权；提交前先看 `git status --short` 与 `git diff --check`。
9. **交接**：跨窗口只留文件事实，不留「聊天里说过的」。

## 3. 清理分类

- **必删（临时产物，不属于任何交付物）**：系统临时目录下的副本与验证脚本、一次性 worktree、构建与编辑器缓存。runner 产物必须先登记到 `temp-manifests/`，只删除与 manifest 精确一致的唯一临时根；清理失败保留状态，不得扩大路径重试。
- **可删（需当次授权）**：被忽略、且不含跟踪文件的测试暂存目录。
- **必须保留**：`.agent-protocol/tasks/**`（含 `rounds/**` 的历史失败轮）、各阶段最终 acceptance/review、决策记录。
- **已进 Git 的中间材料**：从活动树删除即视为压缩，但必须在归档摘要里写出恢复命令。
- **未进 Git 的材料**：删除前必须取得用户授权；先确认它不是唯一证据，删除后不可恢复。

## 4. 阶段收尾比任务收尾多三件事

1. **阶段判定**：更新项目状态文件的阶段结论与下一步；未达成项写成明确债务，不用含糊表述。
2. **归档摘要**：把本次压缩掉的材料写进归档摘要（恢复命令 + 为什么可以压缩）。
3. **复盘**：新经验写进项目复盘；只有「每个新窗口都必须知道的一行」才升到注入层。

## 5. 环境坑要登记，不要靠记忆

项目把本机特有的坑（某种临时目录不可用、某个命令被沙箱拒绝、测试会留下不可删除的目录等）写进项目自己的流程书，并在注入层或状态层留一行索引。踩坑不登记，下一个窗口会重踩，并把它误判成产品缺陷。

## 6. 协议自身的维护

本文件属于协议稳定文件，改动会改变协议指纹：已发出的通道能力证明随之失效，下一次 `init` 会重跑合成接力。要让规则变好，就在协议项目里改模板、跑回归、再同步到各项目——不要只在单个项目里手改 `.agent-protocol/`。
