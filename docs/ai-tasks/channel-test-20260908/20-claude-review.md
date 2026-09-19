# channel-test-20260908 — Claude 第二意见

状态：`changes-needed`

## 通道声明

- 只读取隔离邮箱中的合成任务书；
- 未读取或修改项目源码；
- 未运行测试；
- 无权限拒绝；
- 报告通过 Claude CLI 结构化结果返回，由 Codex写回本文件。

## 主要 findings

1. **High — Unicode 坐标必须统一。** Kotlin `String` 使用 UTF-16 索引；出现位置枚举、范围重建和重叠计算应使用 code point，只在最终字符串边界操作前转换。反例：`a😀b😀c` 中直接把 code-point 下标传给 `substring` 会切开 surrogate pair。
2. **High — 保护项检查必须覆盖完整组合锚点。** 不仅 `original`，`before + original + after` 的完整区间都不能与 URL/数字哨兵重叠；与哨兵相邻但区间不相交可以允许。
3. **High — 插入和 noop 不应借上下文取得新权限。** `original` 为空仍只走严格插入偏移；noop继续要求所有候选位置通过保护项与类别检查。是否把多余上下文字段直接判无效，由 Codex冻结契约。
4. **Medium — 组合锚点必须枚举重叠出现。** `ababa` 中 `before=a, original=ba` 组成的 `aba` 出现两次，不能因只找首个匹配而错误接受。
5. **Medium — 重建范围后再统一检查 offset、排序和重叠。** 模型 offset只能交叉验证，不能消歧；与唯一重建范围不一致应失败。
6. **Medium — JSON空值语义必须固定。** omitted、`null` 和空串如何归一化不能依赖 Gson偶然行为；两侧上下文都缺失时继续 `ambiguous + changed` 失败关闭。
7. **Medium — 上下文必须有 code-point 上限。** 建议每侧固定短上限，例如 32 code points；超长上下文、跨块和吞入保护项都失败。
8. **Low — 匹配必须精确连续。** 不折叠大小写、不 trim、不做 Unicode normalization 或模糊匹配；一侧上下文不能隐含“必须位于章首/章尾”。

## 建议测试矩阵

- 正例：单侧前文唯一、单侧后文唯一、emoji / 补充平面字符、与哨兵相邻但不重叠。
- 反例：组合锚点仍重复或重叠、offset不一致、上下文覆盖哨兵、插入携带上下文、两侧都缺失、上下文超限、大小写或空白归一化后才匹配。

## 原始结论修正

Claude原报告使用了“byte-for-byte literal”表述；Codex修正为“精确 Unicode/UTF-16 序列匹配”，因为 Kotlin `String` 不是按原始字节比较。其余结论仍须在真实源码和反例中独立验证。

