# book-source-d2-match — 任务书

状态：`ready`

## 目标与非目标

- **目标**：修掉 D-2（`.agent-protocol/tasks/book-source-p1-device/findings.json` F-0009）——阶段四「补打」的身份匹配过严，导致调试页 `--<绝对URL>` 入口对相对存储章节永不补打，且失效时摘要**贴错标签**（写「未进入阅读处理」，而阅读其实执行了）。按裁定书 `docs/book-source-audit/D-2-补打匹配口径-裁定.md` 的最小修法落地 M1–M3：
  - **M1**：章节身份按「绝对化 + 去 URL 选项」归一后比较（`BookChapter.getAbsoluteURL()` 同源口径），相对/绝对混用不再失配；
  - **M2**：书源身份归一再比（去首尾空白、去尾斜杠、去查询串）；
  - **M3**：不匹配时把第 4 行说明写成可读原因（原因为「本次会话有 N 次其它书源/书籍/章节的阅读处理被排除」），把静默失效变成可见事实，且**不记录任何 URL**（只记计数）。
- **非目标**（明确不做）：不放宽为「同域名/同站即接受」；不做 URL 查询串排序归一；**本轮不实现 `sortUrls()` 集合兜底**（理由：阅读侧 `ContentProcessor` 手上没有 `BookSource` 对象，两侧信息不对称，硬做会引入数据库查询与更宽耦合；改由 M3 把不匹配显式暴露，留作后续项）；不把匹配键写入数据库；不改正文提取/格式化/替换/请求/阅读缓存语义；不碰 `help/ai/**` 与 `ReadBook` 的 AI 分支；不做真机（真机证据按 §13 授权另行按需发起）；不动 P2/P3 的其它项。

## 实时基线

- 任务风险档：standard
- Git 仓库根：D:/vsproject/legado-E
- 分支：codex/manga-import
- Git HEAD：25b289adb2060da421e437e9766727dac783a0c2
- 修前工作树摘要： M "双平台开发环境流程书.md" | ?? build-dsh-d2-task-create.log
- 修前快照 SHA-256：f0e0a3a366cee0490b148bd67aacbc887b3ebeeb73925e4af34eacb74d3a4d78
- 当前 route_revision：8
- **既有脏基线（非本任务，禁止吸收进本任务提交）**：`双平台开发环境流程书.md`（controller 在冻结前登记的两条环境坑：协议证据行尾、协议快照哈希成本）；`build-dsh-d2-task-create.log`（建任务失败/成功的过程日志，收尾时归位或删除）。两者都不是本任务产物。

## 复审冻结与轮次

- 最大复审轮次：2
- 复审冻结窗口：reviewer 启动至回执完成期间，授权文件、任务书与执行报告不得变化；runner 前后快照不一致时本轮报告失效。
- reviewer 可执行检查：只允许 `06-review-commands.json` 中冻结的参数数组；空数组表示 `NOT RUN`。本轮冻结**三条**（`d2-whitelist-diff-check`、`d2-whitelist-status`、`d2-whitelist-diffstat`），见该文件（第一轮复审 F-3 指出此处原写「两条」，已更正）。
- 网络策略：默认禁止；本任务不需要联网。

## 角色与授权

- controller：dsh
- executor 可读 / 可改：
  - `app/src/main/java/io/legado/app/help/book/diagnostics/**`（含新增文件）
  - `app/src/main/java/io/legado/app/model/Debug.kt`
  - `app/src/main/java/io/legado/app/model/webBook/BookContent.kt`
  - `app/src/main/java/io/legado/app/help/book/ContentProcessor.kt`
  - `app/src/test/java/io/legado/app/help/book/diagnostics/**`
  - `docs/book-source-audit/D-2-补打匹配口径-裁定.md`（仅在实施与裁定不一致时就地更正并披露）
