# AI 正文净化：锚点兼容与发布验收计划书

状态：`review / logging prerequisite accepted, anchor changed blocked, not ready`。更新日期：2026-09-08。
本文件是阶段 E 后续执行入口；历史结果见《AI正文净化-阶段E-Codex独立审查.md》。本次仅制定计划，不授权自动启动外部模型或直接发布。

## 1. 目标与基线

目标：有错章能安全修正，无错章能完成并显示“无需修改”，失败时原文始终可读；据真机证据决定发布。

- 分支 `codex/manga-import`，当前核对 HEAD `d4aa1fcef`、相对 `origin/codex/manga-import` ahead 6；大量 C/D/E 未提交改动必须保留。每轮开工仍须重新核对实际 status/log，不能覆盖共享工作区。
- 无变化兼容已经实现，Prompt v4 / validation v6；AI 协议候选的 32 套件 / 222 项单测与 lint 通过。随后日志 P0/P1/P2 候选在包含当前共享改动的工作区完成 36 套件 / 233 项、lint、release 构建和真机验收；日志验收不能替代 AI 阶段 E 的真实章节验收。
- 日志系统 P0/P1/P2 已于 2026-09-08 accepted：结构化级别/tag、统一门面、内存/文件查看、组合筛选、复制与导出均已落地。提交为 `46b326680`、`453355a1a`、`d4aa1fcef`；P3/P4 仍是独立候选，不再作为恢复 AI 工作的前置条件。
- 共存测试版 `io.legado.app.tjf.releaseS` / `3.26.090623` 已保留数据安装。
- 最新独立真机结果：为 `payload_json_syntax` 增加严格 JSON/EOF 与固定脱敏语法子类后，真实链路未再复现任何 payload syntax 子类；记录开启后的 3 个失败实例均为 `ANCHOR anchor=ambiguous edit=changed`（块号 3、3、2）。旧的单次 `payload_json_syntax chunk=3` 仍不能解释具体载荷，也不能作为放宽 JSON 的依据，尚无整章 Ready。

## 2. 执行顺序与交付

| 步骤 | 工作 | 完成条件 |
| --- | --- | --- |
| A：先定位 | 为 ANCHOR 增加固定 `ambiguous / missing` 子类，并附 `noop / changed` 固定标识和块号；用可注入 reporter，不记录编辑文本或异常内容 | 合成测试证明分类准确、载荷无正文/密钥；真机取得一次本轮分类 |
| B：按证据修正 | 只实施下方对应分支，先反例后代码；同步 Prompt/校验版本和缓存失效 | 针对性测试证明正例可完成、错误位置不会被应用 |
| C：集中验证 | 合并必要的小改动后一次跑编译、全量单测和 lint；再构建共存包验证 | 命令全部成功，测试数量与 APK 哈希入记录，测试包源码改动已恢复 |
| D：发布判定 | 完成下方真机清单，核对真实 diff 和证据 | 全部必测项通过后才转 `accepted`；否则写清阻塞及唯一下一步 |

**当前唯一下一步：按已经可复现的 `ambiguous + changed` 进入步骤 B，先为“唯一精确上下文锚点”补合成正反例，再决定是否扩展编辑协议；旧的 `payload_json_syntax` 未复现，不做宽松 JSON 或 Provider 猜测性修正。`ambiguous + noop` 最小兼容、严格 JSON 脱敏诊断和 222 项完整门禁已完成；未经新授权不再外发真实章节。**

日志前置已经关闭，不再回头重做 P0/P1/P2。后续 AI 诊断统一走结构化 `AppLog` / `LogTag.AI` 和新日志查看器；`recordLog` 继续默认关闭，只有在有明确真机假设时临时开启，取证后恢复。允许入证据的仍只有固定失败枚举、编辑效果、1-based 块号和 PASS/FAIL/NOT RUN，禁止正文、响应体、锚点文本、书名、URL、密钥、长度、哈希和异常 message。

## 3. 分类后的最小方案

