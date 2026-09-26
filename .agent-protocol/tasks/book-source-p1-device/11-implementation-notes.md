# book-source-p1-device — 实施与偏差记录（构建/安装/环境）

本文件记录**怎么把包弄到真机上**、以及为此做的临时改动与还原证明。逐项设备结果见 `16-device-checks.md`。

## 1. 被测 APK

| 项 | 值 |
| --- | --- |
| 来源工作树 | 分支 `codex/manga-import`，HEAD `b8ba92e194468f0d7e6103c1175ba9f2a8233d95` + P1 第三轮返工未提交改动 |
| 任务 | `:app:assembleAppDebug '-PVERSION=3.26.092422' '-PVERSION_CODE=26092422'` |
| 产物（改名版，装在设备上） | `app/build/outputs/apk/app/debug/legado_app_3.26.092422.apk` → 留存副本 `app/build/p1-device-apk-B-renamed.apk` |
| 大小 / SHA-256 | 37693471 B / `31381c4141b8546196ac9f0207468663134c41fbcc6ced99f00e5b72de0a8cff` |
| 产物（还原后冻结树重建，用于对比） | 37688190 B / `87aa25c9ebcbe4058157adba09b469542c04830637c7d4ec2bbb3093d5164941` |
| 版本号锁定原因 | `versionName/versionCode` 由构建时刻生成，锁定后两次构建（改名版/冻结版）除 manifest 外可比 |

## 2. 临时权限改名（唯一的生产文件改动，已还原）

- 位置：`app/src/main/AndroidManifest.xml` 共 4 行 —— 自定义权限声明 `io.legado.READ_WRITE` → `io.legado.app.tjf.debug.READ_WRITE`，
  以及 `ReaderProvider` 的 `android:readPermission`/`android:writePermission` 同步改名。
- 原因：设备上已存在的 `io.legado.app.tjf.release`（异签名 `93:A2:84…`）占用了 `io.legado.READ_WRITE`；
  本工作树产物用 `app/release.keystore`（`BE:A4:23…`）签名，重复声明被 `INSTALL_FAILED_DUPLICATE_PERMISSION` 拒绝。
  卸载单个 `.tjf.release` 无效（同证书的 `io.legado.app.tjf` 会接手占用），且卸载用户现装包不在授权范围。
- 先例：`.agent-protocol/tasks/ai-stage-e-device/00-task.md` 第 51 行（同一方案，已由 claude reviewer 复审通过，
  仅留信息级残余风险）。
- 还原与核对：装包完成后立即还原，`git diff -- app/src/main/AndroidManifest.xml` 与 `git status --short -- app/src/main/AndroidManifest.xml` 均为空。
- 一致性证明：`31-apk-parity.txt`——冻结树产物 vs 被测包，3621 个条目中**只有** `AndroidManifest.xml` 与
  `META-INF/CERT.RSA`、`META-INF/CERT.SF`、`META-INF/MANIFEST.MF` 不同（后三者随签名必然变化），
  `classes.dex`…`classes23.dex` 共 23 个 dex、资源、assets、native 库全部逐字节相同。

## 3. 安装与设备状态

```text
adb -s 10CE5P1M1Z001P9 install -r app/build/p1-device-apk-B-renamed.apk   -> Success
dumpsys package io.legado.app.tjf.debug:
  versionCode=26092422  versionName=3.26.092422debug
  firstInstallTime=2026-09-24 10:01:36   lastUpdateTime=2026-09-24 23:01:06
  自定义权限=io.legado.app.tjf.debug.READ_WRITE (prot=normal)
```

`firstInstallTime` 未变 ⇒ 是更新安装、数据保留（未卸载、未清数据）。设备上另有 `io.legado.app.tjf`、`io.legado.app.tjf.release`、
`io.legado.app.releaseA`，本轮**未安装、未卸载、未清数据**。

## 4. 合成夹具与 PC 侧脚本（`app/build/p1-device-fixture/`，均被 .gitignore 忽略）

| 文件 | 用途 |
| --- | --- |
| `source.json` / `search.html` / `book.html` / `toc.html` / `chapter.html` / `chapter2.html` / `image.png` | 合成书源与页面（沿用 P1 首轮真机轮的夹具） |
| `serve_p1_fixture.py` | 夹具 HTTP 服务；`mode.txt` 控制 `normal` / `hang[:path]` / `hang-once:<path>` / `reset` / `delay:N[:path]`；每次请求写 `server.log` |
| `ws_p1_debug.py` | Web 端调试 WebSocket 客户端：发 `{"tag","key"}`，按到达顺序给每帧打**客户端墙钟**与相对时刻写 `ws-frames.log`（第 2 版加墙钟，对应 F-5） |
| `p1dev.py` | 设备操作助手：uiautomator dump/查找/点击/截图/状态探针（`state` 报告 content/menu/drawer） |
| `p1api.py` | 经 App 自带 Web API 读写合成书源（`/getBookSource`、`/saveBookSource`） |
| `check_parity.py` / `compare_apk.py` | S9 正文一致性、§2 的 APK 逐条目对比（可复跑） |
| `62-install-raw.txt` / `63-dumpsys-package-raw.txt`（在证据目录） | `install -r` 与 `dumpsys package` 的**原始输出**，替代第 1 版的转写（F-5） |

夹具服务的 `hang-once:<path>` 是为 F-0003 窗口专门加的：只让**第一条**匹配该路径的请求停在空中
（即调试链路自己那次抓取），把会话存活窗口拉到约 60s。**更正（reviewer-r001 F-2）**：该窗口内并**没有**第二次成功的
正文抓取——阅读链路读的是**本地缓存**的章节正文；`hang-once` 不制造“并发抓取窗口”，它只保证调试抓取在窗口内一直处于飞行状态。

## 5. 环境事实（建议登记，CLOSEOUT §5）

1. DSH 沙箱下 `gradlew` 写 `D:\gradle_home`（wrapper 锁/缓存）会被拒（`gradle-8.14.4-bin.zip.lck 拒绝访问`），需按任务书授权提权一次；
2. PowerShell 下 `-PVERSION=3.26.092422` 未加引号会被拆成 `-PVERSION=3` + 任务名 `.26.092422`，必须写成 `'-PVERSION=3.26.092422'`；
3. 本机 `adb install` 装本仓库产物会被 `INSTALL_FAILED_DUPLICATE_PERMISSION` 拒绝（D-1），不是构建缺陷；
4. 设备输入法把 ASCII 标点转全角，`adb shell input text` 无法敲 `--<url>` 调试键（P1 首轮已记录，本轮用 Web API 改写书源 `checkKeyWord` 绕过）。
