package io.legado.app.help.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.utils.GSON
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Independent cache; one atomic JSON envelope contains identity, checksum and Completed text.
 * All instances of a canonical directory share the clear/commit lock and epoch. Temporary
 * chunk assemblies never hit. Same-directory rename works on Android API 21+. If replacement
 * is unsupported, keep the old result and fail closed; never delete it before renaming. */
class AiChapterCache(rootDir: File,
    private val rename: (File, File) -> Boolean = { source, target -> source.renameTo(target) }
) {
    private val root = rootDir.canonicalFile
    private val state = STATES.getOrPut(root.path) { State() }
    internal val scope: String get() = root.path
    private class State { var generation = 0L; val active = mutableSetOf<String>() }

    init {
        synchronized(state) {
            root.walkTopDown().filter { it.isFile && it.extension == "tmp" && it.path !in state.active }
                .forEach { it.delete() }
        }
    }
    fun generation(): Long = synchronized(state) { state.generation }

    fun read(identity: AiCacheIdentity): String? = synchronized(state) {
        runCatching {
            val file = resultFile(identity)
            if (!file.isFile || file.length() > MAX_FILE_BYTES) return@synchronized null
            val json = JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonObject
            if (json.get("tag")?.asString != TAG || json.get("completed")?.asBoolean != true ||
                json.get("identity") != GSON.toJsonTree(identity)) return@synchronized null
            val value = json.get("text")
            if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isString) return@synchronized null
            val text = value.asString
            if (json.get("sha256")?.asString != AiCacheKey.sha256(text)) return@synchronized null
            text
        }.getOrNull()
    }

    internal fun begin(identity: AiCacheIdentity, expectedGeneration: Long): Assembly = synchronized(state) {
        check(state.generation == expectedGeneration) { "cache_invalidated" }
        val dir = chapterDir(identity)
        check(dir.isDirectory || dir.mkdirs()) { "cache_unavailable" }
        Assembly(identity, expectedGeneration, File(dir, "${UUID.randomUUID()}.tmp"))
    }

    internal inner class Assembly(private val identity: AiCacheIdentity, private val expected: Long,
                                  private val file: File) : Closeable {
        private val writer: OutputStreamWriter
        private var closed = false
        init {
            state.active.add(file.path)
            try { writer = file.outputStream().writer(Charsets.UTF_8) }
            catch (e: Exception) { state.active.remove(file.path); throw e }
        }
        fun append(text: String) = synchronized(state) {
            check(!closed && state.generation == expected) { "cache_invalidated" }
            writer.write(text)
        }
        fun assembled(): String = synchronized(state) {
            check(!closed && state.generation == expected) { "cache_invalidated" }
            writer.flush()
            check(file.length() <= MAX_FILE_BYTES) { "cache_too_large" }
            file.readText(Charsets.UTF_8)
        }
        fun commit(text: String, checkCurrent: () -> Unit): Boolean = write(identity, text, expected, checkCurrent)
        override fun close() = synchronized(state) {
            if (!closed) {
                closed = true
                try { writer.close() } finally { file.delete(); state.active.remove(file.path) }
            }
        }
    }

    fun write(identity: AiCacheIdentity, text: String, expectedGeneration: Long): Boolean =
        write(identity, text, expectedGeneration) {}

    internal fun write(identity: AiCacheIdentity, text: String, expectedGeneration: Long,
                       checkCurrent: () -> Unit): Boolean = synchronized(state) {
        if (state.generation != expectedGeneration) return@synchronized false
        var temp: File? = null
        try {
            checkCurrent()
            val dest = resultFile(identity)
            val dir = dest.parentFile!!
            if (!dir.isDirectory && !dir.mkdirs()) return@synchronized false
            temp = File(dir, "${UUID.randomUUID()}.tmp")
            val json = JsonObject().apply {
                addProperty("tag", TAG)
                addProperty("completed", true)
                add("identity", GSON.toJsonTree(identity))
                addProperty("sha256", AiCacheKey.sha256(text))
                addProperty("text", text)
            }
            FileOutputStream(temp).use { stream ->
                val writer = OutputStreamWriter(stream, Charsets.UTF_8)
                GSON.toJson(json, writer)
                writer.flush()
                stream.fd.sync()
            }
            if (temp.length() > MAX_FILE_BYTES) return@synchronized false
            checkCurrent()
            if (state.generation != expectedGeneration) return@synchronized false
            rename(temp, dest)
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (_: Exception) {
            false
        } finally { temp?.delete() }
    }

    /** Remove every input/configuration version of this chapter. */
    fun delete(identity: AiCacheIdentity): Long = synchronized(state) {
        state.generation++
        runCatching { chapterDir(identity).deleteRecursively() }
        state.generation
    }
    fun deleteBook(bookUrlHash: String): Long = synchronized(state) {
        state.generation++
        runCatching { bookDir(bookUrlHash).deleteRecursively() }
        state.generation
    }
    fun deleteAll(): Long = synchronized(state) {
        state.generation++
        root.deleteRecursively()
        state.generation
    }
    fun deleteBooksExcept(validBookUrlHashes: Set<String>): Long = synchronized(state) {
        val obsolete = root.listFiles().orEmpty().filter { it.isDirectory && it.name !in validBookUrlHashes }
        if (obsolete.isNotEmpty()) {
            state.generation++
            obsolete.forEach { runCatching { bookDir(it.name).deleteRecursively() } }
        }
        state.generation
    }
    private fun bookDir(hash: String): File {
        require(HASH.matches(hash)) { "invalid_cache_identity" }
        return File(root, hash).also { require(it.canonicalFile.parentFile == root) }
    }
    private fun chapterDir(identity: AiCacheIdentity): File {
        val parent = bookDir(identity.bookUrlHash)
        val chapter = AiCacheKey.sha256("${identity.chapterUrlHash}:${identity.chapterIndex}")
        return File(parent, chapter).also { require(it.canonicalFile.parentFile == parent.canonicalFile) }
    }
    private fun resultFile(identity: AiCacheIdentity): File = File(chapterDir(identity), "${AiCacheKey.create(identity)}.json")

    companion object {
        const val TAG = "ai_cache_v2"
        private const val MAX_FILE_BYTES = 32L * 1024 * 1024
        private val HASH = Regex("[0-9a-f]{64}")
        private val STATES = ConcurrentHashMap<String, State>()
    }
}
