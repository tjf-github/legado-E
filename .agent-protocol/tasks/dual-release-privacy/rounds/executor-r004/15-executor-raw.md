# dual-release-privacy — executor 子代理调用回执

这是 runner 记录的工具元数据，用来审计「谁在什么时候用什么命令拉起了谁」。
它能让人去复核真实会话，但**不能**证明控制代理没有代写：控制代理对证据文件有写权限。

- 角色：executor
- 路由值：dsh
- 提供方家族：dsh
- 入口代理（自述）：codex
- 同品牌新鲜实例要求：false
- 新鲜实例核验：not-required
- 命令：C:\Users\huixu\AppData\Roaming\npm\dsh.cmd --profile headless "读取 D:\vsproject\legado-E\.agent-protocol\tasks\dual-release-privacy\14-executor-prompt.md 并严格按其中的指令执行；除该文件指示的输出外不要做任何其它事。"
- 提示文件：14-executor-prompt.md
- 提示投递：argument
- 消息捕获：stdout
- 捕获策略：stdout
- 捕获文本字符数：0
- 捕获文本 UTF-8 字节数：0
- 捕获文本 sha256：-
- 会话标识：无（该 CLI 未提供可复核的会话号）
- 只读强制：none
- 实际路由：未声明：dsh 没有对外公开的路由/模型环境变量，runner 无从观测它实际打到哪里。这里如实留空，回执会写「该提供方未声明路由变量」——不猜。
- 模型：未指定（用 CLI 自己的默认模型）
- 凭据：未声明
- 工作目录：D:\vsproject\legado-E
- 开始时间（UTC）：2026-09-19T15:12:23Z
- 耗时（秒）：300.03
- 退出码：0
- 结果：timeout
- 失败原因：timeout
- 证据文件：10-execution.md
- 证据写入方式：not-written
- 证据 sha256：-
- 复审快照：not-required
- 复审快照 sha256：-
- 上一轮归档：rounds/executor-r003
- 协议指纹：3a239c56c05a3af36588a2297336d72995633cefbcd8076b5ff30bafa8560cf0

## 原始 stdout

````text

````

## 原始 stderr

````text

````