# book-source-p1-device — 验收（acceptor: dsh）

状态：本轮真机合成链路抽查在代码层、设备层、独立复审层均由证据支撑，且验收侧**自己复跑/复算**了命令类检查、
补做了 reviewer 无法核对的设备最终态核对，并当场发现且修正了一处收尾遗漏（见 `2.6）。判定：

`accepted`

- product_state：`ready`
- assurance_state：`complete`
- 日期：2026-09-24（本地时区）
- acceptor 运行实例：dsh（mode=`dsh-claude`，route_revision=8，assurance=standard；controller=executor=acceptor=dsh，reviewer=claude 由 runner 拉起、两轮）
- 基线：分支 `codex/manga-import`，HEAD `b8ba92e194468f0d7e6103c1175ba9f2a8233d95`（未提交、未推送、未切分支、未发布）
- 判定依据：任务书 `完成条件 + `20-review.md`（reviewer-r002，通过、无阻塞项）+ 本验收自己复跑/复算的证据

---

## 一、验收范围与依据

读取：本任务 `00-task.md`/`05-routing.md`/`06-review-commands.json`、`10-execution.md`（第 3 版）、
`10-execution.summary.json`、`11-implementation-notes.md`、`16-device-checks.md`（第 2 版）、`31-apk-parity.txt`、
`33-device-evidence/**`（含 `60-`/`61-`/`62-`/`63-` 与 png/xml）、`15-reviewer-raw.md`、`20-review.md`、
`.review-snapshot.json`、`rounds/reviewer-r001/**`；只读引用 `book-source-p1-rework-r3/30-acceptance.md` 与
`book-source-p1-diagnostics/16-device-checks.md`（未改一字）。

本验收**未修改**任何生产代码、测试、任务书、执行报告或复审原文。新增文件只有：
`30-acceptance.md`、`33-device-evidence/34-acceptor-*.{png,xml,txt}`、`35-acceptor-*-rerun.txt`、
`33-device-evidence/ws_p1_debug.py`（同步实际使用的带墙钟版本）、`findings.json`（发现登记表）。

---

## 二、独立复核（验收自己跑的，不采信报告自述）

### 2.1 角色回执

`agent-protocol role-verify book-source-p1-device` → **PASS（2 项一致）**：
当前轮 `15-reviewer-raw.md -> 20-review.md` 与 `rounds/reviewer-r001/15-reviewer-raw.md -> 20-review.md` 均一致，
说明两轮复审结论未被事后改动。当前回执 `结果：ok`、`复审快照：stable`、`捕获策略：last-complete-report-boundary`（非 fallback）。
`15-reviewer-raw.md` 记录实际路由 `ANTHROPIC_BASE_URL=http://127.0.0.1:15721`、只读强制 `tool-policy`、会话 `843cbfba-…`。

### 2.2 产物与工作树（reviewer 明确无法核对的两项）

- `git diff -- app/src/main/AndroidManifest.xml` = **空**（0 行），`git status --short` 中**不存在** `AndroidManifest.xml` ⇒
  构建期临时权限改名**确已还原**（原始输出 `34-acceptor-git-manifest-diff.txt`）。
- 业务工作树改动集合（`34-acceptor-git-status.txt`）与 P1 第三轮返工的既有集合逐一对应，本任务**没有**新增任何业务改动：
  tracked 侧仍是 7 个业务文件 + `.agent-protocol/CURRENT.md`（controller 指针）+ 3 个既有文档；untracked 侧是本任务与 P1 的既有产物。
- APK 双份哈希复算：冻结树产物 `87AA25C9…5164941`、被测包 `31381C41…0A8CFF`，与报告一致。

### 2.3 命令类检查（reviewer 的 `06-review-commands.json` 为空数组 ⇒ 其侧全为 NOT RUN）

