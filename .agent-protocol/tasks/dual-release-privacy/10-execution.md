<!-- agent-protocol-binding
round_id: executor-r007
task_digest: 1b5d59a0a4d5ec701f9fb35e56a807318dfe4bf9f2c314230c0b08a014dfdb52
snapshot_digest: 7ea670dd3394ad8e5dcd7c1b15030b38b7eefa430a9e8d0f2656b95cad790961
--># dual-release-privacy — GitHub-only 发布收口执行证据（executor=dsh，route_revision 7）

状态：**完成（仅限本轮授权范围）**

- 本轮只执行 `00-task.md` 顶部「2026-09-21 GitHub-only 双版本发布收口」段授权的唯一业务改动：在 `.github/workflows/test.yml` 硬禁用 `lanzou`、`telegram`、`gitee`、`update-storage` 四个 job，并留下静态证明。
- 未做：提交、推送、切分支、发布、GitHub Actions 实跑、创建/更新 Release、Gitee/link-E/Telegram/蓝奏云任何写入、真机操作、安装/卸载 APK、读取凭据或用户数据。
- 未改：APK 身份、签名、版本、应用功能、AI 功能、Gradle、源码、资源、Manifest、`updateLog.md`、协议其它角色原文（`20-review.md`、`30-acceptance.md`）。
- 修改基线：分支 `codex/manga-import`，HEAD=`3c6685cffb750e83b53aea9cfaafcaa0e61b7fb3`，`test.yml` 改动前与 HEAD 一致（`git diff` 为空）。

## 1. 改了哪些文件、各自为什么改

| 文件 | 变更 | 为什么改 |
| --- | --- | --- |
| `.github/workflows/test.yml` | 4 处：`lanzou`(L312)、`telegram`(L340)、`gitee`(L382)、`update-storage`(L497) 各新增 `if: ${{ false }}  # github-only` 与一行说明注释；同时把这 4 个 job 原来「Secrets 非空即运行」的 `if:` 行改为注释行（原条件文本完整保留在同一行注释里） | 用户 2026-09-21 要求发布只写 GitHub；这四个 job 是仅有的外部写入路径（蓝奏云 / Telegram / Gitee / link-E），必须硬禁用且不依赖 Secrets 是否存在 |
| 本文件 `.agent-protocol/tasks/dual-release-privacy/10-execution.md` | 重写为本轮证据 | 任务书指定的 executor 唯一可写证据文件 |

改动后工作树哈希：`test.yml` SHA-256 = `BA23A58B8DD9DCC35CAB9700238F6BD3E0B1815EAE1A88EFA7DF3BF56FC802B6`，`git diff --numstat` = `12 4`（新增 12 行、删除 4 行）。

逐行 diff（`git diff -- .github/workflows/test.yml` 实际输出要点，四个 job 结构相同，以 lanzou 为例）：

```diff
   lanzou:
+    # 用户 2026-09-21 授权：发布只发 GitHub，本 job 硬禁用（不依赖 Secrets 是否存在）
+    if: ${{ false }}  # github-only
     needs: [ prepare, build ]
-    if: ${{ github.event_name != 'pull_request' && needs.prepare.outputs.lanzou == 'yes' }}
+    # 原自动触发条件（Secrets 存在即运行）：github.event_name != 'pull_request' && needs.prepare.outputs.lanzou == 'yes'
     runs-on: ubuntu-latest
```

- 为什么删掉旧的 `if:` 而不是并存两条：同一 YAML 映射里出现两个 `if` 是重复键，多数解析器按「后者覆盖前者」，那会让「硬禁用」变成依赖解析顺序的假禁用。改为**每个 job 恰好一条 `if`，值为布尔字面量 false**，旧条件文本降级为注释保留可审计性。
- 四个 job 的 `steps`、`needs`、`runs-on` 与所有 Secrets 引用**一个字节未改**（保留禁用痕迹，便于将来单独授权恢复）。
- 未改动：`prepare`、`build`、`prerelease` 三个 job 的任何一行；APK 身份矩阵、`-PRELEASE_IDENTITY` 注入、`legado_app_<versionL>_<identity>.apk` 命名、双身份完整性守卫全部保持原样。

## 2. 实际跑过的命令与真实输出

### 2.1 `git diff --check`（任务书明确要求）

