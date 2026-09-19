# synthetic-20260918-135513 — 合成通道测试

状态：`ready`

## 目标与非目标

- 目标：验证各角色是否真的读到了任务书并能派生 nonce
- 非目标：不验证任何业务功能；不修改业务文件

## 实时基线

- 任务风险档：light
- Git 仓库根：NOT APPLICABLE（合成通道测试不依赖业务仓库）
- 分支：NOT APPLICABLE
- Git HEAD：NOT APPLICABLE
- 修前工作树摘要：NOT CAPTURED（只校验稳定协议定义与角色接力）
- 修前快照 SHA-256：NOT APPLICABLE
- 当前 route_revision：1

## 复审冻结与轮次

- 最大复审轮次：1
- 复审冻结窗口：reviewer 启动至回执完成期间，授权文件、任务书与执行报告不得变化；runner 前后快照不一致时本轮报告失效。
- reviewer 可执行检查：只允许 `06-review-commands.json` 中冻结的参数数组；空数组表示 `NOT RUN`。
- 网络策略：默认禁止；协议只记录策略，不能把未实现的网络隔离宣称为强制证据。

## 角色与授权

- controller：dsh
- executor 可读 / 可改：读 00-task.md、05-routing.md、14-executor-prompt.md、.agent-protocol/README.md、.agent-protocol/CURRENT.md、.agent-protocol/PROJECT.md；只改 10-execution.md（executor=dsh）
- reviewer 可读：00-task.md、05-routing.md、10-execution.md、14-reviewer-prompt.md；禁止读取 .channel-test.json、其它协议文件与业务文件（reviewer=claude）
- 沙箱提权：否（默认）。需要提权时任务书必须写明哪一条命令、为什么、提到哪一档；执行侧一次一申请，不得批量提权。
- 写白名单是**硬上限**：白名单内才是授权范围；白名单外即使只改一个注释也属越界，必须先由 controller 扩权并重新冻结。
- 正式文件白名单：
- 允许产生的临时目录：系统临时目录，或本字段明确列出的项目内目录；不得把临时产物混入正式 diff。
- 收尾前必须清理：本任务产生且不属于正式交付物的临时文件。
- acceptor：dsh
- 外部目的地与模型：按 mode=dsh-claude 路由；提供方见 05-routing.md；模型由用户级 providers.json/CLI 决定
- 明确禁止的数据和操作：凭据、真实用户内容、业务文件、.channel-test.json；禁止提交、推送、发布及任务目录外写入

## 完成条件

- [ ] executor、reviewer、acceptor 分别写出并回显任务书要求的严格字段
- [ ] verify-channel 全部检查通过，role-verify 对已有 runner 回执通过
- [ ] NOT APPLICABLE（合成测试不验证业务可见行为）
- [ ] 10/20/30 证据与必要的 15-角色-raw.md 回执齐全；未运行项写 NOT RUN

## 停止条件

- 权限或数据范围不明确；
- 需要改变产品语义、费用、发布范围或安全边界；
- 当前模式不满足任务要求的保证等级。

## 风险档规则

- `light`：只允许文档、注释与不改变运行行为的说明性文件；一轮独立静态复审。触及代码、脚本、依赖、构建、CI、权限、安全、路由、发布或数据格式时必须升级。
- `standard`：普通代码、启发式和配置行为；独立 reviewer、冻结快照、最多两轮。
- `high`：安全、权限、迁移、协议脚本、发布与高影响数据变更；独立 reviewer 与独立 acceptor，关键门禁必须重跑。

## 合成通道测试挑战

本任务只验证通道与数据边界可用，**不验证任何业务功能**。

- nonce（明文，只允许出现在本文件）：`0241af01e7f0b0f8f919c47d2e9c325d`
- 派生值只做固定格式抄写，不做任何加减、哈希或逐位变换：
  1. 把 32 位 nonce 沿中点抄成`前 16 位`和`后 16 位`。
  2. 取角色标签：`executor=eeeeeeeeeeeeeeee` / `reviewer=bbbbbbbbbbbbbbbb` / `acceptor=aaaaaaaaaaaaaaaa`。
  3. 严格拼成`前16位 + 标签 + 后16位 + 标签`，共 64 位小写 hex。
  完整示例（不要照抄结果）：nonce=`0123456789abcdef0123456789abcdef` → executor 得 `0123456789abcdefeeeeeeeeeeeeeeee0123456789abcdefeeeeeeeeeeeeeeee`。
- 格式要求（机器可读，校验器按字段行解析）：每个要求的字段必须严格写成 `字段名: <64 位小写 hex>` 的**单独一行**；字段名之前、冒号之前、值中间或值末尾都不得添加空格或其它字符。分组渲染只能另起一行，并明确标为非字段值
- 明说边界：完整 nonce 被角色标签打断；抄写整串 nonce 不是证据。这个握手只证明「读到了任务书」，不是密码学证明（控制代理本来就持有 nonce）
- executor：在 `10-execution.md` 写入 `nonce_digest: <role_digest("executor")>`，并把状态置为 `provisional`
- reviewer：读 `10-execution.md` 并独立复算核对，在 `20-review.md` 写入 `review_digest: <role_digest("reviewer")>` 与 `executor_digest: <executor 写入的值>`
- acceptor：在 `30-acceptance.md` 写入 `accept_digest: <role_digest("acceptor")>` 与 `reviewer_digest: <reviewer 写入的值>`
- 禁止把 nonce **明文**（完整 32 位串）写入 `10-execution.md` / `20-review.md` / `30-acceptance.md`：写入派生值是证据，抄写 nonce 不是
- 角色文件不要复述 nonce、不要展示前后半串、不要解释或展开拼接过程；只写最终严格字段和必要的执行事实。即使把两个半串写在相邻位置，也会重新组成完整 nonce 并判为泄漏
- 除本任务目录外不得修改任何文件；`.channel-test.json` 是工具元数据，不是证据，不在读取白名单内；其中不存 nonce 或期望派生值，读取它不能替代读取任务书
- 本测试不涉及目标项目的路径、分支、构建命令、凭据与真实数据