- `python compare_apk.py` 复跑（`35-acceptor-apk-parity-rerun.txt`）：3621 条目，`only_in_A/B = []`，
  `differing_entries = ['AndroidManifest.xml','META-INF/CERT.RSA','META-INF/CERT.SF','META-INF/MANIFEST.MF']`，23 个 dex、`dex identical: True`。
  ⇒「被测包与冻结工作树产物的**代码逐字节相同**，差异只有那一处已还原的权限声明与随之变化的签名」有可复算依据。
- `python check_parity.py` 复跑（`35-acceptor-s9-parity-rerun.txt`）：三份 dump 的 `content_text_view` 的 `content-desc` 完全相同，
  `parity(all three equal): True`（脚本比较的是三个不同文件，非自证）。

### 2.4 复审发现的处置核对

reviewer-r001（`20-review.md` 第 1 轮，已被 runner 归档到 `rounds/reviewer-r001/`）判 **有阻塞项（证据层）**；
执行侧第 2 版逐条处置（F-1 重做 S5 对照、F-2 更正窗口叙述、F-3 补边界、F-4 收窄判定、F-5 补原始件）。
reviewer-r002 判 **通过（无阻塞项）**；其 F-1…F-9 的处置由验收侧核对后认定（更正的落点见下）：

- **采纳**：F-1（S5 再收窄为「部分通过（仅同书其它章节）」）、F-2/F-3（parity 等证据文件实际转存 UTF-8、
  带墙钟的 WS 客户端已同步进证据目录）、F-4（D-4 根因改写为「无 cause 链匹配、`EOFException` 属编码类」）、
  F-5（`90-…png` 改标准备态、`91-…xml` 才是刷新后；S2 耗时标注按原始 XML 重抄）、F-6（S9 表行改标「时刻不可证」）、
  F-7（D-3 收窄为「该时刻无补打帧」）、F-9（F-6 标注「代码可达性已确认」）。
- **记录为后续项**：F-8（夹具脚本路径穿越加固，属测试脚本、超出本任务授权）。

**更正落在哪里（重要）**：`10-execution.md` 是复审冻结对象——`close` 会按 runner 记录的 `ExecutionDigest` 复核它是否被事后改动
（当前值 `a5926af3c34dbd77e0fc220c35105e4457e6b2cf2989a9c46b00ba47c6d8bb2a`，即 reviewer-r002 看到的那一版，验收侧已按字节还原并复核一致）。
按 MODES「最后一轮复审后只允许删除或收窄已有表述的文档修正，且 acceptor 必须逐项披露」，上述收窄/更正**不改写执行报告原文**，
分别落在本验收单、更正清单 `31-acceptor-post-review-corrections.md`，以及不在冻结摘要内的 `16-device-checks.md`（已就地更正）。
⇒ 若在执行报告里仍读到 `S5 = **通过**` 或 §5 D-3/D-4/F-6 的旧措辞，以本验收单与更正清单为准；执行报告的字节没有被验收侧改动。

### 2.5 与任务书 `完成条件逐条对照

| 完成条件 | 结论 | 依据 |
| --- | --- | --- |
| 被测 APK 由当前工作树编译，记录路径/大小/sha256 与安装时间 | 满足 | `10-execution.md` `1/`3；`62-install-raw.txt`；`63-dumpsys-package-raw.txt` |
| S1—S9 逐项判定与原始证据，未跑项 NOT RUN | 满足（S3/S4/S5 按复审收窄口径） | `16-device-checks.md`；`33-device-evidence/**` |
| S4/S7 必须给出「会话期间同章真实阅读」的正面设备证据 | 满足 | S4 帧 #18—#21 + logcat 23:11:17.399；S7 帧 #22—#25（失败未污染第 4 行） |
| 脱敏核对（不含正文/查询串/Cookie/令牌/异常 message） | 满足 | 三处失败摘要均为「异常类型 X，消息默认不输出」；S1 四行只含阶段/分类/长度/行数/图片数/耗时 |
| 设备与工作树状态变更如实登记 | 满足（并含验收侧修正，见 `2.6） | `16-device-checks.md` 末节；`34-acceptor-device-final-state.txt` |
| 独立复审（claude，runner 封套）与验收（dsh）分别产出证据 | 满足 | `15-reviewer-raw.md`/`20-review.md`（两轮）+ 本文件 |
| 发现生产代码缺陷时停止并交 controller | 满足（未发现缺陷；D-2/D-3/F-6 属可达性限制与风险项） | `10-execution.md` `5 |

