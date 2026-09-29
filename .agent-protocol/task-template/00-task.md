# <task-id> — 任务书

状态：`draft`

## 目标与非目标

- 目标：
- 非目标：

## 实时基线

- 任务风险档：standard
- 变更范围：docs / config / code / release / security / data
- 不确定性：low / medium / high
- 推荐 mode：
- 升级触发器：跨层缺陷、范围扩大、结论冲突、同错两轮未收敛、发布/权限/安全/数据边界
- Git 仓库根：
- 分支：
- Git HEAD：
- 修前工作树摘要：
- 修前快照 SHA-256：
- 当前 route_revision：

## 复审冻结与轮次

- 最大复审轮次：2
- 复审冻结窗口：reviewer 启动至回执完成期间，授权文件、任务书与执行报告不得变化；runner 前后快照不一致时本轮报告失效。
- reviewer 可执行检查：只允许 `06-review-commands.json` 中冻结的参数数组；空数组表示 `NOT RUN`。
- 网络策略：默认禁止；协议只记录策略，不能把未实现的网络隔离宣称为强制证据。

## 角色与授权

- controller：
- executor 可读 / 可改：
- reviewer 可读：本任务的 `06-review-commands.json`（冻结命令清单）；其余业务文件由 controller 填写。
- 沙箱提权：否（默认）。需要提权时任务书必须写明哪一条命令、为什么、提到哪一档；执行侧一次一申请，不得批量提权。
- 写白名单是**硬上限**：白名单内才是授权范围；白名单外即使只改一个注释也属越界，必须先由 controller 扩权并重新冻结。
- 正式文件白名单：
- 机器写范围说明：通过 task -ExecutorWrite 或 scope-expand 写入逐条 executor_write 路径；留空时 runner 报 not-configured，不得宣称未越界。
- 增量授权：controller 使用 `scope-expand` 追加的 Scope expansion 节属于本任务授权；只扩展列出的角色/文件，原有禁止项仍优先。不得手工同时改写多份范围与上下文文件。
- 允许产生的临时目录：只允许 runner 通过 `AGENT_PROTOCOL_TEMP_ROOT` 传入并登记的唯一系统临时根；项目内例外必须在本字段另行写明，不得把临时产物混入正式 diff。
- 收尾前必须清理：runner 在 success / failure / timeout 的 finally 路径清理登记根；清理失败必须在 temp manifest 留待清理状态。
- acceptor：
- 外部目的地与模型：
- 明确禁止的数据和操作：

## 完成条件

- [ ] 实现或分析条件
- [ ] 自动化条件
- [ ] 可见行为条件
- [ ] 文档与交接条件

## 停止条件

- 权限或数据范围不明确；
- 需要改变产品语义、费用、发布范围或安全边界；
- 当前模式不满足任务要求的保证等级。

## 风险档规则

- `light`：只允许文档、注释与不改变运行行为的说明性文件；一轮独立静态复审。触及代码、脚本、依赖、构建、CI、权限、安全、路由、发布或数据格式时必须升级。
- `standard`：普通代码、启发式和配置行为；独立 reviewer、冻结快照、最多两轮。
- `high`：安全、权限、迁移、协议脚本、发布与高影响数据变更；独立 reviewer 与独立 acceptor，关键门禁必须重跑。
