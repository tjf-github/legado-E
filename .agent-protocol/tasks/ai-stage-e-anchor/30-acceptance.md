# ai-stage-e-anchor — 最终验收

状态：`accepted`

- **acceptor 运行实例**：人类启动的 DSH 会话（与 executor 同一实例；mode=dsh-claude 允许 controller/executor/acceptor 由该实例兼任）。独立复审方是**两个互不相同的 claude 会话**：round 1 `93e671ea-a684-4ecf-8e5f-bb7b4ba4b25c`、round 2 `92c05785-4bed-4862-992e-581b7db0c7a7`，均非本实例，原始报告分别归档于 `20-review.md` 与 `rounds/reviewer-r001/20-review.md`。
- **mode / assurance / route_revision**：dsh-claude / standard / 1
- **实际 diff**：6 个业务文件，`+429 / −23`（`git diff --stat -- app`），全部落在 `00-task.md` 写白名单内；`git diff --check` exit 0。白名单外未留任何业务改动（`git status` 仅这 6 个文件 + 协议文件）。
- **执行证据核对**：逐条核对 `10-execution.md` 与 `18-review-probes.md`，并在**复审冻结之后未再改动任何文件**（本验收只写任务目录内证据文件）。
- **独立复测（acceptor 亲自执行，均在被复审的同一修订上）**：
  - `:app:compileAppDebugKotlin :app:testAppDebugUnitTest` → `37 套件 / 247 项 / 0 failures / 0 errors / 0 skipped`；
  - `:app:lintAppDebug` → `BUILD SUCCESSFUL in 1m 22s`（**在测试修正之后重跑**，不是沿用旧结果）；
  - 冻结探针 `18-review-probes.md` → **PASS**，两条命令退回码 0，工作树前后 `stable`；
  - **变异测试**：把 `AiOutputValidator.kt:188` 的 `!= 1` 改为 `== 0` → `nonUniqueContextCombinationMustFailClosed FAILED`（已复原并 grep 确认）；
  - **旧实现基线**：HEAD 独立 worktree 上 `BaselineAnchorBehaviorTest` → `tests=1 failures=0`，`BUILD SUCCESSFUL in 3m 9s`。
- **可见行为**：**NOT RUN**——真机 D1/D2 不在本任务范围。
- **NOT RUN**：真机复测（D1）、发布矩阵（D2）、共存包构建与安装、提交、推送、合并、发布、外部模型调用与任何网络请求。

## 复审 findings 逐项处理（round 1 的 F1–F12 与 round 2 的 F-A–F-J）

**round 1（已在 round 2 前处理）**
- F1/F-A（**B1 字面证据**）→ **书面豁免**（理由见下「豁免与裁定」）。
- F2/F12（门禁不可复核、探针 FAIL、命令与冻结清单不一致）→ **已修复**：重建探针并加入 `OpenAiCompatibleProviderTest`，两条命令 PASS、工作树 `stable`、快照/命令哈希与内容自洽（`59c0236b…` / `d36e662d…`）。首两次 FAIL 的根因已查明并留证：①本机 `NoDefaultCurrentDirectoryInExePath=1` 使 `cmd /c gradlew.bat` 找不到脚本（非代码问题）；②探针 worktree 落在 C: 盘而 `GRADLE_USER_HOME` 在 D: 盘，触发 KSP `this and base files have different roots`（环境限制，已通过把 `TEMP`/`TMP` 指向 D: 规避）。
- F3（头号正例 `original` 只出现一次）→ **已修复**：改为空格出现两次的输入，并追加"同输入去上下文必须失败关闭"断言。
- F4（组合仍不唯一分支零覆盖）→ **已修复 + 变异验证**。
- F5（Provider 缺失/null 无正例）→ **已修复**：补两例。
- F6（哨兵用例名不副实）→ **已修复**：改名并注明锚点路径不可达。
- F7（恒真校验被当作验证强度）→ **已在执行报告中更正为"不可达的不变式断言，不计入验证强度"**。
- F8（`start/end` 交叉校验与实际不符）→ **已更正表述**；并在本轮由 acceptor **裁定为不作为本任务必须项**（见「豁免与裁定」F-B）。
- F9（"未新增任何枚举值"不准确）→ **已更正**为"未新增章节级失败码"。
- F10（实现期越界文件）→ 见「边界与越界披露」。
- F11（两侧非空导致块首无法消歧）→ **照实披露**，作为未达成的完成条件记入遗留项。