### 2.6 验收侧当场发现并修正的收尾遗漏（如实披露）

acceptor 复算设备最终态时发现：S5 对照轮起的**夹具服务仍在监听 8765**、`adb reverse tcp:8765` 与
`adb forward tcp:1122/1123` **仍未移除**，而执行报告与 reviewer-r002 都只能核到「开关已关闭」这一半。
acceptor 随即停掉夹具进程、移除全部转发，并重新落盘最终态（`34-acceptor-device-final-state.txt`）：

```text
reverse(device):            （空）
forward(device):            （空）
fixture 8765:               no listener
web service:                not running
pkg version:                versionCode=26092422 | firstInstallTime=2026-09-24 10:01:36
```

另有截图 `34-acceptor-my-page-final.png`（"我的"页，Web 服务开关关闭、说明文案为"用浏览器写源或看书"）+ 同名 `.xml`。
⇒「本任务不留常驻进程、不残留转发、诊断开关已还原」现在有验收侧的一手证据，而不是只靠执行侧自述。

---

## 三、逐项判定（与执行报告一致，S3/S4/S5 采用复审后的收窄口径）

| # | 项 | 判定 | 关键证据 |
| --- | --- | --- | --- |
| S1 | 调试页 `--<正文页URL>` 四阶段摘要 | 通过 | `20-S1-…xml/png`：1/2/3 为真实读数（107/85/81），第 4 行显式「未执行」，四行脱敏 |
| S2 | 目录入口与完整链路 | 通过 | `21-S2-…`、`22-S2b-…`：两个入口的阶段 1/2 都在 |
| S3 | 抓取失败分类与脱敏 | **部分通过** | 仅「连接被拒」子场景满足（`23-S3-…`：网络错误 + 三个显式未执行）；「响应被中途打断」仍是 `未知错误`（D-4） |
| S4 | 会话存活期读同一书同一章 → 真实阶段四补打 | **部分通过** | 仅 `++<tocUrl>` 入口（绑定值与章节存储 URL 逐字相同）复现：帧 #18—#21 + logcat 23:11:17.399；`--<绝对URL>` 入口不复现（D-2）；Android 调试页渠道 NOT RUN（F-0003） |
| S5 | 会话存活期读别的章节/别的书 | **部分通过** | 同会话正/负对照成立（负向：刷新 Chapter two 零 `【诊断】`；正向：刷新绑定章 Chapter one 出现四行）；**别的书/本地书 NOT RUN**（设备上无对照对象） |
| S6 | 会话结束后阅读零诊断输出 | 通过 | logcat 该窗口增量 0 行；`50-…` 正文正常（缓存离线阅读） |
| S7 | 抓取失败不得污染阶段四 | 通过 | 帧 #22—#25：失败贴阶段一，第 4 行仍为真实读数 |
| S8 | Web 端摘要先于终止信号 | 通过 | 帧序 摘要四行 → 终止帧 → close（两段会话各一例） |
| S9 | 开关诊断时正文逐字一致 | 通过 | 三份 dump 的 `content-desc` 逐字相同（验收复跑：`parity(all three equal): True`） |

