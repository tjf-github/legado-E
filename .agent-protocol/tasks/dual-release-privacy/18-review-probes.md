# dual-release-privacy — reviewer 执行探针

- 冻结 HEAD：fd045f69b2bdadcba86d30b525fe1fd244edb9ce
- 冻结快照 SHA-256：1434f7b9447516d1a1588da54d23d0b53723b4c76ac5fa926243d5bd16b70463
- 命令清单 SHA-256：94096bec42d6f2dbac1c61799b15cb8f28ddd37b8241f9f5463f6c5f9fab5371
- 真实工作树前后状态：stable
- 文件系统强制：worktree-copy + 前后快照核对（不是内核级只读沙箱）
- 网络强制：none（配置仅声明 deny，runner 未宣称内核级断网）
- 总结：PASS

## workflow-diff-check — PASS

- 命令：D:\Git\cmd\git.exe diff --check -- .github/workflows/test.yml
- 退出码：0
- 耗时：0.14 秒

```text

```
