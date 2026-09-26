# book-source-p1-device — P1 真机合成链路抽查

状态：`ready`

## 目标与非目标

本任务承接 `book-source-p1-rework-r3` 的 `30-acceptance.md` §七「唯一下一步」：**开一轮真机合成链路抽查**，
把该任务判为 `provisional` 时留下的真机/端到端 NOT RUN 项跑成设备证据。本任务**只做验证**：

1. 用**当前工作树**（HEAD `b8ba92e19` + P1 第三轮返工的未提交改动）编译隔离 debug 包并安装到真实设备；
2. 按 `book-source-p1-rework-r3/10-execution.md` §8 的 7 步与 `book-source-p1-diagnostics/10-execution.md` §8 的入口矩阵，
   跑通「四阶段诊断在真机上的可见性、真实性、隔离性与开关零侵入」；
3. 逐项给出通过 / 不通过 / NOT RUN 判定，附原始设备证据（截图、uiautomator dump、WebSocket 原始帧时序）。

非目标：

- **不改任何生产代码、测试、构建脚本或协议文件**；发现缺陷只记录（含最小复现），交 controller 另开返工任务；
- 不处理 P1 的遗留低项 F-0001 / F-0002 / F-0004 / F-0005，也不处理 P1 真机轮新发现 D-2/D-3/D-4/D-5 的修复；
- 不做 P2—P5；不修真实书源；不恢复封存的 AI 功能；
- 不做发版动作、不跑 `:app:lintAppDebug`（非发布轮）、不提交、不推送、不切分支；
- 不读取真实书源 JSON/ZIP、`docs/book-source-audit/语料/**`、`语料归因.md`、`bookshelf.json`、签名材料与任何凭据；
- 不触碰设备上 `io.legado.app.tjf`、`io.legado.app.releaseA` 两个包的数据。

## 真机抽查范围（每项都要落证据，跑不了的写 NOT RUN）

| # | 场景 | 期望 |
| --- | --- | --- |
| S1 | 调试页 `--<正文页URL>` | 四行摘要齐全；阶段 1/2/3 为真实读数，阶段 4 显式「未执行」；每行只含阶段/分类/长度/行数/图片数/耗时 |
| S2 | 目录→正文入口（`++<tocUrl>`）与完整链路（搜索→详情→目录→正文） | 阶段 1/2 仍然出现（首轮会静默丢失的场景） |
| S3 | 抓取失败（无监听 / 隧道断开） | 第 1 行分类为网络类错误，其余阶段显式「未执行」；摘要不含正文/查询串/Cookie |
| S4 | **会话存活时读同一书源同一本书同一章**（r3 修复点） | 补打一段摘要，`4.阅读处理后` 为**真实读数**而非「未执行」；同一会话只补打一次 |
| S5 | 会话存活时读**别的书**或本地书 | 摘要里不出现别的书/本地书的第 4 阶段读数 |
| S6 | 关闭调试后重复阅读 | 零 `【诊断】` 输出、无额外处理 |
| S7 | 失败分类 + F-01 真机对照（会话期间同章已被阅读，随后抓取失败） | 失败类别贴在链路实际停住的层，**第 4 行仍是阅读的真实读数**，不得被错误类别污染 |
| S8 | Web 端调试（`BookSourceDebugWebSocket`） | 四行摘要**完整到达之后** socket 才关闭（B-4 保序的真机对照） |
| S9 | 开关诊断前后正文一致性 | 阅读页正文在诊断开启/关闭下逐字一致 |

### 窗口修正（依据 rework-r3 `30-acceptance.md` §五 F-0003，必须遵守）

Android 书源调试页的会话在 Activity finish 时经 `onCleared()` → `Debug.cancelDebug(true)` 结束，
**离开调试页去书架阅读就必然先结束会话**，S4/S7 用「调试页 + 切书架」这个窗口无法复现。
本任务改用 **Web 端调试 socket**（`BookSourceDebugWebSocket`，不经 Activity finish，会话在 socket 存活期间一直有效）
作为「会话存活」入口；必要时配合 PC 侧夹具服务的可控延迟/中断来制造「抓取仍在飞行中」的窗口。
若某一步在该入口下仍不可复现，如实写 NOT RUN 并说明判据为何不成立。

## 实时基线

- 任务风险档：standard
- Git 仓库根：D:/vsproject/legado-E
- 分支：`codex/manga-import`；Git HEAD：`b8ba92e194468f0d7e6103c1175ba9f2a8233d95`（本任务不提交、不推送、不切分支）
- 工作树：P1 第三轮返工的未提交改动 + P0 既有脏基线（`.gitignore`、`书源与解析优化计划书.md`、`bookshelf.json`、根目录历史日志等）；
  被测二进制必须由**该工作树**编译，不得使用 2026-09-24 14:37 的旧 APK 或设备上 2026-09-24 10:01 安装的旧包。
