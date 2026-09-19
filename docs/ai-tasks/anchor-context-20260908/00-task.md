# anchor-context-20260908 — 唯一精确上下文锚点契约

状态：`ready-for-claude-review`。轮次上限：5；超过 5 轮仍未闭环由 Codex 接手。

## 目标

只处理当前可复现的 `ANCHOR ambiguous + changed`：先用合成 JVM 测试冻结“短前后文 + original”只能在块内形成唯一精确连续匹配时才允许定位的契约；测试契约经 Codex 验收后，才考虑最小协议修正。

## 已知事实

- 当前 `AiEdit` 只有 `start/end/original/replacement/kind`；校验器对非空 `original` 枚举所有重叠 code-point 出现，重复且 changed 时失败关闭。
- 模型偏移不能用于重复锚点消歧。
- `ambiguous + noop` 已有独立安全兼容，不得扩大。
- `payload_json_syntax` 在严格 Gson + EOF 诊断后未复现；本任务禁止宽松 JSON、容错解析和 Provider 猜测性修复。
- 阶段 E 仍为 `review / NOT READY`；本任务不含真机、提交、推送或发布。

## Claude 可读白名单

- `docs/ai-tasks/anchor-context-20260908/00-task.md`
- `docs/AI正文净化/AI正文净化-锚点兼容与发布验收计划书.md`
- `app/src/main/java/io/legado/app/help/ai/AiProtocol.kt`
- `app/src/main/java/io/legado/app/help/ai/AiOutputValidator.kt`
- `app/src/main/java/io/legado/app/help/ai/OpenAiCompatibleProvider.kt`
- `app/src/main/java/io/legado/app/help/ai/AiCacheKey.kt`
- `app/src/test/java/io/legado/app/help/ai/AiOutputValidatorTest.kt`
- `app/src/test/java/io/legado/app/help/ai/AiNoChangeTest.kt`
- 后续由 Codex 在本任务目录记录的 `20-claude-review.md` 与 `30-codex-acceptance.md`

禁止读取或复述真实章节、书名、URL、API Key、Token、Cookie、未脱敏日志、响应体、缓存、签名材料或任何白名单外文件。禁止写工作区、运行命令/测试、联网搜索、提交、推送、发布、安装或操作设备。

## 待冻结的测试契约

候选新增可选字段 `prefix` / `suffix`，语义均为精确 Unicode 字符序列、只用于定位；实际替换范围仍仅是 `original`。

必须覆盖：

1. 原 `original` 重复，单侧或双侧短上下文使完整组合锚点唯一时可定位。
2. 完整组合锚点仍重复（含重叠出现）、上下文伪造/不连续、大小写/空白/Unicode normalization 后才相等时拒绝。
3. emoji、补充平面汉字、组合字符、零宽字符和换行按精确序列处理，不切 surrogate pair。
4. 每侧上下文最多 32 code points；超限拒绝。两侧均 omitted / null / empty 时不得改变现有行为。
5. 上下文只给 `ambiguous + changed` 新定位能力；unique original、noop、空 original 插入不得借上下文绕过既有分支。
6. 完整 `prefix + original + suffix` 定位区间不得包含或触碰哨兵；仅相邻且不相交是否允许，需给出明确、可实现的边界。
7. 定位后仍统一执行编辑类别、replacement 哨兵语法、排序、重叠和结构校验；模型 `start/end` 不得消歧。
8. omitted、JSON null、empty string 的解析与归一化语义必须明确，并保持严格 JSON。
9. 一条非法 sibling 使整块/整章失败关闭，不得只丢弃非法 changed edit。

## 第一轮 Claude 交付

只审查上述测试契约，返回：

- High/Medium/Low findings；
- 必须先红的正例/反例清单（给出合成文本和字段，但不得含真实正文）；
- 对 `prefix/suffix` 空值、长度、哨兵边界、offset 与重叠规则的精确定义；
- 是否足以进入测试实现，结论只能是 `ready-for-tests` 或 `changes-needed`。