**round 2**
- F-A → 书面豁免（见下）。
- F-B（`start/end` 交叉校验不存在，且 `AiProtocol.kt:38` 的 KDoc 断言不成立）→ **裁定为本任务非必须项 + KDoc 修正列入后续**（见下）。**不在本任务内改代码或 KDoc**：那会破坏复审冻结（`close` 会判"复审后又变化"），且 strict 等式校验有回归风险。
- F-C（组合唯一路径可能在**另一处**相同 `original` 静默落点并判 Completed）→ **如实披露并接受**：该行为**符合**任务书 B2 授权的"组合唯一性"契约，不属越界；但它是本改动唯一的语义副作用（原本失败关闭的输入现在可能改到别处），已列为**后续任务第 1 项**（窄化交叉校验：仅当"模型声明的块内范围本身逐字等于 `original`"且与解析范围矛盾时失败关闭）。
- F-D（超限反例不可判别、`contextAfter` 超限无用例）→ **后续任务第 3 项**（易做、收益直接）。属测试覆盖缺口，非生产缺陷：`MAX_ANCHOR_CONTEXT` 的拒绝分支存在且行为正确，只是缺少能让它变红的用例。
- F-E（三处注释声称覆盖不可达检查）→ 后续任务第 3 项（改注释为"覆盖组合不存在/不唯一分支"）。
- F-F（锚点路径缺补充平面 `original` 正例）→ 后续任务第 3 项。
- F-G（Prompt 的 "32" 与 `MAX_ANCHOR_CONTEXT` 无机械绑定）→ 后续任务第 4 项（常量插值 + 一致性断言）。
- F-H（门禁数字不自洽：首次 248 项 → 现 247 项）→ **已解释**：首次运行包含实现子代理遗留的临时诊断测试 `TmpAnchorDiagnosticTest`（1 项），该文件因属白名单外已被删除，故 248 − 1 = 247；现 37 套件 / 247 项与当前树一致。门禁由 controller 留证是 `00-task.md:41` 明确的分工，且冻结探针在独立快照上重跑了同一批 AI 测试类（PASS）。
- 其余低危项 → 与 F-D…F-G 合并记入后续任务，不阻塞。

## 豁免与裁定（必须显式留痕）

1. **F-A 豁免：B1"旧实现上真实失败"的字面条件不成立**。理由：①新用例引用旧协议不存在的字段，在旧实现上只能得到**编译级**失败，不存在断言级失败输出；②**断言级基线已经真实跑过**（HEAD 独立 worktree、旧 `AiProtocol.kt` 中 `contextBefore`/`MAX_ANCHOR_CONTEXT` 命中数为 0、`BaselineAnchorBehaviorTest` `tests=1 failures=0`、`BUILD SUCCESSFUL in 3m 9s`），数字已记入 `10-execution.md` 第四节；③该 worktree 与基线用例文件**按任务书"收尾前必须清理临时产物"的要求删除**，这正是 reviewer 无法用 Glob 找到它们的原因——不是没有跑过；④"能否区分实现错与测试数据错"这一实质关切已由**变异测试**（改掉分支即变红）与**同用例内的新旧对照断言**覆盖。故按 F-A 建议的第二个选项**书面豁免**，并列为后续任务第 5 项（若需字面满足，应在 HEAD worktree 内跑新用例并留存编译级失败输出）。
2. **F-B 裁定：`start/end` 交叉校验不作为本任务必须项**。理由：①该措辞描述的是**既有实现（HEAD）从未有过**的行为——HEAD 对非空 `original` 从不读取 `start/end`，故这是任务书措辞不准，**不是本次改动移除的能力**；②直接加 strict 等式校验会在模型偏移不精确时拒绝本可正确定位的编辑，**可能使本次修复在 D1 真机上反而失败**，属于在缺少真机偏移数据时的冒进；③F-C 的正确解法是**窄化**校验（仅在"声明范围本身逐字等于 `original` 且与解析范围矛盾"时失败关闭），列入后续任务第 1 项；④`AiProtocol.kt:38` 那条不成立的 KDoc 必须修正，但**不能在复审后改业务文件**（会破坏冻结），故列为后续任务第 2 项。

