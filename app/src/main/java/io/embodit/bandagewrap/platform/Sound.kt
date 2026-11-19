package io.embodit.bandagewrap.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Event sounds only (design sound, B24: no ambience, no beat): a turn laid = a short paper / cloth rustle (CC0
 * recordings, two variants in rotation, ±5 % pitch, D51); aligned = a soft click (Kenney CC0); wrap done = a short
 * synthesised UI chime. One instance per event at a time; on / off and volume are remembered (D15).
 * Sources: factory apps/bandage-wrap/build/sound-sources.md.
 */
class Sound(private val context: Context) {
    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()
    private val playing = HashMap<String, Int>()
    private var master = 0.8f
    private var muted = false
    private var rustleIdx = 0
    private val rnd = java.util.Random()

    fun start() {
        if (pool != null) return
        val p =
            SoundPool.Builder().setMaxStreams(4)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .build()
        pool = p
        for (n in NAMES) {
            runCatching { context.assets.openFd("sfx/$n.wav").use { fd -> ids[n] = p.load(fd, 1) } }.onFailure { BwLog.w("SOUND load $n failed: $it") }
        }
        BwLog.i("SOUND ready ${ids.keys.sorted()}")
    }

    fun release() {
        runCatching { pool?.release() }
        pool = null
        ids.clear()
        playing.clear()
    }

    fun setVolume(percent: Int, mute: Boolean) {
        master = percent.coerceIn(0, 100) / 100f
        muted = mute
        BwLog.i("SOUND volume=$percent muted=$mute")
    }

    private fun play(event: String, file: String, g: Float, rate: Float = 1f) {
        val p = pool ?: return
        val id = ids[file] ?: return
        val v = if (muted) 0f else (master * g).coerceIn(0f, 1f)
        if (v <= 0f) {
            BwLog.i("SOUND $event muted")
            return
        }
        playing.remove(event)?.let { runCatching { p.stop(it) } }
        val s = p.play(id, v, v, 1, 0, rate.coerceIn(0.5f, 2f))
        if (s != 0) playing[event] = s
        BwLog.i("SOUND $event file=$file gain=${"%.2f".format(v)}")
    }

    private fun jitter(pct: Float) = 1f + (rnd.nextFloat() * 2f - 1f) * pct

    /** 走完一圈：绷带摩擦「沙」(near, short). */
    fun turnDone() {
        rustleIdx = (rustleIdx + 1) % 2
        play("turn", if (rustleIdx == 0) "rustle" else "rustle_2", 0.9f, jitter(0.05f))
    }

    /** 对上了：轻「嗒」. */
    fun aligned() = play("cue", "click", 0.8f)

    /** 缠好了：短和弦. */
    fun done() = play("cue", "done", 0.85f)

    companion object {
        val NAMES = listOf("rustle", "rustle_2", "click", "done")
    }
}
