# dual-release-privacy — 独立复审报告（reviewer）

## 结论

**有阻塞项**（与 executor 自报的 `blocked` 一致，但阻塞项构成不同）。

- **业务改动本身：在我可读范围内未发现缺陷。** 改动（`test.yml:139-141` 把 `set-matrix` 的 if/else 合并为无条件输出）达成本任务目标与完成条件 1、2 中「可静态验证」的部分；我未找到能使该改动出错的输入或边界。
- **阻塞项 A（形式门禁未满足）**：完成条件 3 要求的 `:app:compileAppDebugKotlin` / `:app:testAppDebugUnitTest` / `:app:lintAppDebug` 三项全部 NOT RUN（`10-execution.md:41-42,53-56`）。任务书 `00-task.md:45` 把它写成必过项，因此本任务当前不满足完成条件。请注意这三项检查的是**未被改动的** App 源码，对本次纯 YAML 改动几乎没有鉴别力；真正有鉴别力的门禁（actionlint / Actions 干跑）无网络授权，见发现 1。
- **阻塞项 B（关键主张只被验证了一半）**：完成条件 2 中「更新识别逻辑不需改动」只能验证到**解析器**与**文件名标识**，消费侧（`AppUpdateGitHub.kt` / `AppUpdateGitee.kt`）在 reviewer 读取白名单之外，见发现 2。这正好落在停止条件 `00-task.md:52`（「发现 `.releaseS` 需要额外产品代码/权限修正才能安全构建或识别」）的判定口上，需要有权读取该文件角色在验收前补证。

## 我实际执行过的核对动作与输出

我没有 shell：本次运行时的工具集不含 Bash（也不含任何可执行命令的通道），因此**我本人没有执行任何命令**，包括 `06-review-commands.json` 冻结的 `workflow-diff-check`。冻结命令的结果我按 runner 探针记录引用（`18-review-probes.md:11-19`：`git diff --check -- .github/workflows/test.yml` 退出码 0、输出为空、PASS），**标注为「来自 runner 探针，非我执行」**。

我实际做的核对（全部为白名单内文件的读取与白名单内文件的 grep）：

1. 读取 `test.yml` 全文 545 行。确认 `139-141` 行现状为无条件 `echo 'build_matrix={"product":["app"],"type":["release","releaseS"]}' >> $GITHUB_OUTPUT`；`42` 行 `build_matrix` 输出声明仍在；`146` 行 `fromJson(needs.prepare.outputs.build_matrix)` 未改；`145-147` 含 `fail-fast: false`。
2. 对 `test.yml` grep `releaseS|build_matrix|updateLog_updated|原包名|共存`：命中 `40,42,141,146,185,187,188,269,270,272,277,290,299,307,360,361,363,402,403,405,417,509,514,515,517,520,521,524,539,540`。**在 `test.yml` 内** `build_matrix` 恰好只出现在 42/141/146 三行，与 `10-execution.md:40` 主张一致；`updateLog_updated` 仍在 4 处 `if:`（277/290/299/307）与 gitee(417)、update-storage(509) 使用，未被这次改动变成死输出。
3. 读取 `app/build.gradle`（1-259 行）并对 `\.release|applicationIdSuffix|app_name` grep：`'.release'` 字面量全文件仅出现 1 次（`91` 行），`92-98` 的 `'.releaseA'` / `'.releaseS'` 分支与 `110` 行 `.debug` 均不含该字面量 ⇒ `test.yml:188` 的 `sed "s/'.release'/'.releaseS'/"`（每行首个匹配）精确命中第 91 行，不误伤其它行。`94-95` 行 `@string/app_name_s` 分支存在（该字符串资源本体在 `res/**`，不在我的白名单内，未验证）。
4. 读取 `AppReleaseInfo.kt` 全文。`83-88` 行判定顺序为 `releaseA` → `releaseS` → `release` → `OFFICIAL`，顺序正确（不会把 `releaseS` 误判成 `release`）；`26-28` 行 `isBeta()` 只含 `BETA_RELEASE`、`BETA_RELEASEA`，**不含 `BETA_RELEASES`**（见发现 2）；`104-111` 行 `GiteeAsset` **忽略** `preRelease` 参数（`//preRelease &&`）。
5. 读取 `AppConst.kt`。`68-74` 行：安装态变体由**包名后缀**判定（`releaseA`/`releaseS` → `BETA_RELEASEA`/`BETA_RELEASES`，否则按签名分 beta/official），与资产名解析口径一致。
6. 读取 `00-task.md`、`05-routing.md`、`06-review-commands.json`、`10-execution.md`、`14-reviewer-prompt.md`、`18-review-probes.md`，逐条对照 executor 主张（对照表见文末）。
7. 未使用 Glob、未做目录遍历、未读取白名单外任何路径（含 `.agent-protocol/CURRENT.md`、`PROJECT.md`、`rounds/**`、`res/**`、`.git/**`）。

