# 阶段 D 实施与独立验收

状态：accepted（Codex 独立代码级验收）。日期：2026-09-05。

## 协作与基线

用户明确授权 deepseek-official / deepseek-v4-flash-vision-exp，high，读取和修改本阶段相关代码及测试，排除真实正文、书源、密钥、签名材料。会话：session-464649c6-92a3-474b-a24a-8c2de7c9acb5，已核对路由 routable=true。

共享函数先独立提交 a440b3aaf，编译及 153 项全量单测通过。阶段 C 的未提交工作区修改全部保留。DSH 首轮生产实现、限定配置 UI Rework 均为 review，Codex 独立审查、修正与验收；原始 DSH 报告保存在 app/build/reports/ai-stage-d/dsh-initial-report.md，已过时的“预加载转当前不处理”等声明不再代表当前代码。未推送发布。

## 最终交付

- “其他设置”和阅读菜单均提供 AI 配置入口。默认关闭，配置服务地址、模型、安全密钥、超时、块上限与默认非计费网络限制。测试连接使用当前表单与固定测试文本，总开关关闭也可测试，不上传书籍正文；与正文处理共用请求锁。
- 逐书开关使用 Book.ReadConfig.aiEnabled，缺字段默认关闭；用户确认外发后才开启。本设备确认存 noBackupFilesDir，绑定规范化实际端点。地址变更和恢复备份撤销确认；私网 HTTP 显式确认，公开 HTTP 拒绝。
- 密钥不回填，不参与控件状态保存/旋转 Bundle/自动填充，销毁对话框时清空输入。保存先核验安全密钥状态再允许开启；API 21–22 禁止启用。错误仅展示固定分类。
- 两条 contentLoadFinish* 共用准备函数，先排版原文；完整结果只提示就绪，由用户主动从章首切 AI 或原文。候选只替换 textList，保留 sameTitleRemoved/effectiveReplaceRules，不写回原文缓存。
- 前后章准备原文时不请求 AI；预加载章提升为当前章时激活当前章协调器。同章刷新/翻页不重复请求，失败或取消须手动重试。取消后仍有操作入口。
- 重新处理先删除本章 AI 缓存；清 AI 缓存不删除原文，也不会立即自动重新生成。统计在请求入口记录块数及发送字符数，不估算费用。
- 切章、离书、关闭、配置变更、恢复备份、退后台及网络不符合限制时取消旧任务。落盘前核对配置/当前章/确认/网络；UI 发布前核对阅读世代、书章和显示任务令牌。
- 朗读按实际已排版 TextChapter 判断是否 AI，先回原文并完成排版，再自动开始章首朗读。朗读运行中不允许切 AI；离开 AI 章时不把该排版对象作为原文邻章复用。

## Codex 独立修正

首轮编译失败（3m 53s）：lazy 属性误用 isInitialized、配置对话框缺 setLayout 扩展。配置 Rework 又出现 AiTextConfig/ProviderConfig 参数不匹配，均已修正，未将 DSH 完成声明当作验收。

其余实质修正：撤权/取消后的旧候选、配置身份缺失、生产 runner 丢弃 isCurrent、进度回调持有 token 锁导致取消死锁、切换位置未归零、重处理未删缓存、预加载转当前未激活、前后台取消缺失、全局设置缺入口、确认前已开启本书、测试连接使用旧密钥/HTTP 授权，以及配置失效后仍可能发布旧候选。

未新增数据库表列，不改变书源解析、原文缓存、导出和全文搜索。阶段 C 缓存与清理的已有修改继续保留。

## 验证证据

命令：GRADLE_USER_HOME=D:\gradle_home，执行 .\gradlew.bat :app:compileAppDebugKotlin :app:testAppDebugUnitTest --console=plain。

最终门禁：BUILD SUCCESSFUL in 1m 9s；29 套件 / 177 tests / 0 failures / 0 errors / 0 skipped。全部为 JVM/Fake Provider/合成文本测试，未调用真实正文服务。

日志位于 app/build/reports/ai-stage-d/：refactor-build.log（153 项基线）、service-tests.log（初次 3 项集成）、review-build.log 与 rework-build.log（编译失败过程）、validation-build.log（中间 175 项通过）、final-build.log（最终 177 项通过）。日志不纳入 Git。

新增测试：AiDisplayContentTest 4 项、AiReadingCoordinatorTest 8 项、AiStageDReaderBoundaryTest 5 项、AiDeviceConsentTest 2 项、AiStageDServiceIntegrationTest 5 项，共 24 项。覆盖原文元数据、手动选择、撤权/配置变化、取消/迟到、端点确认/恢复、缓存命中/输入变化、跨书同索引、真实处理器统计和进度取消锁边界；修正了 DSH 测试的计数遗漏、线程安全及作用域清理。

## 验收边界与下一步

DSH 最终只读复审返回 ready、blockers=[]，核对当前章世代与显示 token、逐层外发门禁、配置/网络失效以及取消/进度锁顺序。复审未修改代码；仅作为补充证据，accepted 由 Codex 根据真实 diff、177 项全量测试和编译通过给出。git diff --check 通过。阶段 C/D 的功能修改仍保留在工作区，未新增功能提交、推送或发布。

本阶段为代码级验收。没有真实密钥/正文服务调用，也没有 Android 真机交互证据；不能把 JVM 结果作为真机通过结论。下一步阶段 E：慢网/断网/认证/限流、切章/旋转/退后台、阅读位置、朗读、缓存清理、图片及本地书回归。发版前再执行 lint、更新日志并走发布流程；本轮未发布。
