<!-- agent-protocol-binding
round_id: reviewer-r005
task_digest: 1b5d59a0a4d5ec701f9fb35e56a807318dfe4bf9f2c314230c0b08a014dfdb52
snapshot_digest: 391b6a47e7ee60a48ac1aa113e0694061b18821dc46bda3d2002828777c1f019
--># dual-release-privacy — 独立复审报告（reviewer=claude，route_revision 7，round reviewer-r004）

## 0. 结论

**通过** —— 就本轮唯一业务改动（`.github/workflows/test.yml` 的四处 job 级 `if` 硬禁用）而言，**未发现缺陷**：GitHub-only 收敛成立，双身份 matrix、`prerelease` 与"缺一即失败"守卫完好，四个外部 job 无论 Secrets 是否存在都恒不运行。

但**不构成"可发布"**：报告 §2.3 把"push main 必然走 beta 通道"当成已确定结论写出，而该结论实际依赖两个未闭合的触发条件（F1、F2）。这两项不是本 diff 引入的缺陷，是既有 workflow 行为；它们必须在 controller 推送 `main` 之前被显式核对，否则会出现"要么发布不完整、要么违授权建正式版"的两种具体失败。**F1/F2 未闭合前，本轮结论只到"diff 正确"，不到"发布可执行"。**

无阻塞本轮 diff 的问题；F3–F7 为证据/一致性级别，不影响通过对本 diff 的判定。

---

## 1. 我实际执行的核对动作与输出

**能力边界（先说清楚）**：本实例只提供 `Read` / `Grep` / `Glob` 类只读工具，**没有 Bash**。因此 `06-review-commands.json` 冻结的四条命令（`phase-cd-diff-check` / `phase-cd-business-diff` / `phase-cd-targeted-test` / `phase-cd-status`）**我一条都没跑**，全部 NOT RUN（详见 §4）。以下结论全部来自逐行阅读白名单文件与两次 Grep。

### 1.1 读取（全部在白名单内）

- `00-task.md`、`05-routing.md`、`06-review-commands.json`、`10-execution.md`、`14-reviewer-prompt.md`、`18-review-probes.md`
- `.github/workflows/test.yml`（全文 537 行，逐行读完）
- 未读 `PROJECT.md`（任务书 line 108 的 reviewer 白名单未列入）、未读 `发布推送流程书.md`、未读 `app/**`、未读 `updateLog.md`、未读 `.github/` 下其它 workflow 文件。

### 1.2 两次 Grep（目标文件均在白名单内）

第一次，结构化提取 job 键 / job 级 `if` / `needs` / 授权注释：

```text
29:  prepare:            31:    if: ${{ !startsWith(github.event.head_commit.message, 'Merge pull request') }}
144:  build:             145:    needs: prepare
241:  prerelease:        242:    needs: [ prepare, build ]
                        243:    if: github.event_name != 'pull_request' && github.repository == 'tjf-github/legado-E'
312:  lanzou:            313:    # 用户 2026-09-21 授权…   314:    if: ${{ false }}  # github-only   315:    needs: [ prepare, build ]   316:    # 原自动触发条件…
340:  telegram:          341:    # …                       342:    if: ${{ false }}  # github-only   343:    needs: [prepare, build]     344:    # 原自动触发条件…
382:  gitee:             383:    # …                       384:    if: ${{ false }}  # github-only   385:    needs: [prepare, build]     386:    # 原自动触发条件…
497:  update-storage:    498:    # …                       499:    if: ${{ false }}  # github-only   500:    needs: [prepare, build]     501:    # 原自动触发条件…
```

**读出的硬事实**：job 恰为 7 个（`prepare`/`build`/`prerelease`/`lanzou`/`telegram`/`gitee`/`update-storage`），四个外部 job 各**有且仅有一条** job 级 `if`，值为布尔字面量 `false`；`# github-only` 前有两个空格，是合法 YAML 注释（plain scalar 值为 `${{ false }}`）；四个 job 的 `needs` 未变；旧条件只以注释形式存在（L316/344/386/501），无生效残留。