- 修前快照 SHA-256：`9e538117f5fe3110948c31141f677d755e9081f338e67d9cd5624c322ef36b17`
- 当前 route_revision：8（mode=`dsh-claude`）

## 复审冻结与轮次

- 最大复审轮次：2
- 复审冻结窗口：reviewer 启动至回执完成期间，任务书与执行报告不得变化；runner 前后快照不一致时本轮报告失效。
- reviewer 可执行检查：只允许 `06-review-commands.json` 中冻结的参数数组；空数组表示 `NOT RUN`（只读静态复审 + acceptor 独立复核设备证据）。
- 网络策略：禁止访问真实站点；只允许 PC 本机 `127.0.0.1` 的合成夹具与 `adb` 通道。

## 角色与授权

- controller：dsh（本窗口；route_revision 8）
- executor：dsh（真机抽查与证据落盘；不得自验收）
- reviewer：claude（只读 tool-policy，由 controller 用 `agent-protocol role reviewer -Entry dsh` 拉起）
- acceptor：dsh（复审后独立复核证据与判定）
- reviewer 可读（只读白名单，超出即越界）：本任务目录全部证据文件（`00-task.md`、`05-routing.md`、`06-review-commands.json`、
  `10-execution.md`、`10-execution.summary.json`、`11-implementation-notes.md`、`16-device-checks.md`、`31-*.txt`、
  `33-device-evidence/**`，含 png/xml/log 与 PC 侧脚本副本）、`app/src/main/AndroidManifest.xml`（核对临时改名是否已还原）、
  `app/src/main/java/io/legado/app/help/book/diagnostics/**`、`app/src/main/java/io/legado/app/model/Debug.kt`、
  `app/src/main/java/io/legado/app/help/book/ContentProcessor.kt`、
  `app/src/main/java/io/legado/app/web/socket/BookSourceDebugWebSocket.kt`、
  `app/src/main/java/io/legado/app/utils/SerialTaskQueue.kt`、`.agent-protocol/{README,MODES,ROLES,CLOSEOUT}.md`。
  禁止读取：其它任务目录的原文（`book-source-p1-rework-r3/**` 与 `book-source-p1-diagnostics/**` 仅可在 00-task.md 引用的范围内读其
  `30-acceptance.md`/`10-execution.md` 结论段）、`docs/book-source-audit/语料/**`、`bookshelf.json`、`keystore*`、凭据、真实书源 JSON。
- 外部目的地与模型：当前用户级 `providers.json` 配置的 claude reviewer；准确路由以运行回执为准，不猜测、不输出凭据。
- 沙箱提权：**是（逐条申请）**。唯一需要提权的命令是 Gradle 构建
  （`$env:GRADLE_USER_HOME='D:\gradle_home'; .\gradlew.bat :app:assembleAppDebug --console=plain`），
  原因是 wrapper 要写工作区外 `D:\gradle_home` 且已被 workspace-write 拒绝（`gradle-8.14.4-bin.zip.lck 拒绝访问`）；
  提权档位 `danger-full-access`，一次一条命令，不得批量。`adb` 与设备操作不需要提权。
- 正式文件白名单（硬上限）：
  - 本任务目录：`00-task.md`、`05-routing.md`、`06-review-commands.json`、`10-execution.md`、`10-execution.summary.json`、
    `11-implementation-notes.md`、`16-device-checks.md`、`3x-*.md`、`31-*.log`、`33-device-evidence/**`
  - `app/build/p1-device-fixture/**`（合成夹具，可增删；含 PC 侧夹具服务脚本）
  - `app/build/p1-device-*.log`（构建/设备命令日志）
  - `app/src/main/AndroidManifest.xml` —— **仅 4 行临时改动**（第 27-29 行自定义权限声明、第 541-542 行
    `ReaderProvider` 的 `readPermission`/`writePermission`）：构建期把 `io.legado.READ_WRITE` 临时改名为
    `io.legado.app.tjf.debug.READ_WRITE`，**构建与安装完成后必须还原**，并核对
    `git diff -- app/src/main/AndroidManifest.xml` 为空。理由见下「构建期临时改名的必要性」。
  - **除上述 4 行外不含任何 `app/src/**`**：本任务禁止改动生产代码与测试。

### 构建期临时改名的必要性（controller 预先授权，依据同仓库既有先例）

`adb install -r` 安装本工作树产物被 `INSTALL_FAILED_DUPLICATE_PERMISSION` 拒绝，实测证据：

```text
Failure [INSTALL_FAILED_DUPLICATE_PERMISSION: Package io.legado.app.tjf.debug
         attempting to redeclare permission io.legado.READ_WRITE
         already owned by io.legado.app.tjf.release]
```

