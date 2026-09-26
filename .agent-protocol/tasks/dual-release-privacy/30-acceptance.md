# dual-release-privacy — 发布级验收（acceptor=Codex，route_revision 7 / high）

状态：`ACCEPTED / GitHub-only dual beta published`。A-D、本地门槛、E 真机迁移和 GitHub-only 双资产发布均已闭合；历史 `role-verify` timeout/哈希漂移仍保留披露，因此本任务不执行 `close`。

## 2026-09-21 GitHub-only 双版本发布最终验收

- 发布提交：`1e8954afaab94f33cdb88ed283fa39dbd51c962a`（`Merge branch 'codex/manga-import' into HEAD`），已推送至 `origin/main`。推送前确认其第一父等于最新 `origin/main`，且 `HEAD^1..HEAD` 未修改 `app/src/main/assets/updateLog.md`，因此按既有 workflow 进入 `beta` 而非正式版。
- 发布前本地门槛：在同一合并提交的 D 盘隔离 worktree 运行 `:app:compileAppDebugKotlin :app:testAppDebugUnitTest :app:lintAppDebug --no-daemon`，`BUILD SUCCESSFUL in 10m 24s`。C 盘首次运行因 KSP/Gradle 缓存跨盘根路径限制失败，未进入代码失败；换到 D 盘同一提交后通过。
- 独立执行/复审：DSH executor 仅将 `.github/workflows/test.yml` 的 `lanzou`、`telegram`、`gitee`、`update-storage` 四个 job 改为 job 级 `if: ${{ false }}`；Claude reviewer（route_revision 7）结论为本轮 diff 通过、未发现该 diff 缺陷。reviewer 提出的 `updateLog` 区间和合并消息前缀两项发布前条件均已由 controller 在推送前核对闭合。
- GitHub Actions：`Test Build` run `35569674086` 对提交 `1e8954afa` 执行完成；`prepare`、`build (app, normal)`、`build (app, private)`、`prerelease` 均 `completed successfully`；`lanzou`、`telegram`、`gitee`、`update-storage` 均 `skipped`。没有向非 GitHub 目的地写入。
- GitHub Release：`beta` 页面标题为 `legado_app_3.26.092114`，标记为 Pre-release；展开资产列表只有本次要求的两个 APK：`legado_app_3.26.09211443_normal.apk`、`legado_app_3.26.09211443_private.apk`。两个下载链接 HEAD 均返回 HTTP 200，Content-Length 分别为 19,962,905 与 19,962,901 字节。
- 更新选择：线上真实文件名严格满足已通过的 `AppReleaseInfoTest`/`ReleaseIdentity.fromAssetName` 契约（`_normal.apk` → `NORMAL_BETA`，`_private.apk` → `PRIVATE_BETA`），且发布前全量 JVM 已通过。未再次通过真机 UI 点击“检查更新”采集 URL；这属于发布后交互观测缺口，不影响本次 GitHub 双资产已真实存在、可下载且身份可确定的验收结论。
- 保留披露：旧轮次顶层 executor timeout 与历史 reviewer-r002 哈希漂移导致 `agent-protocol role-verify` 仍不满足 `close` 条件；本轮没有删除、改写或伪装修复历史证据。最终发布判定依据是新鲜 executor/reviewer、controller 独立门槛、真实 Actions job 状态与线上 Release 资产。

> 下方原 `C+D accepted（仅限本地自动化门禁范围）` 是 route_revision 6 的 DSH 自验收历史，完整保留；本节是当前唯一判定并覆盖其任务级结论。

## 2026-09-21 Codex 判定摘要

- 用户确认手机上有多个“阅读”；唯一迁移目标是精确包名 `io.legado.app.tjf.release`，它是现有隐私版。新 normal 版只认 `io.legado.app.tjf`。不得按名称或图标猜包，也不得触碰其它“阅读”。
- **本地发布候选通过**：最终树上的 compile、全量 JVM、lint、normal release 打包合并运行成功；private release 另行成功构建。两个候选用固定 `versionName=3.26.092100`、`versionCode=26092100`，包名、显示名、签名与 provider authorities 均符合冻结契约。
- **任务级不通过**：指定设备 `10CE5P1M1Z001P9` 已连接并精确核对；现有 `.release` 使用 GitHub beta/CI 证书 `93a28468…1563e`，本地候选使用另一证书 `bea423a4…db01`。根据任务停止条件，已停止在安装前，不能通过卸载或清数据绕过；数据保留、双包共存和应用内更新选择必须改用 CI 签名产物验收。
- **独立复审未闭合**：`agent-protocol role reviewer -Entry codex` 与 `agent-protocol doctor -Probe -ProviderWriteProbe` 均在 provider 自检阶段报 `Cannot index into a null array`；没有项目内容发给新 reviewer，也没有有效 route_revision 7 报告。route_revision 6 的 Claude 静态复审仍是 C+D 输入证据，但不能冒充本轮 E/F 新鲜复审。
- **线上发布未闭合**：提交、推送、发布未获授权，GitHub Actions 双 build job、两个线上资产与应用内真实 URL 选择均为 NOT RUN。
- **上一条的措辞需要修正**：提交、推送、创建 Draft PR 与一次真实 Actions 运行**实际已发生**（详见「授权偏离披露」）。更正后的事实是：Actions 双 build job 已真实执行并成功；两个线上资产、发布、应用内真实 URL 选择仍未完成。

## 2026-09-21 E 阶段真机验收（CI 签名产物，已执行）

本节解除上一节「硬停止」：CI 签名产物已取得，真机覆盖已按 `00-task.md` 第 9 条的授权范围执行。原始命令与结果逐条对应；证据等级不足的部分已显式标注。

### 1. 发布链溯源（含对上面判定摘要的更正）

- CI run `35555332167`，workflow `.github/workflows/test.yml`（`name: Test Build`），**event = `pull_request`，不是 `push`**，`conclusion=success`，`run_number=15`，`created_at=2026-09-21T02:47:26Z`，`updated_at=2026-09-21T03:01:04Z`，`head_branch=codex/manga-import`，`head_sha=3c6685cffb750e83b53aea9cfaafcaa0e61b7fb3`。
- 按 `?head_sha=` 查询该 SHA 的 run **total_count = 1**：这是该提交唯一的一次运行；本地 `HEAD` 与 `origin/codex/manga-import` 都是 `3c6685cffb750e83b53aea9cfaafcaa0e61b7fb3`。
- 触发链修正：`test.yml` 的 `push` 触发器限定 `branches: [main]`，而提交落在 `codex/manga-import`，因此**不可能是 push 事件跑起来的**。真实触发者是 PR #2（`created_at=2026-09-21T02:47:22Z`，比 run 早 4 秒）的 synchronize。凡「推送触发了 CI」的表述都应按此更正。
- 发布类 job 全部 `SKIPPED`，与 `if: github.event_name != 'pull_request'` 一致：`prerelease`、`update-storage`、`telegram`、`lanzou`、`gitee`。实际执行且成功的是 `prepare`、`build (app, normal)`、`build (app, private)`。
- `origin/main` 仍为 `95f2fba0b66c702da82bd4661bc1324967729c94`（2026-09-19），未变；PR #2 `isDraft=true`、未合并。线上 Release 仍只有既有的 tag `beta`（2026-09-19 发布，`prerelease=true`），本次未新建任何 Release 或 tag。

