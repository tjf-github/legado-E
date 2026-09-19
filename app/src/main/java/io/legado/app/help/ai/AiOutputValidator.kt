package io.legado.app.help.ai

import io.legado.app.utils.LogUtils

/**
 * 校验结果。失败一律携带稳定的 [AiFailureCode]，不含正文、响应体或密钥。
 */
sealed class AiValidationResult {
    data class Valid(val protectedText: String) : AiValidationResult() {
        override fun toString(): String = "Valid(protectedText=[REDACTED])"
    }
    data class Invalid(
        val code: AiFailureCode,
        val anchorDetail: AiAnchorFailureDetail? = null
    ) : AiValidationResult() {
        override fun toString(): String = "Invalid(code=$code, anchorDetail=$anchorDetail)"
    }
}

/**
 * 纯函数：校验并应用“局部编辑列表”。
 *
 * 模型偏移可能不准确，非空 original 只用块内唯一精确子串重建范围。
 * 不用模型偏移消解重复锚点，避免数错位置后碰巧匹配同字而改错句子。
 * 重复锚点只在模型给出只读短前后文、且“前文 + original + 后文”在块内唯一且三者连续时才可定位；
 * 未给上下文、上下文超长、组合不存在或仍不唯一一律退回 ANCHOR + ambiguous(changed) 失败关闭。
 * 无变化编辑也必须经过类别与保护项校验；重复锚点的 noop 可安全丢弃，真实编辑仍失败关闭。
 */
object AiOutputValidator {
    private const val MAX_EDITS = 128
    private const val MAX_TEXT_EDIT_CORE = 12
    private const val DENOISE_MAX_NOISE = 2
    private const val DENOISE_MAX_ORIGINAL = 16

    /** 反和谐“删除插入噪声”白名单：普通/不换行/表意空格、零宽字符、断词用 ASCII 符号与中点。
     * 只允许移除这些有界类别的噪声；字母核心序列必须与替换结果逐字一致。 */
    internal val denoiseNoise: Set<Int> = buildSet {
        addAll("*_-~.".map { it.code })
        addAll("·•・".map { it.code })
        add(' '.code); add(0x00A0); add(0x3000)
        add(0x200B); add(0x200C); add(0x200D); add(0xFEFF)
    }

