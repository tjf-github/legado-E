# book-source-p1-diagnostics — P1 离线测试与阶段诊断（DSH 执行）

状态：`rework`

## 目标与非目标

用户于 2026-09-22 授权将 P1 移交 DSH 处理。唯一范围依据《书源与解析优化计划书.md》§6 B0、§7 P1、§8.1/8.3。P0 已按诊断基线完成退出，保留工作树全部既有改动。

交付：AnalyzeRule、HtmlFormatter、ContentProcessor、ReplaceRule JVM 契约测试；提取后、HTML 格式化后、书源替换后、阅读处理后四阶段诊断摘要；统一错误分类与默认脱敏。先读模块注释，先写契约测试，再加最小可观测性实现；覆盖 CSS/JSoup、XPath、JSONPath、正则、离线 JS，以及标点、诗歌空行、相邻图片、usehtml、相对 URL、已有替换规则。测试应冻结当前行为，不得借测试修正正文语义。

不做 P2—P5，不修真实书源，不恢复封存 AI 功能，不改数据库结构，本地 TXT/EPUB 路径零侵入，不改变最终正文、异常回退、分页或替换语义。未实际经过某阶段时必须如实标记未执行，不能伪造快照。默认摘要只含类型、长度、数量、耗时、阶段和分类；不输出真实正文、Cookie、令牌、URL 查询秘密等。诊断开关关闭时不得引入额外内容处理或持久化。
## 实时基线

