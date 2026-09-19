# ai-stage-e-anchor — 独立复审报告（round 2）

## 结论：**有阻塞项**

代码层面我**未发现违反任务书 B2 失败关闭契约的缺陷**；两条阻塞项均为**契约/证据级**，需要 acceptor 明确处置后才能验收（F-A、F-B/F-C）。其余 7 条为非阻塞发现。

本次复审我**没有 shell 工具**（本会话与可派生的子代理均无 Bash），因此 `06-review-commands.json` 的两条冻结命令我**没有执行**，全部标 `NOT RUN`；我也**没有读到 diff**（无 `git diff` 能力），凡"与 HEAD 对照"的结论一律标注为不可独立复核。

---

## 一、我实际做过的核对动作

| 动作 | 结果 |
| --- | --- |
| 读 `00-task.md` / `05-routing.md` / `06-review-commands.json` / `10-execution.md` / `14-reviewer-prompt.md` / `18-review-probes.md` | 完成 |
| 读白名单内全部改动源码：`AiProtocol.kt`、`AiOutputValidator.kt`、`OpenAiCompatibleProvider.kt`、`AiCacheKey.kt`、`AiChapterProcessor.kt`（全文/相关段） | 完成 |
| 读白名单内测试：`AiOutputValidatorTest.kt`（34 个 `@Test`，Grep 计数核对）、`OpenAiCompatibleProviderTest.kt`、`AiNoChangeTest.kt`、`AiCacheKeyTest.kt` | 完成 |
| 读 `rounds/executor-r001/10-execution.md`（round 1 归档） | 完成，与 `00-task.md:41` 的分工描述一致（Gradle 在 `gradle-8.14.4-bin.zip.lck` 处被拒、无审批通道、零改动） |
| Grep `contextBefore\|contextAfter\|MAX_ANCHOR_CONTEXT\|context_before\|context_after` 于 `app/src/**` | 新字段只出现在白名单内的 4 个文件，无其它消费者 |
| Grep `LogUtils` / `ordinal` / `.entries` 于 `app/src/main/.../ai/**` | 3 处日志全部只记固定枚举名或字面量，无上下文值 |
| **手工推演全部 13 条新增/改动用例的期望值**（含 code point 换算） | 13/13 与源码行为一致，未发现断言写错 |
| `Glob app/src/test/java/io/legado/app/help/ai/*.kt` | 22 个测试类，**无 `TmpAnchorDiagnosticTest.kt` 残留**（F10 的清理可独立确认） |
| `Glob .agent-protocol/tasks/ai-stage-e-anchor/rounds/executor-r001/**` | 3 个归档文件存在 |
| 冻结命令 `git diff --check`、`gradlew :app:testAppDebugUnitTest --tests …` | **NOT RUN**（无 shell 工具）。我引用的执行结果是 `18-review-probes.md` 中 runner 在一次性 worktree 的冻结证据（两命令 PASS、退出码 0、工作树 stable），**我未复现** |
| `git diff` / HEAD 逐行对照 / `+429 −23` / `AiVersions` 旧值 / `AiFailureCode` 未新增 | **NOT RUN**（无 shell、无 diff 能力） |

---

## 二、发现

### F-A（中，阻塞项）B1 完成条件的字面证据缺失，且替代基线已不可复现
- 位置：`00-task.md:59`（"在**旧实现上真实失败**（保留失败输出，不允许只写结论）"） vs `10-execution.md:64,66`。
- 事实：留存下来的基线证据是一个**在 HEAD 上跑通（`tests=1 failures=0`）的用例** `repeatedChangedAnchorFailsClosedOnHeadImplementation`，不是"新契约在旧实现上的失败输出"。executor 已如实更正（`:66`），这一点应予肯定，但完成条件的字面要求仍未满足。
- 具体不可复核性：该用例所在 `BaselineAnchorBehaviorTest.kt` 与 `D:\dsh-anchor-baseline` worktree **均已删除**，白名单内不存在任何失败输出或测试文件，我用 Glob 无法找到；该命令也不在冻结探针清单里。我无法判断该基线是否真的跑过。
- 建议：由 controller 在一次性 worktree 内重跑并**逐字粘贴**输出（含 HEAD 上 `Invalid(ANCHOR, ambiguous/changed)` 的断言证据），或由 acceptor 在 `30-acceptance.md` 中**书面豁免**该条并写明理由。

