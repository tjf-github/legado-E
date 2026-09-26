# P1 Codex 验收探针说明

日期：2026-09-23。仅使用 example.invalid 与 SYNTHETIC_* 合成标记；没有读取真实正文或凭据。

P1RedactionProbe.java 直接调用 app/build/tmp/kotlin-classes/appDebug 内生产 DiagnosticRedactor，classpath 使用既有 Kotlin 标准库。32-redaction-probe.log 为原始输出，退出码 1，三个样例全部保留禁止标记。

- URL 普通查询键 chapter 和 fragment 未移除。
- Cookie / Authorization 冒号后存在空格时，只插入 ***，原值仍保留。

该探针源文件与日志是可复跑的验收证据，不是产品源码、也未加入全量测试套件。本轮未修改生产代码与测试配置。