- `ambiguous + changed`：评估用模型返回的短前后文与 original 组成**唯一精确上下文锚点**，本机据此定位并仅替换 original。上下文只用于定位，不进入替换；仍不唯一就整章回退。不能重新相信模型数字下标。
- `missing + changed`：先用合成样本检查 Unicode、换行和复制协议；要求 original 是原块精确子串。禁止模糊匹配、去空格后匹配或找近似字替换。不证明精确位置就保持失败。
- `ambiguous + noop`：这是不改字的独立兼容分支。可评估非空原串确实存在、类别及长度合法、且所有出现位置都不触碰保护项时丢弃该条；其余真实编辑仍完整校验。先固化新契约及反例，不能顺带放宽 changed。
- `missing + noop`：说明模型回显了本块不存在的内容，不能据此认定检查完成；保持失败并修请求/响应契约。
- 合法空 edits 或全部经过批准规则归一化的安全 noop：检查成功，可缓存“无需修改”。空响应、截断、错误块 ID、非法类别始终失败。
- 若需要更换模型、扩大正文外发范围或改变“非法编辑导致整章回退”的语义，另列决策，不能由执行者悄悄改变。

## 4. 最小测试集

1. 重复“的”且下标碰巧命中错误句子；唯一上下文正例、重复/伪造上下文反例。
2. emoji、补充平面汉字、组合字符、零宽字符、换行；缺失锚点不得近似定位。
3. 合法空列表、全 noop、noop 混合真实修正、noop 混合非法编辑；空占位继续拒绝。
4. URL/数字/哨兵、重叠和乱序不被绕过；伪造空 original 的非空删除范围必须拒绝。
5. 第二块失败不请求后续块、不留下 Completed；无修改缓存命中不重复调用。

命令：先设置 `$env:GRADLE_USER_HOME='D:\gradle_home'`，再执行：

```powershell
.\gradlew.bat :app:compileAppDebugKotlin :app:testAppDebugUnitTest :app:lintAppDebug --console=plain
git diff --check
```

开发中先跑相关测试；只在最终候选上集中跑全量门禁。发生新改动或失败才重跑相关检查。

## 5. 真机与发布门槛

目标设备固定 `10CE5P1M1Z001P9`，只用 releaseS 共存包 `install -r`；禁止卸载、清数据或覆盖其它应用。打包临时 suffix/权限改动须 finally 恢复并确认 diff 为空。

必测：

- [ ] 至少一章真实正文完整 Ready；确认有限修正有效且原文可切回。
- [ ] 无错章完成“无需修改”，不制造改字、不跳页；重进命中缓存不重复请求。
- [ ] 当前失败章复测；失败能读原文、重试有效，无无限重试。
- [ ] 断网/慢响应/认证失败/限流；切章、取消、退后台、旋转无迟到覆盖。
- [ ] 清 AI 缓存保留原文；本地 TXT/EPUB、漫画/图片、朗读/搜索/导出无回归。
- [ ] 总开关/逐书开关、设备外发确认、阅读位置及原文缓存边界通过。

每项只记 PASS/FAIL/NOT RUN 与简短证据路径；未测不能当通过。Ready 只证明整章按协议处理完成，不证明模型能发现所有文字问题。

全部满足后按《发布推送流程书.md》§4 核对实时分支与 CI 触发条件，完成提交、合并及发布验证。更新日志与 beta/正式版的关联按流程书执行，不能为了写日志误触正式发布。未满足则保持 `review`，不因模型原因降低门槛。

## 6. 节省上下文与收尾

- 不重读历史长报告；先读本计划，必要时只查审查记录最后一节和目标模块。
- 不启动额外 agent/DSH，不反复输出日志；失败只提取固定分类，禁止正文、密钥、URL、书名或完整响应入日志/报告。
- 每轮真机重试须有明确待验证假设；相同分类连续两次且无新信息即停止盲试，回到合成测试。
- 原始命令日志放 `app/build/`；只在审查记录末尾追加结果，同步本计划状态、总计划 §14 和维护计划，不再新建多份重复报告。
- 本轮创建计划后停止执行，保留剩余额度；下次从 A 继续。

## 7. 2026-09-07 执行记录

