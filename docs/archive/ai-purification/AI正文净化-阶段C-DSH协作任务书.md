# 阶段 C 协作任务书

状态：accepted（Codex 独立验收）；基线 17c646bae；日期 2026-09-05。最终实现与验证以《AI正文净化-阶段C-DSH实施报告.md》为准。

用户授权与 DSH v4 flash vision 协作完成阶段 C。目标为 deepseek-official / deepseek-v4-flash-vision-exp，high。只允许读取本功能计划、AI 源码/测试、BookHelp 清理与删除书籍调用路径。禁止读取凭据、签名材料、真实书源/正文/缓存。不得提交、推送、发布、改数据库或接阅读页/UI。

DSH 负责阶段 C 生产代码实现，Codex 负责独立反例测试、真实 diff 审查、编译和全量单测验收。DSH 报告只能为 review。

实施范围：严格上限且不拆 Unicode 组合字符/哨兵的分块；200 code point 只读尾上下文；编辑硬校验与整章保护项/结构复验；全局串行、同键单飞、任务失效和取消；独立 cacheDir/ai_text 文件缓存，仅 Completed 可命中，临时文件组装、原子提交、按章/书/全部清理，BookHelp 与删除书籍联动。

清理与提交须同步防竞态：清理后迟到任务不得重新生成缓存。缓存身份须覆盖正文、配置、分块和校验版本。不把配置密钥放入缓存。任一失败停止后续请求；不展示混合正文。配置/任务令牌及协程状态须在请求后、临时写前和正式提交前检查。未接 UI 的调用方责任要明确记录。

验收：超长文本和 Unicode 边界、保护项跨边界、非法编辑、部分失败、忽略取消的迟到 Provider、跨实例并发、去重、损坏/临时缓存、清理竞态和身份失效；compileAppDebugKotlin 与全量 testAppDebugUnitTest 必须通过。