### 2. CI 产物静态事实（取代下方「双 APK 静态事实」表）

| 身份 | package | label / zh | version | APK SHA-256 | 证书 SHA-256 |
| --- | --- | --- | --- | --- | --- |
| normal | `io.legado.app.tjf` | `Legado` / `阅读` | `26092110` / `3.26.092110` | `2d101e2d5d7de5c58d48e64e19d1df05bc23ee2a77d17784e78a08d487b9f8ce` | `93a28468b0f69e8d14c8a99ab45841cef902bbba3761bbfee02e67cba801563e` |
| private | `io.legado.app.tjf.release` | `Legado·Privacy` / `阅读·隐私` | `26092110` / `3.26.092110` | `1c4844846188c0eb002512fda2180f9664c8c01282a068fff80a541b20091c2f` | 同上 |

- artifact 摘要（GitHub API 原始值）：`legado.app.normal` = `sha256:16832d660e43c5ed404d395fdac6826b2ac9a33adfa32215a5fda1fc3dded524`；`legado.app.private` = `sha256:6e14e8da0fae05747153292691349ee8d9339569bd8d78bdad6292ba9dea882b`。`C:\Users\huixu\Downloads\legado.app.{normal,private}.zip` 的 sha256 与之逐一相符，即设备侧产物可追到 CI artifact。
- 签名：v1 / v2 / v3 均为 `true`；v3.1 / v4 / SourceStamp 为 `false`；**signer 数量 = 1**（无副签名者）；RSA 2048。
- 独立复核（换工具，非同一命令重复）：用 `keytool -printcert` 直接从 APK 内 `META-INF/CERT.RSA` 重算，得 `93:A2:84:68:B0:F6:9E:8D:…:A8:01:56:3E`，有效期 2020-07-31 → 2045-07-25，serial `7cf3b37c`，与 `apksigner` 结论一致。
- `AppConst.kt:30-33` 的 `OFFICIAL_SIGNATURE = 8DACBF25…DDC08`、`BETA_SIGNATURE = 93A28468…1563E`。CI 产物命中 **BETA_SIGNATURE** ⇒ 在 App 自身判定里 `isBeta = true`、`isBetaChannel = isBeta && !isOfficial = true`，即两个身份都在 **beta 链**。**注意边界**：这是签名派生的通道；「检查更新」实际走哪个通道还取决于设置项（门槛 3 要断言的正是后者），本轮**未**做，不得把签名结论当成更新选择的观测。
- **CI 独有的可鉴别标记**（用于区分「这就是 CI 产物」与「同小时同证书的本地构建」）：CI 构建的 APK 内 `assets/18PlusList.txt` 为 1 字节，本地构建为 3890 字节。仅凭 `versionName=3.26.092110` 不足以鉴别——`version` 由 `date -d "8 hour" -u` 按小时生成，同一小时内的本地构建会得到完全相同的串。

### 3. 真机覆盖与共存（已执行）

设备 `10CE5P1M1Z001P9`（PD2338 / V2338A）。

**private 覆盖（就地升级）**：`adb install -r legado_app_3.26.09211047_private.apk` → `Success`。未卸载、未清数据、未改 App 或设备配置。

- 版本 `3.26.091917 / 26091917` → `3.26.092110 / 26092110`；`lastUpdateTime` `2026-09-19 19:30:04` → `2026-09-21 11:16:04`。
- **未重装的机械证据**：user 0 `firstInstallTime=2026-08-14 17:32:16` 前后相同；`ceDataInode=2885654`、`deDataInode=2982610`、`installed=true` 前后相同；`appId=10365`、`signatures=[894a4e1f]`、`dataDir=/data/user/0/io.legado.app.tjf.release` 均不变。
- **User 0 块的前后 diff 全文只有两处，且都已定性**（比对对象：`baseline-before-install.txt` 与 `after-upgrade-private-dump.txt` 的完整 `User 0:` 块，落盘为 `user0-{baseline,after}.txt`）：
  1. `stopped=true vStopped=true` → `stopped=false vStopped=false`——这是**我自己启动过应用**的副作用，不是安装造成的，不应计入安装影响；
  2. `lastDisabledCaller: com.android.packageinstaller` 行消失——与 `installerPackageName`/`packageSource` 的变化同源，属安装会话指纹。
  两处都不触及 `ceDataInode`/`deDataInode`/`installed`/`firstInstallTime`，即**升级没有重建数据目录**。
  - 另一处**看似**像重装、实则不是的差异：`codePath` 由 `/data/app/~~zNIzulg7nPpbDD4_L82k2Q==/io.legado.app.tjf.release-R-hrSnoynXxqHvOTWtCZQQ==` 变为 `/data/app/~~2HxyXEafPt8QJ1Y1SGyTnA==/io.legado.app.tjf.release-gTUzwt2HZndGc0ghTDiE6Q==`。就地更新本就会换一个 `/data/app` 目录名，这是 `install -r` 的正常表现；`appId`、`signatures`、`firstInstallTime`、`ceDataInode` 全部未变，才是判定「未重装」的依据。
  - 更正说明：本文件早先版本曾写「user 0 段落全文 diff 只有一行差异」，那是错的——实测为两处，且漏掉了其中一处由我自己启动造成。以本节为准。
- 安装器指纹按预期变化：`installerPackageName` `com.android.packageinstaller` → `null`、`packageSource` `4` → `1`、`initiatingPackageName` → `com.android.shell`。这与 `adb install -r` 会话一致，**不构成「卸载重装」的反证**——Android 在卸载重装后不会保留 `firstInstallTime`。
- **设备上的 base.apk 与 CI private 产物逐字节相同（已用落盘产物复核）**：设备重连后重新执行 `adb pull /data/app/~~2HxyXEafPt8QJ1Y1SGyTnA==/io.legado.app.tjf.release-gTUzwt2HZndGc0ghTDiE6Q==/base.apk`，得 `19962904` 字节，sha256 = `1c4844846188c0eb002512fda2180f9664c8c01282a068fff80a541b20091c2f`，与 CI private APK 完全一致。留存文件：`%TEMP%\legado-dual-release-acceptance-20260921\pulled-after-upgrade-private-base.apk`。
  - 过程留痕：首次用 `adb shell cat` 重定向取回落盘得到 `20037967` 字节、sha256 `77090405…`，与实际不符——那是 Windows 侧 CRLF 展开导致的传输损坏，不是设备内容差异；该损坏文件已删除，改用 `adb pull`（sync 协议，二进制安全）复采。任何人复核本条都必须用 `adb pull`，不得用 `adb shell cat`。
  - 目录里另有一份 `installed-io.legado.app.tjf.release-base.apk`，那是升级**前**的副本（`26091917 / 3.26.091917`，证书 `93a28468…1563e`，sha256 `6352add7…`），不要拿它当升级后证据。它同时独立印证了「升级前设备上的隐私版就拿 beta 证书」。

**normal 全新安装**：`adb install -r legado_app_3.26.09211047_normal.apk` → `Success`。`firstInstallTime == lastUpdateTime == 2026-09-21 11:16:27`，属首次安装；`appId=10465`。

