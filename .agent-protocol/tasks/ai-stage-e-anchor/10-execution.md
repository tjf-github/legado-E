# ai-stage-e-anchor — 执行报告

状态：`完成`（本地 B1→B3 与 C；真机 D1/D2 明确不在本任务范围）

> **本文件已按独立复审（round 1）的放行清单修订。** 修订发生在复审之后，**必须重新复审**：
> 追加/修正了 4 条用例（F3/F4/F5/F6）、补充旧实现基线（F1）、重建冻结探针证据（F2/F12），
> 并按 F7/F8/F9/F11 更正了本文此前不准确的表述（更正只动本文与注释，未改生产行为）。

## 一、运行实例与分工

- 本文件是**当前轮（round 2）**。**round 1** 由 `agent-protocol role executor` 拉起的 dsh 子代理执行，结论 `blocked`：该子进程运行在 workspace-write 下，对 `D:\gradle_home` 只读，`gradlew.bat` 启动即在 `gradle-8.14.4-bin.zip.lck` 处被拒，且没有可用审批通道；它没有绕路（未改 `GRADLE_USER_HOME`、未下载依赖、未二次提权），如实把 B1/B2/B3/C 全写为 `NOT RUN`。三件套归档于 `rounds/executor-r001/`，`role-verify` PASS。
- 本轮执行者：**人类启动的 DSH 会话**（mode=dsh-claude 允许 controller/executor/acceptor 同为一个实例；独立方是 reviewer=claude）。
- 实现方式：controller 向派生实现子代理下发白名单与契约；该子代理同样无法运行 Gradle，只写代码文本；**全部门禁由 controller 在本机完整权限 shell 中执行**（实测 Gradle 8.14.4 / JVM 17）。
- 诚实说明：实现子代理在测试迭代中被 controller **中断**，留下 `AiOutputValidator.kt` 悬空 `if/else`（编译错误 `'if' must have both main and 'else' branches when used as an expression`）。controller 按其注释意图补完控制流，并修正 3 处**测试数据错误**（见三）。
- 未读取：凭据/Token、`keystore*`、`look.ps1`、真实书源与正文、用户缓存、未脱敏日志、白名单外源码。
- 未执行：提交、推送、切分支、发布、删除数据、安装软件、网络请求、任何外部模型调用。

## 二、改动（6 个业务文件，全部在写白名单内；+429 / −23）

| 文件 | 改动 |
| --- | --- |
| `AiProtocol.kt` | `AiEdit` 新增只读字段 `contextBefore` / `contextAfter`（默认 null）与 `MAX_ANCHOR_CONTEXT = 32`；`toString()` 仍为 `text=[REDACTED]` |
| `AiOutputValidator.kt` | 重复 `original` 的真实编辑新增唯一放行路径 `resolveDisambiguatedRange` → `resolveContextAnchor`（+ 防御性 `rangeEqualsOriginal`）；原 noop 丢弃分支保持不变 |
| `OpenAiCompatibleProvider.kt` | 严格解析 `context_before`/`context_after`（新增 `optionalString`；**Provider 私有诊断枚举**新增 `context_before_not_string`/`context_after_not_string`）；SYSTEM_PROMPT 同步 |
| `AiCacheKey.kt` | `AiVersions.PROMPT: ai-prompt-v4 → v5`、`VALIDATION: ai-validate-v6 → v7`，旧缓存自然失效 |
| `AiOutputValidatorTest.kt` | +227 行：见三（含复审后的 3 处修正） |
| `OpenAiCompatibleProviderTest.kt` | +82 行：合法解析、缺失、显式 null、非字符串（失败关闭）、Prompt 规则同步断言 |

### B2 定位契约（本机强制，模型偏移不参与）
1. 仅在 `original` 于当前块出现 **>1 次**且 `original != replacement` 时启用；单次出现仍走原有唯一子串路径。
2. `contextBefore` 与 `contextAfter` **都必须非空**、各自 ≤ 32 code point。
3. 组合锚点 `before + original + after` 必须在块内**恰好出现 1 次**。
4. 任一条不满足 → 返回 null → 维持 `ANCHOR + ambiguous(changed)` **整章失败关闭**；**未新增章节级失败码**（`AiFailureCode` 未变），未放宽 changed，未用模型 start/end 消歧。

## 三、测试用例（含复审后的修正）

新增/改动用例覆盖：组合唯一正例（**original 真的重复**）、同输入去上下文的新旧对照、块首/尾附近正例、上限恰好 32 正例、代理对 code point 对齐正例；伪造上下文、不连续上下文、**组合仍不唯一（两侧均非空）**、超限、乱序重叠、嵌套重叠、仅单侧上下文等反例；Provider 侧合法/缺失/null/非字符串四类。

- 首次运行 `248 tests completed, 3 failed`，3 项均为**测试数据错误**（非实现缺陷）：①上限用例把 `他` 误插在前文与空格之间，前文因此不紧邻，案例没测到上限；②"不连续"用例的文本里空格只出现一次，走的是唯一子串路径；③代理对用例的组合锚点定位的是第一个空格，期望值却写在第二个。
- **复审 round 1 后的修正（本轮新增）**：
  - **F3**：头号正例 `resolvesRepeatedChangedAnchorFromUniqueContextCombination` 原输入里 `original`（空格）只出现一次，根本没走上下文路径。改为 `"他 走得很快，他 说得更好"`（空格两次）并追加**同输入去上下文必须失败关闭**的断言，形成新旧对照。
  - **F4**：`nonUniqueContextCombinationMustFailClosed` 原用 `contextAfter = ""`，在"两侧非空"检查处就返回，永远到不了"组合仍不唯一"分支。改为 `"他好。他好。他坏"` + `before="他"`/`after="。"`（组合出现两次）。
  - **F5**：补 Provider 的**字段缺失**与**显式 null** 两例，断言解析成功且两字段为 null。
  - **F6**：`resolvedRangeTouchingReplacementSentinelMustFailClosed` 改名 `replacementSentinelSyntaxIsRejectedOnUniqueOccurrencePath`，并注明锚点路径不可能触达该检查（含哨兵的 `original` 必然唯一）。

