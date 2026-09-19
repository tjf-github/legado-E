package io.legado.app.help.ai

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AiChapterCacheTest {
    @get:Rule val folder = TemporaryFolder()
    private val config = AiProviderConfig("fake", "https://example.invalid", "fake")
    private fun identity(book: String = "book", chapter: String = "chapter") = AiChapterIdentity.build(
        book, chapter, 0, "原始正文", config, 1, "fingerprint"
    )

    @Test fun completedSurvivesReopenAndIdentityChangesMiss() {
        val root = folder.newFolder()
        val cache = AiChapterCache(root)
        val id = identity()
        assertTrue(cache.write(id, "完整结果", cache.generation()))
        val reopened = AiChapterCache(root)
        assertEquals("完整结果", reopened.read(id))
        listOf(id.copy(inputTextSha256 = "changed"), id.copy(model = "new"),
            id.copy(configGeneration = 2), id.copy(promptVersion = "new"),
            id.copy(validationVersion = "new"), id.copy(chunkingVersion = "new")
        ).forEach { assertNull(reopened.read(it)) }
    }

    @Test fun cleaningThroughAnotherInstanceRejectsLateWrite() {
        val root = folder.newFolder()
        val writer = AiChapterCache(root)
        val cleaner = AiChapterCache(root)
        val generation = writer.generation()
        cleaner.deleteBook(identity().bookUrlHash)
        assertFalse(writer.write(identity(), "迟到结果", generation))
        assertNull(writer.read(identity()))
    }

    @Test fun clearChapterRemovesAllVersionsButKeepsOtherChapter() {
        val cache = AiChapterCache(folder.newFolder())
        val id = identity()
        val old = id.copy(configGeneration = 0)
        val other = identity(chapter = "other")
        listOf(id, old, other).forEach { assertTrue(cache.write(it, "完整结果", cache.generation())) }
        cache.delete(id)
        assertNull(cache.read(id))
        assertNull(cache.read(old))
        assertEquals("完整结果", cache.read(other))
    }

    @Test fun temporaryTruncatedAndNullPayloadNeverHitOrThrow() {
        val root = folder.newFolder()
        val cache = AiChapterCache(root)
        val id = identity()
        val dir = File(root, id.bookUrlHash).apply { mkdirs() }
        File(dir, AiCacheKey.create(id) + ".tmp").writeText("unfinished")
        assertNull(cache.read(id))
        assertTrue(cache.write(id, "完整结果", cache.generation()))
        val file = dir.walkTopDown().first { it.isFile && it.extension == "json" }
        val valid = file.readText()
        file.writeText(valid.replace("\"完整结果\"", "null"))
        assertNull(cache.read(id))
        file.writeText("{\"completed\":true")
        assertNull(cache.read(id))
    }

    @Test fun independentAiCleaningPreservesOriginalCache() {
        val original = folder.newFile("original.txt").apply { writeText("书源正文") }
        val cache = AiChapterCache(folder.newFolder("ai_text"))
        assertTrue(cache.write(identity(), "完整结果", cache.generation()))
        cache.deleteAll()
        assertNull(cache.read(identity()))
        assertEquals("书源正文", original.readText())
    }

    @Test fun bookHashCannotEscapeCacheRoot() {
        val root = folder.newFolder("ai_text")
        val protected = folder.newFolder("outside")
        val marker = File(protected, "keep").apply { writeText("keep") }
        val cache = AiChapterCache(root)
        runCatching { cache.deleteBook("../outside") }
        assertTrue(marker.isFile)
        assertFalse(runCatching {
            cache.write(identity().copy(bookUrlHash = "../outside"), "bad", cache.generation())
        }.getOrDefault(false))
    }

    @Test fun failedRenamePreservesPreviousCompletedAndRemovesTemporaryFiles() {
        val root = folder.newFolder()
        val cache = AiChapterCache(root)
        val id = identity()
        assertTrue(cache.write(id, "之前的完整结果", cache.generation()))
        val failing = AiChapterCache(root) { _, _ -> false }
        assertFalse(failing.write(id, "写入失败的新结果", failing.generation()))
        assertEquals("之前的完整结果", cache.read(id))
        assertFalse(root.walkTopDown().any { it.isFile && it.extension == "tmp" })
    }

    @Test fun payloadCorruptionIsMissEvenWithValidJson() {
        val root = folder.newFolder()
        val cache = AiChapterCache(root)
        assertTrue(cache.write(identity(), "完整结果", cache.generation()))
        val file = root.walkTopDown().first { it.isFile && it.extension == "json" }
        file.writeText(file.readText().replace("完整结果", "破损结果"))
        assertNull(cache.read(identity()))
    }

    @Test fun partialAssemblyNeverHitsAndReopeningDoesNotDeleteActiveAssembly() {
        val root = folder.newFolder()
        val cache = AiChapterCache(root)
        cache.begin(identity(), cache.generation()).use { assembly ->
            assembly.append("第一块")
            assertNull(AiChapterCache(root).read(identity()))
            assembly.append("第二块")
            assertEquals("第一块第二块", assembly.assembled())
        }
        assertFalse(root.walkTopDown().any { it.isFile })
        File(root, "crash.tmp").writeText("崩溃残留")
        assertNull(AiChapterCache(root).read(identity()))
        assertFalse(File(root, "crash.tmp").exists())
    }

    @Test fun invalidationAtFinalGuardCannotPublish() {
        val cache = AiChapterCache(folder.newFolder())
        var checks = 0
        val written = cache.write(identity(), "结果", cache.generation()) {
            checks++
            if (checks == 2) cache.deleteAll()
        }
        assertFalse(written)
        assertNull(cache.read(identity()))
    }
}
