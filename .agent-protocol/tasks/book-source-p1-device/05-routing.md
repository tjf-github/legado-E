# book-source-p1-device — 路由与换挡记录

## 初始路由

- mode：dsh-claude
- assurance：standard
- route_revision：8
- 角色映射：controller=dsh；executor=dsh；reviewer=claude；acceptor=dsh
- 选择理由：由 CURRENT.md 继承（mode=dsh-claude，route_revision=8）；人类在 DSH 窗口直接指派，入口规则要求 controller 品牌等于人类启动的 agent 品牌。

## 本任务的性质（不换挡，只记录）

- 本任务是**验证轮**：不产生生产代码改动，只产出设备证据与判定。任务档 standard 允许「独立 reviewer + 冻结快照、最多两轮」，与 P1 首轮/第三轮返工同为 standard 档。
- 窗口修正：依据 `book-source-p1-rework-r3/30-acceptance.md` §五 F-0003，Android 调试页 finish 即结束诊断会话，
  S4/S7「会话存活时阅读」改用 **Web 端调试 socket**（不经 Activity finish）与 PC 侧夹具服务的可控延迟/中断来构造窗口。
  这是执行方法的选择，不改变被测代码路径：`Debug.diagnosticsFor` / `ContentProcessor` / `BookSourceDebugWebSocket` 都是产品代码本身。
- 执行侧 runner 封套：dsh-claude 下 controller=executor=acceptor=dsh，执行不由 runner 拉起（与 P1 前两轮同一事实，如实记录，不冒充已绑定）。

## 换挡记录

每次追加：时间、旧模式、新模式、原因、最后已验证状态、未完成项、新 controller。不得删除旧记录。
