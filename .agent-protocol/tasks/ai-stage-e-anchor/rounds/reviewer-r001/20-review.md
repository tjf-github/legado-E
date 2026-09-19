# ai-stage-e-anchor — 独立复审报告（reviewer: claude）

复审对象：`00-task.md`（任务书）、`10-execution.md`（执行报告）、`06-review-commands.json`（冻结命令）、`18-review-probes.md`（冻结探针真实运行结果）、`rounds/executor-r001/**`，以及白名单内实际工作树：`app/src/main/java/io/legado/app/help/ai/**`、`app/src/test/java/io/legado/app/help/ai/**`。

## 一、结论

**有阻塞项（不建议按现状验收）。**

- **代码层面未发现功能缺陷**：我逐条手工推演了新增/改动的全部判定路径（`resolveContextAnchor` / `resolveDisambiguatedRange` / 重复锚点分支 / Provider 严格解析），没有找到能让组合锚点路径接受一个「错位置/越界/重叠/触碰哨兵」编辑的输入。改动方向是**净增放行路径**，不会让任何原本成功的输入变成失败。
- **阻塞项在证据与测试效力，不在算法**：①B1 完成条件明文要求的「旧实现上真实失败并保留失败输出」没有任何留证；②C 门禁的 Gradle 结果无法复核，且唯一冻结探针记录为 `FAIL`；③若干净跑，仍有若干新增用例**不经过其声称的被测路径**（含被报告列为「组合唯一正例」的头号用例），B2 的一条关键失败分支**零覆盖**。

## 二、我实际执行的核对动作与输出

只读方式；**未执行任何命令**（本 reviewer 实例没有 shell 工具，冻结命令由 runner 在一次性 worktree 中执行并记入 `18-review-probes.md`，我以该文件为执行侧证据来源）。

1. 完整读取任务书、执行报告、路由、冻结命令、探针文件、round-1 归档（`10-execution.md` + `15-executor-raw.md`）。
2. 完整读取 4 个被改动的生产文件 + 3 个测试文件；另读 `AiChapterProcessor.kt`、`AiChapterCache.kt`、`AiTextChunker.kt`、`AiReaderAccess.kt`（判断新字段是否进缓存身份/日志的必经链路）。
3. Grep `contextBefore|contextAfter|context_before|context_after|MAX_ANCHOR_CONTEXT`（main + test 的 ai 目录）：命中仅 `AiProtocol.kt`、`AiOutputValidator.kt`、`OpenAiCompatibleProvider.kt` 三个生产文件与两个被改测试文件；**未出现在缓存序列化（`GSON.toJsonTree(identity)` 只含 `AiCacheIdentity`）与日志路径中**。
4. Grep `LogUtils\.|AiEdit\(|fromJson`：`OpenAiCompatibleProvider.kt:57`、`AiOutputValidator.kt:53`、`AiReaderAccess.kt:72` 只记固定枚举/detail 字符串，**不含新增字段值**；`AiEdit.toString()`（`AiProtocol.kt:54`）仍为 `text=[REDACTED]`。
5. 用正则 Grep 做**决定性字符计数**：`他走得很快，他  +说得更好`（2 个及以上空格）无匹配 → `AiOutputValidatorTest.kt:370` 字面量中空格**恰好一个**（这是发现 F3 的关键证据）。
6. Glob 两个源码目录，独立确认报告称已删除的越界临时文件 `TmpAnchorDiagnosticTest.kt` 在 main/test 两侧**均已不存在**。
7. 逐例手工执行（心算 trace）`AiOutputValidatorTest.kt:367-566` 与 `OpenAiCompatibleProviderTest.kt:249-303` 的全部新增用例：能跑到的断言我判定为通过；其中 4 例因**走了别的分支**而通过（F3/F4/F6），非其命名所述。
8. 统计 `@Test`：AI 包 22 个类共 146 项；报告称全量 `:app:testAppDebugUnitTest` 为 37 套件/247 项——量级自洽，但**无法复核**（见 F2、局限）。

## 三、发现

### F1 —【阻塞·证据】B1 完成条件未达成：没有任何「旧实现上真实失败」的保留输出