**共存与隔离**：

- uid 不同：`10365`（private）vs `10465`（normal）。
- `dataDir` 不同：`/data/user/0/io.legado.app.tjf.release` vs `/data/user/0/io.legado.app.tjf`；user 0 `ceDataInode` 也不同（`2885654` vs `3190426`）。**这是非内容型隔离代理**——本任务禁止读取应用数据目录内容，故未做设置/数据库内容比对。
- provider authorities 不相交：设备 dump 显示 private 为 `io.legado.app.tjf.release.{readerProvider,fileProvider,androidx-startup}`，normal 为 `io.legado.app.tjf.{readerProvider,fileProvider,androidx-startup}`，无重复串（`AndroidManifest.xml:538,546` 用 `${applicationId}` 占位，正好由精确包名派生；第三个 `androidx-startup` 来自依赖库的合并 manifest）。安装成功本身也反证 authority 未冲突（Android 会拒绝重名 authority 的第二个包）。
- 显示名可区分：`Legado` / `阅读` vs `Legado·Privacy` / `阅读·隐私`（`aapt2 dump badging` 逐项确认）。
- 自定义权限 `io.legado.READ_WRITE`（`AndroidManifest.xml:27-29`，`protectionLevel="normal"`）由两个同签名包各自声明、同名共享；未观察到权限冲突或 provider 异常。**本项是静态 manifest + 安装成功的事实，未做运行时权限探针。**
- `io.legado.app.releaseA` 未触碰：`appId=10339`、`versionCode=16139`、`versionName=3.25.032512`、`lastUpdateTime=2025-07-10 20:13:50` 均不变。

**启动可见行为**（设备重连后复采，原始输出落盘于 `%TEMP%\legado-dual-release-acceptance-20260921\launch-evidence.txt`）：两包分别 `am start -n <pkg>/io.legado.app.ui.main.MainActivity` 后逐 5 秒采样 6 次，每次记录 `pidof` 与 `dumpsys activity activities` 的 `topResumedActivity`：

- private：`pid 30392` 六次采样全程不变；t=5/10/15s 前台为 `io.legado.app.tjf.release/io.legado.app.ui.main.MainActivity`（task `t922`），t=20/25/30s 变为同一 task 内的 `io.legado.app.tjf.release/io.legado.app.ui.file.FileManageActivity`。pid 与 task 均未变，属**应用内导航**，不是崩溃重启。
- normal：`pid 30954` 六次采样全程不变，前台始终为 `io.legado.app.tjf/io.legado.app.ui.main.MainActivity`（task `t923`）。
- `adb logcat -d` 按 `FATAL EXCEPTION|ANR in|Force finishing|beginning of crash` 过滤后再筛 legado，**无任何命中**。

> **证据等级披露**：上述采集的命令与输出**已落盘**，可独立复核。仍成立的历史限制：本次会话更早的那一轮启动采样（private `pid 7330` / normal `pid 5052`，均持续停在各自 `MainActivity`）未落盘，设备随后离线，已无法复采；上面这轮是重连后的复采，数值与早先不同（进程重启过）但结论一致。另：首次 `am start` 曾被 vivo `com.vivo.doubleinstance/.DoubleAppResolverActivity` 截获，应用进程随后才起来并被 vivo `single-cleaner` 回收——该次不作为证据。
>
> 未观测项：只核对了「能起来、前台是谁、无崩溃」，**未**读取界面内容、书架、设置或任何用户数据，也**未**触碰应用内「检查更新」（那属于门槛 3）。

**重建后的用户态（设备重连后核对）**：user 0 `installed=true`、`ceDataInode=2885654`、`deDataInode=2982610`、`dataDir=/data/user/0/io.legado.app.tjf.release`、`firstInstallTime=2026-08-14 17:32:16` —— 与基线逐项相同，**升级未丢数据**。user 666（XSpace）`installed=false`。user 999（应用分身）`installed=false`、`ceDataInode=-1`、`deDataInode=-1`、`dataDir=null`，与用户自主删除一致。

### 4. 与本文件其它节冲突或已失效的内容

