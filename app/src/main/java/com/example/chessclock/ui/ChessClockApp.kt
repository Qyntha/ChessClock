package com.example.chessclock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.chessclock.ChessClockViewModel
import com.example.chessclock.audio.ClockSoundPlayer
import com.example.chessclock.model.ClockSound

/**
 * 应用入口：棋钟界面 + 设置界面 + 提示音播放。
 *
 * 状态全部来自 [ChessClockViewModel]，界面只负责渲染和转发点击事件。
 */
@Composable
fun ChessClockApp(viewModel: ChessClockViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    // ---- 提示音 ----
    val context = LocalContext.current
    val soundPlayer = remember { ClockSoundPlayer(context) }
    DisposableEffect(soundPlayer) {
        onDispose { soundPlayer.release() }
    }
    LaunchedEffect(viewModel) {
        viewModel.soundEvents.collect { event ->
            when (event) {
                ClockSound.BYOYOMI_WARNING -> soundPlayer.playWarning()
                ClockSound.FLAG_FALL -> soundPlayer.playFlagFall()
            }
        }
    }

    // 设置页里按返回键先回到棋钟，而不是退出应用。
    BackHandler(enabled = showSettings) { showSettings = false }

    if (showSettings) {
        SettingsScreen(
            currentSettings = state.settings,
            presets = presets,
            hasGameProgress = state.hasGameProgress,
            onAddPreset = viewModel::addPreset,
            onRemovePreset = viewModel::removePreset,
            onApply = { settings ->
                viewModel.applySettings(settings)
                showSettings = false
            },
            onBack = { showSettings = false },
        )
    } else {
        ChessClockScreen(
            state = state,
            onPlayerTapped = viewModel::onPlayerTapped,
            onTogglePause = viewModel::togglePause,
            onReset = viewModel::reset,
            onOpenSettings = {
                // 进设置前先暂停，避免改设置时白白掉时间
                viewModel.pauseIfRunning()
                showSettings = true
            },
            onResign = viewModel::resign,
            onDraw = viewModel::declareDraw,
        )
    }
}