- 步骤 A 的代码部分已完成：`ANCHOR` 失败现可附固定 `ambiguous / missing` 与 `noop / changed` 枚举，沿 Validator → Processor → Coordinator → reporter 传递；release 日志格式只含稳定枚举与 1-based 块号，不含正文、响应体、异常内容、URL、书名或密钥。严格唯一精确锚点规则未放宽。
- 合成反例覆盖四种组合及 Processor/reporter 边界；定向 4 套件 / 42 项通过。集中门禁 `:app:compileAppDebugKotlin :app:testAppDebugUnitTest :app:lintAppDebug` 为 `BUILD SUCCESSFUL in 7m 25s`，32 套件 / 217 项 / 0 failures / errors；`git diff --check` 为 0。
- `:app:assembleAppRelease` 共存构建 `BUILD SUCCESSFUL in 6m 56s`。APK：`app/build/reports/ai-stage-e/legado_anchor_classification_releaseS.apk`，SHA-256 `A54DD2160CEA2C50E7CF8F1E026DA2635E31FF698C0A40F425666DCE6B5CF7A7`。目标设备 `10CE5P1M1Z001P9` 上 `adb install -r` 成功，包名 `io.legado.app.tjf.releaseS`，`versionName=3.26.090709` / `versionCode=26090709`；未卸载、未清数据。临时 `.releaseS` / `${applicationId}.READ_WRITE` 源码改动已恢复，两个文件无实质 diff。
- 真机冷启动、进入阅读页与 AI 操作面板已确认。一次 UI 重试没有取得本轮固定失败码，菜单随后仍显示“未处理”，不能据此判断请求是否发送、成功或失败。继续点击会把真实章节正文发送到用户配置的外部 AI 服务；本轮对话未明确授权具体目的地和数据范围，故停止，不以旧截图或旧分类冒充本轮证据。
- 发布判定仍为 `review / NOT READY`：真实章节 Ready、无需修改缓存、网络/生命周期/缓存/朗读及非在线章节清单仍未完成，未提交、未推送、未合并、未创建 release。
- 用户明确授权将当前所选在线文字章节按既有哨兵保护/分块协议发送到 `https://api.deepseek.com`、使用应用当前配置模型，且日志不记录正文、响应体、书名、URL 或密钥。设置页随后确认“仅非计费网络”实际为关闭；先前据网络能力推断请求被该策略阻止不成立，已更正。
- 首次真实请求到达第 3 块并以笼统 `PROTOCOL` 失败。随后为 Provider 增加固定、无载荷的协议失败子类及注入测试；定向 `OpenAiCompatibleProviderTest` 通过。新共存包 `app/build/reports/ai-stage-e/legado_protocol_detail_releaseS.apk` 构建并 `install -r` 成功，SHA-256 `4138D8A555BB2EB510ABBBA80566D8049065ABD3A56B328F6FE25D8CCBE68C40`；临时 suffix/权限源码改动已恢复。
- 用户授权的上午最后一次外发复测只触发一次。固定日志取得 `ai-protocol detail=payload_json_syntax` / `ai-fail code=PROTOCOL chunk=3`，并取得本计划目标分类 `ai-fail code=ANCHOR anchor=ambiguous edit=noop chunk=1`；未记录正文或响应载荷。步骤 A 的真机分类目标完成，但章节整体仍失败关闭并保留原文。
- 最新协议诊断改动之后仅跑了 Provider 定向测试和 releaseS 构建；此前 217 项全量测试与 lint 不能冒充最终候选门禁。发布判定保持 `review / NOT READY`，未提交、未推送、未合并、未创建 release，也不再进行第三次模型请求。
- 已先增加 `ambiguous + noop` 安全反例并确认旧实现失败，再做最小兼容：仅当非空 `original == replacement`、原串至少出现一次、编辑类别/长度合法且所有出现位置均不触碰保护项时，才丢弃重复锚点 noop。`ambiguous + changed`、`missing + noop`、非法 sibling、保护项触碰及其它真实编辑继续失败关闭；模型偏移仍不用于重复锚点消歧。校验版本升为 `ai-validate-v6`，旧缓存自然失效。
- 定向 `AiNoChangeTest`、`AiOutputValidatorTest`、`OpenAiCompatibleProviderTest` 通过；Provider 的非法 JSON 仍固定分类为 `payload_json_syntax`，未增加宽松解析。最终门禁 `:app:compileAppDebugKotlin :app:testAppDebugUnitTest :app:lintAppDebug` 为 `BUILD SUCCESSFUL in 7m 38s`，32 套件 / 221 项 / 0 failures / errors / skipped；`git diff --check` 为 0（仅既有 LF/CRLF 提示）。本轮无外部模型调用、真机安装、提交、推送或发布，阶段 E 仍为 `review / NOT READY`。
- 为 `payload_json_syntax` 增加固定、无内容的语法子类：`non_json_prefix / unterminated_string / invalid_escape / raw_control / mismatched_closer / unclosed_container / trailing_data`；诊断不保留或报告载荷、偏移、长度、哈希、解析器 message 或字段值。模型 payload 改用 Gson `STRICT` 并强制读到 `END_DOCUMENT`，拒绝默认兼容模式会接受的原始控制字符和尾随内容；这是收紧 JSON，不是容错放宽。7 类合成载荷各重复 2 次均得到相同枚举，Provider 11 项定向测试通过。
- 最终 compile + 全量单测 + lint 为 `BUILD SUCCESSFUL in 4m 50s`，32 套件 / 222 项 / 0 failures / errors / skipped；`git diff --check` 仅既有换行提示。共存包 `legado_payload_syntax_releaseS.apk` 为 `3.26.090712 / 26090712`，SHA-256 `8E81CAF0E351E2DBBB839A9027C3946CE17A3663B7F119AF8434646BCEECA398`；候选与设备旧包证书 SHA-256 均为 `bea423a492567c23903896aa41fac03f31fbd89c98eb6a32f7a7b7b2fe55db01`，`install -r` 成功，未卸载或清数据，临时 suffix/权限改动已恢复。
- 真机记录开启后提取到 3 个固定失败实例：`ANCHOR ambiguous + changed chunk=3` 两次、`chunk=2` 一次；未观察到 `payload_json_syntax` 或任一新 syntax 子类。故旧的一次性 Provider 分类当前不可复现，不足以支持宽松解析或定向 Provider 修复；当前可复现阻塞转为 `ambiguous + changed`。安全证据在 `app/build/reports/ai-stage-e/payload-syntax-safe-evidence.txt`，临时“记录日志”设置已恢复关闭。阶段 E 仍为 `review / NOT READY`，未提交、未推送、未发布。

