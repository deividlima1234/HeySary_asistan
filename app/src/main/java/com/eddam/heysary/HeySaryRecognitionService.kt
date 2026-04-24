package com.eddam.heysary

import android.content.Intent
import android.speech.RecognitionService

class HeySaryRecognitionService : RecognitionService() {
    override fun onStartListening(intent: Intent?, listener: Callback?) {
        // Implementación básica requerida por el sistema
    }

    override fun onCancel(listener: Callback?) {
        // Cancelar reconocimiento
    }

    override fun onStopListening(listener: Callback?) {
        // Detener escucha
    }
}
