# 阶段 E：Codex 独立审查（2026-09-06）

状态：`review / changes requested`。本轮审查阶段 E 接手成果与最新失败归因；未修改生产代码，未调用外部模型，未安装、卸载、清数据、提交或发布。

## 结论与用户补充

用户补充：优质书源的部分章节本来没有错误。成功不能以“必须改出文字”为条件。合法完整响应中的 `edits: []` 应表示“检查完成，无需修改”，当前 validator 已允许空编辑列表。它与空白 `message.content` 或非法响应不同；后者继续失败关闭。

最新 DSH 报告 §16 的脱敏证据为 `EDIT_KIND / noop / chunk=1`，代码对应 `original == replacement` 时立即拒绝。它支持“当前模型输出与本地契约冲突”，不能证明模型固有不可修复，也不能证明其它编辑全部安全。本轮没有重新触发真机请求，真机结论来自报告引用，不能作为本轮独立实测。

## 需要返工的发现

1. **[P1] 重复锚点仍可按模型偏移获准，违反接手任务书 §4 第二步的唯一性要求。** `AiOutputValidator.resolveRange` 对多次出现的 original，只要模型偏移窗匹配就接受。例：`他走的很快，她的书` 中模型原本应修第一个“的”，数错下标落到第二个“的”时仍可通过，结果可能成为“她得书”。`resolvesRepeatedAnchorViaExactModelOffsets` 等现有测试明确把重复锚点接受作为正例，绿灯不能证明任务书边界满足。应先补上述反例，再统一重复锚点失败关闭；需要扩大上下文定位时另行定义精确、唯一的上下文契约。

2. **[P2] 报告把观测到的错误分类扩大为排他性根因。** §11–12 两次 TIMEOUT 不能排除服务端排队或生成耗时，也不能证明网络不可达；§16 提示词调整后仍发生 noop，只能证明本次兼容问题未解决，不能推出“模型固有行为、本地无法解决”。并且 noop 检查先于锚点、范围、保护项和类别检查，日志只揭示第一个拒绝条件。应保留历史观测，撤回确定性归因，不以外部限制为由降低真实 Ready 验收门槛。

## 建议的兼容方案（尚未实施）

- 合法、完整、正确 chunk ID 且正常结束的空 edits：成功，建议显示“检查完成，无需修改”；缓存表示完成了本协议处理，不承诺原文绝无错误。
- 非空 original 的无变化编辑：评估在数量、唯一锚点、范围、顺序、重叠、保护项和类别约束全部通过后，仅忽略该条编辑的应用动作。不能在校验前直接过滤，也不能连带忽略同响应内的非法编辑。
- 全部为安全无变化编辑时，可归一化为“无需修改”；与空响应伪成功严格区分。空到空的占位编辑、哨兵编辑、错误锚点、截断响应等继续拒绝。
- 先补“正常无错章、全 noop、有效修改混合 noop、noop 混合非法编辑、重复锚点、哨兵与空占位”的合成正反例，再修改协议、版本与实现。无需通过重复真机重试来试探提示词。

## 本轮验证与范围

- 独立执行 `:app:compileAppDebugKotlin :app:testAppDebugUnitTest`：BUILD SUCCESSFUL，31 套件 / 207 测试 / 0 failures / errors / skipped。
- 日志：`app/build/ai-codex-review.log`；测试结果：`app/build/test-results/testAppDebugUnitTest/`。
- `git diff --check` 通过（仅换行提示）。`app/build.gradle` 和 Manifest 无差异，未遗留本轮测试包变更。
- 本轮未重跑 lint、未新增生产改动、未验证真机 Ready 或 §11.2 全部路径。既有自动化通过不替代上述返工与真机验收。

唯一下一步：先用合成反例收敛“无修改成功”和唯一锚点契约，再做最小兼容修正与定向真机复测；阶段 E 仍未验收，不发布。

## 后续实施：用户授权有限兼容（2026-09-06）

- 用户说明部分章节本来无错，并提出“放宽一下感觉可以发布了”。据此实施有限兼容，不把无修改当失败，发布仍依据实际验收证据。
- 先新增 `AiNoChangeTest`：旧实现 7 项 / 5 失败，复现无变化误拒绝、重复锚点及整章缓存问题；日志 `app/build/ai-nochange-red.log`。
- 无变化编辑先通过数量、唯一锚点、重叠/顺序、保护项与类别校验，再跳过应用；空到空占位继续拒绝。现有 denoise 类别仍要求真实删除白名单噪声，不把错误类别伪装成无修改。
- 非空锚点必须唯一；同步修正原先将重复锚点接受作为正例的测试。Prompt 明确无错时返回空 edits，不凭空制造修改，重复锚点省略编辑。Prompt v4 / validation v5 使旧缓存失效。
- 协调器以完整候选和原文逐字比较判定“无需修改”，同时覆盖缓存命中；阅读页显示“AI 检查完成，无需修改”，不提供无意义的同文切换。重置后清除该状态。
- 本轮自动化与设备验收结果待追加；本节取代前文“尚未实施”，历史审查证据保留。

