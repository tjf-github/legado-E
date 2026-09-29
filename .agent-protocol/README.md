# Portable Agent Collaboration Protocol

状态：`active`

本目录是当前项目的文件化多代理协作入口。项目自己的 `AGENTS.md`、安全政策、构建门禁和发布流程优先于本协议。

## 开工顺序

1. 首次导入且尚未跑过合成通道测试时，先按 `MIGRATION.md` 完成它；未通过不得创建真实业务任务。
2. 读取项目根 `AGENTS.md`。
3. 读取本目录 `PROJECT.md`，确认项目专属事实已填写。
4. 读取 `CURRENT.md`，确认当前任务、模式、角色映射和证据目录。
5. 读取当前任务 `00-task.md`；只在白名单和授权操作内工作。白名单以 `executor_write` 行为机器可读来源，runner 的 `agent-protocol scope-check <任务>` 据此做全集越界检查。
6. 将结果写入自己的角色文件；不得修改其他角色原文。

## 合成通道测试

导入后、创建真实任务前必须跑一次。它只证明「通道与数据边界可用」，**不证明任何业务功能已验收**。

- `New-AgentProtocolChannel.ps1` 生成一次性 nonce，只写进合成任务的 `00-task.md`；校验器也从任务书现场派生期望值。`.channel-test.json` 只存时间、预期文件和协议哈希，**不存 nonce 或期望派生值**，因此 Glob/Grep 到工具元数据也拿不到答案。
- 派生规则是定长抄写（不需要计算工具）：把 nonce 分成前后两个 16 位半串，按 `前半 + 角色标签 + 后半 + 角色标签` 拼成 64 位小写 hex；标签为 `executor=eeeeeeeeeeeeeeee`、`reviewer=bbbbbbbbbbbbbbbb`、`acceptor=aaaaaaaaaaaaaaaa`。校验器只接受唯一、独立的严格字段行；63/65 位、值内空格、散文夹值或完整 nonce 泄漏都会失败。
- 每个角色还要回显上一环的值（reviewer 回显 executor、acceptor 回显 reviewer），以证明是真实接力而不是一次性补写。
- 角色证据不得复述 nonce、分别展示后又把两个半串相邻写出，或展开拼接过程；这些写法会重新泄漏完整 nonce。泄漏判定的权威定义是角色文件是否出现完整 32 位明文 nonce；正确派生值必然各含一个前后半串，不能把半串命中误判为泄漏。只保留最终严格字段和必要事实。
- `Test-AgentProtocolChannel.ps1` 校验派生值、明文 nonce 是否泄漏、`10 ≤ 20 ≤ 30` 的写入顺序，以及测试期间协议自身文件哈希是否漂移。
- 若 `init -Update` 或合法探测回填发生在合成任务创建之后，清单会显式标记 `baseline_status: stale` 并说明原因；工具不会重算整表去吞掉其它漂移。此时应新建合成通道任务。
- 稳定模板指纹改变时，`init -Update` 会把旧 `channel_test: passed@...` 降为 `not-run`；已有真实任务占用 `CURRENT.md` 时不会被合成任务抢占，待该任务收口后重验，或复用同指纹的用户级提供方证明。

## 稳定角色

- `controller`：冻结范围、选择模式、维护当前指针和处理换挡。
- `executor`：在授权范围内实现或验证，提交真实 diff 与命令证据。
- `reviewer`：独立查找反例、遗漏、安全问题和证据缺口；默认只读。
- `acceptor`：核对实际工作树、测试和可见行为，作出不可由 controller 改写的独立判定；controller 在回执落盘后按 `CLOSEOUT.md` 作最终收尾处置。

代理品牌只是当前路由。允许一个代理承担多个角色，但必须遵守 `MODES.md` 的隔离和状态上限。

## 角色拉取

控制代理不靠自己转述别的角色，而是**自己把角色拉起来**：人类启动的那个 agent 就是 controller，它用
`agent-protocol role <executor|reviewer|acceptor>` 通过提供方 CLI 把其它角色作为后台子代理跑起来，
交接仍然只走项目文件。controller 不能拉它自己——它已经在那儿了，请求 `controller` 会被直接拒绝。

在 controller=claude 的模式下（例如 `claude-triad` / `claude-codex`；查 `CURRENT.md` 的 `controller` 行），`-Entry` 必须传 controller 品牌：

