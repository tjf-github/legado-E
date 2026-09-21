# dual-release-privacy — 执行报告

> **SUPERSEDED BASELINE（2026-09-20）**：下方报告对应已废止的 `.release/releaseS` 产品映射，不再描述当前 A/B 工作树。route_revision 4 的 executor 必须以 `00-task.md` 顶部新冻结范围重写本文件，记录旧 diff 分类、测试改动和实际红测；不得修改生产文件。

状态：`blocked`

- 运行实例 / 提供方：DSH（本机人类会话实例，`danger-full-access` 级沙箱未获批准）；任务书角色映射 controller=codex / executor=dsh / reviewer=claude / acceptor=codex，`route_revision=3`。
- 实际读取：`.agent-protocol/tasks/dual-release-privacy/14-executor-prompt.md`、本任务 `00-task.md`、`05-routing.md`、`06-review-commands.json`、`10-execution.md`、`20-review.md`、`rounds/executor-r001/15-executor-raw.md`、`.agent-protocol/README.md`、`.agent-protocol/CURRENT.md`、`.agent-protocol/PROJECT.md`、`.github/workflows/test.yml`、`app/build.gradle`（第 86–129 行）、`app/src/main/java/io/legado/app/help/update/AppReleaseInfo.kt`、`app/src/main/java/io/legado/app/help/update/AppUpdateGitHub.kt`、`app/src/main/java/io/legado/app/help/update/AppUpdateGitee.kt`、`app/src/main/java/io/legado/app/constant/AppConst.kt`。授权列表中的 `发布推送流程书.md` 与 `app/src/main/AndroidManifest.xml` 本轮**未打开**（见偏差 1、2）。
- 实际修改：仅 `.github/workflows/test.yml`（1 个 hunk，删除 5 行新增 1 行：把 `set-matrix` 步骤的 if/else 合并为无条件输出）。
- 未触碰：`app/build.gradle`、`AndroidManifest.xml`、`AppReleaseInfo.kt`、`AppConst.kt`、任何 `app/src/**`、AI 相关文件与设置、`20-review.md`、`30-acceptance.md`、签名材料与凭据；未提交、未推送、未切分支、未发布。
- 复现或基线：`git status -sb` = `## codex/manga-import...origin/codex/manga-import`，`git rev-parse HEAD` = `fd045f69b2bdadcba86d30b525fe1fd244edb9ce`，与 `00-task.md` 实时基线一致；工作树 `M .agent-protocol/CURRENT.md`、`M .agent-protocol/tasks/ai-stage-e-anchor-controlled/05-routing.md`、`M .github/workflows/test.yml`（本次改动）、`?? .agent-protocol/tasks/dual-release-privacy/`、`?? bookshelf.json`。与 `00-task.md` 记录的修前摘要相比，`?? .claude/` 当前不存在于 `git status --porcelain` 输出中（差异仅限未跟踪文件的存在与否，不影响业务改动判断；无法在只读边界内进一步判断其去向）。
- 最小实现：

  改动前 `prepare.set-matrix`：

  ```yaml
  - id: set-matrix
    run: |
      if [ "${{ steps.updateLog.outputs.updated }}" == "yes" ]; then
        echo 'build_matrix={"product":["app"],"type":["release","releaseS"]}' >> $GITHUB_OUTPUT
      else
        echo 'build_matrix={"product":["app"],"type":["release"]}' >> $GITHUB_OUTPUT
      fi
  ```

  改动后：

  ```yaml
  - id: set-matrix
    run: |
      echo 'build_matrix={"product":["app"],"type":["release","releaseS"]}' >> $GITHUB_OUTPUT
  ```

  理由：原逻辑把「构建矩阵含两个 type」绑定在 `updateLog_updated == 'yes'` 上，导致更新日志未变化的推送只构建 `release`，与完成条件第 1 条冲突。`releaseS` 变体在独立的 `build` matrix 分片中通过既有 `sed "s/'.release'/'.releaseS'/"` 临时替换包名后缀（`build` 步骤第 188 行，未改），文件名标识仍走既有的 `原包名` / `共存` → beta/plus/`releaseS` 改名链（`prerelease`、`gitee`、`telegram` 步骤未改），因此 App 内更新识别逻辑（`AppReleaseInfo.assetToAppReleaseInfo` 按 `releaseA` > `releaseS` > `release` 匹配）无需改动。没有触碰 `app/build.gradle`、Manifest、任何 Kotlin 源码。
