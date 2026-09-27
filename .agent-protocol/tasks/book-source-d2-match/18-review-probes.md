# book-source-d2-match — reviewer 执行探针

- 冻结 HEAD：25b289adb2060da421e437e9766727dac783a0c2
- 冻结快照 SHA-256：1eb8adfbc8d331e61756f7fe145ca64b432f9c456d795b994f93bcecbd2a474c
- 冻结快照定义域：Git HEAD/分支/状态/工作树与暂存 diff，协议目录除 tasks/ 的逐文件哈希，再加任务书、命令清单、执行报告、项目 .gitignore 和 AGENTS.md 的逐文件 SHA-256；任务证据目录其余文件不属于此摘要。
- Git 工作树 SHA-256：51796de6b9eabcb8e345252f7a26f738bbe7cff7aa9db950437751c479e016a2
- 协议目录（除 tasks/）SHA-256：1a11e32bf0a49f265677142a9eefc2a43106d62a9d2dfba8de32e2c55423dedc
- 任务书 SHA-256：6f975549a88f8d6903fb4a3b1f1e862cd7ca7a6234e937fbc997eed6feb2c996
- 执行报告 SHA-256：037b8e86482fb49c22ba8c01ded2c2ab3a93edba1667fbf271f7306758556dff
- .gitignore SHA-256：bca566443ef22d1bd4cafdbf0a1a5b6e920ee1d41ea7e04d06438762c3752c53
- ignored 正式文件数 / 集合内容 SHA-256：22572 / 4b705f11ced4c2f98cf6474332c3e1b0934de3c51ef0fa1e10651923afa6e3ba
  - ignored 集合公式：Git 枚举的 ignored 文件逐项取项目相对路径与原始文件 SHA-256，按路径排序，形成 path=sha256 行，以 LF 连接后计算 UTF-8 SHA-256；排除 .agent-protocol/tasks/，协议目录其余内容另行散列。
  - 当前 Git 工作树摘要定义域：repository、HEAD、branch、普通 status、工作树 diff、暂存 diff、ignored 路径与逐文件内容摘要；忽略任务证据目录。
  - 当前复合快照：对按 git、protocol、task、commands、execution、gitignore、agents 顺序排列的 key=value 行，以 LF 连接后计算 UTF-8 SHA-256。旧账本可能使用不同定义域，不能由现行值倒推。
- AGENTS.md SHA-256：cbfac3215935054068076288ba56c41746addb0c64dc15d0a864dd8c516fe459
- 命令清单 SHA-256：cca8d906639d8108668a5d6409f71f6d57b4ddfc0a7ec08c959d7ee6f3fb634c
- review bundle SHA-256：ec997bf28173b7620472bcc91e6318408bd47a8bfe50542cd865d95c2ad0e698
- applied_bundle_hash：ec997bf28173b7620472bcc91e6318408bd47a8bfe50542cd865d95c2ad0e698
- gate ledger SHA-256：84299ca328cd56981e2ca6a5ab27dc957b875761ff3c123a2922ac6cdf783493
- 请求角色 / 轮次：controller / controller-pending
- 账本语义：只记录 runner 事实；缓存命中不代表 reviewer/acceptor 已独立判断
- 真实工作树前后状态：stable
- 文件系统强制：worktree-copy + 前后快照核对（不是内核级只读沙箱）
- 网络强制：none（配置仅声明 deny，runner 未宣称内核级断网）
- 总结：PASS

## d2-whitelist-diff-check — PASS

- 来源：EXECUTED
- cache_key：901d605e59b9b91e7ad99eae4a74369b798cc3c325d52ebbc32b6f456dcc9454
- 命令：argv[] digest 228ad39dbaf25ec2668656616c04984b1f2607b709f96681668e9b44bb6bfcac
- 退出码：0
- 耗时：0.76 秒

```text

--- stderr ---
warning: in the working copy of 'app/src/main/java/io/legado/app/help/book/diagnostics/BookSourceDiagnostics.kt', LF will be replaced by CRLF the next time Git touches it
```

## d2-whitelist-status — PASS

- 来源：EXECUTED
- cache_key：813d3441143c1d37040e596749bb453df05f7ec35423fc3bb02f5b26580f37d5
- 命令：argv[] digest 2741adc984dcfa3a7c7d53e54f6ca2c38ba7a2719b9de9f4203efd1efc2f871b
- 退出码：0
- 耗时：4.21 秒

```text
 M app/src/main/java/io/legado/app/help/book/ContentProcessor.kt
 M app/src/main/java/io/legado/app/help/book/diagnostics/BookSourceDiagnostics.kt
 M app/src/main/java/io/legado/app/model/Debug.kt
 M app/src/main/java/io/legado/app/model/webBook/BookContent.kt
?? app/src/main/java/io/legado/app/help/book/diagnostics/DiagnosticIdentity.kt
?? app/src/test/java/io/legado/app/help/book/diagnostics/DiagnosticIdentityContractTest.kt
```

## d2-whitelist-diffstat — PASS

- 来源：EXECUTED
- cache_key：049d35c63912c909fe849586ce59c3645315b0b6d336d1c3cfba7d1823cf0d45
- 命令：argv[] digest d5707c632ff586b6dc060f88e05459954e54a509ce999f9f1f1d5daf9ab15dbb
- 退出码：0
- 耗时：0.07 秒

```text
 .../io/legado/app/help/book/ContentProcessor.kt    |   3 +-
 .../help/book/diagnostics/BookSourceDiagnostics.kt | 132 +++++++++++++++++++--
 app/src/main/java/io/legado/app/model/Debug.kt     |  41 ++++++-
 .../io/legado/app/model/webBook/BookContent.kt     |   7 +-
 4 files changed, 163 insertions(+), 20 deletions(-)
--- stderr ---
warning: in the working copy of 'app/src/main/java/io/legado/app/help/book/diagnostics/BookSourceDiagnostics.kt', LF will be replaced by CRLF the next time Git touches it
```
