# Portable Agent Collaboration Protocol

状态：`active`

本目录是当前项目的文件化多代理协作入口。项目自己的 `AGENTS.md`、安全政策、构建门禁和发布流程优先于本协议。

## 开工顺序

1. 首次导入且尚未跑过合成通道测试时，先按 `MIGRATION.md` 完成它；未通过不得创建真实业务任务。
2. 读取项目根 `AGENTS.md`。
3. 读取本目录 `PROJECT.md`，确认项目专属事实已填写。
4. 读取 `CURRENT.md`，确认当前任务、模式、角色映射和证据目录。
5. 读取当前任务 `00-task.md`；只在白名单和授权操作内工作。
6. 将结果写入自己的角色文件；不得修改其他角色原文。

## 合成通道测试

导入后、创建真实任务前必须跑一次。它只证明「通道与数据边界可用」，**不证明任何业务功能已验收**。

- `New-AgentProtocolChannel.ps1` 生成一次性 nonce，只写进合成任务的 `00-task.md`；校验器也从任务书现场派生期望值。`.channel-test.json` 只存时间、预期文件和协议哈希，**不存 nonce 或期望派生值**，因此 Glob/Grep 到工具元数据也拿不到答案。
- 派生规则是定长抄写（不需要计算工具）：把 nonce 分成前后两个 16 位半串，按 `前半 + 角色标签 + 后半 + 角色标签` 拼成 64 位小写 hex；标签为 `executor=eeeeeeeeeeeeeeee`、`reviewer=bbbbbbbbbbbbbbbb`、`acceptor=aaaaaaaaaaaaaaaa`。校验器只接受唯一、独立的严格字段行；63/65 位、值内空格、散文夹值或完整 nonce 泄漏都会失败。
- 每个角色还要回显上一环的值（reviewer 回显 executor、acceptor 回显 reviewer），以证明是真实接力而不是一次性补写。
- 角色证据不得复述 nonce、分别展示后又把两个半串相邻写出，或展开拼接过程；这些写法会重新泄漏完整 nonce。泄漏判定的权威定义是角色文件是否出现完整 32 位明文 nonce；正确派生值必然各含一个前后半串，不能把半串命中误判为泄漏。只保留最终严格字段和必要事实。
- `Test-AgentProtocolChannel.ps1` 校验派生值、明文 nonce 是否泄漏、`10 ≤ 20 ≤ 30` 的写入顺序，以及测试期间协议自身文件哈希是否漂移。
- 若 `init -Update` 或合法探测回填发生在合成任务创建之后，清单会显式标记 `baseline_status: stale` 并说明原因；工具不会重算整表去吞掉其它漂移。此时应新建合成通道任务。

## 稳定角色

- `controller`：冻结范围、选择模式、维护当前指针和处理换挡。
- `executor`：在授权范围内实现或验证，提交真实 diff 与命令证据。
- `reviewer`：独立查找反例、遗漏、安全问题和证据缺口；默认只读。
- `acceptor`：核对实际工作树、测试和可见行为，作出唯一最终判定。

代理品牌只是当前路由。允许一个代理承担多个角色，但必须遵守 `MODES.md` 的隔离和状态上限。

## 角色拉取

控制代理不靠自己转述别的角色，而是**自己把角色拉起来**：人类启动的那个 agent 就是 controller，它用
`agent-protocol role <executor|reviewer|acceptor>` 通过提供方 CLI 把其它角色作为后台子代理跑起来，
交接仍然只走项目文件。controller 不能拉它自己——它已经在那儿了，请求 `controller` 会被直接拒绝。

```bash
agent-protocol role reviewer -Entry claude     # 拉起复审
agent-protocol role executor -DryRun           # 先看实际要执行的命令，不执行
agent-protocol review-probe <task-id>           # 在一次性 Git worktree 跑冻结命令
agent-protocol role-verify <task-id>           # 事后检出复审结论有没有被改过
agent-protocol close <task-id>                 # 校验证据并收口，不自动提交
```

角色提示词放在本目录 `prompts/<角色>.md`，随模板安装并计入协议指纹：**改提示词即改变指纹**，
已发出的通道能力证明随即失效（这是设计意图，不是缺陷；下次 `init` 会重跑合成接力）。

怎么无头调用某个提供方是**机器事实**，不是项目策略，所以放在用户级 `~/.agent-protocol/providers.json`
（`Install-AgentProtocolProviders.ps1` 生成，`agent-protocol doctor` 检查），不进项目、不进模板。
表缺失或字段仍是 `TODO` 时 runner 拒绝执行并指出该填什么，不猜、不静默降级。
调用表还可声明用户级 `environment_source`：runner 只读取白名单变量并直接传给提供方子进程，用来覆盖
DSH、IDE 或旧终端继承的过期路由；它不改父进程或全局环境，凭据值也不进入项目与回执。
`note` / `requirements` 是用户级自由文本，不得写凭据或用户数据；runner 只提示查看字段，不把正文回显到终端日志。

### 证据是怎么写出来的

| 角色 | 子代理权限 | 证据由谁落盘 |
|---|---|---|
| `executor` / `acceptor` | 项目写权限 | 子代理自己写；runner 校验文件确实前进了，没前进判定失败 |
| `reviewer` | 只读 | 子代理只把报告打到 stdout；runner **原样转录**为 `20-review.md` 并记下它的 sha256 |

三家的只读强制力**不对等，回执如实记录**，不许当作等强：codex 是内核级拒绝写盘（`kernel-sandbox`），
claude 是工具策略（`tool-policy`），dsh **没有任何强制**（`none`）——因此 dsh 默认被拒绝担任 reviewer。
`-AllowUnenforcedReviewer` 是逃生阀，只在明知代价时使用。