- `io.legado.READ_WRITE` 由设备上已存在的 `io.legado.app.tjf.release`（dumpsys `installed=true`，签名
  `93:A2:84:...`）持有；本工作树用 `app/release.keystore`（`BE:A4:23:...`）签名，两者不同证书 → 重复声明被拒。
- `io.legado.READ_WRITE` 自提交 `a4f3c800a`（2026-08-14）起声明，且同证书族的 `io.legado.app.tjf` 也在设备上，
  因此卸载单个 `.tjf.release` 并不能释放占用（属主会落到同证书的另一个包），且卸载用户现装包不在授权范围内。
- 同名改名的先例：`.agent-protocol/tasks/ai-stage-e-device/00-task.md` §角色与授权第 51 行（task 已由 claude reviewer
  复审通过，仅留信息级残余风险）。本次沿用同一方案，改名目标带 `.debug` 前缀以确保设备上无同名占用。
- 影响与披露义务：改名只改变本测试包的 provider 权限名（第三方 app 的 `io.legado.READ_WRITE` 授权不再适用于这个
  **测试包**，对设备上其它包零影响）；被测代码（DEX/resources）与冻结工作树产物**逐字节一致**，执行侧必须用
  「同一版本号、改名前后两个 APK 逐条目对比」给出证明，并如实写进 `10-execution.md`。
- 允许产生的临时目录：系统临时目录、`app/build/`、`D:/gradle_home`。
- 收尾前必须清理：PC 侧夹具服务进程、`adb reverse` 转发、本任务产生的临时探针脚本（保留证据类文件）。
- 明确禁止的数据和操作：真实书源/语料/归因表、`bookshelf.json`、签名材料、凭据、其它包与其它 App 的用户数据；
  不提交、不推送、不发布、不卸载用户现装包、不改系统设置（输入法/网络）。

## 设备与环境（实测值，执行时以实际为准）

- 设备：`10CE5P1M1Z001P9`（vivo `V2338A`/PD2338，USB）；另有 `emulator-5554` 在场但不使用。
- 被测包：`io.legado.app.tjf.debug`（隔离包，测试只发生在该包内）。
- 合成夹具：PC 侧 `app/build/p1-device-fixture/`（source.json、search/book/toc/chapter/chapter2.html、image.png），
  `127.0.0.1:8765` + `adb reverse`；合成书源 `http://127.0.0.1:8765/`（name `P1 synthetic device check`，
  `checkKeyWord` 承载调试键，规避设备输入法把 ASCII 标点转全角的问题）。

## 完成条件

- [ ] 被测 APK 由当前工作树编译，记录 APK 路径/大小/sha256 与安装时间；设备上安装前后包版本一致可复核。
- [ ] S1—S9 逐项给出判定与原始证据；未跑项写 `NOT RUN` 并说明判据为何不成立，不得用静态证据冒充设备证据。
- [ ] S4/S7 必须给出「会话期间同章真实阅读」的正面设备证据（这是 r3 修复的核心，也是 P1 唯一阻塞残留）。
- [ ] 脱敏核对：所有摘要行不含正文、查询串、Cookie、令牌、异常 message。
- [ ] 设备与工作树状态变更如实登记（安装/设置开关/设备数据新增）。
- [ ] 独立复审（claude，runner 封套）与验收（dsh）分别产出证据；执行完成本身不构成 P1 通过。
- [ ] 若发现生产代码缺陷：停止该项、记录最小复现与证据，交 controller 决定是否另开返工任务，**不在本任务里改代码**。

## 停止条件

- 需要改生产代码/测试/协议文件才能继续；
- 需要真实书源、真实用户数据、其它包数据或凭据；
- 需要提交、推送、发布或改系统设置；
- 当前模式不满足任务要求的保证等级（例如 reviewer 不可用）。

## 风险档规则

- `light`：只允许文档、注释与不改变运行行为的说明性文件；一轮独立静态复审。
- `standard`：普通代码、启发式和配置行为；独立 reviewer、冻结快照、最多两轮。
- `high`：安全、权限、迁移、协议脚本、发布与高影响数据变更；独立 reviewer 与独立 acceptor，关键门禁必须重跑。

## 交接说明

- 任务来源：`book-source-p1-rework-r3` `30-acceptance.md` §七「唯一下一步」与《维护计划书-后续修复与增强.md》2026-09-24 条目。
- 原任务证据目录保持只读：不覆盖、不删除、不改写任何角色原文；本任务报告引用其 finding 编号（F-0001…F-0007、F-01…F-06、D-1…D-5）。
- 用户授权：人类在 DSH 窗口直接指派「真机已就绪，收尾 P1」，即授权安装隔离 debug 包并操作合成数据。
