# dual-release-privacy — A/B 阶段执行证据与 C 阶段起点

状态：`red-ready / rework`（A、B 已完成；C 未授权）

## 证据归属

- 本文件由 controller=Codex 在 DSH executor 超时后写入，用于记录 A/B 的真实工作树与红测，不冒充 DSH 的 executor 自述，也不满足未来 C–F 的独立执行/复审/验收要求。
- DSH `executor-r005` 因沙箱内无法写用户级 headless profile，以 `EPERM` 退出；受控提权后的 `executor-r006` 在 600 秒边界 timeout，退出前部分改写测试，但未形成报告。两轮原始回执保留在顶层/`rounds/`，不得当成成功执行证据。
- controller 随后把测试从“引用不存在 API 的编译红”收敛为“只调用现有生产 API 的断言红”，避免测试源码重复定义未来生产类型并妨碍 C 阶段实现。

## A 阶段 — 恢复、分类与重新冻结

- 分支/HEAD：`codex/manga-import` / `fd045f69b2bdadcba86d30b525fe1fd244edb9ce`；`git log --oneline -3` 仍为 `fd045f69b`、`7042b00af`、`d2f862d40`。
- `CURRENT.md` 已保持 task=`dual-release-privacy`、state=`rework`、mode=`triad`，并把 `route_revision` 升到 4；角色仍为 Codex controller+acceptor、DSH executor、Claude reviewer。
- `00-task.md` 顶部已重新冻结 A/B 范围；`05-routing.md` 已记录身份纠偏；`06-review-commands.json` 已改为 A/B diff/status 探针；旧 `20-review.md` 与 `30-acceptance.md` 已显式标记 SUPERSEDED，历史 rounds 未删除。
- 旧未提交业务 diff 分类：
  - `.github/workflows/test.yml` 的 `release + releaseS` 无条件矩阵属于旧映射，C 阶段必须重写为 normal/private；A/B 未继续修改。
  - `AppReleaseInfo.kt` 的 `BETA_RELEASES.isBeta()` 补丁保留历史临时身份兼容，但仍把发布通道与包身份混在一个 `AppVariant` 中；C 阶段必须按两轴分离重写；A/B 未继续修改。
  - 旧 `AppReleaseInfoTest.kt` 的 `.release=普通版 / .releaseS=共存版` 断言已被替换；仅资产构造方式和现有解析入口被复用。
- 用户既有/其它任务改动 `.agent-protocol/tasks/ai-stage-e-anchor-controlled/05-routing.md`、`维护计划书-后续修复与增强.md`、`.claude/`、`bookshelf.json` 均未纳入本阶段业务 diff，也未清理或还原。

## B 阶段 — 测试先行与红测

实际修改：

- `app/src/test/java/io/legado/app/help/update/AppReleaseInfoTest.kt`：新增 6 个纯 JVM 测试，只调用现有 `Asset`、`GithubRelease`、`GiteeRelease`、`AppReleaseInfo`、`AppVariant` API；生产源码未为测试让路。
- 本文件：记录范围、执行归属、真实命令和红测结果。

冻结的契约与覆盖：

1. `io.legado.app.tjf.release -> PRIVATE`，`io.legado.app.tjf -> NORMAL`，`.releaseS -> 非正式双版本身份`。
2. GitHub beta/正式元数据中的 normal/private 必须保持身份、只选自身 APK，且资产顺序互换不影响结果。
3. Gitee beta/正式元数据同样必须区分 normal/private。
4. `.releaseS` 与额外无关 APK 不得进入 normal/private 候选集。

实际命令与结果：

1. 沙箱内运行：`$env:GRADLE_USER_HOME='D:\gradle_home'; .\gradlew.bat :app:testAppDebugUnitTest --tests io.legado.app.help.update.AppReleaseInfoTest --console=plain`
   - 结果：`EXIT=1`，在创建 `D:\gradle_home\...\gradle-8.14.4-bin.zip.lck` 时 `FileNotFoundException (拒绝访问)`；这是沙箱权限失败，不是测试结果。
2. 对同一命令受控提权后运行 DSH 留下的初稿：
   - 结果：`BUILD FAILED in 1m 53s`，测试源码因不存在的 `releaseIdentity`、`BETA_PRIVATE`、`BETA_NORMAL` 而编译失败；controller 判定这种红法会与未来生产类型集成冲突，因此未作为最终 B 阶段红测。
3. controller 收敛测试后再次运行完全相同的定向命令，并在把顺序/通道组合改为一次性汇总断言后最终复跑：
   - 最终结果：测试编译成功，`:app:testAppDebugUnitTest` 真实执行；`BUILD FAILED in 22s`，`6 tests completed, 5 failed`。
   - PASS：`packageIdentityToAssetIdentityContractIsFrozen`。
   - FAIL：`officialReleaseMustNotFlattenNormalAndPrivateIdentity` — normal/private 实际都为 `OFFICIAL`。
   - FAIL：`betaReleaseMustKeepIdentityAndUseBetaChannel` — beta 元数据中的 normal/private 未保持 beta 通道。
   - FAIL：`githubSelectionMustBeIdentityExactAndOrderIndependent` — beta/正式的两个资产顺序共出现 4 个错配：列表第一个身份会冒充另一个身份。
   - FAIL：`giteeSelectionMustBeIdentityExactAndOrderIndependent` — beta/正式的两个资产顺序共 4 组均把 normal/private 压成 `OFFICIAL`。
   - FAIL：`releaseSAndUnrelatedApksMustNotEnterDualReleaseCandidates` — releaseS/无关 APK 与 normal/private 落入同一候选变体。
   - 机器证据：`app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.update.AppReleaseInfoTest.xml`，tests=6 / failures=5 / errors=0。
4. `git diff --check -- app/src/test/java/io/legado/app/help/update/AppReleaseInfoTest.kt .agent-protocol/CURRENT.md`
   - 结果：无 whitespace error；仅有 Git 的 LF→CRLF 提示，不是 diff-check 失败。

## 边界与未运行项

- A/B 没有继续修改 `.github/workflows/test.yml`、`AppReleaseInfo.kt`、`app/build.gradle`、Manifest、资源、签名、数据库、AI 源码或设置；工作树中的 workflow/Kotlin 生产 diff 是前一版映射遗留，已分类但未还原。
- `:app:compileAppDebugKotlin` 独立门禁：`NOT RUN`（定向测试的依赖任务显示 UP-TO-DATE，不等同独立门禁）。
- 全量 `:app:testAppDebugUnitTest`：`NOT RUN`（本阶段只运行指定测试类，且预期红）。
- `:app:lintAppDebug`：`NOT RUN`。
- normal/private release APK 构建、包名/签名检查、真机迁移、在线更新、GitHub Actions：`NOT RUN`，均属 C–F。
- reviewer/acceptor 对新实现的复审与验收：`NOT RUN`；当前只有 controller 对 A/B 白名单和红测做了独立核对。
- 未提交、未推送、未发布、未切分支、未安装/卸载 APK、未清数据。

## C 阶段进入条件

- controller 已核对新白名单和可复现红测；当前可据此另行冻结 C 的生产写白名单，但本轮没有授权或开始 C。
- C 必须让上述测试转绿，同时把“发布通道”和“资产/安装身份”分离；不得用调整断言、依赖资产顺序或继续发布 `.releaseS` 的方式消除红测。