每次拉起都会留下 `tasks/<task-id>/15-<角色>-raw.md`：解析后的完整命令、提示文件、会话标识、
起止时间、耗时、退出码、只读强制等级、实际路由 / 模型 / 凭据状态、证据 sha256、协议指纹，
以及完整原始 stdout/stderr。

回执还记录捕获文本的字符数、UTF-8 字节数和 sha256；reviewer 捕获结果异常短时会告警，controller 必须检查它是否只是补充句或被 CLI 截断。同一角色再次运行前，runner 会把上一轮 prompt、回执和证据复制到 `rounds/<角色>-rNNN/`；顶层始终代表当前轮，`role-verify` 会递归校验历史轮。历史失败调用显示为 `SKIP`，保留审计痕迹但不冒充可信证据。

reviewer 报告用 `<agent-protocol-report>...</agent-protocol-report>` 定界；没有完整块时回执标记 `fallback-unverified`。runner 在 reviewer 调用前后钉住任务书、执行报告与业务 Git 快照，发生变化就把本轮判为 `stale`。

如果 `json-field:result` 只剩报告后的补充消息，runner 会从同一份结构化 stdout 中恢复最后一个完整报告块，并在回执记录 `capture-fallback:stdout`；找不到完整块时不会把短句冒充完整复审。

`06-review-commands.json` 保存冻结的 `file + args[]` 命令。`agent-protocol review-probe` 在一次性 detached Git worktree 中运行它们并生成 `18-review-probes.md`，不给 reviewer 任意 Shell。它靠 worktree 副本和前后快照核对发现业务树漂移，不是内核级文件系统沙箱；当前网络策略也只是声明、不是强制隔离，证据必须如实记录实际强制力。

命令自身应输出 UTF-8。runner 仅在环境未显式设置时补 `PYTHONIOENCODING=utf-8`；Windows 上的 `python -c` 仍建议加 `-X utf8`，避免中文转录依赖父终端代码页。

冻结覆盖业务工作树、`00-task.md` 和 `10-execution.md`。任务目录内其他证据文件（如 `18-review-probes.md`、`30-acceptance.md`、`rounds/**`）可在复审后写；提交以及修改 `CURRENT.md`、`PROJECT.md` 等项目级文件必须放到 `close` 之后，否则 HEAD 或业务快照变化会导致收口失败。

**实际路由必须记**（MODES.md 的要求）：一个品牌的 CLI 经网关转发后，命令完全一样而结局不同，
所以回执写的是 `ANTHROPIC_BASE_URL=` 之类的真实取值，而不是「大概在用官方端点」。观测哪些变量由
用户级调用表的 `route_env` / `secret_env` 声明；凭据类**只记已设置/未设置，绝不写值**。没声明就写
「未声明，无法观测」。观测对象是最终传给提供方子进程的环境：若调用表声明 `environment_source`，
白名单来源值优先；否则才使用 runner 继承的环境。

### 这个机制证明不了什么

回执能让人去 `claude --resume <会话号>` / `codex exec resume <id>` 复核，也能让事后改动复审结论变得可检出，
但它是**审计与威慑，不是密码学证明**：控制代理持有 nonce 且对三个证据文件都有写权限，
所以没有任何纯文件校验能区分「它真的拉了人」和「它自己代笔」。真正起作用的是结构性隔离——
一个真正不同的模型、一个真正新鲜的上下文。别把回执读成比实际更强的保证。

## 每项任务

用 `agent-protocol task <id> -TaskClass <light|standard|high> -Activate` 从 `task-template/` 生成 `tasks/<task-id>/`。它会带上当前路由、Git HEAD、修前快照、临时目录例外、复审冻结窗口与轮次上限。机器规则在 `TASK-PROFILES.json`。每个任务目录包含：

```text
00-task.md
05-routing.md
06-review-commands.json
10-execution.md
18-review-probes.md（配置了命令并运行后生成）
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

`accepted` 至少需要执行证据、不同执行实例的复审证据和 acceptor 的独立核对。所有未运行项必须写 `NOT RUN`。

## 换挡

额度耗尽、服务不可用或工具缺失时：

1. 当前实例停止新增业务改动；
2. 在 `05-routing.md` 记录原因、最后已验证状态、未完成项和新模式；
3. controller 更新 `CURRENT.md` 的模式、角色映射和 `route_revision`；
4. 新实例只从文件恢复上下文，并重新核对工作树；
5. 不把旧实例的自述当作新实例已验证的事实。

推荐使用协议项目提供的 `scripts/Set-AgentProtocolMode.ps1` 完成换挡；若目标环境不能运行 PowerShell，人工修改也必须同时更新角色映射、保证等级、`route_revision` 和 `05-routing.md`。

## 收尾与清理

每个任务 `close` 之后、每个阶段验收之后，按 `CLOSEOUT.md` 走一遍清单：状态指针、证据与 `role-verify`、披露、清理分类、索引与提交边界。它同时规定信息分层与预算——注入层只加索引行，历史进复盘与归档摘要——这样新窗口只读少数文件就能接上，不依赖任何会话记忆。

## 数据边界

提示参数只携带角色、文件名、输出格式和权限边界。代理读取文件仍属于把数据交给对应模型处理，因此任务书必须声明目的地、模型/提供方、文件白名单和禁止数据。凭据、签名材料、真实用户内容和未脱敏日志默认禁止外发。
