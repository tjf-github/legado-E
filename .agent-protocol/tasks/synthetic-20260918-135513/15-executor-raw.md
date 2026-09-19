# synthetic-20260918-135513 — executor 子代理调用回执

这是 runner 记录的工具元数据，用来审计「谁在什么时候用什么命令拉起了谁」。
它能让人去复核真实会话，但**不能**证明控制代理没有代写：控制代理对证据文件有写权限。

- 角色：executor
- 路由值：dsh
- 提供方家族：dsh
- 入口代理（自述）：dsh
- 同品牌新鲜实例要求：false
- 新鲜实例核验：not-required
- 命令：C:\Users\huixu\AppData\Roaming\npm\dsh.cmd --profile headless "读取 D:\vsproject\legado-E\.agent-protocol\tasks\synthetic-20260918-135513\14-executor-prompt.md 并严格按其中的指令执行；除该文件指示的输出外不要做任何其它事。"
- 提示文件：14-executor-prompt.md
- 提示投递：argument
- 消息捕获：stdout
- 捕获策略：stdout
- 捕获文本字符数：649
- 捕获文本 UTF-8 字节数：1057
- 捕获文本 sha256：b9794da3afea5b332e81071303376f95d3fe587574a95bc2f68411b22d458f05
- 会话标识：无（该 CLI 未提供可复核的会话号）
- 只读强制：none
- 实际路由：未声明：dsh 没有对外公开的路由/模型环境变量，runner 无从观测它实际打到哪里。这里如实留空，回执会写「该提供方未声明路由变量」——不猜。
- 模型：未指定（用 CLI 自己的默认模型）
- 凭据：未声明
- 工作目录：D:\vsproject\legado-E
- 开始时间（UTC）：2026-09-18T05:55:55Z
- 耗时（秒）：30.45
- 退出码：0
- 结果：ok
- 失败原因：-
- 证据文件：10-execution.md
- 证据写入方式：subagent
- 证据 sha256：8e4a0d49f97c4b71fa2a02b5f02616536ea77a29feb872e41cd112fec21a81b7
- 复审快照：not-required
- 复审快照 sha256：-
- 上一轮归档：-
- 协议指纹：9c5c705cba2efde46ec3a03e463e0d6ebe6491d24930687adecca4b6d1a6289d

## 原始 stdout

````text
Executor 角色执行完成，产出仅写入授权的 `10-execution.md`。

## 执行事实

- **读取**：`00-task.md`、`14-executor-prompt.md`、`.agent-protocol/README.md`、`CURRENT.md`、`PROJECT.md`（均在任务书白名单内）；未读 `.channel-test.json`、`20-review.md`、`30-acceptance.md`、业务文件。
- **唯一写入**：`.agent-protocol/tasks/synthetic-20260918-135513/10-execution.md`。
- **未做**：无 shell/构建/测试命令；未提权、未提交、未推送、未切分支、未改业务文件、未触碰其他角色原文。
- **状态**：`provisional`（dsh 执行侧上限）。

## 写入的严格字段行

```
nonce_digest: 0241af01e7f0b0f8eeeeeeeeeeeeeeeef919c47d2e9c325deeeeeeeeeeeeeeee
```

字段为独立一行、`字段名: <64 位小写 hex>`、无空格；报告内未出现完整 nonce 明文、未展示半串、未展开拼接过程，未运行项（`verify-channel`、`role-verify`、业务门禁、冻结复审命令）均如实标为 `NOT RUN`，并列出两处不确定项及其所需条件。

````

## 原始 stderr

````text

````