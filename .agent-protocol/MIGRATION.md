# 导入与迁移规则

## 可直接迁移

- 角色定义、模式矩阵、状态机、证据文件结构；
- 单写者、独立复审、`NOT RUN`、换挡和数据边界规则；
- 合成 nonce 通道测试方法，及其实现 `New-AgentProtocolChannel.ps1` / `Test-AgentProtocolChannel.ps1`。

## 必须重填

- 项目路径、分支、构建/测试/发布命令；
- 敏感文件、数据分类和外发目的地；
- 可用代理、模型、preset、工具名、额度和权限；
- 项目特有的完成条件与可见行为门禁。

其中路径、分支、构建/测试命令与默认敏感文件集由 `Initialize-AgentProtocol.ps1` 从目标项目本身探测回填，且**只填仍为 `TODO` 的字段**；探测不到的一律留 `TODO`，绝不猜测。`release_gates`、`allowed_external_providers`、`local_agent_presets_or_tools` 属于策略决定，必须人工给。全部填完前 `PROJECT.md` 的 `status` 保持 `NEEDS_CONFIGURATION`，协议只能用于合成通道测试。

## 风险档与冻结快照升级

新版任务模板新增 `TASK-PROFILES.json`、`06-review-commands.json`、Git 修前基线、临时目录例外、复审轮次上限与冻结窗口。老任务不做静默改写；它们可以保留为历史证据，但要使用 `review-probe` 或 `close` 必须新建任务，或人工补齐这些机器可读字段并重新复审。

`init` 在目标不属于任何 Git 仓库时默认执行 `git init`，但绝不自动创建提交；不希望初始化可显式加 `-SkipGitInit`。`standard/high` 在没有可复算 HEAD 时不能收口为完成状态。

提供方默认表同时修正了 acceptor 的写入姿态：acceptor 必须能写自己的 `30-acceptance.md`。用户级 `providers.json` 属于机器事实，普通合并会保留人工定制参数；若旧表仍把 acceptor 配成只读，应人工核对后更新对应角色参数，不得为了省事覆盖凭据或自定义路由。

## 禁止迁移

- 旧项目的 `tasks/`；
- 旧 `CURRENT.md` 的活动任务；
- 旧 runId、nonce、**业务验收绿灯**、发布状态和模型连接状态；
- 凭据、缓存、日志、真实业务数据。

### 一处明确例外：提供方级通道能力证明

上面禁止迁移的是**业务验收绿灯**——不能因为有旧项目通过就宣称新项目业务通过。

**提供方通道能力**（某提供方能否读懂指定文件并写回派生值）是提供方级别的事实，不随项目变化，因此可以复用：`Test-AgentProtocolChannel.ps1 -Attest` 在全部校验通过后，把 `{提供方家族集合, 时间, 协议模板指纹, mode}` 写入**用户级目录** `%USERPROFILE%\.agent-protocol\attestations\<提供方家族>.json`。

- 证明覆盖的是协议模板的稳定文件指纹，`CURRENT.md` / `PROJECT.md` / `tasks/` 不计入——模板一变证明立即失效；
- 证明存放在项目之外，因此永远不会被 `git clone`、复制或当成某个项目的证据；
- 指纹匹配时 `Initialize-AgentProtocol.ps1` 跳过合成接力，并在项目 `CURRENT.md` 记 `channel_test: reused@<日期>/<提供方集合>`，缺口显式可见；
- 想让新项目亲自跑一遍，随时执行 `agent-protocol channel` 再 `agent-protocol verify-channel -Attest`，记录会变成 `passed@<日期>`。

`channel_test` 字段语义：`passed@<日期>` = 本项目亲自跑通；`reused@<日期>/<提供方集合>` = 沿用同模板的提供方证明；`not-run` = 未跑。它只是记录，不是门禁——创建真实任务时若为 `not-run` 只告警。

导入后先用一条命令完成接入与通道处理：

```powershell
agent-protocol init -Mode <triad|claude-triad|codex-dsh|codex-claude|dsh-claude|dsh-codex|claude-codex|single>
```

需要手工分步时：

```powershell
New-AgentProtocolChannel.ps1 -TargetProject <目标项目> -Activate
# 各角色按合成任务 00-task.md 的挑战写入派生值
Test-AgentProtocolChannel.ps1 -TargetProject <目标项目> -Attest
```

合成测试通过只证明通道和边界可用，不证明业务功能已验收。`.channel-test.json` 是工具元数据，不是证据。

