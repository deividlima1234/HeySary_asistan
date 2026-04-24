package com.eddam.heysary

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.eddam.heysary.R

class SoundManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val prefs = SaryPreferences.getInstance(context)
    private val handler = Handler(Looper.getMainLooper())
    
    private var isDucking = false
    private var baseVolume = prefs.ambientVolume

    private var playCount = 0

    fun playProtocolBackground(maxPlays: Int = 1, onComplete: (() -> Unit)? = null) {
        stopBackground()
        baseVolume = prefs.ambientVolume
        playCount = 0
        
        mediaPlayer = MediaPlayer.create(context, R.raw.protocolo)?.apply {
            setVolume(baseVolume, baseVolume)
            seekTo(3000) // Comenzar desde el segundo 03
            
            setOnCompletionListener {
                playCount++
                if (playCount < maxPlays) {
                    seekTo(3000)
                    start()
                } else {
                    onComplete?.invoke()
                }
            }
            start()
        }
    }

    fun stopBackground() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
        isDucking = false
    }

    /**
     * Reduce el volumen al 20% del volumen base para permitir que el TTS se escuche mejor.
     */
    fun startDucking() {
        if (isDucking || mediaPlayer == null) return
        isDucking = true
        fadeVolume(baseVolume * 0.2f)
    }

    /**
     * Restaura el volumen al nivel configurado por el usuario.
     */
    fun stopDucking() {
        if (!isDucking || mediaPlayer == null) return
        isDucking = false
        fadeVolume(baseVolume)
    }

    private fun fadeVolume(targetVolume: Float) {
        mediaPlayer?.let { player ->
            // Implementación simple de fade (puede mejorarse con un loop)
            player.setVolume(targetVolume, targetVolume)
        }
    }

    fun release() {
        stopBackground()
    }
}
