package com.example.chessclock.model

/** 对局所处的状态。 */
enum class GameStatus {
    /** 还没开始（或刚重置）。 */
    IDLE,

    /** 正在计时。 */
    RUNNING,

    /** 已暂停。 */
    PAUSED,

    /** 已终局。 */
    FINISHED,
}

/** 单个棋手当前处于哪个计时阶段。 */
enum class ClockPhase {
    /** 主时间阶段。 */
    MAIN,

    /** 主时间已经用完（或主时间被关闭），处于“每步 N 秒”的读秒阶段。 */
    BYOYOMI,
}

/** 对局结束的原因。 */
enum class EndReason {
    /** 主时间走完判负（读秒关闭，或没有任何读秒可用）。 */
    TIMEOUT_MAIN,

    /** 读秒走完仍未走子判负。 */
    TIMEOUT_BYOYOMI,

    /** 认输。 */
    RESIGN,

    /** 双方和棋。 */
    DRAW,
}

/**
 * 终局结果。
 *
 * @param loser 判负方；和棋时为 null。
 */
data class GameResult(
    val reason: EndReason,
    val loser: Int?,
) {
    /** 获胜方；和棋时为 null。 */
    val winner: Int?
        get() = loser?.let { 1 - it }

    val isDraw: Boolean
        get() = reason == EndReason.DRAW
}

/**
 * 单个棋手的剩余时间。
 *
 * @param mainRemainingMs 主时间剩余毫秒数。主时间阶段的显示值。
 * @param byoyomiRemainingMs 读秒剩余毫秒数；处于 [ClockPhase.MAIN] 时表示“每步 N 秒”的完整值。
 * @param phase 当前计时阶段。
 */
data class PlayerClock(
    val mainRemainingMs: Long = 0L,
    val byoyomiRemainingMs: Long = 0L,
    val phase: ClockPhase = ClockPhase.MAIN,
)

/**
 * 全部可配置项（同时作为设置页的草稿类型）。
 *
 * 规则组合：
 * - 主时间开 + 读秒开：标准读秒（走完一步读秒重置为 N 秒，轮到该方 N 秒内没走完判负）
 * - 主时间开 + 读秒关：主时间走完直接判负
 * - 主时间关 + 读秒开：纯读秒，每步 N 秒，走完重置
 * - 两种都关不允许（至少保留一种计时方式）
 * - Fischer 加秒独立开关，只对主时间生效（主时间关闭时不生效）
 */
data class ClockSettings(
    val mainTimeEnabled: Boolean = true,
    val mainTimeMs: Long = DEFAULT_MAIN_TIME_MS,
    val byoyomiEnabled: Boolean = true,
    val byoyomiMs: Long = DEFAULT_BYOYOMI_MS,
    val fischerEnabled: Boolean = false,
    val fischerMs: Long = DEFAULT_FISCHER_MS,
    val soundEnabled: Boolean = true,
    val playerOneName: String = DEFAULT_PLAYER_ONE_NAME,
    val playerTwoName: String = DEFAULT_PLAYER_TWO_NAME,
) {
    val mainTimeMinutes: Int
        get() = (mainTimeMs / 1_000L / 60L).toInt()

    val byoyomiSeconds: Int
        get() = (byoyomiMs / 1_000L).toInt()

    val fischerSeconds: Int
        get() = (fischerMs / 1_000L).toInt()

    /** 至少要有一种计时方式可用。 */
    val isValid: Boolean
        get() = mainTimeEnabled || byoyomiEnabled

    fun nameOf(player: Int): String =
        if (player == PLAYER_ONE) playerOneName else playerTwoName

    /** 把外部传入的取值收敛到合法范围（读偏好设置、设置页保存时都会用到）。 */
    fun normalized(): ClockSettings {
        val safeMain = mainTimeEnabled || byoyomiEnabled
        val main = mainTimeMs.coerceIn(MIN_MAIN_TIME_MS, MAX_MAIN_TIME_MS)
        val byoyomi = byoyomiMs.coerceIn(MIN_BYOYOMI_MS, MAX_BYOYOMI_MS)
        return copy(
            // 两个都关时强制打开主时间，避免出现“完全没有计时”的死局
            mainTimeEnabled = if (safeMain) mainTimeEnabled else true,
            mainTimeMs = main,
            byoyomiMs = byoyomi,
            fischerMs = fischerMs.coerceIn(MIN_FISCHER_MS, MAX_FISCHER_MS),
            playerOneName = playerOneName.trim().ifEmpty { DEFAULT_PLAYER_ONE_NAME }.take(MAX_NAME_LENGTH),
            playerTwoName = playerTwoName.trim().ifEmpty { DEFAULT_PLAYER_TWO_NAME }.take(MAX_NAME_LENGTH),
        )
    }

    companion object {
        const val PLAYER_ONE = 0
        const val PLAYER_TWO = 1

        const val DEFAULT_MAIN_TIME_MS = 5 * 60 * 1_000L
        const val DEFAULT_BYOYOMI_MS = 10 * 1_000L
        const val DEFAULT_FISCHER_MS = 5 * 1_000L

        const val DEFAULT_PLAYER_ONE_NAME = "玩家 1"
        const val DEFAULT_PLAYER_TWO_NAME = "玩家 2"
        const val MAX_NAME_LENGTH = 12

        const val MIN_MAIN_MINUTES = 1
        const val MAX_MAIN_MINUTES = 60
        const val MIN_MAIN_TIME_MS = MIN_MAIN_MINUTES * 60 * 1_000L
        const val MAX_MAIN_TIME_MS = MAX_MAIN_MINUTES * 60 * 1_000L

        const val MIN_BYOYOMI_SECONDS = 1
        const val MAX_BYOYOMI_SECONDS = 60
        const val MIN_BYOYOMI_MS = MIN_BYOYOMI_SECONDS * 1_000L
        const val MAX_BYOYOMI_MS = MAX_BYOYOMI_SECONDS * 1_000L

        const val MIN_FISCHER_SECONDS = 0
        const val MAX_FISCHER_SECONDS = 60
        const val MIN_FISCHER_MS = MIN_FISCHER_SECONDS * 1_000L
        const val MAX_FISCHER_MS = MAX_FISCHER_SECONDS * 1_000L

        val DEFAULT = ClockSettings()
    }
}