第二次，扫描外部写入面（`curl|git push|git clone|link-E|gitee|lanzou|telegram|S_GITHUB|GH_TOKEN|workflow_dispatch`，忽略大小写）：命中集中在 L21（`workflow_dispatch` 入口）、L36–38/L54–64（`prepare` 只写 `*_GITHUB_OUTPUT`）、L289（`prerelease` 的 `github.token`）、L312–338 / L340–380 / L382–495 / L497–537（四个已禁用 job 内部）。**在 `prepare`/`build`/`prerelease` 三个启用 job 内，没有任何 `curl`、`git push`、`git clone` 或第三方发布目的地**；GitHub 侧只用到 `softprops/action-gh-release@v2`（L271、L301）、`richardsimko/update-tag@v1`（L293）、`gh release delete beta -y`（L286），全部指向 GitHub 自身。

### 1.3 对 executor 16 项断言的独立复现结果

`10-execution.md` §2.2 的 16 项 PASS 中，**15 项我能从工作树逐行独立复现**（不依赖其脚本）：

| 断言 | 我的独立证据 |
| --- | --- |
| lanzou/telegram/gitee/update-storage 硬禁用 | L314 / L342 / L384 / L499，各仅一条 `if: ${{ false }}` |
| 无启用 job 依赖被禁用 job | L145 `build.needs=prepare`；L242 `prerelease.needs=[prepare,build]`；无人 needs 四个禁用 job |
| `prerelease` 保留且守卫未变 | L241–243，guard 仍要求 `github.repository == 'tjf-github/legado-E'` |
| 缺一即失败守卫在位 | L260–266，`for identity in normal private; do if ! ls legado_app_*_$identity.apk … exit 1` |
| 双通道且互补 | L269–280（`updateLog_updated == 'yes'` → official）/ L282–310（`!= 'yes'` → beta），两者 `files: apk/*.apk` + `fail_on_unmatched_files: true` |
| matrix 仅 normal+private | L142 字面量 `{"product":["app"],"type":["normal","private"]}` |
| 按 matrix 注入身份 | L187 `-PRELEASE_IDENTITY="${{ env.type }}"` |
| 禁用 job 保留 secret 引用 | L320–325、L372–373、L402、L528 |
| 恰 4 处 marker | grep 实测 4 处 `# github-only` / 4 处 `if: ${{ false }}` |
| 旧条件已注释不再生效 | L316/344/386/501 全为注释行 |

**唯一未复现的是第 1 项（YAML 解析器层面）**：我没有运行 PyYAML/actionlint。我给出的等价只读证据是"7 个 job 各只有一条 `if`，且我通读全文未见任何重复映射键"，强度略低于真解析。

### 1.4 diff 形态的自洽性核对（推断，非 git diff 实跑）

按工作树实际结构，每个 job 的变化应为"顶部新增 2 行（授权注释 + `if: ${{ false }}`）+ 原 `if:` 行替换为 1 行旧条件注释"，即每 job **+3 / −1**，四处合计 **+12 / −4**。这与 `10-execution.md` L21 声明的 `git diff --numstat = 12 4` 完全吻合。**推论**：若该 numstat 属实，则四处 job 的 `if` 改动恰好解释了全部增删，`prepare`(L31)、`prerelease`(L243) 的 `if`、`build`、矩阵、守卫、Secrets 引用等其余行确实一字节未动。

⚠️ 这是**自洽性推断**：`git diff --numstat` 本身我无法独立复算（无 Bash）。若该数字不实，本推论不成立。

### 1.5 签名材料外泄面（安全项，我实际核对过）

`build` job 的 `Release Apk Sign` 步骤（L169–178）把 keystore 与口令写入 `$GITHUB_WORKSPACE/app/legado.jks` 与 `gradle.properties`。我核对了本文件内的全部 `upload-artifact`（L209/L228/L235）与 release `files:`（L279/L309）路径，只有 `apk/*.apk` 与 `mapping/*.txt`/`missing_rules.txt`，**没有任何上传路径覆盖 keystore 或 gradle.properties**。→ 未发现凭据外泄路径。

---

## 2. 发现

### F1 — 【中·发布前必须闭合】报告把"必然走 beta"写成结论，但该分支由 `git diff --name-only HEAD~1 HEAD` 是否命中 `updateLog.md` 决定

