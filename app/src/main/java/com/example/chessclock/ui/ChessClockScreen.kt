package com.example.chessclock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chessclock.R
import com.example.chessclock.model.ChessClockState
import com.example.chessclock.model.ClockPhase
import com.example.chessclock.model.ClockSettings
import com.example.chessclock.model.EndReason
import com.example.chessclock.model.GameResult
import com.example.chessclock.model.GameStatus
import com.example.chessclock.model.PlayerClock
import com.example.chessclock.ui.theme.ChessClockTheme
import com.example.chessclock.ui.theme.ClockBackground
import com.example.chessclock.ui.theme.ClockBorderIdle
import com.example.chessclock.ui.theme.ClockCardActive
import com.example.chessclock.ui.theme.ClockCardIdle
import com.example.chessclock.ui.theme.ClockGreen
import com.example.chessclock.ui.theme.ClockOrange
import com.example.chessclock.ui.theme.ClockRed
import com.example.chessclock.ui.theme.ClockSurface
import com.example.chessclock.ui.theme.ClockTextPrimary
import com.example.chessclock.ui.theme.ClockTextSecondary

/** 等待方整体压暗成这个透明度。 */
private const val DIM_ALPHA = 0.42f

/**
 * 棋钟主界面：左右两张卡片 + 中间控制栏。
 */
@Composable
fun ChessClockScreen(
    state: ChessClockState,
    onPlayerTapped: (Int) -> Unit,
    onTogglePause: () -> Unit,
    onReset: () -> Unit,
    onOpenSettings: () -> Unit,
    onResign: (Int) -> Unit,
    onDraw: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showResetConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ClockBackground)
            .safeDrawingPadding()
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlayerCard(
                playerIndex = ClockSettings.PLAYER_ONE,
                clock = state.playerOne,
                state = state,
                onTap = { onPlayerTapped(ClockSettings.PLAYER_ONE) },
                onResign = { onResign(ClockSettings.PLAYER_ONE) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )

            ControlColumn(
                state = state,
                onTogglePause = onTogglePause,
                onReset = {
                    // 点“重置”一律先确认，避免误触把对局清零
                    showResetConfirm = true
                },
                onOpenSettings = onOpenSettings,
                onDraw = onDraw,
                modifier = Modifier
                    .width(140.dp)
                    .fillMaxHeight(),
            )

            PlayerCard(
                playerIndex = ClockSettings.PLAYER_TWO,
                clock = state.playerTwo,
                state = state,
                onTap = { onPlayerTapped(ClockSettings.PLAYER_TWO) },
                onResign = { onResign(ClockSettings.PLAYER_TWO) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }

    if (showResetConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.reset_confirm_title),
            message = stringResource(R.string.reset_confirm_message),
            onConfirm = {
                showResetConfirm = false
                onReset()
            },
            onDismiss = { showResetConfirm = false },
        )
    }

    GameOverDialogHost(state = state, onNewGame = onReset)
}

/**
 * 玩家卡片：整张卡片就是“走子按钮”，右上角有本方的认输按钮。
 */