```text
$ cd D:\vsproject\legado-E
$ git diff --check -- .github/workflows/test.yml
EXIT=0
$ git diff --check
EXIT=0
(stderr 仅有 Git 的 "LF will be replaced by CRLF" 提示，涉及协议 md 文件，与本轮业务改动无关)
```

### 2.2 静态证明：四个外部 job 无论 Secrets 是否存在都不会运行

命令（一次性脚本经 stdin 执行，不在仓库或临时目录留新文件；`-X utf8` 避免中文转录依赖控制台代码页）：

```powershell
cd D:\vsproject\legado-E
Get-Content "$env:TEMP\dual-release-privacy\verify_github_only.py" -Raw |
  python -X utf8 - .github/workflows/test.yml
```

校验脚本 SHA-256 = `29A7112BBF0E735431F496C0DE3A9B4BA4F9A6B91F64D8EADC2E3EDC65D35D32`（136 行，位于系统临时目录，收尾状态见 §5）。

真实输出（16 项全过，退出码 0）：

```text
PASS  YAML parses without duplicate keys  |  yaml.safe_load OK, jobs=7
PASS  job 'lanzou' hard-disabled with if: ${{ false }}  |  present=True if='${{ false }}'
PASS  job 'telegram' hard-disabled with if: ${{ false }}  |  present=True if='${{ false }}'
PASS  job 'gitee' hard-disabled with if: ${{ false }}  |  present=True if='${{ false }}'
PASS  job 'update-storage' hard-disabled with if: ${{ false }}  |  present=True if='${{ false }}'
PASS  no enabled job depends on a disabled job  |  dependents=none
PASS  prerelease job kept  |  if="github.event_name != 'pull_request' && github.repository == 'tjf-github/legado-E'"
PASS  prerelease still gated on non-PR + tjf-github/legado-E  |  if="github.event_name != 'pull_request' && github.repository == 'tjf-github/legado-E'"
PASS  prerelease guard requires both identities  |  guard step run = mv */*.apk . rm -rf */ # 双版本必须同时到位：缺任一身份都视为发布不完整，直接失败 for identity in normal private; do if ! ls legado_app_*_$identity.apk > /dev/null 2>&1; then echo "缺少 $identity APK，双版本发布不完整" exit 1 fi done ls -l *.apk
PASS  prerelease publishes both channels (beta + official)  |  beta pre-release steps=1 official steps=1, complementary if: "needs.prepare.outputs.updateLog_updated != 'yes'" / "needs.prepare.outputs.updateLog_updated == 'yes'"
PASS  prerelease releases every downloaded apk  |  both publish steps use files=<workspace>/apk/*.apk + fail_on_unmatched_files: true
PASS  prepare matrix is exactly normal+private  |  build_matrix={'product': ['app'], 'type': ['normal', 'private']}
PASS  build injects -PRELEASE_IDENTITY per matrix type  |  identity property wired to matrix.type
PASS  disabled jobs keep their external secret references (not deleted)  |  references=['lanzou->secrets.LANZOU', 'telegram->secrets.BOT_TOKEN', 'gitee->secrets.GITEE_TOKEN', 'update-storage->secrets.S_GITHUB_TOKEN'] (job skipped, env never resolved)
PASS  exactly 4 jobs carry the github-only marker  |  marker=4 if-false=4
PASS  old Secrets-driven job conditions are commented out, not active  |  active leftover lines=none

SUMMARY: 16 checks, 0 failed
EXIT=0
```

证明链（每步都对应上面一条 PASS）：

1. **语义**：`if: ${{ false }}` 是布尔字面量 false，不引用 `github.event_name`、`needs.*.outputs.*` 或 `secrets.*`。GitHub 在**展开 matrix 之前**先求值 job 级 `if`，条件为假即 skip，因此四个 job 与「Secrets 是否存在」「事件类型」「matrices」全部无关，恒不运行。
2. **无旁路**：四个 job 都是终端节点（没有任何**启用中**的 job `needs` 它们，`dependents=none`），不存在「被别的 job 拉起来」的路径。
3. **无残留自动条件**：`secrets` 派生的旧条件若还处于生效状态，脚本第 8 项会报 FAIL；实测 `active leftover lines=none`。
4. **YAML 合法**：PyYAML 的 `safe_load` 对**重复映射键直接抛错**——它能解析出 7 个 job，同时反证「每个 job 只有一条 `if`」，不存在重复键被覆盖的假禁用。
5. **外部写入面收敛**：全文 48 处 `lanzou|telegram|gitee|link-E|S_GITHUB|curl|git push|workflow_dispatch|schedule` 命中里，除 `workflow_dispatch`（L21）外**全部**落在四个已禁用 job 内或其 `prepare` 输出/Secrets 读取行；`prepare` 只做 `git fetch`/`git log`/`echo >> $GITHUB_OUTPUT`，`build` 只跑 Gradle 与 artifact 上传，`prerelease` 只调 GitHub 自己的 `softprops/action-gh-release` 与 `richardsimko/update-tag`，无第二个外部目的地。