- 位置：`.github/workflows/test.yml:82`（`UPDATE_LOG_CHANGED=$(echo "$CHANGED_FILES" | grep -q "app/src/main/assets/updateLog.md" …)`）、`:87-88`（`updated=yes`）、`:270`（`if: needs.prepare.outputs.updateLog_updated == 'yes'` → `prerelease: false`）；主张出处 `10-execution.md:102`。
- 报告原文（`10-execution.md:102`）："本轮**没有**改 `updateLog.md`（任务书禁止），所以 `push main` 时 `updateLog_updated != 'yes'` 分支成立 → 走 beta"。这是**结论式表述**，不是条件式表述。
- **具体失败场景**：controller 按授权把开发分支合并到 `main` 并推送。`prepare` 的检出是合并提交，`HEAD~1` 是合并提交的第一父（即 `main` 原头），因此 `HEAD~1..HEAD` 等于**整条开发分支的累计 diff**。只要该区间内**任一历史提交**（不是本轮、不是 executor 的提交）触碰过 `app/src/main/assets/updateLog.md`，`UPDATE_LOG_CHANGED=yes`、`CRONET_JSON_CHANGED=no` → `updated=yes` → L270 走 `Publish Official Release`，`prerelease: false`，**创建正式版 Release**。这与 `00-task.md:11` 的"不得创建正式版"直接冲突，且发生在 controller 已授权推 `main` 之后、不可回滚到"未发布"状态（`softprops/action-gh-release` 创建的正式 Release 需人工删除）。
- **为什么 executor 覆盖不到**：这条路径取决于开发分支与 `main` 的**历史差异**，不是工作树状态；executor 报告 §5 的六条残留里没有任何一条提到它，`§2.3` 反而把它当既定事实。这正是"该写条件却写成了结论"。
- **建议（推送前，controller 执行）**：
  1. `git diff --name-only $(git merge-base origin/main HEAD)..HEAD -- app/src/main/assets/updateLog.md` —— 输出为空才可安全推送；
  2. 推送后立刻核对新建的是 `beta` 预发布还是正式 tag；
  3. 若确实命中，需先用单独授权处理（例如改走非 `HEAD~1` 的判定），不得在本轮授权下直接推。

### F2 — 【中·发布前必须闭合】若以 GitHub PR 方式合并到 `main`，`prepare` 被 job 级 `if` 跳过，整条发布链静默不产出任何 APK 与 Release；报告完全未披露该分支

- 位置：`.github/workflows/test.yml:31`（`if: ${{ !startsWith(github.event.head_commit.message, 'Merge pull request') }}`），连锁影响 `build`(L145) 与 `prerelease`(L242)。
- **具体失败场景**：controller 按《发布推送流程书.md》用 GitHub PR 合并开发分支（GitHub 默认合并提交信息为 `Merge pull request #N from …`），或本地 `git merge --no-ff` 后把合并提交信息改写成该前缀。此时 `prepare` 的 `if` 求值为假 → `prepare` skipped → `build`/`prerelease` 因 `needs` 未满足而 skipped。**结果是：Actions 显示"成功"，但既不构建也不发布，`beta` Release 不会被创建或更新**，controller 在"首次线上资产出现后核对"那一步才会发现，届时已消耗一次推送。
- **为什么算发现**：`10-execution.md` §5 列了 6 条残留（`if: ${{ false }}` 未实跑、`gh release delete beta`、临时文件未清理、`workflow_dispatch` 保留、`prepare` 死输出、服务端校验不确定），**没有任何一条覆盖这个"发布整体 no-op"的分支**。任务书要求 reviewer 重点检查"beta 触发条件"，这就是触发条件面最直接的反例。
- **建议**：推送前确认合并方式（本地 merge 且提交信息不以 `Merge pull request` 开头）；推送后前 1 分钟内核对 `Test Build` 中 `prepare` 是否 `success` 而非 `skipped`。

### F3 — 【低·证据完整性】`18-review-probes.md` 的冻结 HEAD 与实际 HEAD 不一致，冻结窗口无法对齐

