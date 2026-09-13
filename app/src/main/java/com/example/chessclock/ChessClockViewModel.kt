package com.example.chessclock

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chessclock.model.ChessClockState
import com.example.chessclock.model.ClockPhase
import com.example.chessclock.model.ClockPresets
import com.example.chessclock.model.ClockSettings
import com.example.chessclock.model.ClockSound
import com.example.chessclock.model.EndReason
import com.example.chessclock.model.GameResult
import com.example.chessclock.model.GameStatus
import com.example.chessclock.model.PlayerClock
import com.example.chessclock.model.PresetKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 双人棋钟的全部计时逻辑。
 *
 * 计时规则（标准读秒保持不变）：
 * 1. 每方先走主时间；轮到谁就把谁的时间按真实流逝的时间扣掉。
 * 2. 某方主时间用完后立刻进入读秒阶段：该方这一步有 N 秒，超出的部分从读秒里继续扣。
 * 3. 读秒阶段每走完一步，下一次轮到自己时读秒都重置成完整的 N 秒。
 * 4. 读秒在轮到该方时走完仍未走子 → 该方判负。
 * 5. 读秒被关闭时，主时间走完直接判负。
 * 6. 主时间被关闭时为纯读秒模式：每步 N 秒，走完重置，走不完判负。
 * 7. Fischer 加秒：每走完一步给主时间 +N 秒（可累积）；主时间已用完并处于读秒时，
 *    加秒会把主时间加回来，该方回到主时间阶段。
 *
 * 所有时间都基于 [SystemClock.elapsedRealtime]（单调时钟，不受系统时间修改影响），
 * 协程每 [TICK_INTERVAL_MS] 毫秒刷新一次界面，并按“两次刷新的真实差值”扣时间，因此不会累积漂移。
 */
class ChessClockViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(newGame(loadSettings()))

    /** 界面唯一的数据源。 */
    val state: StateFlow<ChessClockState> = _state.asStateFlow()

    /** 提示音事件流（读秒提醒 / 时间到判负）。 */
    private val _soundEvents = MutableSharedFlow<ClockSound>(extraBufferCapacity = 8)
    val soundEvents: SharedFlow<ClockSound> = _soundEvents.asSharedFlow()

    /** 三组用户自定义常用值（改动立刻写盘，不受“开始新对局”影响）。 */
    private val _presets = MutableStateFlow(loadPresets())
    val presets: StateFlow<ClockPresets> = _presets.asStateFlow()

    private var tickerJob: Job? = null

    /** 上一次被接受的点击时间，用于 250ms 防误触。 */
    private var lastTapRealtimeMs = 0L

    /** 上一次已经响过的读秒秒数（5、4、3、2、1），保证每秒只响一次。 */
    private var lastWarnSecond: Int? = null

    // ---------------------------------------------------------------------
    // 对外操作
    // ---------------------------------------------------------------------

    /**
     * 玩家点击自己那张卡片。
     *
     * - 对局进行中：走子 → 结算本方用时 → 交换走棋方（250ms 内重复点击会被忽略）。
     * - 未开始 / 已暂停：开始或继续计时，不交换走棋方。
     * - 点到对方卡片，或者对局已结束：忽略。
     */
    fun onPlayerTapped(player: Int) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTapRealtimeMs < TAP_DEBOUNCE_MS) return

        val snapshot = _state.value
        when (snapshot.status) {
            GameStatus.FINISHED -> return

            GameStatus.IDLE, GameStatus.PAUSED -> {
                if (player != snapshot.activePlayer) return
                startOrResume(now)
            }

            GameStatus.RUNNING -> {
                if (player != snapshot.activePlayer) return
                lastTapRealtimeMs = now
                switchTurn(now)
            }
        }
    }

    /** 中间按钮：开始 / 暂停 / 继续。 */
    fun togglePause() {
        when (_state.value.status) {
            GameStatus.IDLE, GameStatus.PAUSED -> startOrResume(SystemClock.elapsedRealtime())
            GameStatus.RUNNING -> pause()
            GameStatus.FINISHED -> Unit
        }
    }

    /** 进设置页之前调用：正在计时就先暂停，避免改设置时白白掉时间。 */
    fun pauseIfRunning() {
        if (_state.value.status == GameStatus.RUNNING) pause()
    }

    /** 重置成新的一局（配置不变，玩家 1 先走）。 */
    fun reset() {
        stopTicker()
        lastTapRealtimeMs = 0L
        lastWarnSecond = null
        _state.value = newGame(_state.value.settings)
    }

    /** 认输：对方获胜。 */
    fun resign(player: Int) {
        finishGame(EndReason.RESIGN, loser = player)
    }

    /** 和棋：双方平局。 */
    fun declareDraw() {
        finishGame(EndReason.DRAW, loser = null)
    }

    /** 应用设置页的修改并重置对局。 */
    fun applySettings(settings: ClockSettings) {
        val normalized = settings.normalized()
        saveSettings(normalized)
        stopTicker()
        lastTapRealtimeMs = 0L
        lastWarnSecond = null
        _state.value = newGame(normalized)
    }

    /** 添加一个常用值（超出范围会被夹到范围内，重复值忽略）。 */
    fun addPreset(kind: PresetKind, value: Int) {
        val range = ClockPresets.rangeOf(kind)
        val clamped = value.coerceIn(range.first, range.last)
        val current = _presets.value
        if (clamped in current.of(kind)) return
        val updated = current.with(kind, (current.of(kind) + clamped).sorted())
        _presets.value = updated
        savePresets(updated)
    }

    /** 删除一个常用值。 */
    fun removePreset(kind: PresetKind, value: Int) {
        val current = _presets.value
        val updated = current.with(kind, current.of(kind) - value)
        if (updated == current) return
        _presets.value = updated
        savePresets(updated)
    }

    override fun onCleared() {
        super.onCleared()
        stopTicker()
    }

    // ---------------------------------------------------------------------
    // 内部实现
    // ---------------------------------------------------------------------

    private fun loadSettings(): ClockSettings {
        val defaults = ClockSettings.DEFAULT
        return ClockSettings(
            mainTimeEnabled = prefs.getBoolean(KEY_MAIN_ENABLED, defaults.mainTimeEnabled),
            mainTimeMs = prefs.getLong(KEY_MAIN_TIME_MS, defaults.mainTimeMs),
            byoyomiEnabled = prefs.getBoolean(KEY_BYOYOMI_ENABLED, defaults.byoyomiEnabled),
            byoyomiMs = prefs.getLong(KEY_BYOYOMI_MS, defaults.byoyomiMs),
            fischerEnabled = prefs.getBoolean(KEY_FISCHER_ENABLED, defaults.fischerEnabled),
            fischerMs = prefs.getLong(KEY_FISCHER_MS, defaults.fischerMs),
            soundEnabled = prefs.getBoolean(KEY_SOUND_ENABLED, defaults.soundEnabled),
            playerOneName = prefs.getString(KEY_PLAYER_ONE_NAME, null) ?: defaults.playerOneName,
            playerTwoName = prefs.getString(KEY_PLAYER_TWO_NAME, null) ?: defaults.playerTwoName,
        ).normalized()
    }

    private fun saveSettings(settings: ClockSettings) {
        prefs.edit {
            putBoolean(KEY_MAIN_ENABLED, settings.mainTimeEnabled)
            putLong(KEY_MAIN_TIME_MS, settings.mainTimeMs)
            putBoolean(KEY_BYOYOMI_ENABLED, settings.byoyomiEnabled)
            putLong(KEY_BYOYOMI_MS, settings.byoyomiMs)
            putBoolean(KEY_FISCHER_ENABLED, settings.fischerEnabled)
            putLong(KEY_FISCHER_MS, settings.fischerMs)
            putBoolean(KEY_SOUND_ENABLED, settings.soundEnabled)
            putString(KEY_PLAYER_ONE_NAME, settings.playerOneName)
            putString(KEY_PLAYER_TWO_NAME, settings.playerTwoName)
        }
    }

    private fun loadPresets(): ClockPresets {
        val defaults = ClockPresets()
        return ClockPresets(
            main = prefs.getString(KEY_PRESET_MAIN, null)
                ?.toPresetList(ClockPresets.rangeOf(PresetKind.MAIN))
                ?: defaults.main,
            byoyomi = prefs.getString(KEY_PRESET_BYOYOMI, null)
                ?.toPresetList(ClockPresets.rangeOf(PresetKind.BYOYOMI))
                ?: defaults.byoyomi,
            fischer = prefs.getString(KEY_PRESET_FISCHER, null)
                ?.toPresetList(ClockPresets.rangeOf(PresetKind.FISCHER))
                ?: defaults.fischer,
        )
    }

    private fun savePresets(presets: ClockPresets) {
        prefs.edit {
            putString(KEY_PRESET_MAIN, presets.main.joinToString(separator = ","))
            putString(KEY_PRESET_BYOYOMI, presets.byoyomi.joinToString(separator = ","))
            putString(KEY_PRESET_FISCHER, presets.fischer.joinToString(separator = ","))
        }
    }

    /** 解析形如 “5,10,15” 的字符串：丢掉非法值、去重、排序。 */
    private fun String.toPresetList(range: IntRange): List<Int> =
        split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in range }
            .distinct()
            .sorted()

    private fun newGame(settings: ClockSettings): ChessClockState {
        val now = SystemClock.elapsedRealtime()
        val fresh = PlayerClock(
            mainRemainingMs = if (settings.mainTimeEnabled) settings.mainTimeMs else 0L,
            byoyomiRemainingMs = settings.byoyomiMs,
            // 主时间关闭 = 纯读秒，开局就处于读秒阶段
            phase = if (settings.mainTimeEnabled) ClockPhase.MAIN else ClockPhase.BYOYOMI,
        )
        return ChessClockState(
            settings = settings,
            status = GameStatus.IDLE,
            activePlayer = ClockSettings.PLAYER_ONE,
            playerOne = fresh,
            playerTwo = fresh,
            moveCount = 0,
            result = null,
            tick = now,
            lastTickRealtime = now,
        )
    }

    private fun startOrResume(now: Long) {
        lastTapRealtimeMs = now
        stopTicker()
        _state.value = _state.value.copy(
            status = GameStatus.RUNNING,
            lastTickRealtime = now,
            tick = now,
        )
        startTicker()
    }

    private fun pause() {
        val now = SystemClock.elapsedRealtime()
        // 先把“上一次刷新到此刻”的时间结算掉，避免暂停时白送时间。
        val expired = settle(now)
        stopTicker()
        if (!expired) {
            _state.value = _state.value.copy(status = GameStatus.PAUSED)
        }
        lastTapRealtimeMs = now
    }

    private fun finishGame(reason: EndReason, loser: Int?) {
        val snapshot = _state.value
        if (snapshot.status == GameStatus.FINISHED) return
        if (snapshot.status == GameStatus.RUNNING) {
            // 先把当前这一小段走完，界面上的剩余时间才是准的
            settle(SystemClock.elapsedRealtime())
        }
        val current = _state.value
        if (current.status == GameStatus.FINISHED) return
        stopTicker()
        lastWarnSecond = null
        _state.value = current.copy(
            status = GameStatus.FINISHED,
            result = GameResult(reason = reason, loser = loser),
        )
    }

    /** 走子：结算本方用时 → 重置本方读秒 → 加秒 → 交换走棋方。 */
    private fun switchTurn(now: Long) {
        // 这一拍里读秒刚好走完，直接判负。
        if (settle(now)) return

        val snapshot = _state.value
        val settings = snapshot.settings
        val mover = snapshot.activePlayer
        val next = 1 - mover

        val moverClock = snapshot.clockOf(mover)
        // 走子方处于读秒阶段：走完这一步，读秒立即重置为完整的 N 秒。
        val resetMover = if (moverClock.phase == ClockPhase.BYOYOMI) {
            moverClock.copy(byoyomiRemainingMs = settings.byoyomiMs)
        } else {
            moverClock
        }
        // Fischer 加秒（主时间关闭时不生效）
        val settledMover = applyFischer(resetMover, settings)

        val nextClock = snapshot.clockOf(next)
        // 接手方处于读秒阶段（含纯读秒模式）：这一步从完整的 N 秒开始。
        val startedNext = if (nextClock.phase == ClockPhase.BYOYOMI) {
            nextClock.copy(byoyomiRemainingMs = settings.byoyomiMs)
        } else {
            nextClock
        }

        lastWarnSecond = null
        _state.value = snapshot.copy(
            activePlayer = next,
            playerOne = if (mover == ClockSettings.PLAYER_ONE) settledMover else startedNext,
            playerTwo = if (mover == ClockSettings.PLAYER_TWO) settledMover else startedNext,
            moveCount = snapshot.moveCount + 1,
            lastTickRealtime = now,
            tick = now,
        )
    }

    private fun applyFischer(clock: PlayerClock, settings: ClockSettings): PlayerClock {
        if (!settings.fischerEnabled || !settings.mainTimeEnabled) return clock
        val newMain = clock.mainRemainingMs + settings.fischerMs
        return if (clock.phase == ClockPhase.BYOYOMI && newMain > 0L) {
            // 加秒把主时间加回来了：回到主时间阶段，读秒重新蓄满
            clock.copy(
                mainRemainingMs = newMain,
                byoyomiRemainingMs = settings.byoyomiMs,
                phase = ClockPhase.MAIN,
            )
        } else {
            clock.copy(mainRemainingMs = newMain)
        }
    }

    /**
     * 把 [now] 之前流逝的时间从当前走棋方扣除。
     *
     * @return true 表示这一拍里时间走完，对局已经判负结束。
     */
    private fun settle(now: Long): Boolean {
        val snapshot = _state.value
        // 只有正在走表时才扣时间，避免重置/暂停后协程还在偷偷扣秒。
        if (snapshot.status != GameStatus.RUNNING) {
            _state.value = snapshot.copy(lastTickRealtime = now, tick = now)
            return false
        }
        val delta = now - snapshot.lastTickRealtime
        if (delta <= 0L) {
            _state.value = snapshot.copy(lastTickRealtime = now, tick = now)
            return false
        }

        val player = snapshot.activePlayer
        val (updated, endReason) = drain(snapshot.clockOf(player), delta, snapshot.settings)

        val moved = snapshot.copy(
            playerOne = if (player == ClockSettings.PLAYER_ONE) updated else snapshot.playerOne,
            playerTwo = if (player == ClockSettings.PLAYER_TWO) updated else snapshot.playerTwo,
            lastTickRealtime = now,
            tick = now,
        )
        if (endReason == null) {
            _state.value = moved
            updateWarningSound(moved)
            return false
        }

        _state.value = moved.copy(
            status = GameStatus.FINISHED,
            result = GameResult(reason = endReason, loser = player),
        )
        lastWarnSecond = null
        emitSound(ClockSound.FLAG_FALL)
        return true
    }

    /**
     * 扣除 [deltaMs] 毫秒。主时间不足时自动溢出到读秒；读秒也走完则判负。
     *
     * @return 更新后的计时数据，以及“因为什么判负”（没结束则为 null）。
     */
    private fun drain(
        clock: PlayerClock,
        deltaMs: Long,
        settings: ClockSettings,
    ): Pair<PlayerClock, EndReason?> {
        return when (clock.phase) {
            ClockPhase.MAIN -> {
                val remaining = clock.mainRemainingMs - deltaMs
                when {
                    remaining > 0L -> clock.copy(mainRemainingMs = remaining) to null

                    // 读秒关闭：主时间走完直接判负
                    !settings.byoyomiEnabled ->
                        clock.copy(mainRemainingMs = 0L) to EndReason.TIMEOUT_MAIN

                    else -> {
                        // 主时间用尽 → 立刻进入读秒，溢出的部分直接从读秒里扣
                        val byoyomiRemaining = settings.byoyomiMs + remaining
                        clock.copy(
                            mainRemainingMs = 0L,
                            byoyomiRemainingMs = byoyomiRemaining.coerceAtLeast(0L),
                            phase = ClockPhase.BYOYOMI,
                        ) to if (byoyomiRemaining <= 0L) EndReason.TIMEOUT_BYOYOMI else null
                    }
                }
            }

            ClockPhase.BYOYOMI -> {
                val remaining = clock.byoyomiRemainingMs - deltaMs
                if (remaining > 0L) {
                    clock.copy(byoyomiRemainingMs = remaining) to null
                } else {
                    clock.copy(byoyomiRemainingMs = 0L) to EndReason.TIMEOUT_BYOYOMI
                }
            }
        }
    }

    /**
     * 读秒剩余 ≤5 秒时，每跨过一个整秒响一次提示音。
     * 每次交换走棋方、重置、暂停都会把记录清空。
     */
    private fun updateWarningSound(state: ChessClockState) {
        if (!state.settings.soundEnabled || state.status != GameStatus.RUNNING) {
            lastWarnSecond = null
            return
        }
        val clock = state.clockOf(state.activePlayer)
        val remaining = clock.byoyomiRemainingMs
        if (clock.phase != ClockPhase.BYOYOMI || remaining > ChessClockState.URGENT_THRESHOLD_MS) {
            lastWarnSecond = null
            return
        }
        val second = ((remaining + 999L) / 1_000L).toInt()
        if (second in 1..5 && lastWarnSecond != second) {
            lastWarnSecond = second
            emitSound(ClockSound.BYOYOMI_WARNING)
        }
    }

    private fun emitSound(sound: ClockSound) {
        if (!_state.value.settings.soundEnabled) return
        _soundEvents.tryEmit(sound)
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(TICK_INTERVAL_MS)
                if (settle(SystemClock.elapsedRealtime())) break
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    companion object {
        /** 界面刷新间隔：50ms。 */
        const val TICK_INTERVAL_MS = 50L

        /** 走子防误触间隔：250ms。 */
        const val TAP_DEBOUNCE_MS = 250L

        private const val PREFS_NAME = "chess_clock_settings"
        private const val KEY_MAIN_ENABLED = "main_time_enabled"
        private const val KEY_MAIN_TIME_MS = "main_time_ms"
        private const val KEY_BYOYOMI_ENABLED = "byoyomi_enabled"
        private const val KEY_BYOYOMI_MS = "byoyomi_ms"
        private const val KEY_FISCHER_ENABLED = "fischer_enabled"
        private const val KEY_FISCHER_MS = "fischer_ms"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_PLAYER_ONE_NAME = "player_one_name"
        private const val KEY_PLAYER_TWO_NAME = "player_two_name"
        private const val KEY_PRESET_MAIN = "preset_main_minutes"
        private const val KEY_PRESET_BYOYOMI = "preset_byoyomi_seconds"
        private const val KEY_PRESET_FISCHER = "preset_fischer_seconds"
    }
}