    fun validateAndApply(
        chunk: AiTextChunk,
        output: AiChunkOutput,
        knownSentinels: Set<String>
    ): AiValidationResult {
        fun invalid(code: AiFailureCode, anchorDetail: AiAnchorFailureDetail? = null) =
            AiValidationResult.Invalid(code, anchorDetail)
        // 脱敏诊断：只记录是哪类 kind 违规，绝不记录正文/响应体/编辑内容。
        fun rejectEditKind(detail: String): AiValidationResult {
            LogUtils.d("AiFailure", "ai-editkind detail=$detail")
            return invalid(AiFailureCode.EDIT_KIND)
        }
        fun validateEditSemantics(edit: AiEdit): AiValidationResult? = when (edit.kind) {
            AiEditKind.typo, AiEditKind.anti_theft -> {
                val originalCore = coreCharacters(edit.original)
                val replacementCore = coreCharacters(edit.replacement)
                // 文本编辑只作用于文字：核心字符数为字母，数量必须相等、单项不超过上限。
                if (originalCore.isEmpty() || replacementCore.isEmpty() ||
                    originalCore.size != replacementCore.size ||
                    originalCore.size > MAX_TEXT_EDIT_CORE || replacementCore.size > MAX_TEXT_EDIT_CORE
                ) invalid(AiFailureCode.CORE_LENGTH)
                // 只允许纯字母：数字、标点、符号、控制字符或可构造 HTML 标记的字符一律拒绝。
                else if (!isAllLetters(edit.original) || !isAllLetters(edit.replacement)) {
                    rejectEditKind("typo_contains_non_letter")
                } else null
            }
            AiEditKind.denoise -> {
                if (!isValidDenoise(edit)) rejectEditKind("denoise") else null
            }
            AiEditKind.punctuation, AiEditKind.whitespace -> {
                // 只允许空白与标点：禁止插入符号/控制字符/私有区哨兵字符或构造成 HTML 的字符。
                if (!isPunctuationOrWhitespace(edit.original) || !isPunctuationOrWhitespace(edit.replacement)) {
                    rejectEditKind("punct_whitespace_has_text")
                } else null
            }
        }
        if (output.chunkId != chunk.id) return invalid(AiFailureCode.CHUNK_ID)
        if (output.finishReason != AiFinishReason.stop) return invalid(AiFailureCode.FINISH_REASON)
        if (output.edits.size > MAX_EDITS) return invalid(AiFailureCode.TOO_MANY_EDITS)

        val totalCp = chunk.text.codePointCount(0, chunk.text.length)
        var previousEnd = 0
        // (charStart, charEnd, edit)
        val charRanges = mutableListOf<Triple<Int, Int, AiEdit>>()
        for (edit in output.edits) {
            // 空占位不是合法编辑；无错章节应返回空 edits 列表。
            if (edit.original.isEmpty() && edit.replacement.isEmpty()) {
                return rejectEditKind("empty_placeholder")
            }
            val cpRange = if (edit.original.isNotEmpty()) {
                val occurrences = codePointRanges(chunk.text, edit.original)
                if (occurrences.isEmpty()) {
                    val effect = if (edit.original == edit.replacement) AiEditEffect.noop else AiEditEffect.changed
                    return invalid(AiFailureCode.ANCHOR,
                        AiAnchorFailureDetail(AiAnchorFailureKind.missing, effect))
                }
                if (occurrences.size > 1) {
                    if (edit.original == edit.replacement) {
                        // 重复锚点仅在严格 noop 时可丢弃；每个可能位置都必须远离保护项，
                        // 且编辑类别本身仍须合法。它不参与排序/重叠，因为不会应用任何范围。
                        if (containsSentinelSyntax(edit.replacement, knownSentinels) ||
                            occurrences.any { (start, end) ->
                                val charStart = chunk.text.offsetByCodePoints(0, start)
                                val charEnd = chunk.text.offsetByCodePoints(0, end)
                                containsOrTouchesSentinel(chunk.text, charStart, charEnd, knownSentinels)
                            }
                        ) return invalid(AiFailureCode.SENTINEL)
                        validateEditSemantics(edit)?.let { return it }
                        continue
                    }
                    // 重复锚点的真实编辑：只允许用模型给出的只读上下文锚点消歧，且组合必须唯一。
                    // 任何不满足（无上下文/超长/不连续/仍不唯一/不是 original）都退回原有的失败关闭，
                    // 绝不允许落到上面的 noop 丢弃分支把已定位的真实编辑静默丢掉。
                    val contextRange = resolveDisambiguatedRange(chunk.text, edit)
                        ?: return invalid(
                            AiFailureCode.ANCHOR,
                            AiAnchorFailureDetail(AiAnchorFailureKind.ambiguous, AiEditEffect.changed)
                        )
                    contextRange
                } else {
                    occurrences.single()
                }
            } else {
                resolveInsertionRange(edit, totalCp) ?: return invalid(AiFailureCode.RANGE)
            }
            val (cpStart, cpEnd) = cpRange
            if (cpStart < previousEnd) return invalid(AiFailureCode.OVERLAP)
            if (cpEnd < cpStart) return invalid(AiFailureCode.RANGE)
            previousEnd = cpEnd

            val charStart = chunk.text.offsetByCodePoints(0, cpStart)
            val charEnd = chunk.text.offsetByCodePoints(0, cpEnd)
            if (charStart < 0 || charEnd > chunk.text.length) return invalid(AiFailureCode.RANGE)

            if (containsOrTouchesSentinel(chunk.text, charStart, charEnd, knownSentinels) ||
                containsSentinelSyntax(edit.replacement, knownSentinels)
            ) {
                return invalid(AiFailureCode.SENTINEL)
            }

            validateEditSemantics(edit)?.let { return it }
            if (edit.original != edit.replacement) {
                charRanges += Triple(charStart, charEnd, edit)
            }
        }

        val result = StringBuilder(chunk.text)
        charRanges.asReversed().forEach { (start, end, edit) ->
            result.replace(start, end, edit.replacement)
        }
        return AiValidationResult.Valid(result.toString())
    }

    /**
     * 重复 original 的真实编辑的唯一放行路径：组合锚点必须唯一，且解析出的范围必须与 original 逐字相等。
     * 任一不满足返回 null，调用方按 ANCHOR + ambiguous(changed) 失败关闭；模型 start/end 不参与。
     */
    private fun resolveDisambiguatedRange(text: String, edit: AiEdit): Pair<Int, Int>? {
        val contextRange = resolveContextAnchor(text, edit) ?: return null
        // 组合唯一不等于 original 一致：上下文伪造但组合恰好落点唯一时，落点必须是逐字相同的 original。
        if (!rangeEqualsOriginal(text, contextRange, edit.original)) return null
        return contextRange
    }