@Composable
private fun PlayerCard(
    playerIndex: Int,
    clock: PlayerClock,
    state: ChessClockState,
    onTap: () -> Unit,
    onResign: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    val isActive = !state.isFinished && state.activePlayer == playerIndex
    val isWinner = state.isFinished && state.winner == playerIndex
    val highlighted = isActive || isWinner

    val shape = RoundedCornerShape(28.dp)
    val cardColor = if (highlighted) ClockCardActive else ClockCardIdle
    val borderColor = if (highlighted) ClockGreen else ClockBorderIdle
    val borderWidth = if (highlighted) 3.dp else 1.dp
    val contentAlpha = if (highlighted) 1f else DIM_ALPHA

    Box(
        modifier = modifier
            .clip(shape)
            .background(cardColor)
            .border(borderWidth, borderColor, shape)
            .clickable(onClick = onTap),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            // 大号数字按卡片尺寸自适应，避免窄屏溢出。
            val timeFontSize = with(density) {
                minOf(maxWidth * 0.27f, maxHeight * 0.38f)
                    .coerceIn(40.dp, 180.dp)
                    .toSp()
            }

            val urgent = clock.phase == ClockPhase.BYOYOMI &&
                clock.byoyomiRemainingMs <= ChessClockState.URGENT_THRESHOLD_MS
            // 50ms 的 tick 直接拿来算闪烁；暂停/结束时不再闪，只显示红色。
            val blinkOn = !state.isRunning ||
                (state.tick / ChessClockState.BLINK_PERIOD_MS) % 2L == 0L

            val timeColor = when {
                urgent -> if (blinkOn) ClockRed else ClockRed.copy(alpha = 0.10f)
                clock.phase == ClockPhase.BYOYOMI -> ClockOrange
                else -> ClockTextPrimary
            }
            val timeText = if (clock.phase == ClockPhase.MAIN) {
                formatMainTime(clock.mainRemainingMs)
            } else {
                formatByoyomiTime(clock.byoyomiRemainingMs)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .alpha(contentAlpha),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = settings.nameOf(playerIndex),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (highlighted) ClockGreen else ClockTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PhaseChip(
                                text = when {
                                    clock.phase == ClockPhase.BYOYOMI ->
                                        stringResource(R.string.chip_byoyomi_running)

                                    settings.byoyomiEnabled ->
                                        stringResource(R.string.chip_byoyomi_reserve, settings.byoyomiSeconds)

                                    else -> stringResource(R.string.chip_byoyomi_off)
                                },
                                color = if (clock.phase == ClockPhase.BYOYOMI) ClockOrange else ClockTextSecondary,
                            )
                            if (settings.fischerEnabled) {
                                PhaseChip(
                                    text = stringResource(R.string.chip_fischer, settings.fischerSeconds),
                                    color = ClockGreen,
                                )
                            }
                        }
                    }

                    // 认输只在“对局已经开始且还没结束”时可用，避免还没开始就误触
                    if (state.status != GameStatus.IDLE && !state.isFinished) {
                        TextButton(
                            onClick = onResign,
                            modifier = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.action_resign),
                                color = ClockRed,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (clock.phase == ClockPhase.MAIN) {
                            stringResource(R.string.caption_main_time)
                        } else {
                            stringResource(R.string.caption_byoyomi, settings.byoyomiSeconds)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = ClockTextSecondary,
                    )
                    Text(
                        text = timeText,
                        fontSize = timeFontSize,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = timeColor,
                        maxLines = 1,
                    )
                }

                Text(
                    text = statusLine(state = state, playerIndex = playerIndex, isActive = isActive),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (highlighted) ClockGreen else ClockTextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun statusLine(state: ChessClockState, playerIndex: Int, isActive: Boolean): String {
    val result = state.result
    return when {
        result != null && result.isDraw -> stringResource(R.string.badge_draw)
        result != null && result.winner == playerIndex -> stringResource(R.string.badge_winner)
        result != null && result.loser == playerIndex -> stringResource(R.string.badge_loser)
        result != null -> stringResource(R.string.status_finished)

        isActive && state.status == GameStatus.RUNNING -> stringResource(R.string.hint_tap_to_move)
        isActive && state.status == GameStatus.PAUSED -> stringResource(R.string.hint_tap_to_resume)
        isActive -> stringResource(R.string.hint_tap_to_start)

        else -> stringResource(R.string.hint_waiting)
    }
}

@Composable
private fun PhaseChip(text: String, color: Color) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.55f), shape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
        )
    }
}

/**
 * 中间控制栏：状态、暂停/继续、和棋、重置、设置、手数。
 */
@Composable
private fun ControlColumn(
    state: ChessClockState,
    onTogglePause: () -> Unit,
    onReset: () -> Unit,
    onOpenSettings: () -> Unit,
    onDraw: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusText = when (state.status) {
        GameStatus.IDLE -> stringResource(R.string.status_idle)
        GameStatus.RUNNING -> stringResource(R.string.status_running)
        GameStatus.PAUSED -> stringResource(R.string.status_paused)
        GameStatus.FINISHED -> stringResource(R.string.status_finished)
    }
    val toggleText = when (state.status) {
        GameStatus.RUNNING -> stringResource(R.string.action_pause)
        GameStatus.PAUSED -> stringResource(R.string.action_resume)
        GameStatus.IDLE -> stringResource(R.string.action_start)
        GameStatus.FINISHED -> stringResource(R.string.action_new_game)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelLarge,
            color = if (state.status == GameStatus.RUNNING) ClockGreen else ClockTextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = if (state.status == GameStatus.FINISHED) onReset else onTogglePause,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ClockGreen,
                contentColor = Color(0xFF06210F),
            ),
        ) {
            Text(text = toggleText, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onDraw,
            enabled = state.status != GameStatus.IDLE && !state.isFinished,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(text = stringResource(R.string.action_draw))
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onReset,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(text = stringResource(R.string.action_reset))
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(text = stringResource(R.string.action_settings))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.move_count, state.moveCount),
            style = MaterialTheme.typography.labelMedium,
            color = ClockTextSecondary,
        )
    }
}

