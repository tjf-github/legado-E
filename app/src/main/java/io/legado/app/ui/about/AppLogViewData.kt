package io.legado.app.ui.about

import io.legado.app.constant.AppLogEntry
import io.legado.app.constant.AppLogLevel
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class AppLogSource {
    MEMORY,
    FILE
}

internal data class AppLogFilter(
    val level: AppLogLevel? = null,
    val tag: String? = null,
    val keyword: String = ""
)

internal object AppLogFileReader {

    private const val MAX_LINES = 2_000
    private const val MAX_ENTRIES = 500
    private const val MAX_BYTES_PER_FILE = 1024 * 1024
    private val newHeader = Regex(
        "^(\\d{2}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}) \\[(INFO|WARN|ERROR)] (.*)$"
    )
    private val legacyHeader = Regex(
        "^(\\d{2}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}): (.*)$"
    )

    fun read(logFolder: File): List<AppLogEntry> {
        val result = ArrayList<AppLogEntry>()
        val files = logFolder.listFiles { file ->
            file.isFile && file.name.startsWith("appLog-") && file.name.endsWith(".txt")
        }?.sortedByDescending(File::lastModified).orEmpty()
        for (file in files) {
            val entries = parse(readTailLines(file, MAX_LINES)).asReversed()
            for (entry in entries) {
                result.add(entry)
                if (result.size >= MAX_ENTRIES) return result
            }
        }
        return result
    }

    internal fun parse(lines: List<String>): List<AppLogEntry> {
        val result = ArrayList<AppLogEntry>()
        lines.forEach { line ->
            val newMatch = newHeader.matchEntire(line)
            val legacyMatch = legacyHeader.matchEntire(line)
            when {
                newMatch != null -> result.add(
                    newEntry(
                        newMatch.groupValues[1],
                        AppLogLevel.valueOf(newMatch.groupValues[2]),
                        newMatch.groupValues[3]
                    )
                )

                legacyMatch != null -> result.add(
                    newEntry(
                        legacyMatch.groupValues[1],
                        AppLogLevel.INFO,
                        legacyMatch.groupValues[2]
                    )
                )

                result.isNotEmpty() -> {
                    val index = result.lastIndex
                    result[index] = result[index].copy(
                        message = result[index].message + "\n" + line
                    )
                }
            }
        }
        return result
    }

    internal fun readTailLines(
        file: File,
        maxLines: Int,
        maxBytes: Int = MAX_BYTES_PER_FILE
    ): List<String> {
        if (maxLines <= 0 || maxBytes <= 0 || !file.isFile || file.length() == 0L) {
            return emptyList()
        }
        RandomAccessFile(file, "r").use { input ->
            var position = input.length() - 1
            var lineBreaks = 0
            val reversed = ByteArrayOutputStream()
            while (position >= 0 && lineBreaks <= maxLines && reversed.size() < maxBytes) {
                input.seek(position--)
                val byte = input.read()
                if (byte == '\n'.code) lineBreaks++
                reversed.write(byte)
            }
            val bytes = reversed.toByteArray().also(ByteArray::reverse)
            val lines = bytes.toString(Charsets.UTF_8)
                .lineSequence()
                .map { it.removeSuffix("\r") }
                .toList()
            val completeLines = if (position >= 0 && lines.isNotEmpty()) lines.drop(1) else lines
            return completeLines.filter(String::isNotEmpty).takeLast(maxLines)
        }
    }

    private fun newEntry(timestamp: String, level: AppLogLevel, payload: String): AppLogEntry {
        val splitAt = payload.indexOf(' ')
        val rawTag = if (splitAt < 0) payload else payload.substring(0, splitAt)
        val tag = rawTag.removeSurrounding("[", "]").ifBlank { "Unknown" }
        val message = if (splitAt < 0) "" else payload.substring(splitAt + 1)
        return AppLogEntry(parseTime(timestamp), level, tag, message)
    }

    private fun parseTime(value: String): Long {
        return runCatching {
            SimpleDateFormat("yy-MM-dd HH:mm:ss.SSS", Locale.US).apply {
                isLenient = false
            }.parse(value)?.time
        }.getOrNull() ?: 0L
    }
}

internal fun filterLogEntries(
    entries: List<AppLogEntry>,
    filter: AppLogFilter
): List<AppLogEntry> {
    val keyword = filter.keyword.trim()
    return entries.filter { entry ->
        (filter.level == null || entry.level == filter.level) &&
            (filter.tag == null || entry.tag == filter.tag) &&
            (keyword.isEmpty() || entry.tag.contains(keyword, ignoreCase = true) ||
                entry.message.contains(keyword, ignoreCase = true) ||
                entry.throwable?.stackTraceToString()?.contains(keyword, ignoreCase = true) == true)
    }
}

internal fun formatLogEntries(entries: List<AppLogEntry>): String {
    val formatter = SimpleDateFormat("yy-MM-dd HH:mm:ss.SSS", Locale.US)
    return entries.joinToString("\n") { entry ->
        buildString {
            append(formatter.format(Date(entry.time)))
            append(" [").append(entry.level.name).append("] ")
            append(entry.tag).append(' ').append(entry.message)
            entry.throwable?.let { append('\n').append(it.stackTraceToString()) }
        }
    }
}