- 任务风险档：standard
- Git 仓库根：D:/vsproject/legado-E
- 分支：codex/manga-import
- Git HEAD：b8ba92e194468f0d7e6103c1175ba9f2a8233d95
- 修前工作树摘要：git.exe : warning: unable to access 'C:\Users\huixu/.config/git/ignore': Permission denied | At D:\vsproject\agent-collaboration-protocol\scripts\AgentProtocol.Common.ps1:359 char:19 | + ...   $output = & git -c "safe.directory=$absolute" -C $absolute @Argumen ... | +                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ |     + CategoryInfo          : NotSpecified: (warning: unable...rmission denied:String) [], RemoteException |     + FullyQualifiedErrorId : NativeCommandError |   | warning: unable to access 'C:\Users\huixu/.config/git/ignore': Permission denied |  M .agent-protocol/CURRENT.md |  M .gitignore |  M "\344\271\246\346\272\220\344\270\216\350\247\243\346\236\220\344\274\230\345\214\226\350\256\241\345\210\222\344\271\246.md" | ?? .claude/settings.local.json | ?? app/src/test/resources/bookSourceBaseline/INVARIANTS.md | ?? app/src/test/resources/bookSourceBaseline/README.md | ?? app/src/test/resources/bookSourceBaseline/case-01-html-basic/case.json | ?? app/src/test/resources/bookSourceBaseline/case-01-html-basic/input.html | ?? app/src/test/resources/bookSourceBaseline/case-02-json-api/case.json | ?? app/src/test/resources/bookSourceBaseline/case-02-json-api/input.json | ?? app/src/test/resources/bookSourceBaseline/case-03-next-page/case.json | ?? app/src/test/resources/bookSourceBaseline/case-03-next-page/page-1.html | ?? app/src/test/resources/bookSourceBaseline/case-03-next-page/page-2.html | ?? app/src/test/resources/bookSourceBaseline/case-04-inline-img/case.json | ?? app/src/test/resources/bookSourceBaseline/case-04-inline-img/input.html | ?? app/src/test/resources/bookSourceBaseline/case-05-pure-image/case.json | ?? app/src/test/resources/bookSourceBaseline/case-05-pure-image/input.html | ?? app/src/test/resources/bookSourceBaseline/case-06-poetry-blank/case.json | ?? app/src/test/resources/bookSourceBaseline/case-06-poetry-blank/input.html | ?? app/src/test/resources/bookSourceBaseline/case-07-existing-replace/case.json | ?? app/src/test/resources/bookSourceBaseline/case-07-existing-replace/input.html | ?? app/src/test/resources/bookSourceBaseline/case-08-punctuation/case.json | ?? app/src/test/resources/bookSourceBaseline/case-08-punctuation/input.html | ?? app/src/test/resources/bookSourceBaseline/case-09-title-in-body/case.json | ?? app/src/test/resources/bookSourceBaseline/case-09-title-in-body/s1-exact.html | ?? app/src/test/resources/bookSourceBaseline/case-09-title-in-body/s2-indent-u3000.html | ?? app/src/test/resources/bookSourceBaseline/case-09-title-in-body/s3-numeric.html | ?? app/src/test/resources/bookSourceBaseline/case-09-title-in-body/s4-suffix.html | ?? app/src/test/resources/bookSourceBaseline/case-09-title-in-body/s5-wrapper.html | ?? app/src/test/resources/bookSourceBaseline/case-09-title-in-body/s6-nav-plus-title.html | ?? app/src/test/resources/bookSourceBaseline/case-09-title-in-body/s7-duplicate.html | ?? app/src/test/resources/bookSourceBaseline/case-10-entity-newline/case.json | ?? app/src/test/resources/bookSourceBaseline/case-10-entity-newline/input-literal.html | ?? app/src/test/resources/bookSourceBaseline/case-10-entity-newline/input.html | ?? bookshelf.json | ?? "docs/book-source-audit/2026-09-22-\345\215\225\344\273\275\346\270\205\345\215\225-summary.json" | ?? "docs/book-source-audit/2026-09-22-\345\215\225\344\273\275\346\270\205\345\215\225-\351\235\231\346\200\201\345\210\206\347\276\244\346\212\245\345\221\212.md" | ?? "docs/book-source-audit/2026-09-22-\345\275\222\345\271\266\345\220\210\351\233\206-summary.json" | ?? "docs/book-source-audit/2026-09-22-\345\275\222\345\271\266\345\220\210\351\233\206-\351\235\231\346\200\201\345\210\206\347\276\244\346\212\245\345\221\212.md" | ?? "docs/book-source-audit/2026-09-22-\351\231\220\345\256\232\350\201\224\347\275\221\346\216\242\346\265\213.md" | ?? docs/book-source-audit/README.md | ?? docs/book-source-audit/summary.json | ?? "docs/book-source-audit/\350\257\212\346\226\255\345\215\241/01-\346\255\243\346\226\207\351\223\276\350\267\257\347\274\272\345\244\261.md" | ?? "docs/book-source-audit/\350\257\212\346\226\255\345\215\241/02-\346\233\277\346\215\242\350\247\204\345\210\231\345\221\275\344\270\255\346\240\207\350\256\260.md" | ?? "docs/book-source-audit/\350\257\212\346\226\255\345\215\241/03-\345\233\276\347\211\207\345\255\227-imageStyle-TEXT.md" | ?? "docs/book-source-audit/\350\257\212\346\226\255\345\215\241/04-\346\255\243\346\226\207\344\270\213\344\270\200\351\241\265.md" | ?? "docs/book-source-audit/\350\257\212\346\226\255\345\215\241/05-\346\233\277\346\215\242\350\247\204\345\210\231\346\255\243\345\210\231\351\242\204\346\243\200\346\237\245.md" | ?? "docs/book-source-audit/\350\257\212\346\226\255\345\215\241/06-\346\255\243\346\226\207\345\207\272\347\216\260\346\240\207\351\242\230.md" | ?? "docs/book-source-audit/\350\257\212\346\226\255\345\215\241/07-\345\256\236\344\275\223\350\247\243\347\240\201\344\270\216\346\240\274\345\274\217\345\214\226\346\254\241\345\272\217.md" | ?? "docs/book-source-audit/\350\257\212\346\226\255\345\215\241/PENDING.md" | ?? "docs/book-source-audit/\350\257\212\346\226\255\345\215\241/TEMPLATE.md" | ?? "docs/book-source-audit/\350\257\255\346\226\231/2026-09-22-AI\346\255\243\346\226\207\345\274\202\345\270\270\346\240\267\346\234\254\345\272\223V1.md" | ?? "docs/book-source-audit/\350\257\255\346\226\231/README.md" | ?? "docs/book-source-audit/\350\257\255\346\226\231\345\275\222\345\233\240.md" | ?? tools/book-source-audit/README.md | ?? tools/book-source-audit/audit.py | ?? tools/book-source-audit/checks.py | ?? tools/book-source-audit/report.py | ?? tools/book-source-audit/rule_syntax.py | ?? tools/book-source-audit/tests/test_p0_tools.py | ?? tools/book-source-audit/verify_p0.py
- 修前快照 SHA-256：e6057da4fbd971ee088c8384a3d537133807d40a0734705ad07ea5a061b5c392
- 当前 route_revision：8

## 复审冻结与轮次

- 最大复审轮次：2
- 复审冻结窗口：reviewer 启动至回执完成，业务文件、任务书与执行报告冻结。
- reviewer 可执行检查：仅 06-review-commands.json；未配置检查为 NOT RUN。
- 网络策略：本轮离线合成样本，不访问真实站点；已有依赖正常构建可用，不安装新依赖。

## 角色与授权