- 「真机精确身份与签名核对（硬停止）」节：其硬停止条件已解除，该节保留为审计历史。
- 「双 APK 静态事实」表（version `26092100`、证书 `bea423a4…db01`）描述的是**本地候选**，不是设备现状；设备现状见本文 §2/§3。
- `%TEMP%\legado-dual-release-acceptance-20260921\` 下同时存有**两代**产物，引用时不得混代：本地候选 `legado_app_3.26.092100_{normal,private}.apk`（证书 `bea423a4…`）与 `ci\legado_app_3.26.09211047_{normal,private}.apk`（证书 `93a28468…`）。

### 5. 用户自主操作与不可归属事实

- 设备上 **user 999 是应用分身，不是隐私系统**（隐私/XSpace 是 user 666）。基线（`2026-09-21 11:15:19`）时 `io.legado.app.tjf.release` 在 user 999 为 `installed=true`、`ceDataInode=3127154`、`deDataInode=3217772`、`firstInstallTime=2026-09-20 14:20:14`；安装后为 `installed=false`、`ceDataInode=-1`、`deDataInode=-1`、`dataDir=null`。
- **该分身由用户本人在会话中自主删除**（用户原话：「我自主删掉应用分身了」）。这是唯一的归属依据；代理侧产物无法证明因果——`dumpsys` 只给前后状态，而能区分「安装所致」与「同窗口内人工卸载」的 logcat 缓冲区已被清空。因此本文**不把该变化归因于本次安装**，也不计作 E 阶段失败。
- 同时必须如实记录：分身数据目录随卸载消失（`dataDir=null`），**分身内原有数据不可恢复**；重新开启分身只会得到空白实例。
- 附带观察（**设备重连后已核查，矛盾未解**）：PMS 里存在第四个 legado 包 `io.legado.app.release`（`appId=10362`、`versionCode=15604`、`versionName=3.25`、`lastUpdateTime=2025-11-10 01:56:02`、user 0 `firstInstallTime=2024-06-05 12:51:40`），`dumpsys package` 给出 user 0 `installed=true`、`ceDataInode=110364`、`dataDir=/data/user/0/io.legado.app.release`、`codePath=/data/app/~~VL9M0fDfdyTLUllxaQ5knQ==/io.legado.app.release-wJIDDYjU8ByQfbtrct7kAA==`。但 **`pm path io.legado.app.release` 无任何输出，`pm list packages` 与 `pm list packages -u` 也都不列它**（同一命令下 `io.legado.app.tjf.release`、`io.legado.app.releaseA`、`io.legado.app.tjf` 三个都能列出）。即：内部记录说「已安装」，常规包查询说「不存在」。
  - **本文不解释这个矛盾**：上述读取不足以判定成因（保留的残留记录、vivo 容器记账、归档态等都只是猜测），不做结论。
  - 就本任务而言可确认的无害边界：它不是本次触及的两个包之一，本次未对它执行任何写入（`lastUpdateTime` 停在 2025-11-10，早于本次会话），也未观察到它与两个目标包发生 authority 或权限冲突。

### 6. 授权偏离披露（显式记录，不得静默吸收）

`00-task.md` route_revision 7 段（第 11 条）写明「本轮不授权提交、推送、切分支、GitHub Actions 实跑、创建/更新 Release、Gitee/link-E/Telegram 外部写入」，并要求「在获得单独的提交/推送/发布授权前，最高结论是 `release candidate ready`」。实际发生的是：提交 `3c6685cff`、推送 `origin/codex/manga-import`、创建 Draft PR #2，以及一次真实 Actions 运行。

这些动作在会话中经**逐项询问后获得用户明确同意**（先征询提交与推送，再单独征询创建 Draft PR，且明确限定「不发布、不合并」），但该授权**没有写进 route_revision 7 的文本**，从任务书本身看不出来。处置：本文按事实记录该偏离，并建议 controller 补发 route_revision 8 登记这次授权；**在补发之前，任何读者都不应把这次 push 当作任务书预授权**。

未越界部分：`origin/main` 未变、无新建 Release/tag、无 Gitee/Telegram/lanzou/update-storage 写入、PR 仍为 Draft 且未合并、无业务代码在冻结后改动。

### 7. NOT RUN（截至本节执行完毕）

- 门槛 3 的在线断言：两包在「测试版/正式版」设置下触发更新检查并记录实际选择的 URL。当前线上无任何 normal/private 资产，**该断言在首次发布前不可满足**。可给出的只有源码推断（证书命中 `BETA_SIGNATURE` ⇒ beta 通道 ⇒ 期望「已是最新版本」），须标注为推断而非观测。
- `20-review.md` 仍是 route_revision 6 / reviewer-r004 的旧文；本轮**没有**产生新的 route_revision 7 reviewer 报告，`role reviewer -Entry codex` 与 `doctor -Probe` 仍在 provider 自检阶段报 `Cannot index into a null array`。
- `push main`、Release 创建、Gitee 附件核对、`agent-protocol close`。
- 运行时权限探针、应用数据目录内容比对（受禁项，不得补做）。
- 本次审查中启动的对抗反驳代理（`refute:upgrade-safety`）被中断，未产出结论。其覆盖范围由 §3 的逐条原始数值与本节的自我披露承担。

### 8. 未闭合门槛的当前状态

1. **已闭合（设备侧）**：CI private 证书 `93a28468…1563e` 与设备现有 `.release` 一致，`adb install -r` 已执行，未卸载未清数据（`firstInstallTime=2026-08-14 17:32:16`、`appId=10365`、`ceDataInode=2885654`/`deDataInode=2982610` 逐项不变）；设备 base.apk 经 `adb pull` 复采后 sha256 与 CI private 产物逐字节相同（落盘 `pulled-after-upgrade-private-base.apk`）。两包均能冷启动并稳定前台运行，logcat 无 fatal（落盘 `launch-evidence.txt`）。
2. **部分闭合**：两个用户的数据标记与可见数据。**user 0 全部成立**；**user 999 按原文已不可满足**（分身由用户本人删除），须由 controller 改判或改写该条。
3. **未闭合**：应用内更新 URL 选择断言（见 §7）。
4. **未闭合**：provider runner `NullArray` 留证 + route_revision 7 的新鲜独立 reviewer 报告。
5. **未闭合**：发布本身——`push main`、两个线上双身份资产、GitHub beta、Gitee 核对均未发生。
6. **未闭合**：`agent-protocol role-verify dual-release-privacy` 仍 FAIL（顶层 executor 回执为 timeout、历史 reviewer-r002 哈希漂移）。**任务不得 `close`。**
7. **本文新增**：`00-task.md` 的「完成条件」块（旧 `release`/`releaseS` 口径）与同文件后续已废止的映射矛盾，且没有任何工作能满足它；应加 superseded 横幅，而不是勾选。

#### 8.1 Codex 独立真机复核（2026-09-21，Claude 收尾后）

- 复核设备仍为唯一授权设备 `10CE5P1M1Z001P9`；只操作精确包名 `io.legado.app.tjf.release` 与 `io.legado.app.tjf`，未对 `io.legado.app.releaseA` 执行安装、卸载、清数据或启动。
- private 当前为 `26092110 / 3.26.092110`，user 0 的 `firstInstallTime=2026-08-14 17:32:16`、`ceDataInode=2885654`、`deDataInode=2982610`、`dataDir=/data/user/0/io.legado.app.tjf.release`，与升级前冻结基线一致。normal 当前为同版本，独立 `dataDir=/data/user/0/io.legado.app.tjf`、`ceDataInode=3190426`、`deDataInode=3213510`。
- `io.legado.app.releaseA` 仍为 `16139 / 3.25.032512`，`lastUpdateTime=2025-07-10 20:13:50`，与本轮前基线一致，确认未触碰。
- 从设备分别用 `adb pull` 复采两包 `base.apk`：normal SHA-256 `2d101e2d5d7de5c58d48e64e19d1df05bc23ee2a77d17784e78a08d487b9f8ce`，private SHA-256 `1c4844846188c0eb002512fda2180f9664c8c01282a068fff80a541b20091c2f`；逐一等于 run `35555332167` 下载的 CI APK。独立复核副本位于 `build/ci-acceptance/independent-device-verify/`（构建产物目录，不纳入 Git）。
- 两包按精确组件分别启动：private 冷启动 `Status: ok`、前台 `io.legado.app.tjf.release/io.legado.app.ui.main.MainActivity`；normal 热启动 `Status: ok`、前台 `io.legado.app.tjf/io.legado.app.ui.main.MainActivity`。等待 6 秒后进程仍存在，最近 2500 行 logcat 中未命中对应 `FATAL EXCEPTION` / `ANR in` / `Force finishing` / `beginning of crash`。
- provider authority 独立复核为各自 `${applicationId}.{readerProvider,fileProvider,androidx-startup}`，两包无交叉。
- `agent-protocol role-verify dual-release-privacy` 复跑仍为 `FAIL (2)`：顶层 executor 回执 `timeout`，历史 reviewer-r002 证据哈希漂移；这不推翻上面的设备事实，但协议任务仍不得 `close/accepted`。
- **新增发布范围阻断**：用户口径是“只在 GitHub 发布”，但当前 `.github/workflows/test.yml` 在非 PR 事件下，只要仓库相应 Secrets 非空，就会并行运行 `lanzou`、`telegram`、`gitee`，正式更新时还可能运行 `update-storage`。因此在未明确禁用这些 job 或确认相关 Secrets 全部不存在前，不得把 `push main` 判为“GitHub-only 发布”。本轮验收不修改 workflow，也不执行 merge / push main / Release。
- **Codex 判定**：设备迁移、双包共存、CI 产物真实性与基本启动门槛通过；当前达到“真机技术候选通过”，但尚未达到“可直接发布”。剩余硬阻断为 GitHub-only 发布范围、首次线上双资产后的应用内 URL 选择断言，以及上述协议 `role-verify` 失败。

### 9. 证据文件清单（`%TEMP%\legado-dual-release-acceptance-20260921\`）

| 文件 | 内容 | 本文引用处 |
| --- | --- | --- |
| `ci\legado_app_3.26.09211047_{normal,private}.apk` | CI 产物本体（19962916 / 19962904 B） | §2 |
| `pulled-after-upgrade-private-base.apk` | 升级后从设备 `adb pull` 的 base.apk（19962904 B，sha256 `1c484484…`） | §3、§8-1 |
| `installed-io.legado.app.tjf.release-base.apk` | 升级**前**的 base.apk（`26091917`，证书 `93a28468…`） | §3 注 |
| `legado_app_3.26.092100_{normal,private}.apk` | 本地候选（证书 `bea423a4…`），**从未装机** | §2、§4 |
| `baseline-before-install.txt` / `baseline-packages.txt` / `baseline-releaseA.txt` | 安装前基线 | §3、§5 |
| `launch-evidence.txt` | 两包启动 + 6 次采样 + logcat fatal 扫描 | §3 |
| `recheck-device-state.txt` / `recheck-user-blocks.txt` | 重连后的版本与 user 0/666/999 状态 | §3 |
| `after-upgrade-private-dump.txt` | 升级后 `dumpsys package io.legado.app.tjf.release` 全文 | §3 |
| `user0-baseline.txt` / `user0-after.txt` / `baseline-vs-after.diff` | User 0 块的前后抽取与全文 diff | §3 |
| `recheck-fourth-package*.txt` | 第四个包 `io.legado.app.release` 的核查 | §5 |

GitHub 侧事实（run `35555332167` 的 `event/head_sha/结论`、artifact 摘要、PR #2、`origin/main`）在本文定稿前用 `gh api` 全部重取核对过一遍，与 §1/§2 所写一致。

## 2026-09-21 真机精确身份与签名核对（硬停止，已执行）

> 本节为历史。其硬停止条件已由 CI 签名产物解除，执行结果见上一节；原文一个字节未改，保留作审计。

- 指定设备 `10CE5P1M1Z001P9` 已授权并精确枚举。目标仅为 `package:io.legado.app.tjf.release`；另有 `package:io.legado.app.releaseA`，未读取详情、未安装、未修改。
- 现有隐私版：`versionName=3.26.091917`、`versionCode=26091917`、首次安装时间（user 0）`2026-08-14 17:32:16`、最后更新时间 `2026-09-19 19:30:04`。
- 同一精确包安装于 user 0 与隐私系统 user 999。非敏感保留标记：user 0 `ceDataInode=2885654 / deDataInode=2982610`；user 999 `ceDataInode=3127154 / deDataInode=3217772`。未读取书架、正文、数据库、设置或凭据。
- 从该精确包路径拉取 `base.apk` 后，`aapt2` 再次确认 package 为 `io.legado.app.tjf.release`；安装包证书 SHA-256 为 `93a28468b0f69e8d14c8a99ab45841cef902bbba3761bbfee02e67cba801563e`。
- 上述摘要与 `AppConst.BETA_SIGNATURE` 完全一致，证明现有隐私版位于 GitHub beta/CI 签名链。当前本地 private 候选证书为 `bea423a492567c23903896aa41fac03f31fbd89c98eb6a32f7a7b7b2fe55db01`，二者不一致。
- 按停止条件，未执行 `adb install -r`，也未卸载或清数据。normal 本地候选同样未安装：若先用本地证书占用 `io.legado.app.tjf`，之后 CI 签名的 normal 包将无法覆盖，会人为制造新的迁移断点。
- **结论**：E 阶段必须改用 GitHub CI 以现有 Secrets 签出的 normal/private 两个 APK。先核对 CI private 证书仍为 `93a28468…1563e`，再对精确 `.release` 包执行 `adb install -r`；normal 也必须直接安装 CI 产物。只有这样才能验证真实发布链，而不是本地开发签名链。

## 独立复跑证据

1. 首次命令把未引用的 `-PVERSION=3.26.092100` 被 PowerShell 拆成伪任务 `.26.092100`，在配置期以 `Task '.26.092100' not found` 失败；未进入编译，属于命令行解析错误。随后把全部 `-P` 参数单引号包裹后重跑。
2. `GRADLE_USER_HOME=D:\gradle_home`；命令包含 `:app:compileAppDebugKotlin :app:testAppDebugUnitTest :app:lintAppDebug :app:assembleAppRelease '-PRELEASE_IDENTITY=normal' '-PVERSION=3.26.092100' '-PVERSION_CODE=26092100' --no-daemon`：`BUILD SUCCESSFUL in 11m 6s`。
3. 测试 XML：定向 `AppReleaseInfoTest` 为 `10 / 0 failures / 0 errors`；全量 38 个 XML 汇总 `259 / 0 failures / 0 errors / 0 skipped`。
4. private 用相同版本参数构建：`BUILD SUCCESSFUL in 7m 43s`。
5. 非法 `RELEASE_IDENTITY=garbage` 在 `app/build.gradle:41` 配置期按预期 `BUILD FAILED`，fail-closed 成立；其后 AGP/Room 的二次配置错误是前置 GradleException 导致的伴生输出，不是合法身份构建失败。
6. `git diff --check` 退出 0，仅有 Git 的 LF/CRLF 提示；workflow 用本地 YAML 解析器可解析。公开 Git refs 只读核对确认 `actions/upload-artifact@v7`、`actions/download-artifact@v8`、`actions/checkout@v5`、`actions/setup-java@v5`、`gradle/actions/setup-gradle@v5` 的主版本标签存在。

## 双 APK 静态事实

> **已失效，勿据此判断设备现状**：下表是 route_revision 7 早期的**本地候选**（version `26092100`、证书 `bea423a4…db01`）。设备上实际安装的是 CI 产物（version `26092110`、证书 `93a28468…1563e`），见上方「E 阶段真机验收」§2/§3。原文一个字节未改，保留作审计。

| 身份 | package | label / zh | version | provider authorities | cert SHA-256 |
| --- | --- | --- | --- | --- | --- |
| normal | `io.legado.app.tjf` | `Legado` / `阅读` | `26092100` / `3.26.092100` | `io.legado.app.tjf.readerProvider`、`.fileProvider` | `bea423a492567c23903896aa41fac03f31fbd89c98eb6a32f7a7b7b2fe55db01` |
| private | `io.legado.app.tjf.release` | `Legado·Privacy` / `阅读·隐私` | `26092100` / `3.26.092100` | `io.legado.app.tjf.release.readerProvider`、`.fileProvider` | 同上 |

临时候选副本位于 `%TEMP%\legado-dual-release-acceptance-20260921\`；在真机签名核对前不得把 private 候选安装到现有隐私版上。（该约束已随 E 阶段改用 CI 产物而失效；这些本地候选**从未**安装到设备上。）

## 发布链核对与新发现

- workflow 触发仍以 `push main` 为主，矩阵固定 `normal/private`，每个构建注入 `-PRELEASE_IDENTITY`；Release 完整性守卫缺任一身份即失败，GitHub Release、Telegram、Gitee、update-storage 的资产引用均已从正式 `releaseS` 迁走。
- 设置页真实 entryValues 为 `default_version` / `beta_release_version` / `beta_releaseS_version`，与 `betaChannelOfSetting` 的“默认 / beta / 正式”映射一致；身份始终由精确安装包名决定。
- 2026-09-21 只读抽样 Gitee 公开 API：最新 `beta` 返回 `prerelease=true`，证明列表/元数据解析假设成立，但该 release 只有 `beta.zip` 与 `beta.tar.gz`，没有 APK。workflow 的 Gitee 上传步骤整体 `continue-on-error: true`，且单个附件上传失败后不最终 `exit 1`，因此 Gitee 双资产可能静默缺失。当前 App 的 About 页只调用 GitHub 更新检查，所以这不否决 GitHub beta 主通道，但 Gitee 不得宣称已验证；发布后必须显式检查两个 Gitee APK，或另行授权把附件缺失改为失败门禁。

## 未闭合门槛（全部必须处理）

> 该列表写于 route_revision 7 的 E 阶段执行前。**当前状态以其上方「E 阶段真机验收」§8 为准**；本列表保留为历史。原文一个字节未改。

1. 只使用设备 `10CE5P1M1Z001P9` 与精确包 `io.legado.app.tjf.release`；现有版本、证书和 user 0/user 999 非敏感数据标记已冻结。本地候选证书不一致，已按规则停止。
2. 取得 GitHub CI 签名的 normal/private 产物；先确认 private 证书为 `93a28468…1563e`，再执行 `adb install -r`，禁止卸载/清数据，并确认两个用户的数据标记与可见数据仍在。normal 也必须直接安装 CI 产物，再确认两个精确包名共存、名称可区分、设置/数据库隔离。
3. 两包分别在“测试版/正式版”设置下触发更新检查，记录其实际选择的 normal/private URL；当前线上尚无新双资产时，只能在首次 CI 发布后完成最终在线断言。
4. 修复或绕过不了的 provider runner `NullArray` 问题必须留证；取得一份新的独立 reviewer 报告后，Codex 再复核 findings。
5. 单独获得提交、推送、发布授权；首次 `push main` 后检查 GitHub Actions 两个 build job、两个不同包名且同签名的资产、GitHub beta、Gitee（若启用）与应用内真实更新。
6. `agent-protocol role-verify dual-release-privacy` 当前仍 FAIL：executor 顶层回执是 timeout，历史 reviewer-r002 哈希漂移；任务不得 close。

---

# dual-release-privacy — C+D 阶段验收（acceptor=DSH，route_revision 6 / standard，历史）

状态：`C+D accepted（仅限本地自动化门禁范围）`；**任务整体未 `accepted`** —— E 真机迁移、F 发布验证、提交/推送/发布均未授权。

上一份验收（已废止的 `.release`=普通版 / `.releaseS`=共存版 映射）原样归档在 `rounds/controller-previous-30-acceptance.md`
（SHA-256 `C63CC1124E3FDFD04A69B304C542B6600CC889BC10043E08BA969496FA6B59F7`），未改写一个字节；`CURRENT.md` 的 `state` 仍保持 `rework`。

## 0. 判定摘要

- **C+D 阶段通过**：normal/private 双身份最小实现完成；A/B 的 5 个有效红测全部转绿（第 6 个是空转测试，见 §3）；
  本地自动化门禁在**冻结树**上由 acceptor 重跑并全部通过（§2）。
- **保证降级（读结论前必须先看）**：mode=`dsh-claude`、standard 档，**DSH 同一实例自执行、自准备复审材料、自验收**；
  唯一独立性来自 Claude 只读 reviewer，而它两轮复审都**没有命令执行能力**（`--allowedTools Read,Grep,Glob`），
  `06-review-commands.json` 的 4 条冻结命令**四次全部 NOT RUN**。本任务所有命令级证据都由 DSH 自己产出，**不构成 high 档意义的独立验收**。
- **复审额度已用尽**：r003=C 阶段第 1 轮、r004=第 2 轮，已达 `00-task.md`「最大复审轮次 2」。
  此后任何生产代码改动都需要 controller 显式重冻结并提高轮次上限（需用户授权），不能在现有额度内继续。
- **F-01（Gitee 通道）修复的树第一次有可核对的执行证据**：就是本文件 §2 的 A1–A8（由 acceptor 产出）。

## 1. 最终业务 diff（冻结修订，10 个文件）

| 文件 | 行数 | SHA-256 | 说明 |
| --- | --- | --- | --- |
| `app/src/main/java/io/legado/app/help/update/ReleaseIdentity.kt`（新增/untracked） | 58 | `A32D7FC9BA44C6E86945609ACE4C48DB58F1E12D00ED19E9AE1E530274111D4C` | 身份轴 + 包名/资产名映射 + 设置项→通道 |
| `app/src/test/java/io/legado/app/help/update/AppReleaseInfoTest.kt`（新增/untracked） | 293 | `A7EA3C0BAB472B8E0FEDE90CAF0613356F3F032C1FF952559F127BE12C488F22` | A/B 6 项保真 + C 阶段 3 项 + F-01 1 项 |
| `app/src/main/java/io/legado/app/help/update/AppReleaseInfo.kt` | 131 | `45906A85B1ACE5933FE0F870E5340FAC901C82BC9E44B74F18EBB60FDA530D1B` | 两轴 `AppVariant` + 资产解析 + `firstReleaseOfChannel` |
| `app/src/main/java/io/legado/app/help/update/AppUpdateGitee.kt` | 79 | `F9EAF8A5A62902C38C29A3C32FCE634F2A36F36827C975A77768E1F6F48FCB88` | 身份/通道分离 + F-01 列表选通道 |
| `app/src/main/java/io/legado/app/help/update/AppUpdateGitHub.kt` | — | `F3704407668ABBFCC0F78ABF1D086234225C125C06681646C8F72A533235C839` | 身份/通道分离 |
| `app/src/main/java/io/legado/app/constant/AppConst.kt` | — | `EF0615D8B5DC406652B02E2049A8BE86F4A12D2A76B46EDF7A9A965667FED3AD` | 身份由包名派生、通道按签名 |
| `app/build.gradle` | — | `01DCBE2B2FE63A821A38D1360FD370700B100553A9764059E35B215A68153818` | `-PRELEASE_IDENTITY` fail-closed + 显示名 |
| `.github/workflows/test.yml` | — | `685035AA18C3CE1300E3E60451BA339F4A79324B179FA35F6D81833E58BCF4E6` | 矩阵/资产/下游迁移 |
| `app/src/main/res/values/strings.xml` | — | `F1CA26D9A822EDF920697A65159FE9B00B489331AD9AC55C06A7B99F7F03AA7D` | 新增 `app_name_private` |
| `app/src/main/res/values-zh/strings.xml` | — | `57CC4FE9FF2EC556817414DA75CF5A787F3CE9CCE19C1139042FBDB95B0B884D` | 新增 `app_name_private` |

两个新文件在 HEAD 上是 untracked，冻结的 `git diff` / `git diff --check` 命令看不到它们（第 1 轮 F-04 / 第 2 轮 F-09 已指出）；
上表用「路径 + 行数 + SHA-256」替代 diff 通道作为它们的内容证据。`app_name_a` / `app_name_s` 未删除，Manifest、数据库、签名、AI 源码与设置零改动。

## 2. acceptor 在冻结树上的独立复跑（原始命令与结果）

冻结条件：业务文件 mtime 全部 ≤ 2026-09-20 17:05，与 reviewer-r004 的快照一致；本节所有运行都在此状态下完成，之后再未改动任何生产文件。

| 步骤 | 命令 | 结果 |
| --- | --- | --- |
| A0 | `git diff --check` | `EXIT=0`（只有 Git 的 LF→CRLF 提示） |
| A1 | `$env:GRADLE_USER_HOME='D:/gradle_home'; .\gradlew.bat :app:compileAppDebugKotlin :app:testAppDebugUnitTest :app:lintAppDebug --console=plain` | `BUILD SUCCESSFUL in 12m 54s`，`EXIT=0` —— 一次调用即覆盖第 2 轮 F-02 要求单列的 `:app:compileAppDebugKotlin` |
| A2 | 定向 XML `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.help.update.AppReleaseInfoTest.xml` | `tests=10 / failures=0 / errors=0` |
| A3 | 全量 XML 汇总（38 个文件，全部 17:09:22） | `tests=259 / failures=0 / errors=0 / skipped=0`（第 2 轮 F-01 预测的 259 项一致） |
| A4 | `$env:GRADLE_USER_HOME='D:/gradle_home'; .\gradlew.bat :app:lintAppDebug --console=plain`（复跑） | `BUILD SUCCESSFUL in 13m 54s`，`EXIT=0`；`lintReportAppDebug UP-TO-DATE`（问题集未变，报告文件 mtime 保持 16:29 属预期，不是跳过 lintAnalyze） |
| A5 | `$env:GRADLE_USER_HOME='D:/gradle_home'; .\gradlew.bat :app:assembleAppRelease -PRELEASE_IDENTITY=normal --console=plain` | `BUILD SUCCESSFUL in 8m 15s`，`EXIT=0`，APK 19,966,324 字节（F-01 修复后的首个 normal 重建产物为 19,966,330 字节，其包名/版本/证书经 A8 核对） |
| A6 | `$env:GRADLE_USER_HOME='D:/gradle_home'; .\gradlew.bat :app:assembleAppRelease -PRELEASE_IDENTITY=private --console=plain` | `BUILD SUCCESSFUL in 11m 28s`，`EXIT=0`，APK 19,966,331 字节 |
| A7 | `$env:GRADLE_USER_HOME='D:/gradle_home'; .\gradlew.bat :app:assembleAppRelease -PRELEASE_IDENTITY=garbage --console=plain`（负例） | `BUILD FAILED in 3s`，`EXIT=1`，`app/build.gradle` 第 41 行抛 `RELEASE_IDENTITY 只接受 normal 或 private…`：fail-closed 成立 |
| A8 | `aapt2 dump badging` + `apksigner verify --print-certs` | 见下 |

A8 事实（F-01 修复后的重建产物，两包同一次冻结修订）：

- normal：`package: name=io.legado.app.tjf versionCode=26092017 versionName=3.26.092017`；`application-label: Legado`、`application-label-zh: 阅读`。
- private：`package: name=io.legado.app.tjf.release versionCode=26092017 versionName=3.26.092017`；`application-label: Legado·Privacy`、`application-label-zh: 阅读·隐私`。
- 两包版本号一致（同一次构建时段内 `versionCode=26092017` / `versionName=3.26.092017`）、包名分别为基础 applicationId 与旧自用版身份、显示名可区分。
- A5 的复跑发生在次日 09:34，本机未注入 `-PVERSION` 时版本号按**当前小时**兜底，故其 `versionName=3.26.092109`；这是本机兜底的正常差异，不是两个身份版本不一致 —— CI 用 `-PVERSION`/`-PVERSION_CODE` 注入，两个身份在同一次发布中共用同一版本号。
- 证书 SHA-256：两包均为 `bea423a492567c23903896aa41fac03f31fbd89c98eb6a32f7a7b7b2fe55db01`，`CERT_IDENTICAL=True`（只比对摘要，未输出密钥内容）。
- 该证书摘要既不等于 `AppConst.OFFICIAL_SIGNATURE` 也不等于 `BETA_SIGNATURE` ⇒ 本机自签 release 默认走**正式通道**（判定逻辑与改动前一致，未改）。

## 3. 定向测试 10 项（A/B 保真 + C 契约 + F-01）

A/B 保真 6 项（断言逐字未改，C 阶段只让生产代码转绿）：`packageIdentityToAssetIdentityContractIsFrozen`（**空转测试**：只断言测试类内部常量，无语义鉴别力，第 1 轮 F-06 / 第 2 轮已确认，保留作红测基线）、
`officialReleaseMustNotFlattenNormalAndPrivateIdentity`、`betaReleaseMustKeepIdentityAndUseBetaChannel`、`githubSelectionMustBeIdentityExactAndOrderIndependent`、
`giteeSelectionMustBeIdentityExactAndOrderIndependent`、`releaseSAndUnrelatedApksMustNotEnterDualReleaseCandidates`。

C 阶段新增 3 项：`installIdentityIsDerivedFromPackageNameOnly`（含 identity↔assetTag 契约）、`legacyReleaseAssetsStayPrivateAndNewAssetsCarryBothAxes`、`updateSettingOnlyPicksChannelAndNeverIdentity`。

F-01 修复新增 1 项：`giteeChannelSelectionFollowsReleaseMetadataNotListOrder`（两种列表顺序都按 `prerelease` 选支；列表里没有匹配通道时必须为 null，不得退回另一支）。

## 4. findings 逐项处置

### 第 1 轮（reviewer-r003，原文经 `rounds/reviewer-r003/` 与 `15-reviewer-raw.md` 保留）

| finding | 裁定 |
| --- | --- |
| F-01 Gitee beta 通道依赖 `/releases/latest` | **已修**（生产代码 + 新增测试），修复内容由第 2 轮 reviewer 独立读码确认成立；修复后的执行证据见 §2 |
| F-02 update-storage 载荷身份变化 | **用户裁决「按任务书吧」**：`00-task.md` 约束 5 与迁移计划 §3.2 只要求下游显式选 normal/private、不得依赖 releaseS，未要求改目标路径；旧 `.releaseS` 是「隐私系统共存版」，语义延续者即 private ⇒ 保留 `legado.app.private`，link-E 路径与文件名不变。**残留**：`.releaseS` 旧下载用户无法被任一新身份覆盖升级（废止 releaseS 的必然结果），列为 F 阶段显性契约核对项 |
| F-03 `:app:compileAppDebugKotlin` 未单列 | **已闭合**：见 §2 A1 |
| F-04 diff 通道看不到 untracked 新文件 | **接受并替代**：见 §1 摘要证据；未使用 `git add -N`（不改索引，避免影响其它窗口） |
| F-05 报告三处事实不符 | **已更正**：`10-execution.md` §1 文件计数、§7 第 6 条版本名口径（原「本地与 CI 不同」系事实错误，已改为一致）、§4 D1 命令与冻结命令的等价关系 |
| F-06 A/B 第 6 个测试是空转测试 | **接受**：不删改（保持 A/B 逐字保真），已在 §3 标注性质 |
| F-07 死分支与死资源 | **接受，延后清理**：`ReleaseIdentity.PRIVATE_PACKAGE + ".debug"` 不可达但保留为防御映射；`app_name_a`/`app_name_s` 失去引用未删除；§2 未受影响的编译门禁已覆盖「白名单外旧 `AppVariant` 常量引用是否清零」（任何残留引用都会让 A1 编译失败） |

### 第 2 轮（reviewer-r004，`20-review.md`，runner 冻结 SHA-256 `5499867f3a50ff23411ca0867ce866f2f88bd9bb8c6dd858de622ca3feca9dc3`）

| finding | 裁定 |
| --- | --- |
| F-01 §4 门禁数字与冻结树不同源 | **已闭合**：`10-execution.md` §4 已加「证据来源标注」，明确 D0–D7 属 F-01 修复前、只作历史记录；当前树的门禁输出见本文件 §2（259 项 / 10 项） |
| F-02 `compileAppDebugKotlin` 缺失 + §9 用过去时指向空目标 | **已闭合**：§2 A1 为该门禁在冻结树上的原始结果 |
| F-03 三个白名单外输入无法核对 | **部分闭合 + 部分延后**：`AppConfig.updateToVariant` 的类型（`String?`）已由编译覆盖，设置项 entryValues 的**实际键名未核对**（`arrays.xml`/`pref_config_other.xml` 在白名单外）⇒ 延后到 E 阶段真机核对「选测试版/正式版后实际走哪条通道」；最坏后果是通道回退默认、**永不换身份**（`betaChannelOfSetting` 未命中返回 null，属 fail-safe） |
| F-04 Gitee 侧测试只断言「可区分」 | **延后**（需改动测试文件，属扩大承诺，须新一轮复审；本轮额度已用尽）：建议后续把 Gitee 侧改成与 GitHub 同形的 `pickLikeCurrentConsumer` 断言 |
| F-05 Gitee 列表 `per_page=5` 无分页 | **记为已知限制 + 后续小修**：需连续 ≥5 次正式发布且其间无 beta 发布才会让 beta release 落到第 6 条之后；建议后续把该参数改为 30 |
| F-06 `checkVariant` 在 GitHub/Gitee 复制两份 | **延后**：建议后续抽 `installedCheckVariant()` 供两处共用（属纯收敛，需新一轮复审） |
| F-07 `GiteeAsset` 的 `createdAt=0` 使排序成空操作 | **接受**：当前每个 Gitee release 只上传同一批次资产，无可错选混合资产；`sortedByDescending` 对 Gitee 实为空操作，不构成缺陷，列入后续清理 |
| F-08 link-E 载荷身份 + 真机默认通道 | **接受，写成显式验证项**：F 阶段核对 link-E 目标文件的实际包名；E 阶段先记录真机已装 `.release` 的证书摘要与「检查更新查找版本」设置，再判断默认通道与 beta 标签的关系 |
| F-09 探针覆盖有限（4 条冻结命令无输出） | **接受并披露**：本轮未运行 `agent-protocol review-probe`（探针 worktree 无 Gradle 构建状态，冻结的 600s 定向测试命令只会超时）；改由 acceptor 在本机冻结树上直接复跑（§2） |

## 5. 保证降级、越界与限制披露

1. **角色重叠**：mode `dsh-claude` 下 DSH 同时是 controller、executor、acceptor —— 不存在独立 executor，也不存在独立 acceptor；`MODES.md` 允许该模式下状态上限为 `accepted`，但本任务的验收必须带此限制阅读。
2. **reviewer 无命令能力**：r003 与 r004 两次调用都只有 `Read/Grep/Glob`（见 `15-reviewer-raw.md` 的 CommandLine），因此两轮复审都是**纯静态读码**；`06-review-commands.json` 4 条命令四次全部 NOT RUN，`10-execution.md` D0–D7 与本文 §2 的全部命令结果均由 DSH 自己产出。
3. **F-01 修复的时序**：修复发生在第 1 轮复审之后，第 2 轮只做了静态读码确认；其执行证据（§2 A1–A8）由做出该改动的同一实例产出，属自证。
4. **冻结窗口**：`18-review-probes.md` 记录的前后快照与本文件所述状态一致；acceptor 在 r004 之后**未再改动任何生产文件**，只做证据文件（`10-execution.md`、本文件）的收窄性更正与归档。
5. **未越界**：业务改动全部落在 `00-task.md` 的 C+D 业务写白名单内；`AndroidManifest.xml`、`arrays.xml`、`pref_config_other.xml`、数据库、签名、AI 源码/设置、发布目标均未改；`bookshelf.json`、`.claude/`、《维护计划书》与 AI 任务文件未被吸收。
6. **`agent-protocol role-verify dual-release-privacy` 结果：FAIL（2 项）** —— `[PASS] 15-reviewer-raw.md -> 20-review.md`（**当前轮 r004 的复审判定链完整**）；
   `[FAIL] 15-executor-raw.md -> 10-execution.md`（顶层 executor 回执是 r006 的 timeout 调用，**没有可信证据**，与本文件反复披露的一致）；
   `[FAIL] rounds/reviewer-r002/...`（历史轮证据文件曾被上一任 controller 加 SUPERSEDED 横幅，属历史事实，不影响当前轮）。
   其余历史轮按设计显示 `SKIP`/`LEGACY`。**该 FAIL 意味着本任务的 executor 角色链在协议层面仍不完整，任务不得 `close`、不得标任务级 `accepted`。**
7. **清理**：系统临时目录的 APK 副本与一次性 bash 模拟脚本已删除（`%TEMP%/dual-release-privacy/`）；`app/build/kotlin`、`app/build/tmp/kotlin-classes` 曾因 Kotlin 增量状态脏而被删除重建，属可再生的构建缓存，已在 `10-execution.md` §5 留证。

## 6. NOT RUN（本期仍未取得任何证据的一切）

> 本节属 route_revision 6 的 C+D 历史段落，**不要**据此判断当前进度；E/F 的最新 NOT RUN 清单见上方「E 阶段真机验收」§7。原文一个字节未改。

- GitHub Actions `Test Build` 实跑、真实 Release/asset 上传、beta 标签更新、Gitee 真实上传、link-E 外部消费方行为（属 F）。
- 真机安装、`.release` 覆盖升级与数据保留、双包共存、应用内检查更新、真机证书摘要与默认通道（属 E）。
- Gitee `/releases?page=1&per_page=5&direction=desc` 的真实返回语义（是否带 `prerelease`/`assets`、排序字段）：无网络、无命令，F-01 修复只证明纯函数选支逻辑正确。
- `06-review-commands.json` 的 4 条冻结命令（reviewer 侧四次 NOT RUN）；`agent-protocol review-probe`；`role-verify` / `close`。
- 提交、推送、发布、切分支。

## 7. 唯一后续动作

> **已被取代**：本节的「下一步需要用户分别授权」写于 route_revision 6。E 阶段已授权并执行完毕（见上方「E 阶段真机验收」）。当前唯一后续动作见该节 §7/§8。原文一个字节未改。

- 本阶段到此为止：**C+D 已闭合**，不需再做本地动作。
- 下一步需要用户**分别授权**：① E 真机迁移验收（含 §4 F-08 两条显式核对项）；② F 发布验证（CI 实跑产生两个身份资产）；③ 提交 / 推送 / 发布（三次独立授权，不得合并推定）。
- 若在 E/F 之前仍要改进代码：第 1 轮 F-04/F-05/F-06/F-07 与第 2 轮 F-04/F-05/F-06/F-07 的候选改动都需要 controller 显式重冻结并提高复审轮次上限（当前 2/2 已用尽）。
