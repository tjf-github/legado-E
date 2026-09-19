# 三方协作协议迁移清单

本目录的角色/状态/证据模式可以迁移，legado-E 的项目事实不能直接迁移。

## 迁移到新项目

1. 在新项目根 `AGENTS.md` 加入稳定入口：开工前读取 `docs/ai-tasks/README.md` 与 `docs/ai-tasks/CURRENT.md`。
2. 复制 `README.md`、`CURRENT.md` 和 `_template/`；不要把已有任务证据目录当模板复制。
3. 把 `CURRENT.md` 初始化为 `task_id: none`、`evidence_dir: none`、`state: idle`、`owner: codex-controller`。
4. 逐项重填并人工核对：项目根路径、开发/发布分支、构建与测试命令、敏感文件、外部模型目的地、preset 名称、读写权限、提交/发布规则。
5. 做一次只含合成数据的 nonce 通道测试；分别确认 DSH 父代理、Codex 子代理、Claude 子代理的真实工具结果。注册成功、HTTP 200 或父代理复述 nonce 均不算通过。
6. Codex 控制端核对实际工作树无越界改动后，才把新项目协议标为 active。

## 可迁移不变量

- Codex 控制端唯一最终验收；DSH 最多到 `review`；Claude 默认只读。
- 当前任务只认一个机器可读指针；关闭任务必须清空指针。
- 一角色一证据文件；原始结果追加保留，其他角色不得覆盖。
- 任务提示只传角色、文件名、输出格式和权限边界；文件被外部模型读取仍属于外发，必须先限定白名单与数据范围。
- 项目路径、分支、命令、模型和权限一律视为环境变量，不属于可直接复制的不变量。

