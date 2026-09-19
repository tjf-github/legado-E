# 可用性模式与路由

| mode | 默认角色映射 | 保证等级 | 状态上限 |
|---|---|---|---|
| `triad` | Codex=controller+acceptor；DSH=executor；Claude=reviewer | high | accepted |
| `claude-triad` | Claude=controller+acceptor；DSH=executor；Codex=reviewer | high | accepted |
| `codex-dsh` | Codex=controller+reviewer+acceptor；DSH=executor | standard | accepted |
| `codex-claude` | Codex=controller+executor+acceptor；Claude=reviewer | standard | accepted |
| `dsh-claude` | DSH=controller+executor+acceptor；Claude=reviewer | standard | accepted |
| `dsh-codex` | DSH=controller+executor+acceptor；Codex=reviewer | standard | accepted |
| `claude-codex` | Claude=controller+executor+acceptor；Codex=reviewer | standard | accepted |
| `single` | 当前代理=controller+executor+acceptor；reviewer=NOT RUN | provisional | provisional |

保证等级与状态上限的机器可读权威值在 `ROUTES.json` 的 `assurance` / `max_state`；本表必须与它一致。

提供方模式只回答“角色由谁承担”。每个任务还必须从 `TASK-PROFILES.json` 选择 `light / standard / high` 风险档，它回答“需要多少验证”：`light` 只允许文档类路径且最多一轮独立静态复审；`standard` 使用冻结快照并最多两轮；`high` 要求独立 acceptor 与关键门禁复跑。风险档不能改变或绕过本表的角色路由，实际保证取模式与任务档中较弱的一侧。

命名有两套形状，别按字面猜角色：`X-Y` 表示 **X 兼 controller+executor+acceptor、Y 当 reviewer**；
`triad` / `claude-triad` / `codex-dsh` 是特例，指「主控方自己兼一部分角色、其余分给别的提供方」，
具体分工只看上表的角色映射列，不要看名字推。

## 双代理接受条件

- reviewer 必须是不同于 executor 的运行实例；
- 原始复审结果保存在独立文件；
- acceptor 必须在复审后重新检查 diff 和门禁，并逐项处理 findings；
- 如果 reviewer 实际未运行，自动降为 `single`，不得 `accepted`。

## rework 收敛规则

- 同一任务连续两轮复审若只剩 LOW / INFO，且没有功能缺陷、越界、安全问题或阻塞级证据缺口，controller 必须把剩余项转成明确后续任务并进入收尾，不得继续制造无固定点的 rework 循环。
- 最后一轮复审后，只允许删除或收窄已有表述的文档修正；acceptor 必须在 `30-acceptance.md` 逐项披露。任何扩大承诺、改变功能或改变证据含义的修正都要重新复审。
- `14-<角色>-prompt.md`、`15-<角色>-raw.md` 和角色证据文件的顶层版本表示当前轮。runner 再次拉起同一角色时，会把上一轮三件套复制到 `rounds/<角色>-rNNN/`；`role-verify` 同时校验当前轮与历史轮。历史失败调用显示为 `SKIP` 并保留审计痕迹，不冒充可信证据；当前轮失败仍是 `FAIL`。

## 模式选择优先级

1. 满足任务权限和数据政策；
2. 三方可用时优先三方模式：Codex 主控用 `triad`，Claude 主控用 `claude-triad`；
3. 某方额度耗尽时选择仍有独立 reviewer 的双代理模式；
4. 只有一个代理可用时进入 `single` 并保存可复审证据；
5. 代理恢复后从 `provisional` 进入 `review`，不能直接改成 `accepted`。

## 品牌与实例

DSH 启动的 Codex 子代理不是外层 Codex controller；Claude 经不同提供方路由时也必须记录实际目的地。
相同品牌的两个新鲜上下文只能算不同运行实例，不能伪称不同提供方。

实际目的地落在回执的「实际路由」一行：由用户级调用表的 `route_env` 声明要观测哪些环境变量（凭据类
走 `secret_env`，只记已设置/未设置）。没声明时回执写「未声明，无法观测」——**观测不到就写观测不到，
不许替提供方编一个目的地**。

## 入口规则

模式的 `controller` 必须等于**人类启动的那个 agent 的品牌**——DSH 里启动选 `dsh-*`，Codex 里启动选
`codex-*`，Claude 里启动选 `claude-triad`（三方）或 `claude-codex`（双代理）。`agent-protocol role` 拒绝拉起 `controller`（它就是当前会话），
传 `-Entry <品牌>` 时还会校验 `mode.controller` 是否真是它，不符即拒绝；不传则回执记 `unverified`，
不假装查过。`*-fresh-review` 指同品牌的**另一个新鲜实例**，不是换了个提供方。runner 会拒绝 `resume`、要求返回会话号并检查它未在同任务同家族的既有回执中出现；这属于可审计核验，不是对 controller 身份的密码学证明。
