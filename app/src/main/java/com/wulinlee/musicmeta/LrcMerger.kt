package com.wulinlee.musicmeta

import java.util.Locale

object LrcMerger {
    private val timestampRegex = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")
    private val yrcLineRegex = Regex("^\\s*\\[(\\d+),(\\d+)](.*)$")
    private val yrcWordTimingRegex = Regex("\\(\\d+,\\d+,\\d+\\)")

    /**
     * Convert NetEase word-by-word YRC timing into standard line-timed LRC,
     * which is understood by more audio players and tag readers.
     */
    fun normalizeWordByWordToLrc(text: String): String {
        val source = text.trim()
        if (source.isBlank()) return ""

        val converted = source.lineSequence().mapNotNull { line ->
            val match = yrcLineRegex.matchEntire(line) ?: return@mapNotNull null
            val startMs = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val lyricText = yrcWordTimingRegex.replace(match.groupValues[3], "").trim()
            if (lyricText.isBlank()) return@mapNotNull null
            format(startMs) + lyricText
        }.toList()

        if (converted.isNotEmpty()) return converted.joinToString("\n")
        return source.takeIf { value ->
            value.lineSequence().any { line -> timestampRegex.containsMatchIn(line) }
        }.orEmpty()
    }

    fun merge(original: String, translated: String): String {
        // Translation is supplementary, never a substitute for a missing original.
        if (original.isBlank()) return ""
        if (translated.isBlank()) return original.trim()
        val originalMap = parse(original)
        val translatedMap = parse(translated)
        if (originalMap.isEmpty()) return original.trim()
        if (translatedMap.isEmpty()) return original.trim()
        val timestamps = (originalMap.keys + translatedMap.keys).toSortedSet()
        return timestamps.flatMap { time ->
            buildList {
                originalMap[time].orEmpty().forEach { add(format(time) + it) }
                translatedMap[time].orEmpty().forEach { add(format(time) + it) }
            }
        }.joinToString("\n")
    }

    private fun parse(text: String): Map<Long, List<String>> {
        val result = linkedMapOf<Long, MutableList<String>>()
        for (line in text.lineSequence()) {
            val matches = timestampRegex.findAll(line).toList()
            if (matches.isEmpty()) continue
            val lyricText = timestampRegex.replace(line, "").trim()
            if (lyricText.isBlank()) continue
            for (match in matches) {
                val minutes = match.groupValues[1].toLongOrNull() ?: continue
                val seconds = match.groupValues[2].toLongOrNull() ?: continue
                if (seconds >= 60) continue
                val fraction = match.groupValues.getOrElse(3) { "" }
                val millis = when (fraction.length) {
                    1 -> (fraction.toLongOrNull() ?: 0L) * 100
                    2 -> (fraction.toLongOrNull() ?: 0L) * 10
                    3 -> fraction.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val timestamp = minutes * 60_000 + seconds * 1_000 + millis
                val bucket = result.getOrPut(timestamp) { mutableListOf() }
                if (lyricText !in bucket) bucket.add(lyricText)
            }
        }
        return result
    }

    private fun format(timeMs: Long): String {
        val minutes = timeMs / 60_000
        val seconds = (timeMs % 60_000) / 1_000
        val hundredths = (timeMs % 1_000) / 10
        return String.format(Locale.US, "[%02d:%02d.%02d]", minutes, seconds, hundredths)
    }
}