/** “常用值”分组：主时间 / 读秒 / 加秒。 */
enum class PresetKind {
    MAIN,
    BYOYOMI,
    FISCHER,
}

/**
 * 三组用户自定义常用值（设置页的芯片），存 SharedPreferences。
 * 列表可以是空的，此时设置页只显示“＋ 添加当前值”。
 */
data class ClockPresets(
    val main: List<Int> = DEFAULT_MAIN,
    val byoyomi: List<Int> = DEFAULT_BYOYOMI,
    val fischer: List<Int> = DEFAULT_FISCHER,
) {
    fun of(kind: PresetKind): List<Int> = when (kind) {
        PresetKind.MAIN -> main
        PresetKind.BYOYOMI -> byoyomi
        PresetKind.FISCHER -> fischer
    }

    fun with(kind: PresetKind, values: List<Int>): ClockPresets = when (kind) {
        PresetKind.MAIN -> copy(main = values)
        PresetKind.BYOYOMI -> copy(byoyomi = values)
        PresetKind.FISCHER -> copy(fischer = values)
    }

    companion object {
        val DEFAULT_MAIN = listOf(5, 10, 15, 30)
        val DEFAULT_BYOYOMI = listOf(5, 10, 30)
        val DEFAULT_FISCHER = listOf(0, 2, 5, 10)

        /** 每组常用值的合法范围（与设置项一致）。 */
        fun rangeOf(kind: PresetKind): IntRange = when (kind) {
            PresetKind.MAIN ->
                ClockSettings.MIN_MAIN_MINUTES..ClockSettings.MAX_MAIN_MINUTES

            PresetKind.BYOYOMI ->
                ClockSettings.MIN_BYOYOMI_SECONDS..ClockSettings.MAX_BYOYOMI_SECONDS

            PresetKind.FISCHER ->
                ClockSettings.MIN_FISCHER_SECONDS..ClockSettings.MAX_FISCHER_SECONDS
        }
    }
}

/** 提示音事件：由 ViewModel 发出，界面负责播放。 */
enum class ClockSound {
    /** 读秒剩余 ≤5 秒时，每跨过 1 秒响一次。 */
    BYOYOMI_WARNING,

    /** 时间走完判负。 */
    FLAG_FALL,
}

/**
 * 棋钟的全部界面状态。
 *
 * @param activePlayer 当前走棋方：0 = 玩家 1，1 = 玩家 2。
 * @param result 终局结果，对局未结束时为 null。
 * @param tick 最近一次刷新时间（SystemClock.elapsedRealtime()），界面用它做红色闪烁。
 * @param lastTickRealtime 上一次结算消耗时间用的基准，内部使用。
 */
data class ChessClockState(
    val settings: ClockSettings = ClockSettings(),
    val status: GameStatus = GameStatus.IDLE,
    val activePlayer: Int = ClockSettings.PLAYER_ONE,
    val playerOne: PlayerClock = PlayerClock(),
    val playerTwo: PlayerClock = PlayerClock(),
    val moveCount: Int = 0,
    val result: GameResult? = null,
    val tick: Long = 0L,
    val lastTickRealtime: Long = 0L,
) {
    val loser: Int?
        get() = result?.loser

    val winner: Int?
        get() = result?.winner

    val isRunning: Boolean
        get() = status == GameStatus.RUNNING

    val isFinished: Boolean
        get() = status == GameStatus.FINISHED

    /** 是否有会被“重置/应用设置”清掉的对局进度（用于确认弹窗）。 */
    val hasGameProgress: Boolean
        get() = status != GameStatus.IDLE || moveCount > 0

    fun clockOf(player: Int): PlayerClock =
        if (player == ClockSettings.PLAYER_ONE) playerOne else playerTwo

    companion object {
        /** 读秒剩余不超过 5 秒时开始红色闪烁 + 每秒提示音。 */
        const val URGENT_THRESHOLD_MS = 5_000L

        /** 红色闪烁周期：500ms 亮 / 500ms 灭。 */
        const val BLINK_PERIOD_MS = 500L
    }
}