    /**
     * 用只读上下文锚点消解重复 original：要求 before + original + after 在块内恰好出现一次，
     * 且它确实是当前块从 [cpStart, cpEnd) 起的精确连续子串；由组合串起点 + 前文 code point 数推出 original 范围。
     * 未提供有效上下文、上下文超长、组合不存在或不唯一一律返回 null（调用方失败关闭）。
     * 模型 start/end 完全不参与本路径。返回 null 时不携带任何正文。
     */
    private fun resolveContextAnchor(text: String, edit: AiEdit): Pair<Int, Int>? {
        val before = edit.contextBefore ?: return null
        val after = edit.contextAfter ?: return null
        val beforeCp = before.codePointCount(0, before.length)
        val afterCp = after.codePointCount(0, after.length)
        // 上下文长度按 code point 计，任一超出上限即拒绝。
        if (beforeCp > AiEdit.MAX_ANCHOR_CONTEXT || afterCp > AiEdit.MAX_ANCHOR_CONTEXT) return null
        // 必须两侧都给出（都非空）：只给一侧时，同一 original 的两个不同出现可能各自满足
        // “组合唯一 + 一侧紧邻”，从而互相矛盾却都被接受；两侧齐备才是可核对的确定性锚点。
        if (beforeCp == 0 || afterCp == 0) return null
        val anchor = before + edit.original + after
        // 组合必须在块内恰好出现一次，且与 original 在块内的重复次数无关。
        val anchorOccurrences = codePointRanges(text, anchor)
        if (anchorOccurrences.size != 1) return null
        val cpStart = anchorOccurrences.single().first
        // 组合串必须逐字落在同一位置，且 original 段必须与 original 精确相等。
        val anchorCharStart = text.offsetByCodePoints(0, cpStart)
        if (anchorCharStart < 0 || !text.startsWith(anchor, anchorCharStart)) return null
        val originalCpStart = cpStart + beforeCp
        val originalCpEnd = originalCpStart + edit.original.codePointCount(0, edit.original.length)
        val originalCharStart = text.offsetByCodePoints(0, originalCpStart)
        val originalCharEnd = text.offsetByCodePoints(0, originalCpEnd)
        if (originalCharStart < 0 || originalCharEnd > text.length) return null
        if (text.substring(originalCharStart, originalCharEnd) != edit.original) return null
        // 组合必须真的“紧邻”：声明的前后文要落在这个出现位置的左右两侧；任一不成立即拒绝。
        if (!text.startsWith(before, originalCharStart - before.length)) return null
        if (!text.startsWith(after, originalCharEnd)) return null
        return originalCpStart to originalCpEnd
    }

    /** 解析出的区间是否与 original 逐字相等（char 级等值，不依赖 code point 计数）。 */
    private fun rangeEqualsOriginal(text: String, range: Pair<Int, Int>, original: String): Boolean {
        val charStart = text.offsetByCodePoints(0, range.first)
        val charEnd = text.offsetByCodePoints(0, range.second)
        if (charStart < 0 || charEnd > text.length) return false
        return text.substring(charStart, charEnd) == original
    }

    /**
     * 解析单条编辑的实际范围（Unicode code point 区间）。
     * 非空 original：只接受块内唯一精确出现；重复锚点失败关闭。
     * 空 original（纯插入）：只能按模型偏移并做严格边界校验。
     * 返回 null 表示无法可靠定位，应由调用方整章失败。
     */
    private fun resolveInsertionRange(edit: AiEdit, totalCp: Int): Pair<Int, Int>? {
        if (edit.start < 0 || edit.end < edit.start || edit.end > totalCp) return null
        return edit.start to edit.end
    }

    /** 返回 needle 在 text 中所有出现的 (startCp, endCp) 区间，含重叠出现（每次按一个 Unicode code point 前进）。
     * 例如 text="人人人"、needle="人人" 返回 {(0,2),(1,3)}。empty 时返回空列表。 */
    private fun codePointRanges(text: String, needle: String): List<Pair<Int, Int>> {
        if (needle.isEmpty()) return emptyList()
        val out = mutableListOf<Pair<Int, Int>>()
        var fromIdx = 0
        while (fromIdx < text.length) {
            if (text.startsWith(needle, fromIdx)) {
                val endIdx = fromIdx + needle.length
                if (endIdx <= text.length) {
                    out += (text.codePointCount(0, fromIdx) to text.codePointCount(0, endIdx))
                }
            }
            fromIdx = text.offsetByCodePoints(fromIdx, 1)
        }
        return out
    }