- controller：dsh
- executor：dsh，仅执行；完成后交 review，不得自验收。
- acceptor：dsh
- reviewer：claude（后续由 controller 独立拉起，只读）。
- 外部目的地与模型：当前用户级 providers.json 配置的 DSH/DeepSeek executor；后续 Claude reviewer。准确路由/模型以运行回执为准，不猜测，不输出凭据。
- executor 可读 / reviewer 可读：AGENTS.md、.agent-protocol 的入口与本任务文件、《书源与解析优化计划书.md》《维护计划书-后续修复与增强.md》；app/src/main 下非敏感源码、资源，app/src/test 下合成测试，构建脚本；tools/book-source-audit 下工具和测试源码；docs/book-source-audit/README.md。只读理解相关调用链，不读取真实语料。
- 正式文件白名单：app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeRule.kt；app/src/main/java/io/legado/app/utils/HtmlFormatter.kt；app/src/main/java/io/legado/app/help/book/ContentProcessor.kt；app/src/main/java/io/legado/app/data/entities/ReplaceRule.kt；app/src/main/java/io/legado/app/model/webBook/BookContent.kt；app/src/main/java/io/legado/app/model/Debug.kt；app/src/main/java/io/legado/app/ui/book/source/debug/**；app/src/main/java/io/legado/app/help/book/diagnostics/**（允许新建纯诊断组件）；app/src/test/java/io/legado/app/**（仅本任务契约与诊断测试，不改无关测试）；app/src/test/resources/book-source-p1/**（新建合成夹具）；本任务目录 10-execution.md、10-execution.summary.json、11-implementation-notes.md。
- 允许产生的临时目录：系统临时目录、项目 .gradle/、build/、app/build/；Gradle 既有缓存 D:/gradle_home。禁止清理用户既有文件。
- 沙箱提权：否；环境不允许构建时记录命令与失败证据，交 controller 处理。
- 明确禁止的数据和操作：真实书源 JSON/ZIP、bookshelf.json、docs/book-source-audit/语料/**、语料归因.md、签名材料、凭据、用户设备数据；不提交、不推送、不切分支、不发布、不安装软件、不安装 APK、不修改协议/路由/其他任务证据。
- 写白名单是硬上限；超出时写明必要性并交 controller，不自行扩权。

## 完成条件

- [ ] 四组件契约测试与诊断分类/脱敏测试真实通过，包含诊断开关前后正文一致性。
- [ ] 四阶段接入真实调用链，有不可执行/缺失阶段的明确表示；不以重跑另一条链路冒充实际阶段。
- [ ] 设置 GRADLE_USER_HOME=D:/gradle_home 后运行 .\gradlew.bat :app:compileAppDebugKotlin 与 .\gradlew.bat :app:testAppDebugUnitTest，记录实际结果及测试数量。
- [ ] 在 10-execution.md 给出本次改动列表、相对既有脏基线的说明、命令证据、限制和后续真机抽查步骤。真机/UI 未验则写 NOT RUN，不宣称 P1 最终完成。
- [ ] 实现完成仅交 review，独立复审和 Codex 验收尚待执行。

## 停止条件

需要改变正文语义、依赖、数据库、本地导入路径、真实用户数据或白名单之外文件时停止该项并交 controller；可以继续范围内独立测试。发现既有 bug 只记录合成复现，不混入修复。

## 交接说明

当前保留 triad / route_revision 7：用户要求 DSH 处理 P1，按当前路由交 DSH executor；没有静默换挡或授予自验收权。此前 dual-release-privacy 保持 accepted，不改其任何原始证据。任务生成器 -Activate 因 CRLF 状态替换校验失败已自动回滚，随后成功生成 draft，controller 按 CLI 支持的手工激活方式补全本任务及 CURRENT 指针。
## Claude 首轮只读复审（2026-09-22，controller 冻结）

用户明确要求 Claude 复审。执行进程已退出（exit=0），这只证明调用结束，不代表实现合格。保持原执行授权边界，以下是新增的只读审查要求，不追认执行越界。

- 对照实际源码与 10-execution.md，不依赖执行方自述。新增测试和 diagnostics 文件也必须审查。
- 检查 app/build.gradle 的 returnDefaultValues=true 是否掩盖 Android 桩调用失败，使 358 测试通过不能代表契约成立；检查 AnalyzeByXPath.kt 越界修改。上述文件允许只读审查，仍不扩大 executor 写白名单。
- 检查 EXTRACT 是否实际代表规则提取后而非原始响应；四阶段是否属于同一章同一执行链；分页、异常、并发、音视频及未执行阶段是否准确。
- 检查 Debug.diagnostics 全局生命周期、停止调试/切换会话后清理、正常阅读跨会话串扰，以及诊断关闭时是否仍产生额外处理；检查 ContentProcessor 抽取是否影响本地 TXT/EPUB 路径和正文输出。
- 检查错误分类是否真正接入异常路径，默认摘要与异常信息脱敏是否可靠，以及 UI 是否能展示真实阶段读数。
- 逐项评估四组件测试是否调用真实生产逻辑，是否只是复制实现/断言常量，以及诊断开关前后正文一致性证据。
- 构建整体 BUILD FAILED、真机 NOT RUN 必须保留；不要将任务子步骤成功当作完整门禁通过。
- 本轮只读静态复审；06-review-commands.json 为空，不运行 shell、Gradle、联网或真机操作，独立门禁复跑写 NOT RUN。报告明确区分代码缺陷、授权越界与待验证证据，不自行修复。
- 报告逐项提供严重度、准确文件/行、触发场景与建议，结论 review/rework/可继续验收，不替 acceptor 最终接受。输出完整 agent-protocol-report 定界块，由 runner 写 20-review.md。