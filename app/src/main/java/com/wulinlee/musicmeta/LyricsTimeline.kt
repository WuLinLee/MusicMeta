package com.wulinlee.musicmeta

private val lyricTimestampRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")

internal data class TimedLyricLine(val timeMs: Int, val text: String)
internal const val UNTITLED_TRANSLATION_SYNC_HINT = "译文没有时间戳，无法准确同步"

/** Parse every timestamped lyric row without filtering composer/arranger or other credit text. */
internal fun parseTimedLyricLines(
    raw: String,
    includeUntimedRows: Boolean = false,
): List<TimedLyricLine> {
    val lines = mutableListOf<TimedLyricLine>()
    var lastTimestampMs = 0
    for (line in raw.lineSequence()) {
        val matches = lyricTimestampRegex.findAll(line).toList()
        val text = lyricTimestampRegex.replace(line, "").trim()
        if (text.isBlank()) continue
        if (matches.isEmpty()) {
            // When the active LRC contains timestamps, retain untimed credit/lyric rows too.
            // Place them just after the preceding timed row instead of silently dropping them.
            if (includeUntimedRows) {
                lastTimestampMs = (lastTimestampMs + 1_000).coerceAtLeast(0)
                lines += TimedLyricLine(lastTimestampMs, text)
            }
            continue
        }
        for (match in matches) {
            val minutes = match.groupValues[1].toIntOrNull() ?: continue
            val seconds = match.groupValues[2].toIntOrNull() ?: continue
            if (seconds >= 60) continue
            val fraction = match.groupValues.getOrElse(3) { "" }
            val fractionMs = when (fraction.length) {
                1 -> (fraction.toIntOrNull() ?: 0) * 100
                2 -> (fraction.toIntOrNull() ?: 0) * 10
                3 -> fraction.toIntOrNull() ?: 0
                else -> 0
            }
            val timeMs = (minutes * 60 + seconds) * 1_000 + fractionMs
            lastTimestampMs = timeMs
            lines += TimedLyricLine(timeMs, text)
        }
    }
    return lines.sortedBy { it.timeMs }
}

private fun currentTimedLine(lines: List<TimedLyricLine>, positionMs: Int): String =
    lines.lastOrNull { it.timeMs <= positionMs.coerceAtLeast(0) }?.text ?: lines.firstOrNull()?.text.orEmpty()

/**
 * Prefer timestamps on the selected lyrics. Untimed translations use the original timeline
 * when available, mapping rows proportionally if the providers return different row counts.
 * This is display logic only; the underlying lyric text is never removed or rewritten.
 */
internal fun currentLyricLine(
    activeLyrics: String,
    originalLyrics: String,
    positionMs: Int,
    isTranslation: Boolean = false,
): String {
    if (activeLyrics.isBlank()) return ""
    val activeHasTimestamps = lyricTimestampRegex.containsMatchIn(activeLyrics)
    val timed = parseTimedLyricLines(activeLyrics, includeUntimedRows = activeHasTimestamps)
    if (timed.isNotEmpty()) return currentTimedLine(timed, positionMs)

    val plain = activeLyrics.lineSequence()
        .map { lyricTimestampRegex.replace(it, "").trim() }
        .filter { it.isNotBlank() }
        .toList()
    if (plain.isEmpty()) return ""

    if (isTranslation) {
        val originalTimed = parseTimedLyricLines(originalLyrics)
        if (originalTimed.isNotEmpty()) {
            // Translation providers often return plain translated text with a different number
            // of rows than the original LRC (credits, merged verses, etc.). Map the active
            // original timestamp row proportionally onto the translation rows rather than
            // pinning the translation to row zero or dropping credit lines.
            val originalIndex = originalTimed.indexOfLast { it.timeMs <= positionMs.coerceAtLeast(0) }
                .coerceAtLeast(0)
            val translatedIndex = ((originalIndex.toLong() * plain.size) / originalTimed.size)
                .toInt().coerceIn(0, plain.lastIndex)
            return plain[translatedIndex]
        }
        if (plain.size > 1) return UNTITLED_TRANSLATION_SYNC_HINT
    }
    return plain.first()
}