    /**
     * 反和谐删除噪声合法判定：
     *  - original 首尾都必须是字母（噪声必须嵌入词语内部，不允许行首/行尾删除标点）；
     *  - 去掉噪声后的字母序列与 replacement 逐字一致（只删噪声，绝不动换成改变字母）；
     *  - replacement 必须是纯字母；original 只能由“字母 + 白名单噪声”组成；
     *  - 删除的噪声字符数有界；字母核心数有界。
     * 任一不满足即失败关闭。
     */
    private fun isValidDenoise(edit: AiEdit): Boolean {
        val original = edit.original
        val replacement = edit.replacement
        val op = codePointsOf(original)
        val rp = codePointsOf(replacement)
        if (op.isEmpty() || rp.isEmpty()) return false
        // 噪声必须嵌入词语内部：首尾都必须是字母。
        if (!Character.isLetter(op.first()) || !Character.isLetter(op.last())) return false

        val originalLetters = op.filter { Character.isLetter(it) }
        val originalNoise = op.filterNot { Character.isLetter(it) }
        if (originalNoise.isEmpty()) return false                  // 没有可删除的噪声
        if (originalNoise.size > DENOISE_MAX_NOISE) return false    // 噪声数量有界
        if (originalLetters.size > DENOISE_MAX_ORIGINAL) return false
        // original 只能由“字母 + 白名单噪声”组成，不能混入任意符号/数字/控制。
        if (op.any { !Character.isLetter(it) && it !in denoiseNoise }) return false
        // replacement 必须纯字母，且与 original 的字母序列逐字一致（只删噪声，绝不动字母）。
        if (rp.any { !Character.isLetter(it) }) return false
        if (!originalLetters.toIntArray().contentEquals(rp.toIntArray())) return false
        return true
    }

    private fun coreCharacters(value: String): List<Int> = buildList {
        value.forEachCodePoint { codePoint ->
            if (Character.isLetterOrDigit(codePoint)) add(codePoint)
        }
    }

    // 文本编辑必须纯字母（不含数字/标点/符号/控制/私有区哨兵字符）。
    private fun isAllLetters(value: String): Boolean =
        value.isNotEmpty() && value.allCodePoints(Character::isLetter)

    // 空白：isWhitespace（控制空白，如 tab/换行）+ isSpaceChar（空格分隔符/行/段分隔符，含 NBSP）。
    // 标点：Unicode P 大类（含 CJK 标点）。
    private fun isPunctuationOrWhitespace(value: String): Boolean {
        if (value.isEmpty()) return true
        return value.allCodePoints {
            it != AiTextChunker.SENTINEL_START.code &&
                it != AiTextChunker.SENTINEL_END.code &&
                (Character.isWhitespace(it) || Character.isSpaceChar(it) ||
                    Character.getType(it) in punctuationTypes)
        }
    }

    private inline fun String.forEachCodePoint(action: (Int) -> Unit) {
        var index = 0
        while (index < length) {
            val codePoint = codePointAt(index)
            action(codePoint)
            index += Character.charCount(codePoint)
        }
    }

    /** minSdk 21 安全的 code point 列表（替代 Android API 24 才提供的 String.codePoints()）。 */
    private fun codePointsOf(value: String): List<Int> {
        val result = mutableListOf<Int>()
        value.forEachCodePoint { result.add(it) }
        return result
    }

    private inline fun String.allCodePoints(predicate: (Int) -> Boolean): Boolean {
        var index = 0
        while (index < length) {
            val codePoint = codePointAt(index)
            if (!predicate(codePoint)) return false
            index += Character.charCount(codePoint)
        }
        return true
    }

    private val punctuationTypes = setOf(Character.CONNECTOR_PUNCTUATION.toInt(),
        Character.DASH_PUNCTUATION.toInt(), Character.START_PUNCTUATION.toInt(),
        Character.END_PUNCTUATION.toInt(), Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
        Character.FINAL_QUOTE_PUNCTUATION.toInt(), Character.OTHER_PUNCTUATION.toInt())

    private fun containsSentinelSyntax(value: String, sentinels: Set<String>): Boolean =
        value.contains(AiTextChunker.SENTINEL_START) ||
            value.contains(AiTextChunker.SENTINEL_END) ||
            sentinels.any(value::contains)

    private fun containsOrTouchesSentinel(
        text: String,
        start: Int,
        end: Int,
        sentinels: Set<String>
    ): Boolean = sentinels.any { sentinel ->
        var index = text.indexOf(sentinel)
        while (index >= 0) {
            val sentinelEnd = index + sentinel.length
            if (start < sentinelEnd && end > index) return true
            index = text.indexOf(sentinel, sentinelEnd)
        }
        false
    }
}