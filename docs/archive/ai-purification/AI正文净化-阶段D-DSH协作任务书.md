# 阶段 D 协作任务书

状态：accepted（Codex 独立代码级验收）；编译与全量 177 项测试通过，DSH 只读复审 ready。最终结果见《AI正文净化-阶段D-DSH实施报告.md》。日期：2026-09-05。

## 已完成前置门禁

- 已在两条 `ReadBook.contentLoadFinish*` 中抽出共用挂起函数 `prepareDisplayContent`，返回原有标题和 `BookContent`，保留调用方原有取消检查、协程作用域和排版行为。
- 独立提交：`a440b3aaf refactor(reader): share chapter display preparation`，只包含 `ReadBook.kt`。
- 已使用 `GRADLE_USER_HOME=D:\gradle_home` 执行 `:app:compileAppDebugKotlin :app:testAppDebugUnitTest --console=plain`，BUILD SUCCESSFUL in 32s；详细日志：`app/build/reports/ai-stage-d/refactor-build.log`。
- 阶段 C 已验收但仍有未提交工作区文件；必须保留，不回滚，不把已有修改误认为本阶段新增实现。`look.ps1` 为无关文件。

## 拟定 DSH 分工与范围

用户已授权沿用阶段 C 的 `deepseek-official / deepseek-v4-flash-vision-exp`，high。仅限本功能计划、AI 模块、阅读链路、相关 UI/资源、Book.ReadConfig 与相应测试。禁止读取凭据、签名材料、真实书源、正文与缓存；不得提交、推送、发布或自行调用真实正文服务。

DSH 实施限定生产代码并提供 review 报告；Codex 独立审查真实 diff、补反例、执行编译与全量单测并同步验收状态。DSH 的完成声明不能替代 Codex 验收。

## 实施契约

1. 全局配置 UI：默认关闭，服务地址、模型、安全密钥输入、超时、单块上限、默认仅非计费网络、固定文本测试连接与 AI 缓存清理。密钥仅经 AiKeyStore 存储，不回填明文，不进入日志/普通配置/备份；API 21–22 明确不可用。
2. 逐书开关仅在线文字书显示，存 Book.ReadConfig 可选字段；设备外发同意单独保存在 noBackupFilesDir，绑定规范化当前服务地址。变更地址、恢复/迁移后须重新确认。私网 HTTP 另作显式确认。
3. 使用 AiChapterService，不直接拼接 HTTP。只处理当前在线纯文本章，不因前后章预排版发请求。任务绑定书、章、输入哈希、配置快照和 AiTaskToken；切章、离书、关闭、配置更新立即失效。
4. 原文先正常排版；完整结果只通知“AI 正文已就绪”，用户主动选择才从章首重排。AiDisplayContent 保留 original/candidate/selectedSource，候选仅 copy textList，保留 sameTitleRemoved/effectiveReplaceRules。不得写回原始章节缓存或改变搜索、导出、朗读、本地 TXT/EPUB 与漫画行为。
5. 提供处理进度、失败、取消、重试、原文/AI 切换和本章重新处理。失败始终原文可读，不自动循环重试。重处理只清 AI 章缓存。发布结果与刷新页面前均须重新核对任务身份。
6. 关闭全局/本书开关立即取消并恢复原文；网络策略按 Android 计费能力检查，不以 Wi-Fi 名称替代。未确认、缺密钥、配置无效、结构化正文一律不外发。

## 独立验收重点

- 无开关/无确认/不合规网络/本地与结构化正文均零调用；恢复逐书开关不能恢复本设备授权。
- 两条加载路径、预加载章转当前章、缓存命中、切章返回、配置变更、取消后忽略取消的迟到结果均无串章和自动换文。
- 手动显示源切换从章首，保留原正文与 BookContent 元数据；失败可立即读原文。
- 固定连接测试不含正文；UI 密钥不参与状态保存/备份；设置页有块数/发送字符数，不估算费用。
- 编译和全量 JVM 单测通过后方可代码级 accepted。真机网络、旋转/退后台、阅读位置及发布 lint 仍属阶段 E，不能以 JVM 结果冒充设备验收。
