package com.wulinlee.musicmeta

import java.util.Locale

internal enum class LocalMusicSortMode(
    val description: String,
    val buttonLabel: String,
) {
    NEWEST_FIRST("由新到旧", "新→旧"),
    OLDEST_FIRST("由旧到新", "旧→新"),
    TITLE_A_TO_Z("原始 A–Z", "A–Z");

    fun next(): LocalMusicSortMode = when (this) {
        NEWEST_FIRST -> OLDEST_FIRST
        OLDEST_FIRST -> TITLE_A_TO_Z
        TITLE_A_TO_Z -> NEWEST_FIRST
    }

    companion object {
        fun fromPreference(value: String?): LocalMusicSortMode =
            values().firstOrNull { it.name == value } ?: NEWEST_FIRST
    }
}

internal fun <T> sortLocalMusicItems(
    items: List<T>,
    mode: LocalMusicSortMode,
    modifiedAtMs: (T) -> Long,
    title: (T) -> String,
): List<T> {
    val indexed = items.withIndex()
    if (mode == LocalMusicSortMode.TITLE_A_TO_Z) {
        return indexed.sortedWith(
            compareBy<IndexedValue<T>> { title(it.value).lowercase(Locale.ROOT) }
                .thenBy { it.index },
        ).map { it.value }
    }
    return indexed.sortedWith(Comparator { left, right ->
        val leftTime = modifiedAtMs(left.value)
        val rightTime = modifiedAtMs(right.value)
        val leftKnown = leftTime > 0L
        val rightKnown = rightTime > 0L
        when {
            leftKnown && !rightKnown -> -1
            !leftKnown && rightKnown -> 1
            leftKnown && rightKnown && leftTime != rightTime ->
                if (mode == LocalMusicSortMode.NEWEST_FIRST) rightTime.compareTo(leftTime)
                else leftTime.compareTo(rightTime)
            else -> {
                val byTitle = title(left.value).compareTo(title(right.value), ignoreCase = true)
                if (byTitle != 0) byTitle else left.index.compareTo(right.index)
            }
        }
    }).map { it.value }
}