## 8. 2026-09-08 日志优化完成后的后续任务

日志系统 P0/P1/P2 已独立验收完成，AI 阶段 E 从以下顺序继续；本节是后续执行清单，不授权自动调用外部模型、提交或发布：

1. **B1：先冻结上下文锚点测试契约。** 只用合成正文为 `ambiguous + changed` 增加正反例：短前后文与 `original` 组合后唯一时可定位；上下文自身重复、伪造、不连续、跨块、触碰哨兵/URL/数字、Unicode code point 边界错误时必须失败。先让测试在旧实现上失败，不先改生产代码。
2. **B2：最小扩展编辑协议。** 仅在 B1 证明必要且边界明确后，给编辑项增加只读短前/后文；本机必须验证上下文和 `original` 都是当前块的精确连续子串，并以组合锚点唯一性重建范围。模型 `start/end` 仍只作交叉校验，不能单独消歧；定位仍不唯一则整章回退。不得以“忽略 ambiguous changed”或信任模型下标换取 Completed。
3. **B3：同步协议与缓存。** 更新 Prompt/schema、Provider 严格字段解析、Validator 和反例；递增 prompt/validation 版本使旧缓存自然失效。日志只记录固定结构化分类，不记录新增上下文字段的值。
4. **C：集中自动化门禁。** 先跑目标测试，再一次执行 `:app:compileAppDebugKotlin :app:testAppDebugUnitTest :app:lintAppDebug` 和 `git diff --check`；记录套件/项目数。只有最终候选才构建 releaseS，构建后立即恢复临时 suffix/权限改动并核对 diff。
5. **D1：受控真实复测。** 重新取得具体目的地、模型和正文范围授权后，每个明确假设只触发一次真实请求；通过新日志查看器仅导出 AI 固定字段。要求至少取得一章 Ready 和一章“无需修改”缓存命中，否则继续 `review / NOT READY`。
6. **D2：完成剩余发布矩阵。** 执行断网/超时/认证/限流、切章/取消/后台/旋转、清 AI 缓存、阅读位置、朗读/搜索/导出，以及本地 TXT/EPUB/漫画/图片零侵入检查。全部必测项 PASS 后才进入《发布推送流程书.md》§4；任何 NOT RUN 都不能写 accepted。

任务边界：日志 P3/P4 不与本轮 AI 修复捆绑；DSH 或其他外部模型未重新授权时不得接收私有代码或真实正文；任何自动化绿灯、连接测试或单章日志都不等于发布验收。