### F-B（中，阻塞项/需裁定）任务书要求的 `start/end` 交叉校验不存在
- 位置：`00-task.md:9`（"模型 `start/end` 仍只作交叉校验，不得单独消歧"）；`AiOutputValidator.kt:159`（注释"模型 start/end 不参与"）；`AiProtocol.kt:38`（KDoc 仍称"`original` 必须与 `[start, end)` 区间的原文逐值相等（**本机锚定校验**）"）。
- 事实：`resolveContextAnchor` 与 `resolveDisambiguatedRange`（`AiOutputValidator.kt:161-203`）完全不读 `start/end`；全仓无任何按 `[start,end)` 校验 `original` 的代码（与 F8 自述一致）。因此 `AiProtocol.kt:38` 的文档断言是**不成立的**。
- 影响：见 F-C 的具体误定位场景；这是"模型声明的落点"与"本机解析落点"之间唯一的交叉约束，缺它则二者矛盾时无任何信号。
- 建议：在锚点路径加"`start/end` 与解析范围不一致且模型声明落在块内时失败关闭"，或由 acceptor 明确裁定该条不作为必须项，并同步修正 `AiProtocol.kt:38` 的 KDoc。

### F-C（低-中）组合唯一路径可在与模型意图**不同**的 `original` 出现处静默落点，报告未披露
- 位置：`AiOutputValidator.kt:187-188`（组合唯一即放行）。
- 具体失败场景（可逐字复现的合成输入）：
  - `text = "甲很好，甲很坏"`；`original = "很"`（出现 2 次，左邻都是"甲"）；模型本意是第一处（"甲很**好**"），但把后文错抄成第二处的 `"坏"`：`contextBefore="甲", contextAfter="坏"`。
  - 组合锚点 `"甲很坏"` 在块内**恰好出现 1 次**（第二处），前置/紧邻检查全部通过 → 解析范围 = 第二处 `很` → 输出 `"甲很好，甲狠坏"`，**章节 Completed**。
  - 旧实现（HEAD）：`ANCHOR + ambiguous(changed)` **失败关闭**（`AiNoChangeTest.kt:62-68` 即是该回归护栏）。
- 性质判定：这**符合**任务书 B2 授权的"组合唯一性"契约，不属于越界；但它是本改动唯一的**语义副作用**（原本失败关闭的输入现在会静默改到别处），`10-execution.md` 全文未披露。若采纳 F-B 的交叉校验即可闭合此路径（模型声明的 `1..2` 与解析出的 `5..6` 矛盾时会失败关闭）。
- 建议：报告补记该副作用；并优先采纳 F-B。

### F-D（中）"上下文超长"反例不可判别，`contextAfter` 超限完全无用例
- 位置：`AiOutputValidatorTest.kt:450-461`（`contextAfter = ""`）；被保护逻辑 `AiOutputValidator.kt:181`。
- 失败场景：**删掉 `AiOutputValidator.kt:181` 的超限分支，该用例仍然是绿的**——`beforeCp=33`、`afterCp=0`，会由 `:184` 的"两侧必须非空"分支返回 `null`，最终仍是 `ANCHOR + ambiguous(changed)`。即 `MAX_ANCHOR_CONTEXT` 的**拒绝路径没有任何能让它失败的测试**（正例 `:463-475` 只能证明"32 通过"，不能证明"33 被拒"）；`contextAfter > 32` 连用例都没有。
- 建议：把超限反例改为"33 个 code point 前文**紧邻**该空格 + 非空后文"（例如 `"他" + 长*33 + " 说，他 说"` 的对应位置），并补一条 `contextAfter` 超限用例。

### F-E（低）三处测试注释声称覆盖了按构造不可达的检查
- 位置：`AiOutputValidatorTest.kt:411-421`（`fabricatedContextMustFailClosed`）、`:424-434`、`:564-572`；对应 `AiOutputValidator.kt:192、198、200-201、206-211`（F7 已披露为不可达断言）。
- 事实：这三例的输入使 `before+original+after` **在块内根本不出现**，实际都在 `:188` 的 `size != 1` 处返回；用例本身作为"必须失败关闭"的要求测试是有效的，但注释（"落点唯一性成立时仍必须逐字比对 original"、"声明的后文…与紧邻字符不符"）把不可达分支说成了被覆盖。
- 建议：改注释为"覆盖组合不存在/不唯一分支"，或在代码里把不可达断言显式标注为 invariant（保留或删除均可，行为等价）。

