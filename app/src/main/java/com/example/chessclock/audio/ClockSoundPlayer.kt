package com.example.chessclock.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.SoundPool

/**
 * 提示音播放器。
 *
 * 优先播放 res/raw 下的音频：
 * - `res/raw/byoyomi_tick.wav`：读秒剩余 ≤5 秒时每秒的提示音
 * - `res/raw/flag_fall.wav`  ：时间走完判负的提示音
 *
 * 资源是按名字查找的，所以：
 * - 想换音效，直接把同名文件丢进 res/raw 覆盖即可；
 * - 如果把文件删了（或加载失败），会自动退回系统默认通知音，不会崩。
 *
 * 注意：release 构建若开启资源压缩（shrinkResources），需要用 keep 规则保留 res/raw，
 * 本项目没有开启资源压缩，因此不受影响。
 */
class ClockSoundPlayer(context: Context) {

    private val appContext: Context = context.applicationContext

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val tickSoundId: Int
    private val flagSoundId: Int

    @Volatile
    private var tickReady = false

    @Volatile
    private var flagReady = false

    private var fallbackRingtone: Ringtone? = null

    init {
        tickSoundId = loadRaw("byoyomi_tick")
        flagSoundId = loadRaw("flag_fall")
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                if (sampleId == tickSoundId) tickReady = true
                if (sampleId == flagSoundId) flagReady = true
            }
        }
    }

    /** 读秒剩余 ≤5 秒时的每秒提示音。 */
    fun playWarning() {
        play(tickSoundId, tickReady, volume = 0.85f)
    }

    /** 时间走完判负的提示音。 */
    fun playFlagFall() {
        play(flagSoundId, flagReady, volume = 1f)
    }

    fun release() {
        soundPool.release()
        fallbackRingtone?.stop()
        fallbackRingtone = null
    }

    private fun loadRaw(name: String): Int {
        val resId = appContext.resources.getIdentifier(name, "raw", appContext.packageName)
        if (resId == 0) return 0
        return soundPool.load(appContext, resId, 1)
    }

    private fun play(soundId: Int, ready: Boolean, volume: Float) {
        if (soundId != 0 && ready) {
            soundPool.play(soundId, volume, volume, 1, 0, 1f)
        } else {
            playSystemNotification()
        }
    }

    private fun playSystemNotification() {
        try {
            val ringtone = fallbackRingtone ?: RingtoneManager.getRingtone(
                appContext,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            )?.also { fallbackRingtone = it }
            ringtone?.play()
        } catch (_: Exception) {
            // 系统没有通知音等情况：静默忽略，不影响计时
        }
    }
}
