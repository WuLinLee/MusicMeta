package com.wulinlee.musicmeta

import java.util.Locale

/**
 * Extracts original and translated lines from same-timestamp interleaved LRC stored in audio tags.
 * Ordinary single-language lyrics remain untouched.
 */
internal object EmbeddedLyricsParser {
    private val timestampRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")

    private data class LyricRow(val sourceLine: Int, val text: String)

    fun split(raw: String): PreviewLyrics {
        val source = raw.trim()
        if (source.isBlank()) return PreviewLyrics()
        val grouped = sortedMapOf<Long, MutableList<LyricRow>>()

        source.lineSequence().forEachIndexed { lineIndex, line ->
            val tags = timestampRegex.findAll(line).toList()
            if (tags.isEmpty()) return@forEachIndexed
            val lyricText = timestampRegex.replace(line, "").trim()
            if (lyricText.isBlank()) return@forEachIndexed
            tags.forEach { tag ->
                val minutes = tag.groupValues[1].toLongOrNull() ?: return@forEach
                val seconds = tag.groupValues[2].toLongOrNull() ?: return@forEach
                if (seconds >= 60) return@forEach
                val fraction = tag.groupValues.getOrElse(3) { "" }
                val fractionMs = when (fraction.length) {
                    1 -> (fraction.toLongOrNull() ?: 0L) * 100
                    2 -> (fraction.toLongOrNull() ?: 0L) * 10
                    3 -> fraction.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val timestamp = minutes * 60_000 + seconds * 1_000 + fractionMs
                val rows = grouped.getOrPut(timestamp) { mutableListOf() }
                if (rows.none { it.sourceLine == lineIndex }) rows += LyricRow(lineIndex, lyricText)
            }
        }

        if (grouped.isEmpty()) return PreviewLyrics(original = source)
        val pairedTimestampCount = grouped.values.count { it.size >= 2 }
        if (pairedTimestampCount == 0 || pairedTimestampCount * 2 < grouped.size) {
            return PreviewLyrics(original = source)
        }
        val original = grouped.entries.joinToString("\n") { (time, rows) ->
            formatTimestamp(time) + rows.first().text
        }
        val translated = grouped.entries.mapNotNull { (time, rows) ->
            rows.getOrNull(1)?.let { formatTimestamp(time) + it.text }
        }.joinToString("\n")
        return if (translated.isBlank()) PreviewLyrics(original = source)
        else PreviewLyrics(original = original, translated = translated)
    }

    private fun formatTimestamp(timeMs: Long): String {
        val minutes = timeMs / 60_000
        val seconds = (timeMs % 60_000) / 1_000
        val hundredths = (timeMs % 1_000) / 10
        return String.format(Locale.US, "[%02d:%02d.%02d]", minutes, seconds, hundredths)
    }
}
