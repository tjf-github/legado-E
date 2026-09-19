package io.legado.app.help.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AiNoChangeTest {
    @get:Rule val folder = TemporaryFolder()
    private fun validate(text: String, vararg edits: AiEdit): AiValidationResult =
        AiOutputValidator.validateAndApply(AiTextChunk("chunk-0", text, null),
            AiChunkOutput("chunk-0", edits.toList()), emptySet())

    @Test fun normalChapterAndSafeNoopBothSucceed() {
        assertEquals(AiValidationResult.Valid("正文"), validate("正文"))
        assertEquals(AiValidationResult.Valid("正文！"), validate("正文！",
            AiEdit(0, 2, "正文", "正文", AiEditKind.typo),
            AiEdit(2, 3, "！", "！", AiEditKind.punctuation)))
    }

    @Test fun safeNoopDoesNotDiscardRealRepair() {
        assertEquals(AiValidationResult.Valid("正文走得快"), validate("正文走的快",
            AiEdit(0, 2, "正文", "正文", AiEditKind.typo),
            AiEdit(3, 4, "的", "得", AiEditKind.typo)))
    }

    @Test fun ambiguousNoopIsDiscardedWithoutDiscardingRealRepair() {
        assertEquals(AiValidationResult.Valid("甲甲走得快"), validate("甲甲走的快",
            AiEdit(99, 100, "甲", "甲", AiEditKind.typo),
            AiEdit(3, 4, "的", "得", AiEditKind.typo)))
    }

    @Test fun ambiguousNoopMustStillPassKindAndProtectedValueChecks() {
        assertEquals(AiValidationResult.Invalid(AiFailureCode.EDIT_KIND), validate("甲甲",
            AiEdit(0, 1, "甲", "甲", AiEditKind.punctuation)))

        val protected = AiTextChunker.protect("网址 123 和 456", "repeat")
        val chunk = AiTextChunk("chunk-0", protected.protected, null)
        assertEquals(AiValidationResult.Invalid(AiFailureCode.SENTINEL),
            AiOutputValidator.validateAndApply(chunk, AiChunkOutput(chunk.id,
                listOf(AiEdit(0, 1, "repeat", "repeat", AiEditKind.typo))),
                protected.sentinels.keys))
    }

    @Test fun noopCannotHideInvalidAnchorKindOrEmptyPlaceholder() {
        listOf(
            AiEdit(0, 1, "不存在", "不存在", AiEditKind.typo),
            AiEdit(0, 1, "甲", "甲", AiEditKind.punctuation),
            AiEdit(0, 0, "", "", AiEditKind.whitespace)
        ).forEach { assertTrue(validate("甲", it) is AiValidationResult.Invalid) }
    }

    @Test fun noopCannotHideOverlapOrInvalidSibling() {
        val noop = AiEdit(0, 2, "甲乙", "甲乙", AiEditKind.typo)
        assertTrue(validate("甲乙丙", noop,
            AiEdit(1, 2, "乙", "丁", AiEditKind.typo)) is AiValidationResult.Invalid)
        assertTrue(validate("甲乙丙", noop,
            AiEdit(2, 3, "丙", "", AiEditKind.typo)) is AiValidationResult.Invalid)
    }

    @Test fun repeatedAnchorCannotBeResolvedByCoincidentallyMatchingOffset() {
        assertEquals(AiValidationResult.Invalid(
            AiFailureCode.ANCHOR,
            AiAnchorFailureDetail(AiAnchorFailureKind.ambiguous, AiEditEffect.changed)
        ),
            validate("他走的很快，她的书", AiEdit(7, 8, "的", "得", AiEditKind.typo)))
    }

    @Test fun missingNoopAndInvalidSiblingStillFailClosed() {
        assertEquals(
            AiValidationResult.Invalid(
                AiFailureCode.ANCHOR,
                AiAnchorFailureDetail(AiAnchorFailureKind.missing, AiEditEffect.noop)
            ),
            validate("甲甲", AiEdit(0, 1, "乙", "乙", AiEditKind.typo))
        )
        assertEquals(AiValidationResult.Invalid(AiFailureCode.EDIT_KIND),
            validate("甲甲丙", AiEdit(0, 1, "甲", "甲", AiEditKind.typo),
                AiEdit(2, 3, "丙", "丙", AiEditKind.punctuation)))
    }

    @Test fun noopCannotTouchSentinelOrBypassTruncation() {
        val protected = AiTextChunker.protect("网址 https://example.com", "test")
        val sentinel = protected.sentinels.keys.single()
        val chunk = AiTextChunk("chunk-0", protected.protected, null)
        assertEquals(AiValidationResult.Invalid(AiFailureCode.SENTINEL),
            AiOutputValidator.validateAndApply(chunk, AiChunkOutput(chunk.id,
                listOf(AiEdit(0, 1, sentinel, sentinel, AiEditKind.typo))), setOf(sentinel)))
        assertEquals(AiValidationResult.Invalid(AiFailureCode.FINISH_REASON),
            AiOutputValidator.validateAndApply(chunk, AiChunkOutput(chunk.id, emptyList(),
                finishReason = AiFinishReason.length), setOf(sentinel)))
    }

    @Test fun noopChapterCachesButInvalidLaterChunkNeverDoes() = runBlocking {
        for (failLater in listOf(false, true)) {
            val cache = AiChapterCache(folder.newFolder())
            val input = "甲乙丙丁戊己"
            val config = AiProviderConfig("fake", "https://example.invalid", "fake")
            val id = AiChapterIdentity.build("book", "chapter", 0, input, config, 1, "fp")
            var calls = 0
            val provider = object : AiTextProvider {
                override suspend fun processChunk(request: AiChunkRequest, config: AiProviderConfig,
                    apiKey: AiApiKey): AiChunkOutput {
                    calls++
                    return AiChunkOutput(if (failLater && calls == 2) "wrong" else request.chunkId,
                        listOf(AiEdit(0, 2, request.text, request.text, AiEditKind.typo)))
                }
            }
            val processor = AiChapterProcessor(provider, cache)
            suspend fun process() = processor.process(AiCacheKey.create(id), AiTextChunker.chunk(input, 2),
                config, AiApiKey.from("synthetic"), id)
            if (failLater) {
                assertEquals(AiProcessResult.Failed(AiFailureCode.CHUNK_ID, 2), process())
                assertEquals(2, calls)
                assertNull(cache.read(id))
            } else {
                assertEquals(input, (process() as AiProcessResult.Completed).text)
                assertEquals(0, (process() as AiProcessResult.Completed).requestCount)
                assertEquals(3, calls)
            }
        }
    }
}
