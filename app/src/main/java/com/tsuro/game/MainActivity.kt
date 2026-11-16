package com.tsuro.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.tsuro.game.audio.GameAudio
import com.tsuro.game.ui.TsuroApp
import com.tsuro.game.ui.TsuroTheme

class MainActivity : ComponentActivity() {
    private lateinit var audio: GameAudio

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audio = GameAudio(applicationContext)
        setContent { TsuroTheme { TsuroApp(audio) } }
    }

    override fun onPause() { audio.pauseMusic(); super.onPause() }
    override fun onResume() { super.onResume(); audio.resumeMusic() }
    override fun onDestroy() { audio.release(); super.onDestroy() }
}
