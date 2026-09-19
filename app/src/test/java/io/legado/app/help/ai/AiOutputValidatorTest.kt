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
    // ---- 阶段E B1：重复锚点的上下文消歧契约（仅合成正文）----

    private fun validateEdit(text: String, edit: AiEdit): AiValidationResult =
        AiOutputValidator.validateAndApply(
            AiTextChunk("chunk-0", text, null),
            AiChunkOutput("chunk-0", listOf(edit)),
            emptySet()
        )

    private fun assertAnchorAmbiguousChanged(result: AiValidationResult) =
        assertEquals(
            AiValidationResult.Invalid(
                AiFailureCode.ANCHOR,
                AiAnchorFailureDetail(AiAnchorFailureKind.ambiguous, AiEditEffect.changed)
            ),
            result
        )

    @Test
    fun resolvesRepeatedChangedAnchorFromUniqueContextCombination() {
        // original（空格）在块内出现两次，唯一子串路径不成立，必须走上下文锚点；组合 "他 说" 只出现一次。
        val text = "他 走得很快，他 说得更好"
        assertEquals(
            AiValidationResult.Valid("他 走得很快，他\n说得更好"),
            validateEdit(
                text,
                AiEdit(99, 100, " ", "\n", AiEditKind.whitespace,
                    contextBefore = "他", contextAfter = "说")
            )
        )
        // 同一输入去掉上下文即为旧行为：重复锚点的真实编辑必须失败关闭（B1 的新旧对照）。
        assertAnchorAmbiguousChanged(
            validateEdit(text, AiEdit(0, 1, " ", "\n", AiEditKind.whitespace))
        )
    }

    @Test
    fun contextAnchorNearChunkStartAndNearChunkEndResolvesUniquely() {
        // 紧邻块首/块尾的空格：两侧上下文在块内齐备时可定位（真·块首元素没有左侧上下文，
        // 协议要求两侧齐备，故块首元素本身不做上下文消歧——见仅单侧上下文必须失败关闭的用例）。
        val nearStart = "他 开始，他 后来"
        assertEquals(
            AiValidationResult.Valid("他\n开始，他 后来"),
            validateEdit(
                nearStart,
                AiEdit(99, 100, " ", "\n", AiEditKind.whitespace,
                    contextBefore = "他", contextAfter = "开")
            )
        )

        val nearEnd = "他来，他 就是他 "
        assertEquals(
            AiValidationResult.Valid("他来，他\n就是他 "),
            validateEdit(
                nearEnd,
                AiEdit(99, 100, " ", "\n", AiEditKind.whitespace,
                    contextBefore = "他", contextAfter = "就")
            )
        )
    }

    @Test
    fun fabricatedContextMustFailClosed() {
        // 组合锚点 "他 他" 在两个空格处都落点，落点唯一性成立时仍必须逐字比对 original。
        val text = "他 走得很快，他 说"
        assertAnchorAmbiguousChanged(
            validateEdit(
                text,
                AiEdit(0, 1, " ", "\n", AiEditKind.whitespace,
                    contextBefore = "他", contextAfter = "他")
            )
        )
    }

    @Test
    fun contextThatIsNotAdjacentToOriginalMustFailClosed() {
        // 空格在块内出现两次（必须走上下文定位），声明的后文“走”虽是块内片段，却不是紧邻第一个空格右侧的字符。
        val text = "他 说得很快，他 。走得很慢"
        assertAnchorAmbiguousChanged(
            validateEdit(
                text,
                AiEdit(0, 1, " ", "\n", AiEditKind.whitespace,
                    contextBefore = "他", contextAfter = "走")
            )
        )
    }

    @Test
    fun nonUniqueContextCombinationMustFailClosed() {
        // 两侧上下文都非空、长度都在上限内，但组合锚点 "他好。" 在块内出现两次：
        // 这正是"组合仍不唯一必须失败关闭"的分支，必须失败关闭（去掉该分支会让本用例变红）。
        val text = "他好。他好。他坏"
        assertAnchorAmbiguousChanged(
            validateEdit(
                text,
                AiEdit(0, 1, "好", "佳", AiEditKind.typo,
                    contextBefore = "他", contextAfter = "。")
            )
        )
    }

    @Test
    fun contextLongerThanLimitMustFailClosed() {
        // 前文确实逐字存在于块中、组合本可唯一定位，但前文 33 > 32 超过上限，必须失败关闭。
        val over = "长".repeat(AiEdit.MAX_ANCHOR_CONTEXT + 1)
        assertAnchorAmbiguousChanged(
            validateEdit(
                "他 结尾，" + over + "他 ",
                AiEdit(0, 1, " ", "\n", AiEditKind.whitespace,
                    contextBefore = over, contextAfter = "")
            )
        )
    }

    @Test
    fun contextAtExactlyTheLimitResolves() {
        // 前文恰好 32 个 code point：上限内且组合唯一，必须可定位。
        val exact = "长".repeat(AiEdit.MAX_ANCHOR_CONTEXT)
        assertEquals(
            AiValidationResult.Valid("他 结尾，" + exact + "\n。"),
            validateEdit(
                "他 结尾，" + exact + " 。",
                AiEdit(0, 1, " ", "\n", AiEditKind.whitespace,
                    contextBefore = exact, contextAfter = "。")
            )
        )
    }

    @Test
    fun replacementSentinelSyntaxIsRejectedOnUniqueOccurrencePath() {
        // 本用例覆盖的是既有 replacement 哨兵语法检查（original "编" 唯一，走唯一子串路径）。
        // 复审 F6：含哨兵的 original 必然唯一，锚点路径不可能触达该检查，故不能把本用例当作
        // "锚点路径下的哨兵保护"证据。
        val protected = AiTextChunker.protect("编号 123456 结束", "anchor")
        val sentinel = protected.sentinels.keys.single()
        val result = AiOutputValidator.validateAndApply(
            AiTextChunk("chunk-0", protected.protected, null),
            AiChunkOutput(
                "chunk-0",
                listOf(
                    AiEdit(0, 1, "编", sentinel, AiEditKind.whitespace)
                )
            ),
            setOf(sentinel)
        )
        assertEquals(AiValidationResult.Invalid(AiFailureCode.SENTINEL), result)
    }

    @Test
    fun contextAnchoredRangesOutOfOrderMustFailClosed() {
        // 两条编辑都能被上下文唯一定位，但顺序颠倒（后一条落在前一条之前）：必须 OVERLAP 失败关闭。
        val text = "他很早，很早就走了"
        val first = AiEdit(0, 1, "很", "十", AiEditKind.typo,
            contextBefore = "，", contextAfter = "早")
        val second = AiEdit(0, 1, "很", "十", AiEditKind.typo,
            contextBefore = "他", contextAfter = "早")
        val result = AiOutputValidator.validateAndApply(
            AiTextChunk("chunk-0", text, null),
            AiChunkOutput("chunk-0", listOf(first, second)),
            emptySet()
        )
        assertEquals(AiValidationResult.Invalid(AiFailureCode.OVERLAP), result)
    }

    @Test
    fun contextAnchoredNestedRangeMustFailClosed() {
        // 第一条解析到 [2,4)，第二条解析到 [3,4)：范围嵌套，必须 OVERLAP 失败关闭。
        val text = "起初早早他都"
        val first = AiEdit(0, 4, "早早", "甲乙", AiEditKind.typo,
            contextBefore = "起初", contextAfter = "他")
        val second = AiEdit(0, 1, "早", "丙", AiEditKind.typo,
            contextBefore = "早", contextAfter = "他")
        val result = AiOutputValidator.validateAndApply(
            AiTextChunk("chunk-0", text, null),
            AiChunkOutput("chunk-0", listOf(first, second)),
            emptySet()
        )
        assertEquals(AiValidationResult.Invalid(AiFailureCode.OVERLAP), result)
    }

    @Test
    fun singleSidedContextMustFailClosedBecauseItIsNotAContractAnchor() {
        // 协议要求两侧都给出：只给一侧时一律失败关闭，绝不退回“只靠一侧消歧”。
        val text = "他很早，很早就走了"
        assertAnchorAmbiguousChanged(
            validateEdit(
                text,
                AiEdit(0, 1, "很", "十", AiEditKind.typo,
                    contextBefore = "早", contextAfter = "")
            )
        )
        assertAnchorAmbiguousChanged(
            validateEdit(
                text,
                AiEdit(0, 1, "很", "十", AiEditKind.typo,
                    contextBefore = "", contextAfter = "早")
            )
        )
    }

    @Test
    fun supplementaryPlaneContextMustBeCodePointAligned() {
        // 补充平面字符（\uD840\uDC00 / \uD840\uDC01，即 U+20000 / U+20001）各占一个 code point、两个 UTF-16 char。
        val base = "\uD840\uDC00"
        val other = "\uD840\uDC01"
        // 正例：紧邻空格的完整补充平面字符作为后文（两侧齐备），必须能按 code point 唯一定位。
        val positive = "他 " + base + "，他 " + other
        assertEquals(
            AiValidationResult.Valid("他 " + base + "，他\n" + other),
            validateEdit(
                positive,
                AiEdit(0, 1, " ", "\n", AiEditKind.whitespace,
                    contextBefore = "他", contextAfter = other)
            )
        )
        // 反例：original 在块内重复，声明的后文“说”在块内存在但与紧邻字符不符，必须失败关闭。
        val negative = "他 " + base + "，他，" + base + " 说"
        assertAnchorAmbiguousChanged(
            validateEdit(
                negative,
                AiEdit(0, 1, "，", "。", AiEditKind.punctuation,
                    contextBefore = "他", contextAfter = "说")
            )
        )
    }
}