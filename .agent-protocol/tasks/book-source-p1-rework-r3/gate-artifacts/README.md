# gate-artifacts — P1 阶段收尾门禁（controller 复跑，2026-09-26）

这不是 runner 的 gate ledger 条目：P1 收尾由 controller 手工跑门禁，没有 runner 封套，所以这里只按
「原始输出 + 现算 sha256 + 命令」自描述留证，**不冒充**账本证据（`conclusion_owner=none` 仍成立，结论由
《书源与解析优化计划书》§7 P1 收尾记录与《维护计划书》待办池 2026-09-26 块承担）。

- 目的：在**当前工作树**上真跑一次 P1 门禁（不是 `UP-TO-DATE` 复用），为提交 `41344ef42` 背书。
- 命令：
  - `build-dsh-p1-closeout-gate.log`：`$env:GRADLE_USER_HOME="D:\gradle_home"; .\gradlew.bat :app:compileAppDebugKotlin :app:testAppDebugUnitTest --console=plain`
    → `BUILD SUCCESSFUL in 52s`，57 tasks 全 `UP-TO-DATE`（**只证明当前树已被同输入的绿灯覆盖**，不作真跑证据）。
  - `build-dsh-p1-closeout-test.log`：`.\gradlew.bat :app:testAppDebugUnitTest --rerun --no-problems-report --console=plain`
    → `BUILD SUCCESSFUL in 5m 28s`，`14 executed, 43 up-to-date`；结果 XML 汇总 **45 套件 / 394 用例、0 失败 0 错误**。
- 与任务书命令的差异：加 `--no-problems-report`。原因是提权进程创建的 `build/reports/problems/problems-report.html`
  会让非提权构建以 `AccessDeniedException` 失败（《双平台开发环境流程书》§8 已登记），等价通道见该节。
- 这份日志原先是仓库根文件（违反 README §145），2026-09-26 收尾时归位到本目录，字节未改动。

| 文件 | 字节 | sha256 |
| --- | ---: | --- |
| `build-dsh-p1-closeout-gate.log` | 8172 | `5178908831daef634c70597cdd86f07f228d9bab7a102d2705a6ef0cce88c4e3` |
| `build-dsh-p1-closeout-test.log` | 18612 | `3f9991c05a2c29a2e9ea61da9cb2e441238d749e9719d0b4ea8efc3a3a1bce00` |

用例数的独立复算通道（不依赖日志）：
`app/build/test-results/testAppDebugUnitTest/*.xml` 的 `testsuite@tests` 求和。