```bash
agent-protocol role reviewer -Entry claude     # 拉起复审
agent-protocol role executor -DryRun           # 先看实际要执行的命令，不执行
agent-protocol review-probe <task-id> -RequestedByRole executor # 在一次性 Git worktree 跑冻结命令；同键复用
agent-protocol role-verify <task-id>           # 事后检出复审结论有没有被改过
agent-protocol scope-check <task-id>           # 全集枚举 − 显式排除：runner 越界检查（只读）
agent-protocol close <task-id>                 # 校验证据并收口，不自动提交
```

角色提示词放在本目录 `prompts/<角色>.md`，随模板安装并计入协议指纹：**改提示词即改变指纹**，
已发出的通道能力证明随即失效（这是设计意图，不是缺陷；下次 `init` 会重跑合成接力）。

怎么无头调用某个提供方是**机器事实**，不是项目策略，所以放在用户级 `~/.agent-protocol/providers.json`
（`Install-AgentProtocolProviders.ps1` 生成，`agent-protocol doctor` 分层检查）。真实 `role` 启动前会复用同一 preflight：结构、提供方声明的用户级必写目录、真实 route probe；**成功**的 route probe 按主机与提供方配置指纹缓存 30 分钟，失败不缓存、下次重探。缓存只在用户级 `~/.agent-protocol/preflight-cache.json`，不进项目、不进模板。
表缺失或字段仍是 `TODO` 时 runner 拒绝执行并指出该填什么，不猜、不静默降级。
调用表还可声明用户级 `environment_source`：runner 只读取白名单变量并直接传给提供方子进程，用来覆盖
DSH、IDE 或旧终端继承的过期路由；它不改父进程或全局环境，凭据值也不进入项目与回执。
`note` / `requirements` 是用户级自由文本，不得写凭据或用户数据；runner 只提示查看字段，不把正文回显到终端日志。

### 证据是怎么写出来的

| 角色 | 子代理权限 | 证据由谁落盘 |
|---|---|---|
| `executor` / `acceptor` | 项目写权限 | 子代理自己写；runner 校验文件确实前进了，没前进判定失败 |
| `reviewer` | 只读 | 子代理只把报告打到 stdout；runner 保留报告正文并加机器绑定封套，转录为 `20-review.md` 后记 sha256 |

三家的只读强制力**不对等，回执如实记录**，不许当作等强：codex 是内核级拒绝写盘（`kernel-sandbox`），
claude 是工具策略（`tool-policy`），dsh **没有任何强制**（`none`）——因此 dsh 默认被拒绝担任 reviewer。
`-AllowUnenforcedReviewer` 是逃生阀，只在明知代价时使用。

每次拉起都会留下 `tasks/<task-id>/15-<角色>-raw.md`：round/task/snapshot 绑定、解析后的完整命令、提示文件、会话标识、
起止时间、耗时、退出码、只读强制等级、实际路由 / 模型 / 凭据状态、证据 sha256、协议指纹，
以及 stdout/stderr artifact 的哈希和有界摘要。完整日志由 runner 搬入 `temp-artifacts/`，后续角色不重读长日志。
每次角色调用和 review-probe 都创建唯一系统临时根，并在 `temp-manifests/` 登记创建者、用途、绝对解析路径、保留要求、artifact 哈希与清理状态。子进程只使用 `AGENT_PROTOCOL_TEMP_ROOT`；不得在仓库根自建缓存、长日志或复现目录。成功、失败和 timeout 都统一清理，失败会保留待清理状态；删除前严格核对登记路径，禁止宽泛递归删除。

回执还记录捕获文本的字符数、UTF-8 字节数和 sha256；reviewer 捕获结果异常短时会告警。同一角色再次运行前，runner 把上一轮文件复制到 `rounds/<角色>-rNNN/`，顶层旧报告改成 `SUPERSEDED` 指针；报告封套与回执共同绑定当前轮，`role-verify` 会递归校验。新回执同时绑定机器摘要 sha256、findings 前后 sha256 与轮次；旧回执缺字段时标记 `legacy-unbound`。升级前已归档且哈希仍一致、但没有新封套的历史成功轮只显示为 `LEGACY`（保证上限 `legacy-hash-only`）；顶层当前轮不享受兼容。超时另写 `partial-<角色>-manifest.json`，记录机器可见的部分事实，但不能替代角色报告。

reviewer 报告用 `<agent-protocol-report>...</agent-protocol-report>` 定界；没有完整块时回执标记 `fallback-unverified`。runner 在 reviewer 调用前后钉住任务书、执行报告与业务 Git 快照，发生变化就把本轮判为 `stale`。