- 命令与结果：

  1. `git status -sb; git log --oneline -3; git rev-parse HEAD` → HEAD `fd045f69b`，分支 `codex/manga-import`，与基线一致（正常信息走 stderr，非失败）。
  2. `git diff -- .github/workflows/test.yml` → 恰好 1 个 hunk，`@@ -138,11 +138,7 @@ jobs:`，删除 if/else 的 5 行、新增 1 行 echo；无其它文件出现在本任务的业务 diff 中。
  3. `git diff --check -- .github/workflows/test.yml` → 无输出，退出码 0（与 `06-review-commands.json` 冻结的 `workflow-diff-check` 完全同参）。另跑无路径限定的 `git diff --check` 作对照 → 退出码 2，唯一输出为 `.agent-protocol/CURRENT.md:19: new blank line at EOF.`：该文件属 controller/协议指针的既有未提交改动，不在本任务白名单内、本轮未触碰，且 `06-review-commands.json` 的冻结命令已限定路径，故不构成本业务 diff 的问题（仅作如实记录）。
  4. `python -X utf8 -c "yaml.safe_load(open('.github/workflows/test.yml'))"`（PyYAML 6.0.3，Python 3.12.9）→ 解析成功；改动后 `prepare.steps[-1]` 的 run 块为 `'echo \'build_matrix={"product":["app"],"type":["release","releaseS"]}\' >> $GITHUB_OUTPUT\n'`，仍是合法 YAML 且 `$GITHUB_OUTPUT` 重定向存在。
  5. 矩阵展开模拟（脚本 `%TEMP%\drp_matrix_check.py`）：`json.loads` 得到 `{'product': ['app'], 'type': ['release', 'releaseS']}`，展开为 `[('app','release'), ('app','releaseS')]`，对应 artifact 名 `legado.app.release` / `legado.app.releaseS`（`update-storage` 第 514 行下载的正是 `legado.app.releaseS`），typeName 映射为 `原包名` / `共存`；`build` 的 `strategy.matrix` 仍引用 `fromJson(needs.prepare.outputs.build_matrix)`，`prepare.outputs` 未变动。
  6. 全仓（PowerShell `Get-ChildItem ... | Select-String`）检查 `build_matrix` 只出现在 `test.yml` 的 42/141/146 行，没有第二个消费点依赖旧的 `updated` 分支。
  7. `.\gradlew.bat :app:compileAppDebugKotlin :app:testAppDebugUnitTest --console=plain`（已设 `GRADLE_USER_HOME=D:\gradle_home`，JDK 17.0.20）→ **失败**：`java.io.FileNotFoundException: D:\gradle_home\wrapper\dists\gradle-8.14.4-bin\...\gradle-8.14.4-bin.zip.lck (拒绝访问)`，`EXIT=1`。
  8. 对第 7 条按任务书第 32 行申请一次 `danger-full-access` 后重跑同一命令 → **未执行**：沙箱返回 `Error: sandbox escalation to "danger-full-access" requires approval, but no approval channel is available`（无审批通道，提权不可用）。因此编译与单测门禁在本轮**无法取得真实结果**。
  9. `bash -n` 对全部 18 个 `run:` 块做 shell 语法校验（脚本 `%TEMP%\drp_bash_lint.py`）→ **无效，不作为证据**：本机 `bash` 是 WSL 转发器，18/18 均以 rc=1 且错误信息为 GBK 编码的 WSL 报错退出（连未改动的旧块同样失败）；系统未安装 Git Bash，`wsl --status` 报 `E_ACCESSDENIED`。该结果不能解释为脚本有语法错误。

