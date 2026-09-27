# gate-artifacts — book-source-d2-match 门禁原始输出

由 executor（controller 同一 DSH 实例）手工跑，**不在 runner gate ledger 内**，故不冒充账本证据；
结论由 `10-execution.md` §1 与 `30-acceptance.md` 承担，这里只做原始件与哈希冻结。
（第一轮复审 F-3 指出本目录原先不在 reviewer 可读范围，控制器已扩权——见 `00-task.md` 的 reviewer 可读列表。）

## d2-gate-final.log（第三轮全量门禁，当前有效）

- 命令：`$env:GRADLE_USER_HOME="D:\gradle_home"; .\gradlew.bat :app:compileAppDebugKotlin :app:testAppDebugUnitTest --no-problems-report --console=plain`
- 结果：`BUILD SUCCESSFUL in 1m 27s`，`57 actionable tasks: 8 executed, 5 from cache, 44 up-to-date`
- 用例统计（同批 `app/build/test-results/testAppDebugUnitTest/*.xml` 解析）：**suites=46, tests=424, failures=0, errors=0, skipped=0**（新增测试类 `DiagnosticIdentityContractTest` = 30 条）
- 文件：8702 字节，sha256 `a6d29216ed6b773e2bf5e0e8a96ce210b219530420692895c2eef18a3f82254d`
- 与任务书命令的差异：加了 `--no-problems-report`。原因见《双平台开发环境流程书》§8。

## d2-gate-commit.log（提交前强制重跑，2026-09-26 由 controller 补跑）

- 命令：`$env:GRADLE_USER_HOME="D:\gradle_home"; .\gradlew.bat :app:compileAppDebugKotlin :app:testAppDebugUnitTest --rerun --no-problems-report`（`--rerun` 强制真跑，避免拿 `UP-TO-DATE` 冒充已验证）
- 结果：`BUILD SUCCESSFUL in 3m 17s`，`57 actionable tasks: 14 executed, 43 up-to-date`；实读到 `> Task :app:compileAppDebugKotlin`、`> Task :app:testAppDebugUnitTest`（均非 UP-TO-DATE）
- 用例统计（同批 `app/build/test-results/testAppDebugUnitTest/*.xml` 重新解析）：**suites=46, tests=424, failures=0, errors=0**
- 文件：sha256 `027927d82f9e36bed5736265fa68663524e3a317faede8705054b6a19c3e9b6f`
- 生效范围：`d2-gate-final.log`（r004 复审看过的那一版）仍是**被复审版本**的原始件；本件证明「提交前的代码与它逐行一致」。

## 未入库的机器产物

- `.review-bundle.json`（7.5 MB，`entries[22581]`，`bundle_hash=ec997bf2…`）：`review-probe` 复制到一次性 worktree 的**文件清单+哈希**，属机器派生物、不是角色结论；同一材料的精简版已在 `18-review-probes.md` 冻结（`applied_bundle_hash`）。**有意不入库**（CLOSEOUT §3「证据层不放临时副本」），需要时按 `Invoke-AgentProtocolReviewProbe.ps1` 重跑重建。

## d2-gate-round3.log（第二轮复审整改后的中间版本）

- 结果：`BUILD SUCCESSFUL`，**suites=46, tests=422, failures=0**（该版本尚未含 r003 的 F-2/F-3/F-4 整改）。
- 保留原因：对应 r003 复审看到的那一版代码。

## d2-gate-round2.log（第一轮整改后的版本）

- 结果：`BUILD SUCCESSFUL in 47s`，**suites=46, tests=422, failures=0**。（`d2-gate.log` 为更早的 415 用例版本。）

## d2-task-create.log（建任务过程）

- 记录第二次 `agent-protocol task book-source-d2-match -TaskClass standard -Activate` 的成功输出（`BaselineHead=25b289adb…`、`Activated=True`）。
- **第一次同名命令失败**：`任务状态写后校验失败`（`New-AgentProtocolTask.ps1:113`）。根因是 `.agent-protocol/**` 非任务文件在工作区是 CRLF、索引里是 LF，而该脚本的状态行替换正则 `(?m)^状态：\`draft\`$` 不容忍 `\r`；修复（按索引恢复 LF）与判据见 `05-routing.md` 披露 1 与《双平台开发环境流程书》§8。**失败原文未能留存**（Tee-Object 被第二次运行覆盖），此处按事实记录，不伪造原始件。
- 文件：1070 字节，sha256 `a2f19b6a74c98544b26653f3043d52345d3468c341c0d10cc31a2cd1facb88a0`

复算：`Get-FileHash <file> -Algorithm SHA256`（PowerShell 输出大写，比较时忽略大小写）。

## 冻结命令原始件的定义域缺口（第三轮复审 r004 F-3）

三条冻结命令**都带显式 pathspec**，其中 `d2-whitelist-*` 的 args 含非 ASCII 路径 `docs/book-source-audit/D-2-补打匹配口径-裁定.md`；而 `d2-whitelist-status` 的原始件只有 6 行（该文件**未出现**），`d2-whitelist-diffstat` 也只算到 4 个文件。**结论：这两份原始件的定义域不覆盖非 ASCII 路径，且它们在构造上发现不了白名单外改动**（带 pathspec 的 `git status`/`git diff` 只报告匹配项）。

因此：

- 「没有白名单外改动」这一结论**不能**由这两份原始件承担，只能由 `10-execution.md` §6 的 repo 级 `git status --short` 对账承担；
- 需要重新冻结时，pathspec 应改用 `-- 'docs/book-source-audit/*.md'` 或 `:(literal)` 形式；
- 本条由 r004 提出、controller 采纳并在此登记（第三轮复审后仅改文档，未动代码）。