### F-F（低）锚点路径缺 code point 对齐的关键覆盖
- 位置：`AiOutputValidatorTest.kt:549-573`（补充平面只作 **context**，`original` 仍是 BMP 空格）；`overlappingSupplementaryAnchorUsesCodePointBoundaries:215-228` 走的是唯一子串路径，不覆盖锚点路径的 `cpStart + beforeCp` 换算（`AiOutputValidator.kt:193-196`）。
- 建议：补一条 `original` 为补充平面字符、两侧 context 齐备的正例。

### F-G（低）Prompt 的 "32" 与 `MAX_ANCHOR_CONTEXT` 无机械绑定
- 位置：`OpenAiCompatibleProvider.kt:433`（硬编码 "at most 32 Unicode code points"）；`OpenAiCompatibleProviderTest.kt:325`（只断言字面串）。
- 失败场景：只把 `AiEdit.MAX_ANCHOR_CONTEXT` 改成 64（不动 prompt）时，**没有任何测试变红**，模型会持续被要求 32 而本机接受 64。
- 建议：prompt 由常量插值生成，或加一条断言两者一致的测试。

### F-H（低）C 门禁证据为自述、数字内部不自洽（风险有限）
- 位置：`10-execution.md:60`（37 套件 / 247 项）与 `:39`（首次 248 tests 3 failed）。在新增用例之后总数反而下降，报告未解释；全量运行与 `lintAppDebug 7m 40s` 均无输出留存，我无 shell 无法核对。
- 风险有限的两个理由：(1) 按 `00-task.md:41` 的分工，门禁由 controller 留证属授权安排；(2) 新字段默认 `null`，`resolveContextAnchor:176-177` 立即返回 `null`，**未提供上下文的既有调用行为逐字节不变**，因此全量套件中"新增字段"影响面仅限冻结探针已覆盖的 3 个类（探针 PASS）。
- 建议：补一段全量运行的原始输出尾部（含套件/项目数行）。

### F-I（低）`AiVersions` 递增不可独立核对
- 位置：`AiCacheKey.kt:48,50` 当前为 `ai-prompt-v5` / `ai-validate-v7`；`AiCacheKeyTest.kt` 不 pin 任何版本串。我无 diff，无法确认旧值确为 v4/v6，也无测试能让"忘记递增"变红。
- 建议：`AiCacheKeyTest` 增加一条对 `AiVersions` 常量值的显式断言（改版本必须显式改测试）。

### F-J（低，表述来源不可核对）"任务书 B1 曾写的块首/块尾正例"
- 位置：`10-execution.md:73`。当前 `00-task.md:59` 的 B1 只要求"正例可定位 / 反例必须失败"，**没有**"块首/块尾正例"字样。该说法可能引自计划书 §8（不在我的读取白名单内，无法核对）。不作为缺陷，仅提示来源需注明，避免让 reader 以为任务书有此要求。

---

## 三、安全与合规核对（未发现问题的方面）