### 本轮自动化结果

`:app:compileAppDebugKotlin :app:testAppDebugUnitTest :app:lintAppDebug` 最终 `BUILD SUCCESSFUL in 7m 43s`。全量 32 套件 / 215 项 / 0 failures / errors；新增协调器“无需修改”状态与重置测试通过。日志 `app/build/ai-nochange-validation.log`。真机与发布验收尚不由这些自动化结果替代。

### 本轮共存包与独立真机结果

- Release 共存构建 `BUILD SUCCESSFUL in 6m 46s`，含 R8 与 lintVital；日志 `app/build/ai-nochange-release.log`。
- 包：`app/build/reports/ai-stage-e/legado_nochange_releaseS.apk`；SHA-256 `5020A6E4FF37C214C82C19AD1B29F9CB85D98FF232723E680B51E47BA5D62096`。
- 显式指定目标设备 `10CE5P1M1Z001P9`，`adb install -r` 返回 Success；已安装包名 `io.legado.app.tjf.releaseS`，versionName `3.26.090623` / versionCode `26090623`。未卸载、未清数据。构建脚本 finally 恢复源文件，`app/build.gradle` / Manifest diff 为空。
- 独立冷启动成功，进入现有书籍，三点菜单实测显示“AI 失败，正在显示原文（第 2 块）”。当前应用 PID 的日志仅提取稳定分类得到 `ai-fail code=ANCHOR chunk=2`；操作面板显示“AI 失败：结果校验或处理失败，已保留原文（第 2 块）”。取证未输出正文、书名、URL、密钥或完整模型响应。
- 这次已经越过第一块，但第二块仍有锚点无法可靠定位。ANCHOR 本身不能区分锚点重复与原串不存在，不能猜测模型具体输出，也不能以跳过锚点检查换取 Ready。
- 当前结论：无变化兼容已完成自动化与安装验证；真实章节完整 Ready / 无需修改尚未确认，阶段 E 继续 `review`，未提交、未推送、未发布。其余未执行真机项仍保持 NOT RUN。
- 唯一下一步：补只含 `ambiguous / missing` 的锚点分类并用合成样本定位下一处兼容问题；保留严格的改字定位边界后再定向真机复测，不再循环猜测 noop 或网络问题。

## 2026-09-07：锚点分类实施与发布验收续记

