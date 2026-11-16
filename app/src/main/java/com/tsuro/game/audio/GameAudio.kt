package com.tsuro.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.tsuro.game.R

enum class Sfx { PLACE, MOVE, ELIMINATE, VICTORY }

/** Sound effects (SoundPool) and looping background music (MediaPlayer) with independent volumes. */
class GameAudio(private val context: Context) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()
    private val ids = HashMap<Sfx, Int>()
    private val loaded = HashSet<Int>()
    private var music: MediaPlayer? = null
    private var musicWanted = false

    /** 0..1 slider values; a squared curve makes the slider feel more natural. */
    var sfxVolume: Float = 0.7f
    var musicVolume: Float = 0.5f
        set(value) { field = value; music?.setVolume(curve(value), curve(value)) }

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
        ids[Sfx.PLACE] = pool.load(context, R.raw.sfx_place, 1)
        ids[Sfx.MOVE] = pool.load(context, R.raw.sfx_move, 1)
        ids[Sfx.ELIMINATE] = pool.load(context, R.raw.sfx_eliminate, 1)
        ids[Sfx.VICTORY] = pool.load(context, R.raw.sfx_victory, 1)
    }

    private fun curve(v: Float) = v * v

    fun play(sfx: Sfx) {
        val id = ids[sfx] ?: return
        if (id !in loaded || sfxVolume <= 0f) return
        val v = curve(sfxVolume)
        pool.play(id, v, v, 1, 0, 1f)
    }

    fun startMusic() {
        musicWanted = true
        if (music == null) {
            music = MediaPlayer.create(context, R.raw.music_loop)?.apply {
                isLooping = true
                setVolume(curve(musicVolume), curve(musicVolume))
            }
        }
        music?.let { if (!it.isPlaying) it.start() }
    }

    /** Call from Activity.onPause. */
    fun pauseMusic() { music?.let { if (it.isPlaying) it.pause() } }

    /** Call from Activity.onResume. */
    fun resumeMusic() { if (musicWanted) music?.let { if (!it.isPlaying) it.start() } }

    fun release() {
        pool.release()
        music?.release(); music = null
    }
}