如果 `json-field:result` 只剩报告后的补充消息，runner 会从同一份结构化 stdout 中恢复最后一个完整报告块，并在回执记录 `capture-fallback:stdout`；找不到完整块时不会把短句冒充完整复审。

`06-review-commands.json` 保存冻结的 `file + args[]` 命令。`agent-protocol review-probe` 先生成 `.review-bundle.json`，把当前 HEAD 与未提交最终文件状态绑定成 hash；只有逐文件应用到一次性 detached Git worktree 并核验成功后才运行命令，`18-review-probes.md` 记录 `applied_bundle_hash`。应用失败就阻塞，不能退回只测 HEAD。它不给 reviewer 任意 Shell，也不是内核级文件系统/网络沙箱，证据必须如实记录实际强制力。ignored 正式文件在单文件 ≤ 4 MiB 且集合 ≤ 64 MiB 时仍全量复制；超阈值时只按门禁声明的 `ignored_inputs` 复制，其余保留集合摘要与数量，报告写 `declared-only-no-read-monitor`。

耗时门禁的 runner 事实写进任务级 `.gate-ledger.json`，键至少绑定 snapshot、已核验 review bundle 摘要、argv、environment、runner；bundle 摘要区分 ignored 文件实际复制范围。完整 stdout/stderr 放在 `gate-artifacts/` 并验 hash。同键后续角色复用事实但仍须独立判断；损坏账本或 artifact 一律拒绝复用。命令可声明 `environment`（敏感项只记 set/unset），high 任务可用 `independent_roles` 要求指定角色形成独立执行范围；reviewer 要求重跑时由 controller 使用 `-GateName <name> -ForceGate`。账本不保存任何角色结论，`requested_by_roles` 也不是身份签名。大 ignored 集合需在同一文件声明 `ignored_materialization_policy: declare-on-large`（模板默认），并给会读取 ignored 的命令写 `ignored_inputs` 与 `materialize_ignored: true`；`-MaterializeIgnored all` 是显式逃生阀。
argv 数组会原样进入任务证据以便复算，禁止把凭据值放进参数；应改用受控环境或工具自己的安全存储。

命令自身应输出 UTF-8。runner 仅在环境未显式设置时补 `PYTHONIOENCODING=utf-8`；Windows 上的 `python -c` 仍建议加 `-X utf8`，避免中文转录依赖父终端代码页。

冻结覆盖业务 Git 工作树（含 ignored 正式文件的路径与内容）、协议目录除 `tasks/` 外的文件、`00-task.md`、`06-review-commands.json`、`10-execution.md`，以及项目根的 `.gitignore`、`AGENTS.md`。逐文件 SHA-256 写入新门禁账本条目的 `frozen_inputs`；探针报告列出快照定义域。任务目录内其他证据文件（如 `18-review-probes.md`、`30-acceptance.md`、`rounds/**`）可在复审后写。ignored 集合摘要用于发现冻结窗口内的变动；但**集合摘要与 pathspec 收窄查询都不能证明未越界**（pathspec 只能缩小输出，不能证明补集为空）。未越界证明必须是「全集枚举 − 显式排除项」，由 runner 的 `agent-protocol scope-check <任务>` 完成：`git -c core.quotepath=false status --porcelain=v2 -z --untracked-files=all` 枚举全集，减去 `.agent-protocol/tasks/**`、`CURRENT.md`、`ARCHIVE.md`、`.agent-protocol-backups/**` 与任务书 `executor_write` 授权，输出 `pass / violations / not-configured`；`review-probe` 与 `close` 把结论写进 gate ledger、探针报告与 `40-archive.json`，`close` 在 `accepted` 且有 violations 时阻塞。`doctor` 会如实报出本项目的越界检查能力档（`none` / `declared-gate` / `runner-check`）。reviewer 不给任意 Shell，结论只能基于它读到的内容；缺 `write_scope_check` 时写 `NOT RUN`。`snapshot_exclude` 只让列出的 ignored 生成物不再散列内容，其路径集合与规则摘要仍进快照与越界检查，不构成越界豁免。旧账本的快照定义域不可事后改写，缺诊断的历史失败子条件保持 `unknown`。提交以及修改 `CURRENT.md`、`PROJECT.md` 等项目级文件必须放到 `close` 之后，否则 HEAD 或业务快照变化会导致收口失败。

