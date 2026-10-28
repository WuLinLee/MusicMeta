package com.wulinlee.musicmeta

private val lyricTimestampRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")
private val lyricCreditLineRegex = Regex(
    """^\s*(?:作词|作詞|作曲|编曲|編曲|词曲|詞曲|演唱|演奏|制作人|製作人|制作|製作|监制|監製|混音|母带|母帶|录音|錄音|翻译|翻譯|译者|譯者|歌词翻译|歌詞翻譯|字幕|composer|arranger|producer|translator|translation(?:\s+by)?|translated\s+by|composed\s+by|arranged\s+by|produced\s+by|lyrics?\s+by|music\s+by|lyricist)\s*(?:[:：/\-]\s*|\s+).+""",
    RegexOption.IGNORE_CASE,
)

private fun isLyricCreditLine(text: String): Boolean = lyricCreditLineRegex.containsMatchIn(text.trim())

internal data class TimedLyricLine(val timeMs: Int, val text: String)

/** Parse only rows with explicit LRC timestamps for time-synchronized display. */
internal fun parseTimedLyricLines(raw: String): List<TimedLyricLine> {
    val lines = mutableListOf<TimedLyricLine>()
    for (line in raw.lineSequence()) {
        val matches = lyricTimestampRegex.findAll(line).toList()
        val text = lyricTimestampRegex.replace(line, "").trim()
        if (text.isBlank() || matches.isEmpty()) continue
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
    val timed = parseTimedLyricLines(activeLyrics)
    if (timed.isNotEmpty()) {
        // Filter only clearly labeled credits for display; never rewrite the underlying raw lyrics.
        return currentTimedLine(timed.filterNot { isLyricCreditLine(it.text) }, positionMs)
    }

    val plain = activeLyrics.lineSequence()
        .map { lyricTimestampRegex.replace(it, "").trim() }
        .filter { it.isNotBlank() && !isLyricCreditLine(it) }
        .toList()
    if (plain.isEmpty()) return ""

    if (isTranslation) {
        val originalTimed = parseTimedLyricLines(originalLyrics).filterNot { isLyricCreditLine(it.text) }
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
        // No reliable timeline is available: preserve the translation and show its first line.
    }
    return plain.first()
}