- 已实现 `ANCHOR` 安全细分：`ambiguous / missing` 与 `noop / changed` 为固定枚举，贯穿 Validator、Processor、Coordinator 和可注入 reporter；生产日志仅输出固定分类、编辑效果和可选块号。未放宽唯一精确锚点、保护项、类别、范围或整章失败关闭边界。
- 四象限合成反例与 Processor/reporter 传播测试已加入。定向 4 套件 / 42 项通过；编译、全量 32 套件 / 217 项、lint 全部通过，`git diff --check` 为 0。
- releaseS 共存构建成功（6m56s，含 R8/lintVital），APK `app/build/reports/ai-stage-e/legado_anchor_classification_releaseS.apk`，SHA-256 `A54DD2160CEA2C50E7CF8F1E026DA2635E31FF698C0A40F425666DCE6B5CF7A7`。目标真机 `install -r` 成功，版本 `3.26.090709` / `26090709`；未卸载、未清数据，临时 suffix/权限改动已恢复且无实质 diff。
- 冷启动、阅读页和 AI 操作面板可达。一次重试后未取得本轮固定失败码，菜单仍为“未处理”，因此不推断请求结果。进一步重试会外发真实章节正文；因本轮没有针对具体目的地与数据范围的明确授权，已停止。
- 判定：`review / NOT READY`。真实 Ready、“无需修改”缓存、异常网络、切章/取消/后台/旋转、清缓存、朗读/搜索/导出与本地 TXT/EPUB/漫画路径仍未完成，不能提交或发布。唯一下一步是取得明确外发授权后执行一次脱敏分类复测。
- 用户随后明确授权把当前所选在线文字章节按既有哨兵/分块协议发送到 `https://api.deepseek.com`，使用应用当前配置模型。设置页确认“仅非计费网络”为关闭，故先前仅据网络能力推断请求被策略阻止不成立。首次授权请求实际到达第 3 块并报笼统 `PROTOCOL`。
- Provider 随后增加固定、无载荷的协议子类诊断；对应定向测试通过。新共存包 `legado_protocol_detail_releaseS.apk` 构建并保留数据安装成功，SHA-256 `4138D8A555BB2EB510ABBBA80566D8049065ABD3A56B328F6FE25D8CCBE68C40`，临时构建源码改动已恢复。
- 用户批准的上午最后一次外发只触发一次；真机固定日志同时取得 `payload_json_syntax / PROTOCOL / chunk=3` 与目标分类 `ANCHOR / ambiguous / noop / chunk=1`。未记录正文、响应体、书名、URL 或密钥。分类目标完成，但整章仍失败关闭、原文可读；没有第三次请求。
- 判定维持 `review / NOT READY`：`ambiguous + noop` 可进入计划中的最小兼容分支，但 `payload_json_syntax` 是独立阻塞；不得在无响应证据时放宽 JSON。最新协议诊断改动之后只完成 Provider 定向测试和 releaseS 构建，最终完整单测/lint 仍需下次补跑。未提交、未推送、未发布。
- 后续已先补安全反例并证明旧实现拒绝 `ambiguous + noop`，再实施最小兼容：只丢弃非空、确实存在、类别/长度合法、所有出现位置均不触碰保护项的重复锚点 noop；任何 changed、missing noop、非法 sibling 或保护项风险仍失败关闭。模型下标没有重新获得消歧权限，校验版本升至 v6。
- 定向 Validator/no-change/Provider 测试通过；Provider 的非法 JSON 仍严格报 `payload_json_syntax`，未加入 Markdown 提取、尾逗号修复或其它宽松解析。最终 compile + 全量单测 + lint 为 `BUILD SUCCESSFUL in 7m 38s`，32 套件 / 221 项 / 0 failures / errors / skipped，`git diff --check` 为 0（仅换行提示）。本轮未调用外部模型、未安装、未提交、未推送、未发布；阶段 E 仍为 `review / NOT READY`，独立下一步是先为 Provider 阻塞取得安全、可复现的精确证据。

## 2026-09-07：`payload_json_syntax` 精确协议取证

- Provider 新增 7 个固定且无内容的 payload JSON 语法子类，并将模型 payload 解析改为 Gson `STRICT` + `END_DOCUMENT`；拒绝原始控制字符、尾随内容及其它非严格 JSON，不做 Markdown 提取、尾逗号修复或猜测性容错。分类器不记录载荷、字段值、位置、长度、哈希、解析器/异常 message。7 类合成反例各重复 2 次分类一致；Provider 11 项定向测试通过。
- 最终 compile + 32 套件 / 222 项全量单测 + lint 为 `BUILD SUCCESSFUL in 4m 50s`，0 failures / errors / skipped；`git diff --check` 仅既有换行提示。
- 共存包 `app/build/reports/ai-stage-e/legado_payload_syntax_releaseS.apk`：`io.legado.app.tjf.releaseS`，`3.26.090712 / 26090712`，SHA-256 `8E81CAF0E351E2DBBB839A9027C3946CE17A3663B7F119AF8434646BCEECA398`。安装前确认候选与设备原包签名证书 SHA-256 同为 `bea423a492567c23903896aa41fac03f31fbd89c98eb6a32f7a7b7b2fe55db01`；`adb install -r` 成功，未卸载、未清数据，临时 suffix/权限源码已恢复。
- 为取得可见固定日志临时打开原本关闭的“记录日志”，完成后已恢复关闭。记录开启后的 3 个真实失败实例均为 `ANCHOR anchor=ambiguous edit=changed`（块 3、3、2）；未再次出现 `payload_json_syntax` 或任一新 syntax 子类。证据文件 `app/build/reports/ai-stage-e/payload-syntax-safe-evidence.txt` 不含响应载荷、正文、书名、URL、密钥、长度、哈希或异常内容。
- 结论：旧的单次 `payload_json_syntax chunk=3` 当前不可复现，不能支持放宽 JSON 或猜测性 Provider 修复；当前可复现阻塞是 `ambiguous + changed`。阶段 E 继续 `review / NOT READY`，未提交、未推送、未发布；下一步按计划先写唯一精确上下文锚点的合成正反例。