**实际路由必须记**（MODES.md 的要求）：一个品牌的 CLI 经网关转发后，命令完全一样而结局不同，
所以回执写的是 `ANTHROPIC_BASE_URL=` 之类的真实取值，而不是「大概在用官方端点」。观测哪些变量由
用户级调用表的 `route_env` / `secret_env` 声明；凭据类**只记已设置/未设置，绝不写值**。没声明就写
「未声明，无法观测」。观测对象是最终传给提供方子进程的环境：若调用表声明 `environment_source`，
白名单来源值优先；否则才使用 runner 继承的环境。

### 每轮的上下文包与机器摘要

每轮 runner 生成 `.agent-protocol/tasks/<任务>/13-<角色>-context.json`：写明这一轮该读哪些文件（带 sha256）、
哪些历史默认不读（`rounds/**`、其它任务的报告、`15-*-raw.md`）、角色契约与协议稳定文件的 sha256，
以及规则相对上一轮有没有变化——**规则没变就不要重读协议本体**。

角色正文之外要附一段 `<agent-protocol-summary>` JSON；runner 把它提取成 `10-execution.summary.json` /
`20-review.summary.json` / `30-acceptance.summary.json`，并把发现登记进 `findings.json`（稳定 ID `F-0001`…，
下一轮只引用 ID）。关闭旧发现时使用 `resolves: [{"id":"F-0001","evidence":"..."}]`；非提出方没有 evidence 的关闭只能记为 `claimed_resolved`，`findings[].status` 也不能绕过这条规则。提取不到就如实标 `source=runner-fallback`，不替角色写结论。
`close` 自动写短归档 `40-archive.md` / `40-archive.json`（含各角色顶层轮次、机器摘要哈希、校验结果及 gate ledger 状态/摘要），并追加一行到 `.agent-protocol/ARCHIVE.md`。acceptor 自身回执须在其报告完成后由 runner 生成，回执落盘后的 `role-verify` 由 controller/`close` 核对；不因此重复拉起 acceptor。独立 acceptor 判 `rework` / `blocked` 时原文保留，产品已验证且保证债务可登记时，controller 可按 `CLOSEOUT.md` 另写 `35-controller-closeout.md`，只以 `provisional / ready + partial` 关闭；机器归档同时记录原判与处置。损坏的 findings 标为 `unreadable`，损坏的旧归档拒绝覆盖；归档、索引与 `CURRENT.md` 可回滚发布。

归档字段口径：`review_rounds` = **有效复审轮**（回执结果为 ok）；`review_attempts` = 有效轮 + 中止轮；`review_aborted_rounds` 列出 `superseded-rounds/<轮次 id>-aborted/`（`round_counted=false`）。中止轮不占复审配额。

角色开工时 runner 先写 `12-<角色>-checkpoint.json`，角色在长操作前补充计划文件/命令。任务档为 finalize 预留 60–120 秒；进入窗口后停止新增工作和长门禁，优先写最小报告。声明 `finalize_delivery=control-file` 的 provider 包装器会收到结构化 finalize 指令；不支持注入的 provider 必须采用“先更新检查点，再启动长操作”的策略。超时后的 `partial-<角色>-manifest.json` 只记录 runner 事实，并把产品状态（默认 unknown）与保证状态（partial）分开，绝不把未封套报告冒充当前轮结论。

### 这个机制证明不了什么

回执能让人去 `claude --resume <会话号>` / `codex exec resume <id>` 复核，也能让事后改动复审结论变得可检出，
但它是**审计与威慑，不是密码学证明**：控制代理持有 nonce 且对三个证据文件都有写权限，
所以没有任何纯文件校验能区分「它真的拉了人」和「它自己代笔」。真正起作用的是结构性隔离——
一个真正不同的模型、一个真正新鲜的上下文。别把回执读成比实际更强的保证。

## 每项任务

用 `agent-protocol task <id> -TaskClass <light|standard|high> -Activate` 从 `task-template/` 生成 `tasks/<task-id>/`。脚手架先在临时目录生成和校验，再原子发布并重新读取 `CURRENT.md` 后置条件；失败恢复原字节。`light` 默认不拉外部 reviewer，`standard` 要求 executor/reviewer 不同运行身份，`high` 要求 executor/reviewer/acceptor 三个判断环节相互独立。激活和换挡都会机器校验 mode 兼容性。

```text
00-task.md
05-routing.md
06-review-commands.json
10-execution.md
12-<角色>-checkpoint.json（开工与 timeout 恢复检查点；不是角色结论）
18-review-probes.md（配置了命令并运行后生成）
.review-bundle.json（运行 review-probe 后生成）
.gate-ledger.json / gate-artifacts/（门禁 runner 事实与原始输出；不是角色结论）
temp-manifests/ / temp-artifacts/（临时根登记、清理状态、完整日志 artifact；角色只消费哈希与有界摘要）
partial-<角色>-manifest.json（角色超时后生成，不是角色结论）
20-review.md
30-acceptance.md
```

