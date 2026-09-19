package io.legado.app.help.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiOutputValidatorTest {

    @Test
    fun appliesValidatedEditsFromEndToStart() {
        val chunk = AiTextChunk("chunk-0", "他走的很快！！", null)
        val output = AiChunkOutput(
            chunkId = chunk.id,
            edits = listOf(
                AiEdit(2, 3, "的", "得", AiEditKind.typo),
                AiEdit(5, 7, "！！", "！", AiEditKind.punctuation)
            )
        )

        val result = AiOutputValidator.validateAndApply(chunk, output, emptySet())
        assertEquals(AiValidationResult.Valid("他走得很快！"), result)
    }

    @Test
    fun acceptsSupplementaryPlaneLetterAsOneCoreCharacter() {
        val chunk = AiTextChunk("chunk-0", "𠀀", null)
        val output = AiChunkOutput(
            chunkId = chunk.id,
            edits = listOf(AiEdit(0, 1, "𠀀", "𠀁", AiEditKind.typo))
        )

        assertEquals(
            AiValidationResult.Valid("𠀁"),
            AiOutputValidator.validateAndApply(chunk, output, emptySet())
        )
    }

    @Test
    fun rejectsOverlapWrongAnchorAndWholeSentenceDeletion() {
        val chunk = AiTextChunk("chunk-0", "甲乙丙丁", null)
        val overlap = AiChunkOutput(
            chunk.id,
            listOf(
                AiEdit(0, 2, "甲乙", "戊己", AiEditKind.typo),
                AiEdit(1, 2, "乙", "庚", AiEditKind.typo)
            )
        )
        val wrongAnchor = AiChunkOutput(
            chunk.id,
            listOf(AiEdit(0, 1, "错", "戊", AiEditKind.typo))
        )
        val deletion = AiChunkOutput(
            chunk.id,
            listOf(AiEdit(0, 4, "甲乙丙丁", "", AiEditKind.typo))
        )

        assertTrue(AiOutputValidator.validateAndApply(chunk, overlap, emptySet()) is AiValidationResult.Invalid)
        assertTrue(AiOutputValidator.validateAndApply(chunk, wrongAnchor, emptySet()) is AiValidationResult.Invalid)
        assertTrue(AiOutputValidator.validateAndApply(chunk, deletion, emptySet()) is AiValidationResult.Invalid)
    }

    @Test
    fun rejectsTextInsidePunctuationEditAndSentinelMutation() {
        val protected = AiTextChunker.protect("编号 123456 结束", "validator")
        val sentinel = protected.sentinels.keys.single()
        val chunk = AiTextChunk("chunk-0", protected.protected, null)
        val textAsPunctuation = AiChunkOutput(
            chunk.id,
            listOf(AiEdit(0, 1, "编", "号", AiEditKind.punctuation))
        )
        val sentinelStart = chunk.text.codePointCount(0, chunk.text.indexOf(sentinel))
        val sentinelMutation = AiChunkOutput(
            chunk.id,
            listOf(
                AiEdit(
                    sentinelStart,
                    sentinelStart + sentinel.codePointCount(0, sentinel.length),
                    sentinel,
                    "123456",
                    AiEditKind.typo
                )
            )
        )

        assertTrue(
            AiOutputValidator.validateAndApply(chunk, textAsPunctuation, setOf(sentinel)) is AiValidationResult.Invalid
        )
        assertTrue(
            AiOutputValidator.validateAndApply(chunk, sentinelMutation, setOf(sentinel)) is AiValidationResult.Invalid
        )
    }

    @Test
    fun rejectsTruncationWrongChunkAndTooManyEdits() {
        val chunk = AiTextChunk("chunk-0", "甲", null)
        assertTrue(
            AiOutputValidator.validateAndApply(
                chunk,
                AiChunkOutput(chunk.id, emptyList(), finishReason = AiFinishReason.length),
                emptySet()
            ) is AiValidationResult.Invalid
        )
        assertTrue(
            AiOutputValidator.validateAndApply(
                chunk,
                AiChunkOutput("other", emptyList()),
                emptySet()
            ) is AiValidationResult.Invalid
        )
        val excessive = List(129) { AiEdit(0, 0, "", "", AiEditKind.whitespace) }
        assertTrue(
            AiOutputValidator.validateAndApply(
                chunk,
                AiChunkOutput(chunk.id, excessive),
                emptySet()
            ) is AiValidationResult.Invalid
        )
    }

    // ---- 偏移鲁棒定位：用唯一精确的 original 重建范围，容忍模型数不准的 start/end ----

    @Test
    fun rebuildsRangeFromUniqueOriginalWhenModelOffsetsAreWrong() {
        val chunk = AiTextChunk("chunk-0", "他走的很快", null)
        val output = AiChunkOutput(
            chunk.id,
            listOf(AiEdit(99, 100, "的", "得", AiEditKind.typo))
        )
        assertEquals(
            AiValidationResult.Valid("他走得很快"),
            AiOutputValidator.validateAndApply(chunk, output, emptySet())
        )
    }

    @Test
    fun rebuildsPunctuationRangeFromUniqueOriginalWhenOffsetsAreWrong() {
        val chunk = AiTextChunk("chunk-0", "很好！！", null)
        val output = AiChunkOutput(
            chunk.id,
            listOf(AiEdit(-5, -1, "！！", "！", AiEditKind.punctuation))
        )
        assertEquals(
            AiValidationResult.Valid("很好！"),
            AiOutputValidator.validateAndApply(chunk, output, emptySet())
        )
    }

    @Test
    fun rejectsRepeatedAnchorEvenWithMatchingModelOffsets() {
        val chunk = AiTextChunk("chunk-0", "甲甲乙乙", null)
        val output = AiChunkOutput(
            chunk.id,
            listOf(AiEdit(2, 3, "乙", "丙", AiEditKind.typo))
        )
        assertEquals(
            AiValidationResult.Invalid(
                AiFailureCode.ANCHOR,
                AiAnchorFailureDetail(AiAnchorFailureKind.ambiguous, AiEditEffect.changed)
            ),
            AiOutputValidator.validateAndApply(chunk, output, emptySet())
        )
    }

    @Test
    fun rejectsAmbiguousAnchorWhenModelOffsetsDoNotMatch() {
        val chunk = AiTextChunk("chunk-0", "甲甲乙乙", null)
        val output = AiChunkOutput(
            chunk.id,
            listOf(AiEdit(1, 2, "乙", "丙", AiEditKind.typo)) // 偏移窗是“甲”，不是“乙”
        )
        assertTrue(AiOutputValidator.validateAndApply(chunk, output, emptySet()) is AiValidationResult.Invalid)
    }

    @Test
    fun rejectsAnchorNotFound() {
        val chunk = AiTextChunk("chunk-0", "甲乙", null)
        val output = AiChunkOutput(chunk.id, listOf(AiEdit(5, 6, "不存在", "无", AiEditKind.typo)))
        assertTrue(AiOutputValidator.validateAndApply(chunk, output, emptySet()) is AiValidationResult.Invalid)
    }

    @Test
    fun rejectsOverlapViaReconstructedRanges() {
        val chunk = AiTextChunk("chunk-0", "甲乙丙", null)
        val output = AiChunkOutput(
            chunk.id,
            listOf(
                AiEdit(0, 2, "甲乙", "丁戊", AiEditKind.typo),
                AiEdit(1, 2, "乙", "己", AiEditKind.typo)
            )
        )
        assertTrue(AiOutputValidator.validateAndApply(chunk, output, emptySet()) is AiValidationResult.Invalid)
    }

    // ---- 唯一锚点搜索必须枚举 Unicode code point 边界上的重叠出现 ----

    @Test
    fun overlappingAnchorOccurrencesMustRemainAmbiguous() {
        // "人人人" 中锚点 "人人" 实际出现在 [0,2) 与 [1,3)（重叠）。必须枚举全部出现，
        // 重复锚点不接受模型范围消歧；旧实现按 idx+needle.length 前进会漏掉 [1,3)。
        val chunk = AiTextChunk("chunk-0", "人人人", null)
        val secondOccurrence = AiChunkOutput(chunk.id, listOf(AiEdit(1, 3, "人人", "仁仁", AiEditKind.typo)))
        assertEquals(
            AiValidationResult.Invalid(
                AiFailureCode.ANCHOR,
                AiAnchorFailureDetail(AiAnchorFailureKind.ambiguous, AiEditEffect.changed)
            ),
            AiOutputValidator.validateAndApply(chunk, secondOccurrence, emptySet())
        )
        // 模型偏移不精确（[0,3) 窗不是 "人人"）不得回退重建到第一处，必须整章失败关闭。
        val wrongOffset = AiChunkOutput(chunk.id, listOf(AiEdit(0, 3, "人人", "仁仁", AiEditKind.typo)))
        assertTrue(AiOutputValidator.validateAndApply(chunk, wrongOffset, emptySet()) is AiValidationResult.Invalid)
    }

    @Test
    fun overlappingSupplementaryAnchorUsesCodePointBoundaries() {
        // 补充平面字符按一个 code point 计数；"𠀀𠀀𠀀" 中锚点 "𠀀𠀀" 重叠出现在 [0,2) 与 [1,3)。
        val chunk = AiTextChunk("chunk-0", "𠀀𠀀𠀀", null)
        val secondOccurrence = AiChunkOutput(chunk.id, listOf(AiEdit(1, 3, "𠀀𠀀", "𠀁𠀁", AiEditKind.typo)))
        assertEquals(
            AiValidationResult.Invalid(
                AiFailureCode.ANCHOR,
                AiAnchorFailureDetail(AiAnchorFailureKind.ambiguous, AiEditEffect.changed)
            ),
            AiOutputValidator.validateAndApply(chunk, secondOccurrence, emptySet())
        )
        val wrongOffset = AiChunkOutput(chunk.id, listOf(AiEdit(0, 3, "𠀀𠀀", "𠀁𠀁", AiEditKind.typo)))
        assertTrue(AiOutputValidator.validateAndApply(chunk, wrongOffset, emptySet()) is AiValidationResult.Invalid)
    }

    // ---- 反和谐 denoise：仅删除词语内部白名单噪声，字母序列与替换结果必须逐字一致 ----

    @Test
    fun denoiseRemovesInsertedDecorationInsideWord() {
        val chunk = AiTextChunk("chunk-0", "他杀*人", null)
        val output = AiChunkOutput(chunk.id, listOf(AiEdit(1, 4, "杀*人", "杀人", AiEditKind.denoise)))
        assertEquals(AiValidationResult.Valid("他杀人"), AiOutputValidator.validateAndApply(chunk, output, emptySet()))
    }

    @Test
    fun denoiseHandlesSpace() {
        val chunk = AiTextChunk("chunk-0", "杀 人", null)
        val output = AiChunkOutput(chunk.id, listOf(AiEdit(0, 3, "杀 人", "杀人", AiEditKind.denoise)))
        assertEquals(AiValidationResult.Valid("杀人"), AiOutputValidator.validateAndApply(chunk, output, emptySet()))
    }

    @Test
    fun denoiseRejectsNonWhitelistedNoise() {
        val chunk = AiTextChunk("chunk-0", "杀@人", null)
        val output = AiChunkOutput(chunk.id, listOf(AiEdit(0, 3, "杀@人", "杀人", AiEditKind.denoise)))
        assertTrue(AiOutputValidator.validateAndApply(chunk, output, emptySet()) is AiValidationResult.Invalid)
    }

    @Test
    fun denoiseRejectsChangedOrAddedLetters() {
        val chunk = AiTextChunk("chunk-0", "杀.人", null)
        val output = AiChunkOutput(chunk.id, listOf(AiEdit(0, 3, "杀.人", "杀我", AiEditKind.denoise)))
        assertTrue(AiOutputValidator.validateAndApply(chunk, output, emptySet()) is AiValidationResult.Invalid)
    }

    @Test
    fun denoiseRejectsLeadingAndTrailingNoise() {
        val leading = AiTextChunk("chunk-0", "*杀人", null)
        val leadingOut = AiChunkOutput(leading.id, listOf(AiEdit(0, 3, "*杀人", "杀人", AiEditKind.denoise)))
        assertTrue(AiOutputValidator.validateAndApply(leading, leadingOut, emptySet()) is AiValidationResult.Invalid)

        val trailing = AiTextChunk("chunk-0", "杀*", null)
        val trailingOut = AiChunkOutput(trailing.id, listOf(AiEdit(0, 2, "杀*", "杀", AiEditKind.denoise)))
        assertTrue(AiOutputValidator.validateAndApply(trailing, trailingOut, emptySet()) is AiValidationResult.Invalid)
    }

    @Test
    fun denoiseRejectsExcessiveNoise() {
        val chunk = AiTextChunk("chunk-0", "杀*·~人", null)
        val output = AiChunkOutput(chunk.id, listOf(AiEdit(0, 5, "杀*·~人", "杀人", AiEditKind.denoise)))
        assertTrue(AiOutputValidator.validateAndApply(chunk, output, emptySet()) is AiValidationResult.Invalid)
    }

    @Test
    fun recoversDeletedCharacterIsNotSupportedAndFailsClosed() {
        // 第一版不实现“缺字恢复”，任何通过 typo/denoise 增补字母的编辑必须失败关闭。
        val chunk = AiTextChunk("chunk-0", "他杀", null)
        val addLetter = AiChunkOutput(chunk.id, listOf(AiEdit(1, 2, "杀", "杀人", AiEditKind.typo)))
        assertTrue(AiOutputValidator.validateAndApply(chunk, addLetter, emptySet()) is AiValidationResult.Invalid)

        val denoiseAddLetter = AiChunkOutput(chunk.id, listOf(AiEdit(1, 2, "杀", "杀人", AiEditKind.denoise)))
        assertTrue(AiOutputValidator.validateAndApply(chunk, denoiseAddLetter, emptySet()) is AiValidationResult.Invalid)
    }

    // ---- 稳定失败分类 ----

    @Test
    fun returnsStableFailureCode() {
        val chunk = AiTextChunk("chunk-0", "甲", null)
        fun codeOf(output: AiChunkOutput) =
            (AiOutputValidator.validateAndApply(chunk, output, emptySet()) as AiValidationResult.Invalid).code

        assertEquals(AiFailureCode.CHUNK_ID, codeOf(AiChunkOutput("wrong", emptyList())))
        assertEquals(
            AiFailureCode.FINISH_REASON,
            codeOf(AiChunkOutput(chunk.id, emptyList(), finishReason = AiFinishReason.length))
        )
        assertEquals(
            AiFailureCode.ANCHOR,
            codeOf(AiChunkOutput(chunk.id, listOf(AiEdit(0, 1, "不存在", "乙", AiEditKind.typo))))
        )
        assertEquals(
            AiFailureCode.TOO_MANY_EDITS,
            codeOf(AiChunkOutput(chunk.id, List(129) { AiEdit(0, 0, "", "", AiEditKind.whitespace) }))
        )
        assertEquals(
            AiFailureCode.EDIT_KIND,
            codeOf(AiChunkOutput(chunk.id, listOf(AiEdit(0, 1, "甲", "乙", AiEditKind.punctuation))))
        )
    }

    @Test
    fun anchorFailureDetailDistinguishesRejectedOccurrenceCountAndEditEffect() {
        fun detail(text: String, edit: AiEdit): AiAnchorFailureDetail? {
            val result = AiOutputValidator.validateAndApply(
                AiTextChunk("chunk-0", text, null),
                AiChunkOutput("chunk-0", listOf(edit)),
                emptySet()
            ) as AiValidationResult.Invalid
            assertEquals(AiFailureCode.ANCHOR, result.code)
            return result.anchorDetail
        }

        assertEquals(
            AiAnchorFailureDetail(AiAnchorFailureKind.ambiguous, AiEditEffect.changed),
            detail("甲甲", AiEdit(0, 1, "甲", "乙", AiEditKind.typo))
        )
        assertEquals(
            AiValidationResult.Valid("甲甲"),
            AiOutputValidator.validateAndApply(
                AiTextChunk("chunk-0", "甲甲", null),
                AiChunkOutput("chunk-0", listOf(AiEdit(0, 1, "甲", "甲", AiEditKind.typo))),
                emptySet()
            )
        )
        assertEquals(
            AiAnchorFailureDetail(AiAnchorFailureKind.missing, AiEditEffect.changed),
            detail("甲乙", AiEdit(0, 1, "丙", "丁", AiEditKind.typo))
        )
        assertEquals(
            AiAnchorFailureDetail(AiAnchorFailureKind.missing, AiEditEffect.noop),
            detail("甲乙", AiEdit(0, 1, "丙", "丙", AiEditKind.typo))
        )
    }
}