- 位置：`00-task.md:59`（B1 要求「保留失败输出，不允许只写结论」）；`10-execution.md:49`（自述：仅编译级证据，**未做**断言级复现）。
- 事实：`10-execution.md` 与 `18-review-probes.md` 中都没有任何旧实现上的失败输出（无编译错误文本、无断言失败文本）。「在旧实现上真实失败」只以自述形式存在。
- 更实质的问题：报告列为头号正例的 `resolvesRepeatedChangedAnchorFromUniqueContextCombination`（`AiOutputValidatorTest.kt:367-379`）**在旧实现上也会通过**——其 `original = " "` 在 `"他走得很快，他 说得更好"` 中只出现 1 次（Grep 决定性验证），走的是既有的唯一子串路径，与上下文锚点无关。也就是说，该用例连「新旧行为不同」都不成立，无法承担 B1 的基线作用。
- 具体失败场景：若某条新用例其实写错了期望值（如 round-2 已修 3 处那样），由于不存在旧实现基线输出，reviewer/acceptor 无法区分「实现错」与「测试数据错」，只能采信执行者自述。
- 建议：提供断言级基线证据——把生产改动临时回退（或在独立 worktree 上用 HEAD 版本）跑新增用例并**逐字留证**；至少给出「旧实现下结果为 X，新实现下为 Y」的对照输出。另建议把 B1 正例改为**原始串在块内确实重复**的输入。

### F2 —【阻塞·证据】C 门禁的 Gradle 结果不可复核，且冻结探针记录为 FAIL，探针命令与冻结命令不一致