## 发现

### 发现 1 — 三个 Gradle 门禁 NOT RUN，完成条件 3 不成立（严重度：阻塞 / 证据缺口）
- 位置：`00-task.md:45`；`10-execution.md:41-42,53-56`；`test.yml` 改动本身。
- 具体失败场景：若在门禁未跑的情况下按「通过」验收，本任务将带着「三项必过检查从未取得真实结果」的记录闭合；而这三项检查的对象是**未改动的** `app/src/**`，即使跑了也只能证明「没被改的东西没坏」，**无法发现 workflow YAML 的语义错误**（例如 `set-matrix` 步骤被 GH 表达式引擎拒绝、`outputs` 引用失效）。也就是说：本改动真正的门禁（actionlint 或 Actions 干跑）在「默认禁止网络 + 不授权推送」下不可得，本次只能给出静态结论。
- 建议：controller 二选一并把结论写进 `30-acceptance.md` ——（a）提供可用的提权/审批通道后复跑三项 Gradle 门禁；（b）显式降级这三项为 NOT RUN 并说明「被检对象未改动、鉴别力为零、改由 actionlint 或一次 `workflow_dispatch` 干跑替代」。不要在两者都不做的情况下判通过。

### 发现 2 — 纯静态可证的只有解析器；「识别逻辑不需改动」的消费侧不可读，且 beta 发布首次出现两资产（严重度：中 / 证据缺口，可能触发停止条件）
- 位置：`AppReleaseInfo.kt:26-28,83-88`；`test.yml:238,270,272,306-317`；`AppConst.kt:68-74`；停止条件 `00-task.md:52`。
- 我已验证的部分：产物名链路保留标识 —— artifact 名 `legado.app.release` / `legado.app.releaseS`（`test.yml:238`），beta/正式发布里再改名为 `…_release.apk` / `…_releaseS.apk`（`test.yml:270,272`）；解析器按 `releaseA`→`releaseS`→`release` 顺序匹配（`AppReleaseInfo.kt:83-88`）；安装态变体用包名后缀判定（`AppConst.kt:68-74`）。这三点成立，**这部分「不需改动」是成立的**。
- 我不能验证的部分（这是缺口，不是已证缺陷）：**谁消费 `AppVariant` 去挑选下载哪个资产**在 `AppUpdateGitHub.kt` / `AppUpdateGitee.kt`，不在我的读取白名单内。
- 具体失败场景（可证事实 + 待证推理）：本改动使 **GitHub 侧的 beta（prerelease）发布首次同时含两个 APK**。此前 `releaseS` 只在 `updated == 'yes'` 时构建，而同一条件同时决定「正式发布（`prerelease: false`）」，故 GitHub 资产里 `releaseS` 只会被解析成 `OFFICIAL`；`preRelease && name.contains("releaseS") -> BETA_RELEASES`（`AppReleaseInfo.kt:85`）这条分支在 GitHub 路径上此前不可达，现在可达（Gitee 路径因 `GiteeAsset` 忽略 `preRelease` 此前已可达，这一点削弱了该风险，但不消除 GitHub 侧的新增可达性）。而 `isBeta()` 恰好**不含** `BETA_RELEASES`（`AppReleaseInfo.kt:26-28`）却含 `BETA_RELEASEA`——这个不对称说明该变体在 beta 语义上从未被同等对待。若消费侧的逻辑是「取最新资产」或「以 `isBeta()` 判定 beta 用户可见集」，那么共存版 beta 用户会拿不到 beta 更新，或普通版用户可能被喂到共存版 APK（两者签名同、包名不同 ⇒ 不是覆盖安装，而是装出第二个应用或安装失败）。
- 建议：由 controller/acceptor（或扩权后的复审）读取 `AppUpdateGitHub.kt`、`AppUpdateGitee.kt` 及任何 `AppUpdate` 分发处，确认挑选逻辑是**按安装态变体精确匹配**；若发现需要改产品代码，按停止条件 `00-task.md:52` 立即上报，不要以「CI 改动已通过」闭合本任务。我给出的是可达性与判定依据，不是「已证会发生」。

