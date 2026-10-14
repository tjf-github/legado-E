# 阶段 C 实施与独立验收

状态：accepted（Codex 独立验收）；日期 2026-09-05；基线 17c646bae。

DSH 会话：session-7602e4c8-26c5-4b1f-bd08-900718e41573。
已实查路由：deepseek-official / deepseek-v4-flash-vision-exp，high，routable=true。
范围与分工见《AI正文净化-阶段C-DSH协作任务书.md》。未调用真实正文净化服务，未读取真实书籍或密钥，未推送发布。

## 独立反例基线

Codex 在生产修改前补入 AiStageCBoundaryTest；运行结果 9 tests / 7 failures / 0 errors。
失败项：哨兵邻近上限向右扩块、保护值重排、中文邻接数字漏保护、符号/控制字符冒充标点、过长组合字符原子、组合字符/尾上下文拆分、文本编辑构造数字。
命令：`$env:GRADLE_USER_HOME='D:\gradle_home'; .\gradlew.bat :app:testAppDebugUnitTest --tests 'io.legado.app.help.ai.AiStageCBoundaryTest' --console=plain`。
基线结果 BUILD FAILED in 2m 57s。初次沙箱运行因 D:\gradle_home 锁文件拒绝访问中止，获准后执行。

## 交付验收

DSH 两轮实现后明确停止写入、状态 review。Codex 独立编译发现 Book 导入错误、不存在的 Character.PUNCTUATION、缓存路径校验字符字面量错误，未直接接受 DSH 独立脚本的通过结论。

Codex 进一步修正：

- 线性原子边界扫描与惰性块列表，严格限制正文/尾上下文，不累计前块请求正文；超大原子单元失败回退。整章先保护 URL/Unicode 数字，恢复时检查唯一性和顺序，最终再次对比保护值并验证纯文本结构。
- 文件缓存按书/章/完整身份隔离；同一 canonical 根目录共用清理/提交锁和世代。每块校验后写临时文件，所有块成功后才组装完整结果，写盘或原子重命名失败不产生新命中。
- 元数据与完整正文放在同一个 JSON 文件中，包含 Completed 标记、完整身份与正文 SHA-256；通过一次同目录 rename 提交，避免两个独立文件分别替换导致撕裂。此处是对计划 §7“小 JSON 元数据+结果”的具体化：使用单个完整文件，而非双文件事务。先 flush + fd.sync；不使用 API 26 才有的 java.nio.file，不先删旧结果，不回退成非原子复制。
- 缓存读取拒绝损坏/截断/null/身份不符/校验和不符的文件；临时文件永不命中，重开清除非活动残留；失败/取消清理当前临时文件。缓存文件硬上限 32 MiB，超限安全回退。
- 全局请求互斥、同键单飞、跟随者独立取消；清理不再依赖可取消的 Mutex。AiTaskToken 绑定任务、失效时取消，发布与 token 失效共用同步边界；每次请求前后、块写入前、正式提交前和返回前核验。
- 配置仓库更新取消在途任务；AiChapterService 同时检查快照仍有效，默认拒绝未开总开关/未开本书/未确认设备外发/本地或结构化正文。服务入口在 IO 上处理，未连接阅读 UI。
- BookHelp 全部/单书/孤立缓存清理联动；Book.delete（含书籍详情/API 删除）、两种书架批量删除及音视频移出书架入口只补 AI 缓存清理，不改原文与数据库结构。

基线并发反例：最初 2 tests / 2 failures，验证跨实例并发和忽略取消后返回 Completed 的问题。修正后一轮 AI 编译/测试 BUILD SUCCESSFUL in 3m 58s。

最终门禁：`$env:GRADLE_USER_HOME='D:\gradle_home'; .\gradlew.bat :app:compileAppDebugKotlin :app:testAppDebugUnitTest --console=plain`，BUILD SUCCESSFUL in 1m 1s。24 套件 / 153 tests / 0 failures / 0 errors / 0 skipped；其中 AI 64 tests，阶段 C 新增 32 tests。`git diff --check` 通过。

DSH 第三轮基于 Codex 最终缓存/处理器/token/服务入口源码进行无工具只读复审，返回 `ready`、`blockers=[]`，未发现未完成缓存可命中或明显取消死锁。非阻塞说明：不支持覆盖的文件系统上 rename 失败会保留旧完整条目；清理任一本书会保守失效该缓存根目录的在途任务。此复审只作为补充证据，accepted 仍由 Codex 根据真实代码与上述门禁给出。

新增测试套件：AiStageCBoundaryTest（11）、AiStageCConcurrencyTest（3）、AiStageCProcessingTest（6）、AiChapterCacheTest（10）、AiChapterServiceTest（2）。覆盖重命名失败保留旧完整结果、校验和损坏、临时/崩溃残留、重开不删除活动块、最终提交点失效、忽略取消的 Provider、配置切换、默认未授权不发送和原文不变。

本地完整日志保存在 `app/build/reports/ai-stage-c/`，不纳入 Git；历史基线失败与最终通过证据均保留。

## 阶段 D 调用约定

使用 AiChapterService，传当前 BookContent、书/章身份、由安全存储取得的 AiApiKey、当前章 AiTaskToken；逐书授权和当前服务地址的设备确认由阶段 D 提供，参数默认 false。切章/离书/关闭功能必须使 token 失效。Completed 只表示候选正文，UI 仍需核对当前任务、由用户主动切换显示；Original/Failed 保持原文，CancellationException 由阅读协调器按协程取消处理。

无缓存 AiChapterProcessor 重载仅保留离线契约测试用途；生产服务注入独立缓存并有命中短路。同章重新处理先 delete(identity)，只删除 AI 结果。配置、模型、Prompt、块大小、校验版本、输入正文变更形成新身份；API Key 不进入身份/缓存。

## 验证边界

本阶段使用合成文本、Fake Provider 和 JVM 临时目录；未使用真实密钥/正文服务。崩溃测试模拟残留临时文件与损坏正式文件，未实际杀死 Android 进程。真实设备、文件系统、阅读交互与发布 lint 仍按阶段 E 执行。本轮未提交、推送或发布。