- 位置：`18-review-probes.md:3-5`（`冻结 HEAD：fd045f69b2bdadcba86d30b525fe1fd244edb9ce`、`冻结快照 SHA-256：1434f7b9447516d1a1588da54d23d0b53723b4c76ac5fa926243d5bd16b70463`）；对照 `10-execution.md:12`（`HEAD=3c6685cffb750e83b53aea9cfaafcaa0e61b7fb3`、`snapshot_digest: 7ea670dd3394ad8e5dcd7c1b15030b38b7eefa430a9e8d0f2656b95cad790961`）。
- **具体失败场景**：会话初始 `git status` 快照显示当前 HEAD 为 `3c6685cff`（`feat(release): add normal and private build identities`），而探针声称冻结在它的父提交 `fd045f69b`（该值同时出现在 `00-task.md:92` 的"实时基线"，说明是任务书写就时的旧值被沿用到探针）。这意味着探针里那条 PASS 的 `workflow-diff-check`（`git diff --check -- .github/workflows/test.yml`，退出码 0、输出为空）**跑的可能是本轮改动落地之前的树**，其"真实工作树前后状态：stable"也不覆盖当前 HEAD。若如此，`00-task.md:100` 依赖的"runner 前后快照不一致时本轮报告失效"这一完整性保证，本轮**实际未被建立**。
- **建议**：controller 在验收时刷新冻结基线（HEAD 与 snapshot digest），或明确声明该探针属上一轮遗留、本轮改用 `06-review-commands.json` 的 `phase-cd-diff-check` 重跑取证。**注意**：该条不改变 §1 的实质结论——我是直接读当前工作树得出的判断，不依赖探针。

### F4 — 【低·证据缺口】"其它 workflow 无外部写入路径"这一支撑性结论落在我白名单外，无法独立复核

- 位置：`10-execution.md:104-109`（§2.4：`Select-String -Path .github/workflows/{cronet,release,stale,web}.yml -Pattern 'gitee|lanzou|telegram|link-E'` → 无输出）。
- **失败场景**：`test.yml` 同时带 `workflow_run: workflows: [Build Web]`（L16–20），即 `web.yml` 每次完成后会再触发本 workflow。若这四个文件里存在**该正则覆盖不到**的外部目的地（例如别的上传服务、别的 token 变量名、大写以外的写法），GitHub-only 就不成立，而报告用这条不可复核的检查把它当成了已闭合并写进 §2.3 的结论链。
- **建议**：controller 推送前用更宽的模式全量复核 `.github/workflows/`（例如按 `uses:|curl|wget|git push|gh release|api\.` 而非仅四个关键词），并把输出贴回 `10-execution.md`。本条**不是**说存在该路径，是说该主张目前只有不可复核的单点证据。

### F5 — 【低·产品侧后果未披露】禁用 `update-storage` 切断 link-E 更新链，既有 private 用户将收不到更新

- 位置：`.github/workflows/test.yml:497-537`。
- **事实**：该 job 的职责是把 `legado.app.private` 产物推到 `Luoyacheng/link-E` 的 `sigma/leagado_Sigma_正式版.apk`（L532），服务的是既有 `io.legado.app.tjf.release` 升级链。禁用后该文件永久停留在旧版本。
- **为什么算发现**：这是本轮授权内**预期**的后果（任务书明令禁止写 link-E），executor 报告中属"未做"，但 §5 的残留清单里没把它列为"对既有用户的影响"。发布后若用户反馈"检查不到更新"，需要能立刻定位到这是本轮的有意切断，而不是新 bug。
- **建议**：在 beta Release 说明或后续任务里登记"link-E 更新链自本轮起停更"，并确认 private 用户的更新源已迁移（或明确接受停更）。

### F6 — 【低·信息性安全】被禁用 job 中保留的 PAT 内嵌 clone URL，未来恢复时风险复现

- 位置：`.github/workflows/test.yml:528`（`git clone https://${{ secrets.S_GITHUB_TOKEN }}@github.com/Luoyacheng/link-E.git link-E-repo`）。
- **性质**：token 会落入克隆仓库的 `.git/config` remote URL，并可能出现在 `git` 命令的错误输出中。本轮该 job 被 `if: ${{ false }}` 挡住，**风险已被中和**，因此不构成本轮问题。
- **建议**：将来若按 L27 的"便于恢复"意图重新启用，改用 `http.extraheader` 或 `GH_TOKEN` 环境变量 + 匿名 clone，避免凭据进 URL。