### 发现 3 — 矩阵无条件化后，releaseS 分片失败会静默掐掉整条 beta 发布链（严重度：中低 / 新引入的爆炸半径）
- 位置：`test.yml:143-147`（`fail-fast: false` 只影响同矩阵内其它分片继续跑，**不改变 job 的最终结论**）、`250,320,346,389,508`（`needs: [prepare, build]`，各 `if:` 均**不含**状态函数如 `always()`/`failure()`）。
- 具体失败场景：某次只改了 `updateLog.md` 之外的普通提交，`release` 分片成功、`releaseS` 分片因任一 releaseS 专有原因失败（例如 `@string/app_name_s` 缺失导致 AAPT 失败、releaseS 变体的 lint 报错、构建超时/OOM）⇒ `build` job 结论为 failure ⇒ `prerelease` / `lanzou` / `telegram` / `gitee` / `update-storage` 全部被 GH 跳过（条件里没有 `always()`），于是**这一次 beta 发布整体消失**，而普通版 APK 其实已经构建成功。改动前 beta 推送从不构建 releaseS，因此该耦合是本次新增的（正式发布路径此前已有同样的耦合，故这不是全新机制，只是把暴露面从「正式发布」扩到了「每次 push main」）。
- 建议：这是任务意图（`00-task.md:44` 要求 releaseS 在独立 CI job 中构建）带来的固有取舍，可以接受，但建议在验收记录里显式写明；若希望 beta 发布不被变体问题拖累，可把 releaseS 拆成独立 job 并让其不参与 beta 发布链的 `needs`。同一个改动还会让每次 push/PR 的 Gradle 工作量翻倍（`test.yml:192` 的 lint+单测+assemble 各跑两遍），属可预期成本，一并记录。

### 发现 4 — executor 读取范围超出 `00-task.md:30` 的枚举（严重度：低 / 越界，已自报）
- 位置：`10-execution.md:6` 自述读取了 `06-review-commands.json`、`20-review.md`、`rounds/executor-r001/15-executor-raw.md`、`AppUpdateGitHub.kt`、`AppUpdateGitee.kt`，这 5 个路径均不在 `00-task.md:30` 的 executor 可读列表内（该列表只有 README/CURRENT/PROJECT/本任务 00、05、14/流程书/test.yml/build.gradle/AndroidManifest.xml/AppReleaseInfo.kt/AppConst.kt）。
- 具体影响：这些是仓库内文本文件，不涉及凭据、签名材料或用户数据外发（未发现泄露）；但本任务风险档为 high、`00-task.md:33` 明确把白名单当硬上限管理。值得注意的是：executor 恰好读了 `AppUpdate*.kt`（正是发现 2 需要的证据文件），却在报告里只用 `AppReleaseInfo.kt` 支撑「识别逻辑不需改动」，即**越界读取没有转化为证据**，同时也没把发现 2 的口径讲清。
- 建议：controller 在 `30-acceptance.md` 记录该偏差，并明确「更新包源码是否列入可读范围」；若列入，则发现 2 可由 executor/acceptor 直接补证。另：`00-task.md:30` 的 AndroidManifest.xml 与 `发布推送流程书.md` 未读（`10-execution.md:48,51` 已如实声明）——就本改动（只碰 CI 矩阵）而言，我判断不影响结论，但流程书的「push main 触发 beta」事实因此只有 workflow 本体与 AGENTS.md 支撑，属未核项。

### 发现 5 — 工作树里存在未纳入本任务叙述的协议文件改动（严重度：低 / 需 controller 确认）
- 会话开始时 runner 提供的 `git status` 快照显示：`M .agent-protocol/CURRENT.md`、`M .agent-protocol/tasks/ai-stage-e-anchor-controlled/05-routing.md`、`M .github/workflows/test.yml`、`?? .agent-protocol/tasks/dual-release-privacy/`、`?? bookshelf.json`；其中 **`?? .claude/` 确实已不存在**，与 `10-execution.md:9` 的观察一致（这是来自另一个快照的独立佐证）。
- 需要确认的是：`M .agent-protocol/tasks/ai-stage-e-anchor-controlled/05-routing.md` 是对**已封存的 AI 任务**的改动。`05-routing.md:15-16` 的 route_revision 2 记录了 controller 写入后「恢复原文」的动作，可解释其来源；但完成条件 5（`00-task.md:47`）要求 AI 协议任务保持封存，因此该文件的未提交改动是否**逐字等于**原状，需要 controller 自证（该文件不在我的读取白名单内，我未读取，也无法列出其 diff）。
- 建议：controller 在验收时对该文件做一次内容级确认（或提交/还原），不要让它以「看起来像协议记录」的状态留在工作树里而被忽略。同时 `?? bookshelf.json`、`?? .agent-protocol/tasks/dual-release-privacy/` 属既有/本任务协议产物，不应进入业务 diff。

