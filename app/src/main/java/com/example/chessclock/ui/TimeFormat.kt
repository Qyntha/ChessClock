package com.example.chessclock.ui

import java.util.Locale

/**
 * 主时间格式化：
 * - 不足 1 小时 → mm:ss
 * - 超过 1 小时 → h:mm:ss
 *
 * 秒数向上取整，这样只有真正走完才会显示 00:00。
 */
fun formatMainTime(remainingMs: Long): String {
    val totalSeconds = (remainingMs.coerceAtLeast(0L) + 999L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/**
 * 读秒格式化：始终保留一位小数（例如 10.0 / 4.3 / 0.0），向上取整到 0.1 秒。
 */
fun formatByoyomiTime(remainingMs: Long): String {
    val tenths = (remainingMs.coerceAtLeast(0L) + 99L) / 100L
    return String.format(Locale.US, "%d.%d", tenths / 10L, tenths % 10L)
}
