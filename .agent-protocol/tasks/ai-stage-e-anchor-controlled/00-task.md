# ai-stage-e-anchor-controlled — 阶段E：ANCHOR 修复在生产分块下的受控复现

状态：`ready`

## 目标与非目标

- 目标：在**生产分块 600**下对样本 A（开头含重复锚点/重复标题的那一章）做**一次受控重跑**，用固定字段判定『含修复的构建在生产配置下是否仍以 `ANCHOR ambiguous + changed` 失败』：
  1. 确认 App 内分块上限为 **600**（**本任务不得改动任何 App 配置**）；
  2. **清空 logcat** 并开启 recordLog，建立干净取证窗口；
  3. 固定取证流程：**配置落定 → 读基线 ai_stats（含块数与字符数）→ 触发『重新处理本章』→ 读结果 ai_stats → 给出同一身份下的差值**；
  4. 抄录窗口内全部 AI 固定字段（含 `ai-ok` / `ai-protocol` / `ai-editkind` / `ai-fail code=… anchor=… chunk=N`）；**若检索不到某类，必须写『已检索，未找到』**；
  5. 结束前恢复 recordLog 关闭。
- 非目标：不改任何业务代码、测试、Prompt 或缓存版本；不改 App 配置（含分块上限）；不重新构建/安装；不做发布判定；不更换目的地或模型。

## 外发授权（用户 2026-09-19 授权，逐项）

- 目的地 / 模型：沿用 App 内**已配置**的 AI 服务与模型（**名称不记录**）。
- 正文范围：**样本 A 一章**（必要时可对同一章重复至多 1 次），**真实请求上限 8 次**。依据：该章在 600 分块下若仍在第 1 块失败，则每次尝试只消耗 1 块；若意外全部通过，整章约 7 块——上限 8 覆盖两种情形。
- 证据边界（硬要求）：只记固定脱敏字段、时刻（到秒）、线程组编号、块数、字符数与状态文案。**书名、章节名、正文、响应体、服务地址、密钥、模型名、PID/TID、设备序列号一律不得进入证据**；样本以『样本 A』指代。

## 实时基线

- 任务风险档：standard
- Git 仓库根：D:/vsproject/legado-E
- 分支：codex/manga-import
- Git HEAD：daab2ea440c056257075c17aee85ae5b86017cae
- 修前工作树摘要： M .agent-protocol/CURRENT.md
- 修前快照 SHA-256：16069bdaf0371c465e3ef091aba9b387eef3570d0371c69c1760e082808f61af
- 当前 route_revision：1

## 复审冻结与轮次

- 最大复审轮次：2
- 复审冻结窗口：reviewer 启动至回执完成期间，授权文件、任务书与执行报告不得变化；runner 前后快照不一致时本轮报告失效。
- reviewer 可执行检查：只允许 `06-review-commands.json` 中冻结的参数数组。
- 网络策略：本任务**需要**受控真实外发（范围以上一节为准），其它网络行为禁止。

## 角色与授权

- controller：dsh
- executor 可读 / 可改：读 `00-task.md`、`05-routing.md`、`14-executor-prompt.md`、`.agent-protocol/README.md`、`CURRENT.md`、`PROJECT.md`、上一任务 `../ai-stage-e-anchor-proof/30-acceptance.md`（承接其未闭合项）。
- reviewer 可读：`00-task.md`、`05-routing.md`、`06-review-commands.json`、`10-execution.md`、`14-reviewer-prompt.md`、`18-review-probes.md`。禁止读取凭据、`keystore*`、`look.ps1`、真实书源/正文、用户缓存、未脱敏日志。
- 沙箱提权：**需要**（`adb` 读写设备）。一次一申请，档位 `danger-full-access`，不得批量扩大。
- **正式文件白名单：无。** 本任务不得改动任何业务文件与 App 配置；证据只写 `.agent-protocol/tasks/ai-stage-e-anchor-controlled/**`。
- 允许产生的临时目录：系统临时目录、`D:\dsh-temp`。
- 收尾前必须清理：recordLog 恢复关闭（复核）、设备侧临时文件。
- acceptor：dsh
- 明确禁止的数据和操作：凭据/Token、`keystore*`、`look.ps1`、书名与章节名、正文与响应体、模型名、PID/TID、卸载、清数据、改 App 配置、提交、推送、发布。

## 完成条件

- [ ] 触发前确认分块上限为 **600**（保留复核证据）
- [ ] **同身份**前后 `ai_stats` 配对与差值当场记录（读不到即 `NOT RUN` 并停止）
- [ ] 窗口内全部 AI 固定字段抄录；缺失的类别写明『已检索，未找到』
- [ ] 记录本次实际请求块数（差值），并与 8 次上限核对
- [ ] recordLog 恢复关闭并复核；分块仍为 600
- [ ] 业务树零改动（`git status --porcelain` 仅协议文件；冻结命令覆盖）

## 停止条件

- 需要超过 8 次请求，或需要改动 App 配置/更换目的地/模型；
- 任一 `ai_stats` 读数读不到（停止，不下结论）；
- 需要卸载、清数据或改动业务代码。

## 风险档规则

- `light`：只允许文档与说明性文件；`standard`：普通代码、启发式和配置行为，独立 reviewer、冻结快照、最多两轮；`high`：安全、权限、迁移、协议脚本、发布与高影响数据变更，需独立 acceptor。
