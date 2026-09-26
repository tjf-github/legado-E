# gate-artifacts — P1 首轮（`book-source-p1-diagnostics`）原始门禁日志

来源与归位说明（2026-09-26，controller 收尾）：

- 这 4 份日志原先是**仓库根**的未跟踪文件，由首轮 executor 于 2026-09-22 在根目录直接写出。
  这违反协议 README §145「不得在仓库根自建 `.gradle-home`、长日志或复现目录」，当时以 `.gitignore`
  挡住而非清理；P1 阶段收尾时按 `.agent-protocol/CLOSEOUT.md` §3 归位到本目录（字节未改动，哈希如下）。
- **没有删除它们**：`rounds/executor-r001/10-execution.md:77-78` 明确把它们登记为「保留（供 reviewer 复核）」，
  正文 §4 的编译/测试真实输出就是从这些文件抄录的；同任务 reviewer 把「读不到这些日志」记为 NOT RUN。
  它们不是「没有唯一性的临时产物」，所以 CLOSEOUT §3「先确认它不是唯一证据」不成立。
- 这些日志**不在** runner 的 gate ledger 内（首轮没有 runner 封套，见 `40-superseded.md`）；哈希由 controller 现算，
  只作字节冻结凭据，不冒充账本证据。

| 文件 | 字节 | sha256 | 报告引用位置 |
| --- | ---: | --- | --- |
| `build-dsh-p1-run4.log` | 80354 | `ff40cb37c3d62e214c5fa3fdec8bbbb375d5103b38effed132b5b38d54312518` | `rounds/executor-r001/10-execution.md:153`（改动后首次全量编译，5m08s） |
| `build-dsh-p1-test15.log` | 5344 | `4b64a392f036ef6b11ecf51108ae6c94c8ae1da2a67fde9334874034b571324a` | 同文件 `:183`（全量测试最终一轮） |
| `build-dsh-p1-compile-final.log` | 4293 | `a4c58136dd9b21455814d2bc01069e6d6d013126057069d589875fdb5de31edc` | 同文件 `:165`（编译任务 UP-TO-DATE） |
| `build-dsh-p1-final-verify.log` | 5152 | `49b599f9e4da1f10c68745f807e48cd90f34145203d732fa002a217a070380e0` | 同文件 `:210`（清理后最终复核） |

复算：`Get-FileHash <file> -Algorithm SHA256`（Windows PowerShell 会输出大写，比较时按不区分大小写处理）。
