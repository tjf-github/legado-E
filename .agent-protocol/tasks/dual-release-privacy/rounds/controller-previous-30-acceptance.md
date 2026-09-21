# dual-release-privacy — 最终验收

> **SUPERSEDED（2026-09-20）**：本验收针对已废止的 `.release=普通版 / .releaseS=共存版` 映射，当前不具有接受效力，只保留作历史证据。任务已按 normal/private 身份迁移回到 `rework`。

状态：`rework`（业务实现通过；协议执行证据未闭合）

- acceptor 运行实例：Codex（与 DSH executor、Claude reviewer 分离）。
- mode / assurance / route_revision：triad / high / 3。
- 实际业务 diff：`.github/workflows/test.yml` 无条件输出 `release + releaseS` 矩阵；`AppReleaseInfo.kt` 把 `BETA_RELEASES` 纳入 `isBeta()`；新增 `AppReleaseInfoTest.kt` 7 个回归测试。未改 AI 源码、AI 设置、签名、Manifest、版本号或发布目标。
- 执行证据核对：DSH 第一轮完成 YAML 改动并留下可验证报告；第二轮在超时前写入 Kotlin 修正与测试，但未更新报告；第三轮仅做报告对账仍超时。当前 `10-execution.md` 因而落后于工作树，`role-verify` 对顶层 executor 回执 FAIL。acceptor 不代写 executor 原文，不把该证据缺口伪装成通过。
- 独立复测：acceptor 在最终代码上运行 `:app:compileAppDebugKotlin :app:testAppDebugUnitTest :app:lintAppDebug --console=plain`，`BUILD SUCCESSFUL in 4m 54s`；收窄测试描述后又定向运行 `AppReleaseInfoTest`，7/7 通过，`BUILD SUCCESSFUL in 2m 45s`。最终测试 XML：tests=7、failures=0、errors=0、skipped=0。`git diff --check` 通过。
- 可见行为（静态链路）：beta 与正式发布均展开 `release`、`releaseS` 两个 build job；两者继续分别以 `release` / `releaseS` 命名。GitHub/Gitee 消费端均按 `appVariant == checkVariant` 精确过滤；`releaseS` 现在会选择 beta 端点，不再错误访问 latest 后过滤为空。
- NOT RUN：未推送 `main`、未触发 GitHub Actions、未创建/更新 Release、未安装 APK；因此远端真实双资产发布仍待下一次已授权发布由 CI 验证。

## 复审 findings 逐项处理

1. **旧执行报告与工作树矛盾（阻塞）**：事实成立；两次 DSH 对账均超时，协议证据未闭合，故本任务不能标 `accepted` 或执行 `close`。业务正确性由 acceptor 独立 diff 与门禁支撑，但不替代 executor 角色证据。
2. **任务白名单未扩围（复审误读）**：当前 `00-task.md` 第 30、31、34 行已在第二轮执行前纳入 `AppReleaseInfo.kt`、两个消费端与新测试；授权链存在。该误读不构成业务阻塞。
3. **测试不能绑定端点选择（中）**：核心回归由 `BETA_RELEASES.isBeta()` 直接断言覆盖；未为一个 URL 三元表达式抽取新产品抽象。将正式版测试改名并收窄描述为“资产解析为 OFFICIAL”，避免夸大覆盖。
4. **测试镜像消费端逻辑（低）**：保留为合成资产选择验证，但注释明确它是镜像、版本阈值只用于让资产进入候选集，不宣称绑定消费端完整实现。
5. **版本名解析（信息）**：与当前 CI 的 `VERSIONL`/`VERSION` 口径一致，保持测试钉住既有事实，不扩展本任务。
6. **releaseS 失败会阻断整次 beta（中低取舍）**：接受。用户要求每次发布同时有两个版本；任一版本失败就不发布不完整的一对，比静默只发普通版更符合目标。代价是 CI 构建工作量约翻倍。
7. **首轮 executor 越界读取（低）**：已披露；未涉及凭据或用户数据。第二轮扩围后相关源码均进入明确白名单。
8. **AI 封存文件状态**：`ai-stage-e-anchor-controlled/05-routing.md` 内容相对 HEAD 仅差 EOF 换行，无语义改动；该文件不属于本业务交付，后续提交必须排除。AI 源码与设置零改动。

## 最终判定

`rework`（仅协议证据债务；业务实现与本地门禁通过）

- 理由：最终代码满足用户目标，静态发布链、消费端选择与自动化门禁均已独立验证；但 high 任务要求的 executor 顶层可信回执因连续超时未闭合，`role-verify` 仍 FAIL，不能按项目协议标记 `accepted`。
- 唯一下一步：若用户授权提交/发布，先只提交三份业务文件与本任务协议证据、排除既有 `.claude/`、`bookshelf.json` 和 AI 任务 EOF 换行；随后发布时以 GitHub Actions 真实产生并上传两个 APK 作为最终远端证据。
- 提交 / 推送 / 发布状态：均未执行。