- reviewer 可读：本任务 `00-task.md`、`05-routing.md`、`06-review-commands.json`、`10-execution.md`、`11-implementation-notes.md`、**`gate-artifacts/**`**（第一轮复审 F-3 指出门禁原始件不在可读范围，控制器据此扩权），以及上列全部业务文件与 `docs/book-source-audit/D-2-补打匹配口径-裁定.md`。
- 沙箱提权：否（默认）。本任务不需要提权命令。
- 写白名单是**硬上限**：白名单内才是授权范围；白名单外即使只改一个注释也属越界，必须先由 controller 扩权并重新冻结。
- 正式文件白名单：见「executor 可读 / 可改」全部条目，**另加 `双平台开发环境流程书.md`**——该文件在执行期间由 controller（同一实例）追加了两条环境坑登记（协议快照哈希成本、`review-probe` 复制成本），第二轮复审 §3.2 要求 controller 明确追认；现按扩权处理并纳入白名单，仅限 §8 这两条登记。
   - reviewer 白名单追加（第三轮复审的前置，按第二轮复审建议）：`app/src/main/java/io/legado/app/utils/NetworkUtils.kt` 的 `getAbsoluteURL`、`app/src/main/java/io/legado/app/data/entities/BookChapter.kt` 的 `getAbsoluteURL/baseUrl`、`app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeUrl.kt` 的 `paramPattern`、`app/src/main/java/io/legado/app/model/webBook/BookChapterList.kt:230-245`——用于独立核对 M1「与请求路径同源口径」，不再只能采信执行侧转述。
- 允许产生的临时目录：系统临时目录；项目内不新建临时目录。门禁原始输出放本任务 `gate-artifacts/`。
- 收尾前必须清理：本任务产生的临时文件；`build-dsh-d2-task-create.log` 归位到本任务 `gate-artifacts/`（它是建任务过程证据）。
- acceptor：dsh
- 外部目的地与模型：无外发；reviewer 由 runner 拉起（claude，只读 tool-policy）。
- 明确禁止的数据和操作：真实书源 URL/Cookie/令牌/正文不得进入代码、测试、文档或提交；不得执行联网请求；不得改动数据库结构；不得修改上述白名单以外文件。

## 完成条件

- [ ] **实现条件**：M1（章节身份归一：绝对化 + 去选项）、M2（书源身份归一）、M3（不匹配原因可见且不含 URL）三条都落地；归一实现集中在 `help/book/diagnostics/` 内的纯 JVM 代码，Android 侧只传参。
- [ ] **自动化条件**：`.\gradlew.bat :app:compileAppDebugKotlin` 与 `.\gradlew.bat :app:testAppDebugUnitTest` 全绿（`$env:GRADLE_USER_HOME="D:\gradle_home"`；如需 `--no-problems-report` 必须披露差异）；新增/扩展的契约测试至少覆盖：相对+base vs 绝对（M1 正例）、绝对 vs 绝对、去选项、书源尾斜杠/查询归一（M2 正例）、以及**负例**——别的书、别的章节、本地书（`loc_book`）一律不接受；`mismatchCount` 在拒绝时递增。
- [ ] **可见行为条件**：绑定会话后，阅读同一章时第 4 行出现真实读数（M1 场景）；身份不匹配时第 4 行说明变为「…有 N 次其它书源/书籍/章节的阅读处理被排除」，不再出现「未进入阅读处理」这种错误因果（M3）。本轮以 JVM 契约测试 + 合成构造证明；真机同屏证据标 NOT RUN。
- [ ] **文档与交接条件**：`11-implementation-notes.md` 写清改动点与口径；若与裁定书有出入（例如 `sortUrls` 兜底未做），必须就地更正裁定书并在执行报告披露；`gate-artifacts/` 存门禁原始输出与 sha256。

## 停止条件

- 权限或数据范围不明确；
- 需要改变产品语义、费用、发布范围或安全边界；
- 当前模式不满足任务要求的保证等级；
- 发现必须放宽到「同域名/同站接受」才能修复（那会推翻 S5 隔离证据，须回到 controller 重新裁定）。

## 风险档规则

- `light`：只允许文档、注释与不改变运行行为的说明性文件；一轮独立静态复审。触及代码、脚本、依赖、构建、CI、权限、安全、路由、发布或数据格式时必须升级。
- `standard`：普通代码、启发式和配置行为；独立 reviewer、冻结快照、最多两轮。
- `high`：安全、权限、迁移、协议脚本、发布与高影响数据变更；独立 reviewer 与独立 acceptor，关键门禁必须重跑。