- 偏差：

  1. 本轮未真正打开 `发布推送流程书.md`；该文件的发布流程事实来自项目 `AGENTS.md`（§4 发布合并、push main 触发 beta）与本次读到的 workflow 本体，**不是**从流程书正文核对得来，属未读项。
  2. 提示词要求「按 `14-executor-prompt.md` 执行并只做该文件指示的输出」，因此本轮**未修改任务书之外任何文档**，包括未在流程书/维护计划书里补充 CI 行为变化说明；若 controller 认为需要，需另行扩权。
  3. 沙箱审批通道不可用（第 8 条）是本轮唯一硬阻塞，属任务书授权条款未预见的环境事实，非执行选择。
  4. 未运行 `:app:lintAppDebug`（同因提权不可用），完成条件第 3 条因此无法判定。
  5. 本轮未打开 `app/src/main/AndroidManifest.xml`：改动只落在 CI 矩阵，未涉及包名/权限声明，且 `app/build.gradle` 的 `.releaseS` 分支已确认存在（第 91、94 行），故判断不需要读该文件；如需覆盖，请作为复审补充项。

- NOT RUN：
  - `:app:compileAppDebugKotlin` —— NOT RUN（提权被拒，见命令 7/8）。
  - `:app:testAppDebugUnitTest` —— NOT RUN（同上）。
  - `:app:lintAppDebug` —— NOT RUN（同上）。
  - GitHub Actions 实跑（`prepare` → matrix fan-out → `prerelease` 上传两个 APK）—— NOT RUN，本任务禁止推送/发布，且无网络授权；只能给出静态读取 + YAML/JSON 解析结论。
  - `bash -n` shell 语法校验 —— NOT RUN（本机无可用 Linux shell；第 9 条的 rc=1 不可用）。
  - `发布推送流程书.md` 正文核对 —— NOT RUN（见偏差 1）。

- 不确定（缺少什么才能判断）：
  1. 不确定 `.releaseS` 变体在**更新日志未变化的 beta 发布**里被上传后，是否有人依赖「beta release 只有 1 个 APK」这一旧事实（例如第三方镜像、Telegram 频道文案、用户脚本）。要判断需读仓库外的下发渠道配置与本机之外的消费方，超出白名单。
  2. 不确定 `update-storage` 的 `sgithub == 'yes'` 在正式发布场景下的取值；若为 `yes`，其下载 `legado.app.releaseS` 的行为在改动前后一致（改动前正式发布也已构建 releaseS），故本轮不涉及回归，但要确认需查该仓库 Secrets 是否配置（禁止读取凭据）。
  3. 不确定 GitHub Actions 对本 workflow 的 YAML 语义校验是否另有告警（如 `steps[-1]` 的 `id` 位置、`outputs` 引用）；本地 PyYAML 只能证明语法合法，要判断需真实运行或使用远端 linter（需网络授权）。
  4. 不确定 Gradle/单测门禁在本改动下是否通过；`app/src/**` 与 `app/build.gradle` 均未改动，理论上不影响 JVM 编译与单测，但**这是推理不是证据**，要判断必须能在有审批通道的实例里跑完第 7 条命令。

- 清理：本轮只在系统临时目录生成过两个一次性校验脚本（`%TEMP%\drp_matrix_check.py`、`%TEMP%\drp_bash_lint.py`，命令 5/9 所用），已删除，`Get-ChildItem $env:TEMP\drp_*` 计数为 0；工作树内未产生任何临时产物（`git status --porcelain` 除既有条目外只多出 `.github/workflows/test.yml` 的改动与任务目录本身）。

- 当前结论：`blocked` —— 业务改动已完成且静态证据自洽（完成条件 1、2 已满足；4 的「acceptor 处理全部 findings」由后续角色完成；5 成立：本任务业务 diff 只含 `.github/workflows/test.yml`，无 AI 源码或设置变更），但完成条件 3 要求的 `compileAppDebugKotlin` / `testAppDebugUnitTest` / `lintAppDebug` 因沙箱提权无审批通道而全部为 NOT RUN，无法在本轮给出通过结论。请 controller 决定：为 Gradle 门禁提供可用的提权/审批通道后由同一或新一轮 executor 复跑，或显式把门禁降级为 NOT RUN 并据此调整完成条件。
