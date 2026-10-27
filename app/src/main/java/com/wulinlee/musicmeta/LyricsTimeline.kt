package com.wulinlee.musicmeta

private val lyricTimestampRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")

internal data class TimedLyricLine(val timeMs: Int, val text: String)
internal const val UNTITLED_TRANSLATION_SYNC_HINT = "译文没有时间戳，无法准确同步"

/** Parse every timestamped lyric row without filtering composer/arranger or other credit text. */
internal fun parseTimedLyricLines(raw: String): List<TimedLyricLine> {
    val lines = mutableListOf<TimedLyricLine>()
    for (line in raw.lineSequence()) {
        val matches = lyricTimestampRegex.findAll(line).toList()
        if (matches.isEmpty()) continue
        val text = lyricTimestampRegex.replace(line, "").trim()
        if (text.isBlank()) continue
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
            lines += TimedLyricLine((minutes * 60 + seconds) * 1_000 + fractionMs, text)
        }
    }
    return lines.sortedBy { it.timeMs }
}

private fun currentTimedLine(lines: List<TimedLyricLine>, positionMs: Int): String =
    lines.lastOrNull { it.timeMs <= positionMs.coerceAtLeast(0) }?.text ?: lines.firstOrNull()?.text.orEmpty()

/**
 * Prefer timestamps on the selected lyrics. Untimed translations may use the original timeline
 * only if every row matches one-to-one; otherwise show a clear limitation, not a stuck first line.
 * This is display logic only; the underlying lyric text is never removed or rewritten.
 */
internal fun currentLyricLine(
    activeLyrics: String,
    originalLyrics: String,
    positionMs: Int,
    isTranslation: Boolean = false,
): String {
    if (activeLyrics.isBlank()) return ""
    val timed = parseTimedLyricLines(activeLyrics)
    if (timed.isNotEmpty()) return currentTimedLine(timed, positionMs)

    val plain = activeLyrics.lineSequence()
        .map { lyricTimestampRegex.replace(it, "").trim() }
        .filter { it.isNotBlank() }
        .toList()
    if (plain.isEmpty()) return ""

    if (isTranslation) {
        val originalTimed = parseTimedLyricLines(originalLyrics)
        if (originalTimed.isNotEmpty()) {
            if (plain.size != originalTimed.size) return UNTITLED_TRANSLATION_SYNC_HINT
            return currentTimedLine(originalTimed.mapIndexed { index, line -> line.copy(text = plain[index]) }, positionMs)
        }
        if (plain.size > 1) return UNTITLED_TRANSLATION_SYNC_HINT
    }
    return plain.first()
}