- **白名单**：按会话起始的 `git status` 快照，改动仅 `AiCacheKey.kt`、`AiOutputValidator.kt`、`AiProtocol.kt`、`OpenAiCompatibleProvider.kt`、`AiOutputValidatorTest.kt`、`OpenAiCompatibleProviderTest.kt`（6 个，全部在写白名单内）+ `.agent-protocol/CURRENT.md`。后者不在写白名单，但 `rounds/executor-r001/10-execution.md:7` 已把 `CURRENT.md` 与任务目录归因为 **controller 侧产物**（"两者均为 controller 侧产物"），我也**无权限读取 `CURRENT.md`**，只能按该归因记录，请 acceptor 自行确认。`AiChapterProcessor.kt`、`AiNoChangeTest.kt` 未改（白名单是上限，未用不算缺陷）。
- **敏感路径/禁止操作**：未见触碰凭据、`keystore*`、`look.ps1`、真实书源/正文/缓存；未见提交、推送、切分支、发布、网络请求、外部模型调用。
- **注入面**：锚点字段是**模型输出**且为只读——`AiOutputValidator.kt:150-153` 只把 `edit.replacement` 写回文本，`contextBefore/After` 从不进入结果文本、也不进入请求体；无命令执行、无路径拼接、无 SQL，故不存在命令注入/路径穿越/XSS 的新增面。
- **凭据/正文外泄**：新增日志为零；既有 3 处日志（`AiOutputValidator.kt:53`、`OpenAiCompatibleProvider.kt:57`、`AiReaderAccess.kt:72`）只输出固定枚举名/字面量；`AiEdit.toString()`（`AiProtocol.kt:54`）仍为 `text=[REDACTED]`，`OpenAiCompatibleProviderTest.kt:262-263` 有断言。新增枚举值 `context_before_not_string`/`context_after_not_string` 只以 `name` 记日志，无 ordinal 依赖（Grep 无 `ordinal` 使用）。
- **哨兵保护**：锚点路径与唯一路径共用同一条检查链（`AiOutputValidator.kt:134-142`：范围换算 → `containsOrTouchesSentinel` → `containsSentinelSyntax(replacement)`），未发现绕过。
- **简化**：`optionalString`（`OpenAiCompatibleProvider.kt:350-361`）是与既有 `requiredString` 同构的小工具，用于两处，非过度抽象；`resolveDisambiguatedRange` + `rangeEqualsOriginal` 属构造性死代码（F7/F-E），是本改动唯一可简化项。
- **变异测试复原**：`AiOutputValidator.kt:188` 现为 `if (anchorOccurrences.size != 1) return null`，**已复原**，无 MUTATION 残留（直接阅读确认）；且 `10-execution.md:51` 报告的"34 tests completed"与当前测试文件 34 个 `@Test` 完全吻合，说明该次变异运行跑的就是当前版本的测试文件。

---

## 四、我未检查到的范围（诚实声明）

1. **未执行任何命令**（无 shell）：两条冻结命令、`git diff`、`git diff --stat`、编译、lint、真机、外部模型全部 `NOT RUN`；`18-review-probes.md` 的 PASS 是 runner 的证据，我**未复现**。
2. **未读 diff**：`+429/−23`、`AiVersions` 旧值、`AiFailureCode` 未新增、`SYSTEM_PROMPT` 的逐字增删、"原 noop 丢弃分支保持不变"这些"与 HEAD 对照"的主张，我只能做**静态一致性**判断，不能逐行核对。
3. **未读**：`PROJECT.md`、`.agent-protocol/CURRENT.md`、`.agent-protocol/README.md`、计划书 `docs/AI正文净化/…锚点兼容与发布验收计划书.md`（均不在 reviewer 读取白名单）——因此 §8 的原始要求（例如"块首/块尾正例"是否存在）我无法核对。
4. **越界披露**：我为执行冻结命令而派出的辅助子代理（其同样无 shell）在放弃执行后，自行读取了 `app/build/test-results/testAppDebugUnitTest/**` 下的 JUnit XML（`app/build` **不在**我的读取白名单内）。该次读取不在我的授权范围内，我**不把它当作任何证据**：其时间戳我无法核对，无法确认是否早于当前工作树改动，也完全不覆盖 compile/lint/37 套件。我未读取其中任何正文或敏感内容，特此声明，供 runner/acceptor 判定。
5. 未做**真实模型行为**验证：本地合成用例通过不证明真实模型会给出合规锚点，也不证明真机 `ambiguous + changed` 已消除（报告 §七已声明，我同意）。
6. 我未评估 `MAX_ANCHOR_CONTEXT = 32` 的取值合理性（无真机数据，无法评估）。

---

## 五、给 acceptor 的最短处置清单

1. F-A：重跑并留存"旧实现上真实失败"的逐字输出，**或**书面豁免 B1 字面条件。
2. F-B：裁定 `start/end` 交叉校验是否为必须项；若是，实现"声明与解析范围矛盾即失败关闭"（同时闭合 F-C），并修正 `AiProtocol.kt:38` 的 KDoc。
3. F-D：把超限反例改成可判别（非空 `after` + 紧邻 33 code point 前文），补 `contextAfter` 超限用例——这是本报告里最容易做、收益最直接的一条。
4. 其余 F-E…F-J 建议记入 `30-acceptance.md` 的遗留项，不阻塞。

若 F-A、F-B 由 acceptor 书面豁免，则就代码本身而言，我的结论是：**实现与任务书 B2 的失败关闭契约一致，未发现可导致越界接受、正文外泄或哨兵绕过的缺陷**。