## 入口规则：谁是你，决定你能用哪个模式

模式的 `controller` 必须**等于人类启动的那个 agent 的品牌**。DSH 里启动的项目就选 `dsh-*`，
Codex 里启动的就选 `codex-*`，Claude 里启动的就选 `claude-triad`（三方）或 `claude-codex`（双代理）。这条不是建议：

- `agent-protocol role` 拒绝拉起 `controller`——controller 已经是当前会话本身；
- 传 `-Entry <品牌>` 时 runner 校验 `mode.controller` 是否真是它，不符即拒绝并建议换模式或换窗口；
- 不传时回执记 `入口代理（自述）：unverified`——**检查不了的事就如实标注，不假装查过**。

`ROUTES.json` 里的 `*-fresh-review` 表示**同品牌的另一个新鲜实例**，不是「换了个提供方」。
相同品牌的两个新鲜上下文只能算不同运行实例；代理不可用时按上面「换挡」显式换挡，不许静默换模型后继承原身份。
runner 对这类路由拒绝 `resume` 调用、要求 CLI 返回会话标识，并确认该标识未被同任务同家族的既有回执使用；
回执记录核验结果。这使声明可检查，但仍不能证明它与未留下回执的 controller 会话必然不同。

## 诚实的边界（不许含糊）

### 一、回执是审计与威慑，不是密码学证明

控制代理持有合成 nonce，且对三个证据文件都有写权限。因此**没有任何纯文件校验能证明「控制代理确实拉了人」**
而不是自己代写。能防住的是「一个模型自己给自己打分」，防不住「控制代理假装拉了人」。

真正起作用的是**结构性隔离**：一个真正不同的模型、一个真正新鲜的上下文。回执里的会话标识、
起止时间、退出码和完整原始输出，能让人去 `claude --resume <会话号>` / `codex exec resume <id>` 复核，
也能让事后改动复审结论变得可检出（`agent-protocol role-verify <task-id>`）——但这是可审计性，不是证明。

### 二、各提供方的只读强制力不对等

| 提供方 | reviewer 只读靠什么保证 | 回执记的 `enforce` |
|---|---|---|
| codex | `-s read-only`，内核级拒绝写盘 | `kernel-sandbox` |
| claude | `--restricted` + 工具白名单 | `tool-policy` |
| dsh | 无任何强制（且无 `-C`、无输出文件） | `none` → **默认拒绝担任 reviewer** |

这是 dsh 的 CLI 能力缺口，不是设计取舍；**不许用提示词弥补后就宣称等强**。`-AllowUnenforcedReviewer`
是明知代价才用的逃生阀，用了回执会如实记 `只读强制：none`。

### 三、DSH 子代理的执行能力边界

当前用户级 runner 能无头拉起 DSH，但 DSH CLI 没有可由本协议授予的受限 shell，也没有只允许单一证据文件的
输出开关。它可能按提示直接写出文本，却不能据此声称已实际重跑构建、测试、mtime 或哈希门禁。

- DSH 作为 executor / acceptor 时，凡任务要求真实命令输出，子代理没有工具就必须写 `NOT RUN`，结果不得晋升为 `accepted`；
- `dsh-*` 模式允许人类启动的 DSH controller 兼 acceptor，应由该主控会话用它实际拥有的工具完成门禁并写验收；
- runner 不代跑门禁再伪装成 acceptor 的独立核对，也不向 DSH 开放整个 shell；以后若增加受限执行适配器，必须另行记录强制边界并补桩回归。

### 四、`prompts/` 会改变协议指纹

角色提示词随模板装在 `.agent-protocol/prompts/` 下、计入 `Get-ProtocolFingerprint`，
所以**这一步会让现有提供方通道能力证明全部失效**。这是设计意图（「模板指纹一变证明即失效」），
不是 bug：下次 `init` 会重跑合成接力而不是复用旧证明。一次性代价。

### 五、老项目要 `-Update` 才带得进 `prompts/`

老项目里没有 `prompts/` 目录，直接跑 `role` 会报「角色提示词缺失：…/prompts/<角色>.md」。补装：

```bash
agent-protocol init -Update
```

`-Update` 沿用 `CURRENT.md` 里已有的模式并保留 `CURRENT.md`、`PROJECT.md` 与 `tasks/`；
想换模式不能用 `init -Mode`（会被忽略并告警），必须走 `Set-AgentProtocolMode.ps1`。
