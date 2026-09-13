package com.example.chessclock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 棋钟固定使用深色配色（不用动态取色，保证绿色高亮 / 橙色读秒 / 红色告警的对比度一致）。
 */
private val ChessClockColorScheme = darkColorScheme(
    primary = ClockGreen,
    onPrimary = Color(0xFF06210F),
    primaryContainer = ClockGreenDark,
    onPrimaryContainer = ClockGreenBright,
    secondary = ClockTextSecondary,
    onSecondary = Color(0xFF0A0E0C),
    tertiary = ClockOrange,
    onTertiary = Color(0xFF241300),
    background = ClockBackground,
    onBackground = ClockTextPrimary,
    surface = ClockSurface,
    onSurface = ClockTextPrimary,
    surfaceVariant = ClockCardIdle,
    onSurfaceVariant = ClockTextSecondary,
    outline = ClockBorderIdle,
    error = ClockRed,
    onError = Color(0xFF2B0707),
)

@Composable
fun ChessClockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ChessClockColorScheme,
        typography = Typography,
        content = content,
    )
}