### 变异测试（证明 F4 分支真的被覆盖）
把 `AiOutputValidator.kt:188` 的 `if (anchorOccurrences.size != 1)` 临时改为 `== 0`（组合重复时取第一个匹配）后运行：
```
> Task :app:testAppDebugUnitTest FAILED
AiOutputValidatorTest > nonUniqueContextCombinationMustFailClosed FAILED
34 tests completed, 1 failed
BUILD FAILED in 21s
```
随后**已复原**为 `!= 1`（`grep anchorOccurrences.size` 确认无 MUTATION 残留）。

## 四、门禁与证据

| 项 | 结果 |
| --- | --- |
| `:app:compileAppDebugKotlin :app:testAppDebugUnitTest` | `BUILD SUCCESSFUL`；**37 套件 / 247 项 / 0 failures / 0 errors / 0 skipped** |
| `:app:lintAppDebug` | `BUILD SUCCESSFUL in 7m 40s` |
| `git diff --check` | exit 0 |
| **冻结探针（重建）** | `18-review-probes.md`：**总结 PASS**、工作树前后 `stable`、快照 `59c0236b…`、命令清单 `d36e662d…`；两项命令均 PASS（`git diff --check` 1.23s；三个 AI 测试类 63.81s，`BUILD SUCCESSFUL in 1m 3s`） |
| **旧实现基线（F1）** | 在 `git worktree add --detach D:\dsh-anchor-baseline HEAD` 的**旧实现**（`AiProtocol.kt` 中 `contextBefore`/`MAX_ANCHOR_CONTEXT` 命中数为 0）上运行 `BaselineAnchorBehaviorTest`：`tests=1 failures=0`，用例 `repeatedChangedAnchorFailsClosedOnHeadImplementation`，`BUILD SUCCESSFUL in 3m 9s`。即同一输入在**旧实现下为 `Invalid(ANCHOR, ambiguous/changed)`**，新实现下带上下文为 `Valid`。该 worktree 已 `git worktree remove --force` 清理，未进入业务树。 |

> 基线是**用旧 API 写的新用例在 HEAD 上跑通**（断言级 X 值），而非"新用例在旧实现上失败"——后者对新字段只能得到编译级失败。此前的"仅编译级"表述按此更正。

## 五、按复审意见更正的表述（只改本文/注释，未改生产行为）

- **F7（死代码）**：`resolveContextAnchor` 中 `startsWith(anchor, …)`、`substring != original`、`startsWith(before, …)`、`startsWith(after, …)` 与 `rangeEqualsOriginal`，在 `anchorOccurrences.size == 1` 之后**按构造恒真**，属**不可达的不变式断言**，**不计入验证强度**。此前列为"双重校验"是错误表述。
- **F8（start/end）**：任务书写的"模型 start/end 仍只作交叉校验"与实际不符——**既有实现（HEAD）对非空 `original` 从不读取 start/end**，本任务既未移除也不存在该交叉校验，新增锚点路径同样不读。**未新增**该拒绝路径（会改变行为且无必要）。
- **F9（枚举）**：更正为"未新增**章节级失败码**（`AiFailureCode`）；Provider 私有诊断枚举新增两个非字符串分类"。
- **F11（能力收窄）**：要求两侧上下文都非空，意味着**块首（左侧无可复制字符）的重复锚点在任何情况下都无法用上下文消歧**，模型只能通过延长 `original` 迂回。这是相对"短前文/短后文"设计意图的**能力收窄**（安全方向），任务书 B1 曾写的"块首/块尾正例"因此无法成立，属**未达成的完成条件**，由 acceptor 判断。
- **F10（越界留痕）**：实现子代理曾创建白名单外的 `TmpAnchorDiagnosticTest.kt`（main 与 test 两侧），已由 controller 删除并经 reviewer 独立 Glob 确认。

## 六、NOT RUN（不得按"应该没问题"推断）
- **D1 受控真机复测**：NOT RUN（本任务非目标；用户已就外发授权，另立任务执行）。
- **D2 发布矩阵**：NOT RUN。
- 合并、发布、更新日志、共存包构建与安装：NOT RUN（未授权）。
- 外部模型调用与任何网络请求：NOT RUN。

## 七、不确定
- 真机行为未验证：本地合成用例与门禁通过**不证明**真实模型会稳定给出合规上下文锚点，也不证明真机 `ambiguous + changed` 已消除——这正是 D1 要回答的。
- `MAX_ANCHOR_CONTEXT = 32` 未经真机数据校准。
- `review-probe` 在 C: 盘临时目录下会因 KSP 跨盘根路径报 `this and base files have different roots`（环境限制）；本次通过把 `TEMP`/`TMP` 指向 D: 盘规避，而该规避**不在冻结命令里**，复现者需自行设置。