原始子代理输出可增加 `15-<agent>-raw.md`，但不得覆盖执行报告或复审报告。由 runner 拉起角色时还会生成
`14-<角色>-prompt.md`（实际发出去的角色提示词，可复核发了什么）与 `15-<角色>-raw.md`（执行回执）。

## 状态机

```text
draft -> ready -> executing -> review -> accepted
                                  |       |
                                  |       `-> closed
                                  `-> rework -> executing

single mode: draft -> ready -> executing -> provisional
```

`accepted` 至少需要相应任务档要求的执行、复审和 acceptor 核对，并在 `30-acceptance.md` 同时满足 `product_state=ready` 与 `assurance_state=complete`。产品已正确但证据未闭合时写 `ready + partial`，不要误写成产品返工。所有未运行项必须写 `NOT RUN`。

## 换挡

额度耗尽、服务不可用或工具缺失时：

1. 当前实例停止新增业务改动；
2. 在 `05-routing.md` 记录原因、最后已验证状态、未完成项和新模式；
3. controller 更新 `CURRENT.md` 的模式、角色映射和 `route_revision`；
4. 新实例只从文件恢复上下文，并重新核对工作树；
5. 不把旧实例的自述当作新实例已验证的事实。

推荐使用 `agent-protocol mode <mode> -Reason '...' -TaskId <id> -ExpectedTaskId <id> -ExpectedRouteRevision <n>` 完成带 compare-and-swap 的换挡。工具只允许修改 CURRENT 指向的活动任务，并在返回前核验 `CURRENT.md`、`05-routing.md`、`00-task.md` 一致；失败回滚。若目标环境不能运行 PowerShell，人工修改也必须同时更新这些事实。

## 收尾与清理

每个任务 `close` 之后、每个阶段验收之后，按 `CLOSEOUT.md` 走一遍清单：事务式状态/归档、证据与 `role-verify`（升级前历史轮只允许显式 `LEGACY`）、披露、清理分类、索引与提交边界。它同时规定信息分层与预算——注入层只加索引行，历史进复盘与归档摘要——这样新窗口只读少数文件就能接上，不依赖任何会话记忆。

## 扩权与恢复

授权范围不足时，由 controller 确认后运行 `agent-protocol scope-expand <task-id> -ExpectedTaskId <task-id> -ExpectedRouteRevision <n> -ExpectedTaskDigest <sha256> -Reason '原因' -ScopeFile <UTF-8-JSON>`；可先加 `-DryRun`。JSON 使用 `version:1`，可选路径数组为 `executor_read`、`executor_write`、`reviewer_read`、`acceptor_read`，只列明确的项目相对业务文件。新增路径追加到任务书，原有禁止项、外发与提权边界优先；不会自动升级任务档或换模式。任务的初始写白名单由 `agent-protocol task -ExecutorWrite <项目相对文件>` 写入，`scope-expand` 追加同格式的 `executor_write` 行。

工具事务式发布任务书、`.scope.json` 与 `13-<角色>-scope.json` 预备包，递增 scope revision 并记录原因/快照，保留旧角色上下文与证据。预备包不是已执行轮次；下次 `role` 仍重新生成上下文。任务/路由/任务书摘要 CAS 不匹配就拒绝；共享互斥锁阻止协议写入并发。发布异常回滚，强制中断留下的 `.scope-pending.json` 会阻止继续写入。

`agent-protocol recover <task-id> -Json` 只读输出 `next_action`，执行该项后重新诊断。timeout 先核对检查点和 partial manifest；缺哪项补哪项，失效哪项重跑哪项。门禁恢复默认利用 gate ledger，不强制整轮重跑。`deferred_actions` 仅是后续待诊断项，不是执行清单。已有角色报告不因恢复命令变成 accepted；扩权后旧 acceptance 不得直接收口。无新回执的 preflight 故障需结合当次错误诊断，恢复计划不推断隐藏的 provider 状态。

## 数据边界

提示参数只携带角色、文件名、输出格式和权限边界。代理读取文件仍属于把数据交给对应模型处理，因此任务书必须声明目的地、模型/提供方、文件白名单和禁止数据。凭据、签名材料、真实用户内容和未脱敏日志默认禁止外发。