**P1 退出条件（《书源与解析优化计划书》`7 P1）的设备侧含义**：①不改变最终正文语义——由 S9 的设备级逐字一致支撑；
②既有构建与全量 JVM 单测通过——沿用 r3 门禁（45 套件 / 394 用例，本轮未重跑测试，属 NOT RUN）；
③诊断信息能指出问题发生在哪一层——由 S1/S2/S3a/S7 的设备读数支撑。

---

## 四、NOT RUN（不得读作已覆盖）

1. Android 调试页渠道的「补打」同屏证据（F-0003：离开调试页即结束会话）；
2. 本地 TXT/EPUB 与「别的书」的隔离对照（设备上只有一本合成书、无本地书）；
3. 真实书源与真实站点、`docs/book-source-audit/语料/**`、`bookshelf.json`；
4. 音频/视频/文件类书源、多页章（`nextContentUrl`）、`@webjs:`；
5. `:app:lintAppDebug`（非发布轮；本轮也未改业务代码）；
6. 浏览器端 Web 调试页（本轮用 PC 侧 WebSocket 客户端直连 `/bookSourceDebug`）；
7. JVM 单测与契约测试本轮**未执行**（沿用 r3 的门禁结论；命令类检查在复审侧为 NOT RUN）；
8. F-6 的组合场景（绑定会话内让阅读自身的抓取失败）。

---

## 五、后续项（交 controller / 用户，不在本任务范围）

| # | 项 | 建议 |
| --- | --- | --- |
| D-1 | 本机安装任何本仓库产物都会被 `INSTALL_FAILED_DUPLICATE_PERMISSION` 拒绝（设备上异签名的 `io.legado.app.tjf.release` 占用 `io.legado.READ_WRITE`） | 登记为环境事实（《发布推送流程书》或《维护计划书》），否则每个真机轮都会重踩并可能误判为构建缺陷 |
| D-2 | 阶段四补打按章节 URL 字符串精确匹配，`--<绝对URL>` 入口对相对 URL 的存量书永不补打 | 立项：会话键与章节 URL 规范化匹配，或在调试页明示「按 URL 精确匹配」 |
| D-3 | 补打依赖阅读链路真的重新执行处理；本次会话里「切章那一刻」没有补打帧、「刷新」有 | 作为已知行为记录，不单独立项 |
| D-4 | 中途被打断的失败仍归 `未知错误`（无 cause 链匹配；`EOFException` 属编码类） | 后续在 `DiagnosticClassifier` 增加 cause 链与消息标记 |
| F-6 | 阶段四对「非正文输入」不设防，代码路径可达（未触发） | 与 D-2 一并考虑：为哨兵/失败文案加守卫 |
| F-8 | 夹具脚本路径穿越加固 | 仅测试脚本，后续顺手修 |
| P1 遗留 | r3 登记的 F-0001 / F-0002 / F-0004 / F-0005（低项） | 按 r3 验收单的建议随下一次改动一并处理 |

---

## 六、最终判定与边界

**判定：`accepted`（product_state=ready，assurance_state=complete）。**

理由：本任务是一次**验证轮**，其目标是「把 P1 第三轮返工后遗留的真机/端到端项跑成设备证据」。
该目标达成：被测二进制与冻结工作树产物的代码逐字节相同（可复算）、安装与数据保留有原始输出、
S1/S2/S6—S9 通过、S3/S4/S5 在收窄口径下成立、两轮独立复审（第二轮无阻塞项）与验收侧复跑复算一致、
设备与 PC 侧最终态由验收侧一手核对并修正了收尾遗漏。

**边界（不要把本判定读成超过它的东西）**：

- 这是**本任务**的验收结论，不等于「P1 的全部场景都已在真机验证」——`四的 NOT RUN 依旧成立；
- S4/S5 的场景覆盖是窄的（一个入口、同书其它章节），任务书里「别的书/本地书」的隔离**从未在设备上验过**；
- 本验收不改变 r3 已归档的 F-0001…F-0007 状态，也不代表 D-1/D-2/D-4/F-6 已被修复。

- 唯一下一步：由 controller 收口本任务（`agent-protocol close book-source-p1-device`）并更新《维护计划书》P1 状态；
  D-1/D-2 按 `五 立项。
- 提交 / 推送 / 发布状态：**均未授权、均未执行**；本轮全部工作树改动仍未提交，提交边界由用户决定。
