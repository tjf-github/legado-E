# book-source-d2-match — 路由与换挡记录

## 初始路由

- mode：dsh-claude
- assurance：standard
- route_revision：8
- 角色映射：controller=dsh；executor=dsh；reviewer=claude；acceptor=dsh
- 选择理由：由 CURRENT.md 继承（mode=dsh-claude，route_revision=8）

## 换挡记录

每次追加：时间、旧模式、新模式、原因、最后已验证状态、未完成项、新 controller。不得删除旧记录。

## 披露（非换挡，controller 主动登记）

1. **建任务前修复了协议文件的行尾漂移（2026-09-26）**：`.agent-protocol/**` 的非任务文件在工作区是 CRLF、在索引里是 LF（`git ls-files --eol` = `i/lf w/crlf`），而 `New-AgentProtocolTask.ps1:92` 的状态行替换正则按 LF 写（`^状态：\`draft\`$`，不容忍 `\r`），导致首次 `-Activate` 报「任务状态写后校验失败」。
   - 判据：`book-source-p1-device/13-reviewer-context.json` 记录的 `MODES.md`/`README.md` sha256 **正好等于当前内容的 CRLF 变体**（`3f116cc8…` / `ea8f7c0c…`）⇒ 内容未变，只是行尾被换过，且漂移发生在 2026-09-24 之后。
   - 处置：把 21 个非任务协议文件按索引恢复为 LF（字节现与 `git cat-file -s` 一致、`git status` 干净），并在 `.gitattributes` 钉 `.agent-protocol/** -text` 防复发。**未改动 `tasks/**` 任何字节**，`role-verify` 不受影响。
   - **副作用（如实记录）**：协议指纹由 P1 回执记录的 `3a239c56c05a3af36588a2297336d72995633cefbcd8076b5ff30bafa8560cf0` 变为 `9c5c705cba2efde46ec3a03e463e0d6ebe6491d24930687adecca4b6d1a6289d`（指纹按工作树字节计算）。这是把工作区恢复成索引内容的结果，不是协议规则改动；`CURRENT.md` 的 `channel_test: passed@2026-09-18` 能力证明因此不再与当前字节对应，若需重新取得须按协议 `init` 重跑合成接力（本任务不做）。
2. **环境坑登记（冻结前已写入，属既有脏基线，不计入本任务 diff）**：`双平台开发环境流程书.md` §8 新增两条——协议证据行尾不能交给 `autocrlf`；协议快照会哈希全部 ignored 文件（本机实测 9.4 ms/文件，清理前 110k 文件 ⇒ 约 17 分钟/次，清理可再生缓存后 22.6k ⇒ 约 3.5 分钟/次）。
3. **执行侧与验收侧同源**：mode=`dsh-claude`，controller=executor=acceptor=dsh，reviewer=claude（runner 拉起，只读 tool-policy）。按协议，本档保证上限为 `provisional`（无独立 acceptor），执行报告与验收单必须写明这一点。
4. **真机项**：本任务不安装、不联网；设备侧证据一律 `NOT RUN`，不得因「JVM 测试通过」宣称真机已验证。