### F7 — 【低·一致性 nit】报告 §2.2 输出与工作树的 secret 引用不完全对应

- 位置：`10-execution.md:80`（`references=['lanzou->secrets.LANZOU', …]`）对照 `.github/workflows/test.yml:320-325`（实际为 `secrets.LANZOU_ID`、`secrets.LANZOU_PSD`、`secrets.LANZOU_FOLDER_ID`、`secrets.LANZOU_URL` 四个）。
- **判断**：极可能只是该校验脚本做了前缀/首次匹配后截断显示（另三条 `BOT_TOKEN`/`GITEE_TOKEN`/`S_GITHUB_TOKEN` 都精确），**不影响任何结论**。按"报告与工作树不一致即为发现"的要求记录在此，供审计，不建议为此返工。

---

## 3. 我检查过、**未发现问题**的方面（明确写"未发现"）

- **YAML 结构与重复键**：7 个 job；四个目标 job 各恰一条 job 级 `if`；未见任何重复映射键；`if: ${{ false }}  # github-only` 的注释符前有空格，属合法 YAML 注释，值确为布尔字面量。**未发现**重复键导致"后者覆盖前者"的假禁用——这正是 executor 主动规避的点，我确认其规避有效。
- **job 依赖图**：不存在任一**启用中**的 job `needs` 这四个被禁用 job；禁用它们不会连带 skip `prerelease`。**未发现**级联误伤。
- **残留自动条件**：旧 `secrets.*`/`needs.*` 条件仅存在于 L316/344/386/501 注释行。**未发现**生效残留。
- **GitHub 发布路径完整性**：matrix（L142）、身份注入（L187）、无 `name` 过滤的全量 artifact 下载（L251–253）、`mv */*.apk .`（L257）、缺一即失败守卫（L261–266）、双通道互补发布（L270/L283/L300）。**未发现**单一身份可被单独发布或缺失时可发出。
- **file 内其它外部写入路径**：`curl`（L409–492）全在 `gitee` 内，`git push`（L537）与 `git clone`（L528）全在 `update-storage` 内，频道投递（L370）在 `telegram` 内。**未发现**三个启用 job 内的第二外部目的地。
- **凭据外泄**：见 §1.5。**未发现** keystore/口令进入 artifact 或 Release。
- **越界**：会话初始 `git status` 快照中，唯一被修改的业务文件是 `.github/workflows/test.yml`；`app/**`、`AndroidManifest.xml`、`updateLog.md`、Gradle、资源文件均未出现在修改列表里。这与 executor 声明的"本轮业务侧只改一个文件"一致，也与"本轮无需重跑 Android 全量门禁"的豁免理由（任务书 `:9`）自洽。**未发现**业务侧越界。（其余变更全部落在 `.agent-protocol/` 协议文件与 `bookshelf.json`/《维护计划书》，属 runner/controller 或用户既有改动，非本轮业务 diff；谁写的我无法从白名单内定责。）
- **简化**：本轮未引入新抽象、无向后兼容垫片、无新增文件。禁用方式是"每 job 恰一条 `if` + 旧条件降级为注释"，是在"最小可审计"约束下的合理取舍；`prepare` 中 `lanzou/telegram/gitee/sgithub` 四个 outputs 成为死输出（`10-execution.md:140` 已主动披露并给出保留理由）。**判定可接受，不作为 finding**。

---

## 4. 我没检查到的范围（NOT RUN / 白名单外，不得据此推论通过）

1. **所有 `git` 命令：NOT RUN**（本实例无 Bash）。`git diff`、`git diff --check`、`git diff --numstat`、`git status -sb`、`git merge-base` 全部未由我执行。§1.4 的 diff 形态结论是**推算**，不是实跑。
   - 其中 `phase-cd-diff-check` 由 runner 探针跑过：`18-review-probes.md:11-19` 记录 `git diff --check -- .github/workflows/test.yml` 退出码 0、输出为空 —— 属 **runner 证据，非我亲跑**，且受 F3 的冻结 HEAD 不一致影响。