- 位置：`18-review-probes.md:9`（`总结：FAIL`）、`:25-36`（`ai-anchor-unit-tests` 退出码 1，`'gradlew.bat' 不是内部或外部命令`）；`10-execution.md:35-36`（声称 `BUILD SUCCESSFUL`、`37 套件 / 247 项 / 0 failures`、lint `BUILD SUCCESSFUL in 7m 40s`）；`06-review-commands.json:9-15`（冻结为 `".\\gradlew.bat"`）。
- 事实：探针记录的命令行是 `cmd.exe /c gradlew.bat ...`（**无 `.\` 前缀**），与当前冻结参数数组不一致 → 该探针文件是在冻结命令被修改**之前**生成的，而修改后的运行结果**没有任何文件留存**。报告写「改为 `.` 前缀后重跑，结果以该文件为准」，但该文件里只有失败那次，且总评为 `FAIL`。
- 具体失败场景：`:app:compileAppDebugKotlin` / `:app:testAppDebugUnitTest` / `:app:lintAppDebug` 三者只要有一项实际未通过（甚至根本未成功执行），本轮证据链无法暴露；C 是本任务唯一的机器门禁，其可信度完全依赖执行者自述。
- 建议：按当前冻结数组重跑并把原始输出（`BUILD SUCCESSFUL` + 计数行）贴进证据文件，或重新生成 `18-review-probes.md`，使命令哈希与内容自洽。

### F3 —【中·测试效力】「组合唯一正例」未经过被测路径

- 位置：`AiOutputValidatorTest.kt:367-379`（`resolvesRepeatedChangedAnchorFromUniqueContextCombination`）。
- 事实：`original = " "` 在 `"他走得很快，他 说得更好"` 中只出现一次 → `AiOutputValidator.kt:123` 走 `occurrences.single()`，`resolveContextAnchor` 从未被调用（注释「‘他’在块中出现两次」说的是 `他`，不是 `original`）。该用例实际验证的是「模型 start/end 不准时按唯一 original 重建」，这在旧实现上已通过。
- 建议：改成原始串确实重复的输入（如 `"他 走得快，他 说得更好"` + `contextBefore="他"`/`contextAfter="说"`），并断言若移除锚点必须失败关闭。

### F4 —【中·覆盖缺口】B2 明确要求的「组合仍不唯一 → 整章失败关闭」零覆盖

- 位置：`AiOutputValidatorTest.kt:432-443`（`nonUniqueContextCombinationMustFailClosed`）与 `:445-456`（`contextLongerThanLimitMustFailClosed`）都用 `contextAfter = ""`，因而在 `AiOutputValidator.kt:184`（`afterCp == 0` 提前返回）就已返回 null，**永远到不了** `AiOutputValidator.kt:188` 的 `anchorOccurrences.size != 1` 分支。
- 具体失败场景：把 `if (anchorOccurrences.size != 1) return null` 改成 `if (anchorOccurrences.size == 0) return null`（即组合重复时取第一个匹配）——**现有全部用例仍然全绿**，而「组合仍不唯一必须失败关闭」这条完成条件已被破坏。这正是该任务要防的「用不可靠定位改错句子」。
- 建议：补一条两侧上下文都非空、组合在块内出现两次的用例（例：`"他好。他好。他坏"` + `original="好"` + `before="他"` + `after="。"`），断言 `ANCHOR + ambiguous(changed)`。

### F5 —【中·覆盖缺口】Provider 侧「字段缺失/null → 视为未提供」无正例，报告的覆盖声明偏大

- 位置：`OpenAiCompatibleProviderTest.kt:249-303`；报告声明见 `10-execution.md:23`（「`context_before/after` 缺失、null、非字符串（失败关闭）与合法解析用例」）。
- 事实：该测试只断言了**非字符串**两种（`:281`、`:285`）。**缺失**与**显式 null** 两种情形没有任何断言；唯一能成功解析 `edits` 的用例（`:250`）恰恰是**带**上下文字段的那个。我 Grep 确认 `OpenAiCompatibleProvider` 的真实解析只在这一个测试文件里被驱动（其余测试用 FakeProvider）。
- 具体失败场景：把 `optionalString` 改成「字段缺失即 `throw ProtocolDetail(...)`」，全部现有测试仍绿，但真实模型一旦省略这两个可选字段，**整章都会 PROTOCOL 失败**（生产侧可从「偶发失败」恶化到「全线失败」）。
- 建议：补三例——字段缺失 → `contextBefore == null` 且解析成功；显式 `null` → 同上；合法字符串 → 赋值正确。

### F6 —【低·测试效力】「触碰哨兵」反例不在锚点路径上

- 位置：`AiOutputValidatorTest.kt:472-488`（`resolvedRangeTouchingReplacementSentinelMustFailClosed`）。
- 事实：`original = "编"` 在 protect 后的文本中唯一 → 走唯一子串路径；其 `contextBefore = ""` 在锚点路径下会直接失败关闭，说明作者意图在锚点路径，实际测的是 `AiOutputValidator.kt:139` 的 replacement 哨兵语法检查。B2 要求的「锚点路径下触碰哨兵必须失败关闭」未被真正覆盖。
- 说明（对实现有利的一点）：我推演后认为该路径**不可达**——锚点解析出的范围必然逐字等于 `original`，而含哨兵的 `original` 必然唯一（哨兵不含重复），因此不可能从锚点路径触达哨兵重叠。建议把该用例改名为其真实覆盖面，或删除以免误导。

### F7 —【低·简化】`resolveContextAnchor` 中 4 处校验恒真，属死代码；报告把它当作额外验证

- 位置：`AiOutputValidator.kt:192`、`:198`、`:200`、`:201`，以及 `:164`（`rangeEqualsOriginal`）。
- 事实：锚点已在 `:187-189` 以 code point 精确匹配到唯一位置，`anchor = before + original + after` 的切分是**按构造**精确的，因此 `startsWith(anchor, …)`、`substring != original`、`startsWith(before, …)`、`startsWith(after, …)` 都不可能为 false；`originalCharStart < 0` / `originalCharEnd > text.length` 也不可能成立（我按 code point 偏移推演过）。报告 `10-execution.md:28` 把「startsWith 双重校验 + 解析区间逐字相等」列为独立保证，实为同义反复。
- 建议：保留可以，但请改注释为「不可达的不变式断言」，或直接删除；不要把它计入验证强度。

### F8 —【低·偏离任务书措辞】模型 `start/end` 在锚点路径上完全未参与，连交叉校验也没有

- 位置：`AiOutputValidator.kt:175-203`（全函数不读 `edit.start/end`）；任务书 `00-task.md:9` 要求「模型 `start/end` 仍只作交叉校验，不得单独消歧」。
- 事实：实现是「不消歧、也不校验」，比任务书措辞少了一道拒绝理由（方向偏松）。执行报告只声明了「未使用 start/end 消歧」，未声明「交叉校验被取消」。
- 建议：要么补上交叉校验（解析出的范围与模型声明范围不一致时失败关闭），要么在证据文件里显式写明该交叉校验被有意移除及其理由。

### F9 —【低·事实准确性】「未新增任何枚举值」不成立

- 位置：`OpenAiCompatibleProvider.kt:46-47` 新增 `context_before_not_string`、`context_after_not_string`；报告声明见 `10-execution.md:29`。
- 说明：想表达的「未新增 `AiFailureCode`」是对的，字面表述不准确。建议改为「未新增章节级失败码，复用 `ANCHOR + ambiguous(changed)`」。

### F10 —【低·过程】实现期越界文件已清理，无残留（此项闭合）

- `10-execution.md:50` 自述实现子代理曾创建白名单外的 `TmpAnchorDiagnosticTest.kt`（main 与 test 两侧）。我用 Glob 独立确认两侧当前均无该文件；会话起始 `git status` 快照中也只有 6 个白名单内业务文件 + `.agent-protocol/CURRENT.md` + 任务目录。
- 结论：越界已由 controller 清除，**不构成本轮阻塞**，但这是「写白名单是硬上限」被突破过的一次实例，建议在 `30-acceptance.md` 中留痕。

### F11 —【低·规格偏离】「两侧都必须非空」永久排除了块首元素的消歧

- 位置：`AiOutputValidator.kt:184`；自述见 `10-execution.md:48`。
- 事实：`contextBefore` 必须非空 ⇒ 位于块首（或左侧无可取字符）的重复元素**在任何情况下**都无法通过上下文消歧（该位置根本没有左侧字符可复制）。模型只能改用延长 `original` 的迂回手段。这是相对「短前文/短后文锚点」设计意图的**能力收窄**（安全方向，故非高危），但你我都无法从白名单内文件确认计划书 §8 是否要求块首/块尾正例——`10-execution.md:48` 提到任务书曾有此要求，而 `00-task.md:7-11` 的 B1/B2 措辞中并无此句（我未读计划书，见局限）。

### F12 —【中·证据设计】冻结探针没有覆盖 Provider 解析改动

- 位置：`06-review-commands.json:20-26` 只跑 `AiOutputValidatorTest` 与 `AiNoChangeTest`（合计 44 项）；B3 的 Provider 严格解析与 Prompt 同步改动全部落在 `OpenAiCompatibleProviderTest`（12 项）里，**不在任何冻结的可执行检查中**。
- 建议：把 `OpenAiCompatibleProviderTest` 加入冻结 `--tests` 列表。

### 未发现问题的方面（我确实查过）

- **安全**：改动不含任何 I/O、Shell、SQL、HTML 拼接、反射或权限调用；新增的两个字段只参与与块文本的本地比较，不进请求体（`requestBody` 只序列化 `chunk`/`model`/`messages`）、不进缓存身份（`AiCacheIdentity` 未变）、不进日志或异常（`AiEdit.toString()` 与 `PROTOCOL` detail 均为固定串）。`AiProviderSecurity`/redirect/同源判定未被触碰。**未发现命令注入、路径穿越、XSS、SQL 注入、凭据外泄或权限放大。**
- **越界**：改动的 6 个文件与 `10-execution.md:14` 表格一致，且全部在 `00-task.md:43-50` 写白名单内；`.agent-protocol/CURRENT.md` 的修改在 round 1 之前即已存在（`rounds/executor-r001/10-execution.md:67` 记录其 mtime 为 controller 激活任务时），非本轮产物。
- **缓存失效**：`AiCacheKey.kt:48/50` 已把 `PROMPT` v4→v5、`VALIDATION` v6→v7；两个被 bump 的常量确实参与 `AiCacheKey.create`（`:29`、`:34`），旧缓存会自然失效；测试里没有硬编码版本串，不会因此变红。
- **回归面**：锚点路径仅在 `original` 在块内出现 >1 且 `original != replacement` 时启用（`AiOutputValidator.kt:100-122`），该分支旧行为是失败关闭；新代码只可能把「失败」变「成功」，不会把「成功」变「失败」。其余 21 个 AI 测试类不传上下文（`contextBefore/contextAfter` 在 test 目录仅出现在两个被改文件），行为与改动前一致。

## 四、我未检查到的范围（不要当作已覆盖）

1. **未执行任何命令**：`git diff --check` 与单测/编译/lint 全部 **NOT RUN**（我无 shell）；这两项我唯一能引用的是 `18-review-probes.md`，而其中单测项为 FAIL。
2. **未读取 diff 本身**（无 `git diff`/`git show`）：`+397 / −23`、7 个文件的逐 hunk 对照**无法核对**；我的结论基于当前完整文件内容 + 会话起始 `git status` 快照，理论上无法排除「报告未提及的其它 hunk」。
3. **未读取白名单外任何路径**：`PROJECT.md`、`AGENTS.md`、计划书 §8、`20-review.md`、`30-acceptance.md`、`.review-snapshot.json`、非 AI 包源码与测试均未读。因此：无法确认 `AiEdit` 是否在 AI 包之外还有构造点（编译门禁本可覆盖，但我跑不了）、无法确认 B1 的块首/块尾正例是否确为计划书要求、无法确认 `247 项` 的构成。
4. **未验证复审冻结哈希**：`00-task.md`/`10-execution.md` 在窗口内是否被改动、探针快照 `3cc2942e…` 与命令清单 `4ba45c47…` 是否与实际一致，均无法计算。
5. **手工 trace 不等于运行**：第三节中我判定「通过」的用例是心算结果，未经 JVM 执行；若某条用例实际失败，F2 已说明现有证据无法暴露它。
6. **真机/D1/D2 未涉及**：本轮不评。另注意 `MAX_ANCHOR_CONTEXT = 32` 未经真机数据校准（`10-execution.md:61` 已自述），我无法判断真实模型是否会因其整章失败。

## 五、给 controller/acceptor 的最小放行清单

1. 按当前 `06-review-commands.json` 重跑并留原始输出（或重建探针文件），把 `AiOutputValidatorTest`+`AiNoChangeTest`+**`OpenAiCompatibleProviderTest`** 纳入（F2、F12）。
2. 补 B1 的断言级基线输出（F1）。
3. 修 F3/F4/F6 三处名不副实的用例，补 F5 的缺失/null 正例；F4 是其中唯一「删掉实现分支仍全绿」的实质缺口。
4. F8/F9/F11 请在证据文件里如实改写，而不是改代码。