### 2.3 静态证明：GitHub Release 路径仍会接收两个 APK

- `prepare` 的 matrix 字面量仍是 `{"product":["app"],"type":["normal","private"]}`（解析后结构相等，非文本匹配）；`build` 仍把 `matrix.type` 注入 `-PRELEASE_IDENTITY`，并按 `legado_app_<VERSIONL>_<type>.apk` 命名后上传 artifact `legado.app.normal` / `legado.app.private`。
- `prerelease` job 保留，`if` 仍为 `github.event_name != 'pull_request' && github.repository == 'tjf-github/legado-E'`；`actions/download-artifact@v8` **不带 `name` 过滤**，因此两个身份 artifact 都会落到 `apk/`。
- **缺一即失败守卫**未改：`for identity in normal private; do if ! ls legado_app_*_$identity.apk ...; then echo "缺少 $identity APK，双版本发布不完整"; exit 1; fi; done`。
- 两个发布步骤（beta 预发布 / 正式）都是 `files: <workspace>/apk/*.apk` + `fail_on_unmatched_files: true`，即把守卫放行的全部 APK 一起上传；两者 `if` 互补（`updateLog_updated == 'yes'` / `!= 'yes'`），不会重复发布。
- 本轮**没有**改 `updateLog.md`（任务书禁止），所以 `push main` 时 `updateLog_updated != 'yes'` 分支成立 → 走「删旧 beta → 更新 beta 标签 → 发布 prerelease=true 的 beta Release」，两个 APK 同时进入该 Release。

### 2.4 其它 workflow 不含外部写入路径

```text
$ Select-String -Path .github/workflows/cronet.yml,.github/workflows/release.yml,.github/workflows/stale.yml,.github/workflows/web.yml -Pattern 'gitee|lanzou|telegram|link-E'
(无输出 = 无匹配)
```

## 3. 对照任务书的三条硬要求

| 要求 | 事实 |
| --- | --- |
| 只改 `.github/workflows/test.yml`（唯一业务改动） | 本轮业务侧只改这一个文件，`git status` 可见新增的 ` M .github/workflows/test.yml`；其余改动均为任务开始前既有（协议 md 指针/报告、`bookshelf.json`、`.claude/`），未触碰 |
| 硬禁用四个 job、保留 `prerelease` + 双身份 matrix + 缺一即失败守卫 | 见 §1、§2.2、§2.3，全部为结构断言，非文本猜测 |
| 不改 APK 身份、签名、版本、应用功能、AI 功能或其它文件 | `app/**`、`AndroidManifest.xml`、`updateLog.md`、`.claude/`、AI 相关文件在本轮 `git status` 中无任何新变化 |

## 4. 未运行项（NOT RUN）

以下一律 **NOT RUN**，不得从本报告推论其通过：

- GitHub Actions `Test Build` 实跑、`push main` 触发、真实 beta Release 创建/更新、线上两个 APK 核对：NOT RUN（属 controller 的后续授权动作，本次未授权）。
- 本轮未重复 Android 门禁：`:app:compileAppDebugKotlin`、`:app:testAppDebugUnitTest`、`:app:lintAppDebug`、两次 release 打包，以及 `git diff --check`（后者本轮已跑，见 §2.1）。任务书明确「不要求重复已经在 `3c6685cff` 和 CI run `35555332167` 上通过的全量门禁」，本轮改动只碰 workflow 的 `if` 行、未触及 Gradle/源码，故按授权不重跑。
- 真机/设备侧动作、`adb` 任何命令：NOT RUN。
- `actionlint` 或 GitHub 官方 workflow schema 校验器：NOT RUN（本机未安装 actionlint，任务书禁止安装软件）。替代证据是 PyYAML 解析 + §2.2/§2.3 的结构断言。
- Gitee / link-E / Telegram / 蓝奏云任何真实或手工写入：NOT RUN（且已由本轮改动静态禁用）。