/** 通用确认弹窗。 */
@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ClockSurface,
        titleContentColor = ClockTextPrimary,
        textContentColor = ClockTextSecondary,
        title = { Text(text = title, fontWeight = FontWeight.Bold) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.action_confirm), color = ClockGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel), color = ClockTextSecondary)
            }
        },
    )
}

/**
 * 终局弹窗：可以“再来一局”，也可以先关掉看看双方剩余时间。
 */
@Composable
private fun GameOverDialogHost(state: ChessClockState, onNewGame: () -> Unit) {
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(state.status, state.result) {
        if (!state.isFinished) dismissed = false
    }

    val result = state.result
    if (!state.isFinished || result == null || dismissed) return

    AlertDialog(
        onDismissRequest = { dismissed = true },
        containerColor = ClockSurface,
        titleContentColor = ClockTextPrimary,
        textContentColor = ClockTextSecondary,
        title = {
            Text(
                text = stringResource(R.string.game_over_title),
                fontWeight = FontWeight.Bold,
            )
        },
        text = { Text(text = resultMessage(state = state, result = result)) },
        confirmButton = {
            TextButton(onClick = onNewGame) {
                Text(text = stringResource(R.string.action_new_game), color = ClockGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = { dismissed = true }) {
                Text(text = stringResource(R.string.action_view_board), color = ClockTextSecondary)
            }
        },
    )
}

@Composable
private fun resultMessage(state: ChessClockState, result: GameResult): String {
    if (result.isDraw) return stringResource(R.string.result_draw)

    val loserIndex = result.loser ?: return stringResource(R.string.result_draw)
    val winnerIndex = result.winner ?: return stringResource(R.string.result_draw)
    val loserName = state.settings.nameOf(loserIndex)
    val winnerName = state.settings.nameOf(winnerIndex)

    return when (result.reason) {
        EndReason.TIMEOUT_MAIN ->
            stringResource(R.string.result_timeout_main, loserName, winnerName)

        EndReason.TIMEOUT_BYOYOMI ->
            stringResource(R.string.result_timeout_byoyomi, loserName, winnerName)

        EndReason.RESIGN ->
            stringResource(R.string.result_resign, loserName, winnerName)

        EndReason.DRAW -> stringResource(R.string.result_draw)
    }
}

@Preview(name = "棋钟 · 横屏", widthDp = 800, heightDp = 360, showBackground = true)
@Composable
private fun ChessClockScreenPreview() {
    ChessClockTheme {
        ChessClockScreen(
            state = ChessClockState(
                settings = ClockSettings(
                    mainTimeEnabled = true,
                    mainTimeMs = 5 * 60_000L,
                    byoyomiEnabled = true,
                    byoyomiMs = 10_000L,
                    fischerEnabled = true,
                    fischerMs = 5_000L,
                    soundEnabled = true,
                    playerOneName = "玩家 1",
                    playerTwoName = "玩家 2",
                ),
                status = GameStatus.RUNNING,
                activePlayer = ClockSettings.PLAYER_ONE,
                playerOne = PlayerClock(
                    mainRemainingMs = 4 * 60_000L + 12_000L,
                    byoyomiRemainingMs = 10_000L,
                ),
                playerTwo = PlayerClock(
                    mainRemainingMs = 0L,
                    byoyomiRemainingMs = 3_400L,
                    phase = ClockPhase.BYOYOMI,
                ),
                moveCount = 24,
                tick = 0L,
                lastTickRealtime = 0L,
            ),
            onPlayerTapped = {},
            onTogglePause = {},
            onReset = {},
            onOpenSettings = {},
            onResign = {},
            onDraw = {},
        )
    }
}