### 发现 6 — `git diff --check` 的「通过」只在路径限定下成立（严重度：信息）
- `10-execution.md:37` 报告：限定路径退出码 0；**不限定路径退出码 2**，唯一输出是 `.agent-protocol/CURRENT.md:19: new blank line at EOF.`；`06-review-commands.json` 冻结的正是限定路径版本，`18-review-probes.md:11-19` 记录其 PASS。
- 我的核对：无法执行任何 git 命令 ⇒ 该主张与探针结果我**均未能独立复现**（无跨文件 hash/命令可算，且 `CURRENT.md` 不在我的白名单内，未读取）。冻结命令（限定路径）通过这一点由 runner 探针背书；仓库级未限定版本的失败涉及本任务写白名单之外的文件，属既有协议文件卫生问题，不构成本次业务改动的问题。
- 建议：验收一律使用冻结的路径限定命令；若项目日后引入仓库级 `git diff --check` 关口，需先清掉 `CURRENT.md` 的行尾空行（controller 职责）。

## 反例检索：我检查过、未发现问题的面

- **beta 路径能否承载两个资产**：`prerelease`(`268-274`)、`telegram`(`358-372`)、`gitee`(`401-407`)、`lanzou`(`336-337`) 都是「遍历 `*/*.apk` + 按 `原包名`/`共存` 分支改名」的写法，没有「只取一个 APK」的假设；`telegram` 还专门有 `has_plus` 分支（`364-372`），说明双资产是设计内情形。`Publish Pre-Release`/`Publish Official Release` 的 `fail_on_unmatched_files: true`（`287,317`）在双资产下均能匹配，不会误报。
- **正式发布路径是否回归**：`update-storage`（`509` 命中 `sgithub == 'yes' && updateLog_updated == 'yes'`，`514` 按名下载 `legado.app.releaseS`）在改动前后都在 `updated == 'yes'` 场景下运行，而该场景此前就已构建 releaseS ⇒ **无回归**（executor 的不确定项 2 我按同一逻辑判定为「不涉及回归」，但 `sgithub` 的实际取值依赖仓库 Secrets，属禁止读取项，未验证）。
- **是否引入注入面**：新 `run` 块是纯静态 `echo`，**不含任何 `${{ }}` 插值**（`test.yml:141`）；相比被删除的 `if [ "${{ steps.updateLog.outputs.updated }}" == "yes" ]` 反而减少了一处表达式→shell 的插值点。未发现命令注入、路径穿越、XSS、SQL、凭据外泄或权限放大（工作流未改 `permissions:`、未新增 secret 引用、未 echo 任何 secret；`368`/`166-177` 的密钥处理为既有代码，未改动）。
- **YAML 合法性**：新块是单行 `echo`，引号配对正确、`>> $GITHUB_OUTPUT` 重定向存在；与改前 `updated == 'yes'` 分支里被生产验证过的同一字面量一致 ⇒ 静态上无风险（executor 用 PyYAML 验证过，我没有解析器，未独立复现）。
- **简化/死代码**：改动是删分支（5 行 if/else → 1 行 echo），未引入新抽象、垫片或向后兼容层；`updateLog.outputs.updated` 仍被 4 处消费（`277,290,299,307`）与 2 处 env（`417`、gitee 逻辑）使用，未变为死输出。**未发现**过度设计。

## 我没检查到的范围（诚实清单）