2. **`README`/`PROJECT.md`/`发布推送流程书.md`：未读。** 任务书 line 108 的 reviewer 白名单未列 `PROJECT.md`，故按提示词第 4 条不读；`发布推送流程书.md` 同理未列，因此 F2 里"controller 用什么方式合并"我无法查证，只能给出条件式结论。
3. **`.github/workflows/` 下 test.yml 以外的任何文件：未读**（含 `web.yml`/`release.yml`/`cronet.yml`/`stale.yml`）。F4 因此无法闭合。**没有**用 Glob/Grep 间接探测这些路径。
4. **`app/**` 全部：本轮未读**（含 `AppReleaseInfo.kt`、`AppUpdateGitHub.kt`、`AppUpdateGitee.kt`、`AppConst.kt`、`AppReleaseInfoTest.kt`、`build.gradle`、`AndroidManifest.xml`、`updateLog.md`）。它们在白名单内但**不属本轮 diff**（本轮只审 workflow 改动），故未展开；C+D 的消费端正确性依赖此前各轮的 reviewer 结论，本轮**未重新验证**。
5. **Gradle / 定向 JVM 测试 / compile / lint / 两次 release 构建 / `apkanalyzer` / `apksigner`：NOT RUN**，与任务书 `:9` 的豁免一致（本轮未触及 Gradle 与源码），我仅确认"未触及"这一点与工作树快照相符（§3 越界项）。
6. **`actionlint` / GitHub workflow schema 校验 / PyYAML：NOT RUN**。F1 里 `if: ${{ false }}` 的**服务端**接受性、以及 §1.3 表格第 1 项（无重复键的解析器级证明）均未由我运行工具验证，只有只读结构证据与文档依据。
7. **executor 的 `verify_github_only.py`：未读、未复算**。它位于 `C:\Users\huixu\AppData\Local\Temp\dual-release-privacy\`（工作区外，非白名单路径），其 16 项输出与 SHA-256 `29A7112B…` **我无法独立核验**；§1.3 的 15 项是我自己从工作树重算的等价断言，不是对该脚本的复算。
8. **所有哈希值未验证**：`3c6685cffb750e83b53aea9cfaafcaa0e61b7fb3`（全 SHA）、`test.yml` SHA-256 `BA23A58B…`、脚本 SHA-256 `29A7112B…`、`snapshot_digest 7ea670dd…`、探针 `1434f7b9…` —— 一律未复算。
9. **任何 GitHub Actions 实跑、线上 beta Release、真机/`adb`、外部目的地写入：NOT RUN**，且本轮未授权。
10. **临时文件残留**：`10-execution.md:133-138` 自述两个调试草稿未清理（沙箱拒绝 + 无审批通道）。我**未尝试**读取或清理系统临时目录（白名单外）。

---

## 5. 给 controller 的最小闭合清单（按顺序）

1. 推送前：跑 F1 的 `merge-base` 检查，确认区间不含 `updateLog.md`；确认合并提交信息不以 `Merge pull request` 开头（F2）。
2. 推送前：刷新冻结基线或声明探针属上轮遗留，并用 `06-review-commands.json:phase-cd-diff-check` 对当前 HEAD 重跑一次 `git diff --check`（F3）。
3. 推送前：对 `.github/workflows/` 全量复扫外部写入面，把输出贴回 `10-execution.md` §2.4（F4）。
4. 推送后：核对 `prepare`/`build`(×2)/`prerelease` 均为 `success`，四个外部 job 均为 `skipped`；核对新建的是 `beta` 预发布且含 `legado_app_*_normal.apk` 与 `legado_app_*_private.apk` 两个资产（任务书 `:12`）。
5. 同时：确认未创建正式版；在残留清单登记 link-E 停更（F5）与本次 workflow 的服务端接受性（`if: ${{ false }}`，F1 同源风险）。

---

**一句话结论**：本轮 diff 做对了它承诺的事，且我用只读手段独立复核了 16 项断言中的 15 项；但"推送即得双资产 beta Release"这句话在 F1/F2 任一命中时不成立，报告把它当结论写死了。闭合 §5 第 1、2 条之前，本轮应视为"diff 通过、发布待核对"，而非"可发布"。