## 边界与越界披露

- **写白名单被突破过一次**：实现子代理曾创建白名单外的 `TmpAnchorDiagnosticTest.kt`（一度同时出现在 main 与 test 源集）。已由 controller 删除，并被 reviewer 独立 Glob 确认无残留。这是"写白名单是硬上限"的一次真实违反，留痕备查。
- **reviewer 自己的越界读取**：round 2 的 reviewer 声明其派生辅助子代理为执行冻结命令而读取了 `app/build/test-results/**`（不在其读取白名单内）。该读取未被用作任何证据，内容为本地构建产物；在此照实登记，供后续判定读取边界的严格程度。
- **数据边界**：全程未读取凭据/Token、`keystore*`、`look.ps1`、真实书源与正文、用户缓存或未脱敏日志；本任务零外发。

## 最终判定

`accepted`

- **理由**：实现与任务书 B2 的失败关闭契约一致，两轮独立复审均**未发现**可导致越界接受、正文外泄或哨兵绕过的功能缺陷；C 门禁在最终修订上由 acceptor 亲自复跑（编译 + 37 套件/247 项 + lint + `git diff --check`），冻结探针在独立快照上 PASS 且工作树稳定，关键分支有**变异测试**证明被真实覆盖，旧实现行为有**独立 worktree 基线**留证。两条阻塞项（F-A/F-B）已按 reviewer 明示的选项书面豁免/裁定，其余全部转为后续任务。
- **未达成项（如实登记，不掩盖）**：①B1 的"块首/块尾（单侧上下文）正例"在设计上无法成立——两侧非空是本次的有意收窄（安全方向），代价是块首重复锚点无法用上下文消歧；②B1 的"旧实现上真实失败"字面条件（见豁免 1）；③F-C 的语义副作用（见后续任务第 1 项）。
- **唯一下一步**：**D1 受控真机复测**（用户已授权外发范围：沿用 App 内已配置的 AI 服务与模型、1–2 个极短章、最多 3 次真实请求），另立任务 `ai-stage-e-device` 执行；D2 发布矩阵在其后。
- **提交 / 推送 / 发布状态**：**未提交、未推送、未合并、未发布**（按协议，提交须在 `close` 之后另行授权）。发布判定维持 **review / NOT READY**——真机证据缺失，且 `MAX_ANCHOR_CONTEXT = 32` 未经真机数据校准。

## 后续任务（按优先级，均需另立协议任务）

1. **窄化 `start/end` 交叉校验以闭合 F-C**：仅当模型声明的块内范围本身逐字等于 `original` 且与解析范围矛盾时失败关闭；配 F-C 的可复现合成反例（`"甲很好，甲很坏"`）。**最高优先**——它消除"静默改到另一处"这一最危险的副作用方向。
2. **修正 `AiProtocol.kt:38` 的 KDoc**：删除"必须与 `[start, end)` 区间逐值相等（本机锚定校验）"这一不成立表述。
3. **测试加固**：F-D（超限反例改为可判别 + 补 `contextAfter` 超限）、F-E（注释改为真实覆盖分支）、F-F（锚点路径补补充平面 `original` 正例）。
4. **F-G**：Prompt 的 32 上限改为由 `AiEdit.MAX_ANCHOR_CONTEXT` 插值生成，并加一致性断言。
5. **F-A 补强（可选）**：如需字面满足 B1，在 HEAD 一次性 worktree 内跑新用例并留存编译级失败输出。