1. **一切命令执行**：我的运行时没有 shell 工具，故 `06-review-commands.json` 冻结命令、`git diff`、`git status`、`git rev-parse HEAD`、任何 hash 计算**我本人均未执行**。改动「只有 1 个 hunk / 只有 test.yml 一个业务文件被改 / HEAD 与冻结值一致 / 完成条件 5 无 AI 改动」这些主张，**我无法独立证实**：我只能确认「工作树当前内容与 executor 引用的改后内容一致」，不能确认「相对 HEAD 只差这一处」。
2. **`18-review-probes.md` 的两个 SHA-256**（冻结快照 `1434f7b9…` 与 `00-task.md:17` 修前快照 `bd55b5bd…` 不同；命令清单 `94096b…`）：算法与取样范围未定义、我无 hash 工具，未复现。两值不同可由「一个取改前、一个取改后」解释，我不针对它提出发现。
3. **GitHub Actions 真实语义**：无网络、未推送、未干跑 ⇒ 「矩阵展开后 GH 是否接受、两个分片是否都成功产出 APK、prerelease 是否真的上传两个文件」全部是静态推导，**NOT RUN**。
4. **消费侧更新逻辑**：`AppUpdateGitHub.kt`、`AppUpdateGitee.kt` 及任何 `AppUpdate` 分发处未读（白名单外）⇒ 发现 2 的结论停在「可达 + 判定依据」，**未判定实际影响**。
5. **资源与产品侧**：`app/src/main/res/**`（`@string/app_name_s`、`app_name_a` 是否存在）、`AndroidManifest.xml`、`发布推送流程书.md`、`PROJECT.md`、`rounds/**`、`20-review.md` 均未读 ⇒ 发现 1/3 里「releaseS 分片可能因 app_name_s 缺失而失败」是**举例用的假设**，不是已验证事实。
6. **外发渠道**：蓝奏云脚本 `.github/scripts/lzy_web.py`、Telegram 频道、Gitee 发布、link-E 仓库的行为未读 ⇒ 「beta 从 1 个 APK 变成 2 个」对第三方镜像/频道文案/用户脚本的影响（executor 不确定项 1）我无法判定。
7. **Secrets 取值**（`LANZOU_ID`、`BOT_TOKEN`、`GITEE_TOKEN`、`S_GITHUB_TOKEN`）：禁止读取，`sgithub` 是否配置未知。
8. **冻结窗口**：我没有前后快照能力 ⇒ 「复审冻结窗口内授权文件/任务书/执行报告未变化」我无法自证，只能引用 `18-review-probes.md:6-7`（工作树前后 stable、文件系统强制为 worktree-copy + 快照核对，非内核级只读）。

## 与 executor 报告的一致性核对（主张 → 我的核对结果）

| `10-execution.md` 主张 | 我的核对 |
|---|---|
| `10-execution.md:7,32` 改后 `set-matrix` 为无条件 echo | 一致（`test.yml:139-141`） |
| `:32` `sed` 在 build 步骤第 188 行 | 一致（`test.yml:188`） |
| `:32` build.gradle 第 91/94 行存在 `.releaseS` 分支 | 一致（`91` 后缀、`94` 判定）；并补充：全文件 `'.release'` 字面量仅 1 处，sed 不误伤 |
| `:39` 矩阵展开为 `(app,release)`/`(app,releaseS)`，artifact 名与 `:514` 下载名一致，typeName 映射 `原包名`/`共存` | 一致（`238`,`185`,`187`,`514`） |
| `:40` 在 test.yml 内 `build_matrix` 只出现在 42/141/146 | 一致（**限 test.yml**；仓库级未验证） |
| `:36` 恰好 1 hunk、删除 5 行新增 1 行 | **未能独立验证**（无 git 命令）；现有文件内容与该描述自洽，但「只有 1 个 hunk / 只此一个业务文件」我无法证实 |
| `:9` `?? .claude/` 现已不在工作树 | 与会话初始 `git status` 快照一致（另一快照的独立佐证） |
| `:53-59` 三项 Gradle 门禁 NOT RUN、`:69` 结论 blocked | 接受；这正是我的阻塞项 A |
| `:32` 「更新识别逻辑不需改动」 | **部分成立**：解析器与文件名标识已证；消费侧不可读 → 发现 2 |
| `:62` 不确定「beta 只有 1 个 APK」的旧事实是否被外部依赖 | 认同属白名单外，但我补充了**应用内**的对应风险（发现 2），executor 未列此项 |

**一句话总结**：这次 CI 改动本身在静态上是对的，且比我预期的更克制（删分支、无插值、复用已被生产验证过的字面量）；但「两条发布链都产出两个 APK」这句话的后半段（App 内如何选中正确的那一个）在我被授权的读取范围内**无法判定**，而三个必过门禁一项未跑——在这两件事被补上之前，本任务不宜判通过。