## 5. 不确定项、偏离与残留

1. **`if: ${{ false }}` 的语义未在本机实跑验证**（无 GitHub 运行环境、不授权实跑）。依据是 GitHub 文档：表达式中的布尔字面量 `false` 为假值，job 级 `if` 求值为假即 skip（[Evaluate expressions in workflows and actions](https://docs.github.com/en/actions/reference/workflows-and-actions/expressions)、[Workflow syntax: jobs.<job_id>.if](https://docs.github.com/en/enterprise-server@3.16/actions/reference/workflows-and-actions/workflow-syntax)）。**最终确认只能来自 controller 的 `push main` 实跑**：四个 job 应显示 `skipped`，`prerelease` 应执行并产出双 APK。
2. **`prerelease` 的 `Delete Pre-Release` 步骤会 `gh release delete beta -y`**：这是既有行为（beta 通道每次重建），不是外部目的地写入，本轮未改。它意味着 beta Release 在重发窗口内会短暂消失，属既有发布语义，仅作披露。
3. **收尾清理未完全达成**：任务书要求「收尾前必须清理本任务额外产生且不属于 Gradle 标准构建输出的临时文件」。系统临时目录 `C:\Users\huixu\AppData\Local\Temp\dual-release-privacy\` 下有三个我自建的文件：`verify_github_only.py`（6,031 字节，§2.2 校验脚本，建议保留供 reviewer 复算）、`debug_dump.py`（660 字节）、`debug2.py`（482 字节）——后两个是调试用草稿。我尝试删除它们，被沙箱拒绝：
   ```text
   Remove-Item: Access to the path 'C:\Users\huixu\AppData\Local\Temp\dual-release-privacy\debug_dump.py' is denied.
   [sandbox: file access denied under workspace-write mode]
   ```
   按提示发起一次性提权 `danger-full-access` 重试，结果 `Error: sandbox escalation to "danger-full-access" requires approval, but no approval channel is available`。因此**这两个草稿文件仍在系统临时目录，未清理**，且未再尝试其它绕过方式。它们在工作区之外、不进 git、不含项目内容或凭据，仅含 YAML 解析调试语句；请 controller 或具备权限的实例清理。本轮没有在仓库内产生任何临时文件或日志。
4. **`workflow_dispatch` 保留**：手动触发入口未禁用。它只是入口，四个外部 job 在手动触发下同样被 `if: false` 挡住；`prerelease` 仍受 `github.repository == 'tjf-github/legado-E'` 约束。此项未改属最小改动原则，若 controller 要求连手动入口一并收窄，需要新的授权。
5. **`prepare` 仍会读取四个外部 Secrets 的名称并据此设置 outputs**（`lanzou/telegram/gitee/sgithub`）。这些 outputs 现在没有任何消费者（四个消费 job 已被禁用），保留是为了让 diff 最小、且将来恢复时有据可查；它不产生任何外部写入，只影响 `skip` 判断所需的输出集合。
6. **判断不了的地方**：GitHub 服务端的 workflow 校验是否接受 `if: ${{ false }}`（YAML 层合法、也是 GitHub 表达式语法内的布尔字面量），我只能给出静态与文档依据；若服务端拒绝，症状会是 workflow 无法启动或 job 直接失败，**这一点必须在 `push main` 后核对实跑结果**，不能以本报告代替。

## 6. 证据位置

- 业务 diff：`git diff -- .github/workflows/test.yml`（12 增 4 删，工作树哈希 `BA23A58B…`）
- 校验脚本：`C:\Users\huixu\AppData\Local\Temp\dual-release-privacy\verify_github_only.py`（SHA-256 `29A7112B…`，见 §2.2 复算命令）
- 冻结复审命令：`06-review-commands.json`（其中的 `phase-cd-status` 可核对工作树文件清单；`phase-cd-diff-check` / `phase-cd-business-diff` 的路径数组包含 `.github/workflows/test.yml`）
