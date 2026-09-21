# 项目专属配置（导入后必须填写）

status: READY

- project_name: legado
- project_root: D:\vsproject\legado-E
- development_branch: codex/manga-import
- release_branch: main
- build_commands: .\gradlew.bat :app:compileAppDebugKotlin（编译门槛）；发版前补 :app:lintAppDebug 与 :app:assembleAppRelease
- test_commands: .\gradlew.bat :app:testAppDebugUnitTest（全量 JVM 单测，测试代码在 app/src/test）
- release_gates: 提交前 `.\gradlew.bat :app:compileAppDebugKotlin` 与 `:app:testAppDebugUnitTest` 必须通过；发版前补跑 `:app:lintAppDebug`；`push origin main` 触发 CI `Test Build`（test + lint + assembleRelease）自动发布 beta 预发布；正式版需先在 `app/src/main/assets/updateLog.md` 顶部加日期条目
- sensitive_paths: .env, .env.*, **/secrets/**, *.pem, *.key, *.p12, id_rsa*, .npmrc, .pypirc, credentials*, **/service-account*.json
- prohibited_operations: 提交、推送、切分支、发布、删除数据、安装软件、改变账户/网络/系统设置；凭据与真实用户内容默认禁止外发
- allowed_external_providers: 当前 mode=triad 授权 codex、dsh 与 claude 在本任务白名单内接收项目内容；凭据、签名材料、真实书源/正文和未脱敏日志仍禁止外发
- local_agent_presets_or_tools: 已授权本地代理 codex（人类启动，当前 controller+acceptor）、dsh（executor）与 claude（reviewer，只读）；本地工具链 git、Windows PowerShell 5.1/pwsh、Gradle wrapper（须先设 GRADLE_USER_HOME=D:\gradle_home）、Android SDK/adb 与真机 10CE5P1M1Z001P9、Python 造测试书脚本

这些字段不能从模板项目或上一个业务项目继承。完成填写并人工核对前，协议只能用于合成通道测试，不能授权业务代码改动。



