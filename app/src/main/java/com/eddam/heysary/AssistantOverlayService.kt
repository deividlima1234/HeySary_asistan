package com.eddam.heysary

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.*
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.net.Uri
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import java.util.Locale
import kotlinx.coroutines.*
import android.view.ContextThemeWrapper
import java.util.ArrayList

class AssistantOverlayService : Service(), TextToSpeech.OnInitListener {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var statusText: TextView
    private lateinit var voiceWave: VoiceWaveView
    private lateinit var responseCard: View
    private lateinit var responseText: TextView
    private lateinit var dynamicContainer: FrameLayout
    
    // Vistas del Avatar Animado
    private lateinit var outerGear: ImageView
    private lateinit var innerGear: ImageView
    private lateinit var coreGlow: View
    
    private lateinit var groqClient: GroqClient
    private lateinit var prefs: SaryPreferences
    private lateinit var contactHelper: ContactHelper
    private lateinit var locationHelper: LocationHelper
    private lateinit var weatherClient: WeatherClient
    private lateinit var soundManager: SoundManager
    private var pendingContact: ContactInfo? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private val handler = Handler(Looper.getMainLooper())
    
    private val messages = listOf(
        "Pídeselo a Sary...",
        "Sary a la orden...",
        "¿En qué te ayudo?",
        "Hey Sary está lista",
        "Dime lo que quieras..."
    )
    private var currentMessageIndex = 0
    private var isListening = false
    private var isThinking = false
    private var isRunningProtocol = false

    private val gearRunnable = object : Runnable {
        override fun run() {
            if (::outerGear.isInitialized) {
                val speed = if (isThinking) 4f else 1f
                outerGear.rotation += speed
                innerGear.rotation -= speed * 1.5f
                
                // Si está pensando, añadimos una pequeña vibración aleatoria
                if (isThinking) {
                    outerGear.translationX = ((-2..2).random()).toFloat()
                    outerGear.translationY = ((-2..2).random()).toFloat()
                } else {
                    outerGear.translationX = 0f
                    outerGear.translationY = 0f
                }
            }
            handler.postDelayed(this, 30)
        }
    }

    private val messageRunnable = object : Runnable {
        override fun run() {
            if (::statusText.isInitialized && !isListening) {
                statusText.alpha = 0f
                statusText.text = messages[currentMessageIndex]
                statusText.animate().alpha(1f).setDuration(500).start()
                currentMessageIndex = (currentMessageIndex + 1) % messages.size
            }
            handler.postDelayed(this, 2000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startSaryForeground()
        if (intent?.action == "START_PROACTIVE_VOICE") {
            val protocolId = intent.getStringExtra("PROTOCOL_ID")
            handleProactiveProtocol(protocolId)
        } else if (intent?.action == "ACTION_NOTIFICATION_RECEIVED") {
            val appName = intent.getStringExtra("APP_NAME") ?: "Sistema"
            val sender = intent.getStringExtra("SENDER") ?: "Alguien"
            val message = intent.getStringExtra("MESSAGE") ?: ""
            handleNotification(appName, sender, message)
        }
        return START_STICKY
    }

    private fun startSaryForeground() {
        val channelId = "sary_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "HeySary Active Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("Sary a la orden")
            .setContentText("El asistente está activo y cuidando sus protocolos.")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun handleProactiveProtocol(id: String?) {
        serviceScope.launch {
            // 1. Esperar a que la UI esté lista
            var uiRetry = 0
            while (!::overlayView.isInitialized && uiRetry < 10) {
                delay(500)
                uiRetry++
            }

            // 2. Recolectar Contexto Real (Ubicación y Clima)
            val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            val location = locationHelper.getLastLocation()
            val city = locationHelper.getCityName(location)
            
            val weatherData = if (location != null) {
                weatherClient.getCurrentWeather(location.latitude, location.longitude)
            } else null
            
            val battery = getBatteryLevel()

            val protocolName = when (id) {
                "WAKE_UP" -> "Despertar con Estilo"
                "WORK" -> "Salida al Trabajo"
                "LUNCH" -> "Hora de Almorzar"
                "RETURN" -> "Regreso a Casa"
                "REST" -> "Descanso"
                else -> "General"
            }

            val contextData = mutableMapOf(
                "time" to time,
                "city" to city,
                "battery" to battery.toString()
            )
            
            weatherData?.let {
                contextData["temp"] = it.temp.toString()
                contextData["condition"] = it.condition
            }

            // 3. Obtener respuesta de la IA
            val script = groqClient.getProtocolResponse(protocolName, contextData)

            // 4. Esperar a que el motor de voz esté listo
            var ttsRetry = 0
            while (!isTtsInitialized && ttsRetry < 20) {
                delay(250)
                ttsRetry++
            }
            
            withContext(Dispatchers.Main) {
                if (::overlayView.isInitialized) {
                    isRunningProtocol = true
                    soundManager.playProtocolBackground(maxPlays = 2) {
                        handler.post { stopSelf() }
                    }
                    showResponse(script)
                    if (isTtsInitialized) {
                        speak(script, "PROTOCOL_REPORT")
                    } else {
                        statusText.text = "Jarvis listo (Voz no disponible)"
                    }
                }
            }
        }
    }

    private fun handleNotification(appName: String, sender: String, message: String) {
        serviceScope.launch {
            var uiRetry = 0
            while (!::overlayView.isInitialized && uiRetry < 10) {
                delay(500)
                uiRetry++
            }

            val response = groqClient.getNotificationResponse(appName, sender, message)

            var ttsRetry = 0
            while (!isTtsInitialized && ttsRetry < 20) {
                delay(250)
                ttsRetry++
            }

            withContext(Dispatchers.Main) {
                if (::overlayView.isInitialized) {
                    showResponse(response)
                    speak(response, "NOTIFICATION_READ")
                }
            }
        }
    }

    private fun getBatteryLevel(): Int {
        val batteryStatus: Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
    }

    override fun onCreate() {
        super.onCreate()
        try {
            // Inicialización de componentes
            prefs = SaryPreferences.getInstance(this)
            groqClient = GroqClient(this)
            contactHelper = ContactHelper(this)
            soundManager = SoundManager(this)
            locationHelper = LocationHelper(this)
            weatherClient = WeatherClient()

            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            val contextWrapper = ContextThemeWrapper(this, R.style.Theme_HeySary)
            val inflater = LayoutInflater.from(contextWrapper)
            overlayView = inflater.inflate(R.layout.floating_pill_layout, null)
            
            statusText = overlayView.findViewById(R.id.status_text)
            voiceWave = overlayView.findViewById(R.id.voice_wave)
            responseCard = overlayView.findViewById(R.id.response_card)
            responseText = overlayView.findViewById(R.id.response_text)
            dynamicContainer = overlayView.findViewById(R.id.dynamic_content_container)
            
            // Inicialización de vistas del avatar
            outerGear = overlayView.findViewById(R.id.outer_gear)
            innerGear = overlayView.findViewById(R.id.inner_gear)
            coreGlow = overlayView.findViewById(R.id.core_glow)

            setupSpeechRecognizer()
            setupTts()

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or 
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
                PixelFormat.TRANSLUCENT
            )

            params.gravity = Gravity.TOP or Gravity.START

            // Cierre al tocar el fondo
            overlayView.setOnClickListener {
                stopSelf()
            }

            overlayView.findViewById<View>(R.id.close_response).setOnClickListener {
                hideResponse()
            }

            overlayView.findViewById<ImageView>(R.id.mic_button).setOnClickListener {
                if (isListening) stopListening() else startListening()
            }

            overlayView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

            windowManager.addView(overlayView, params)
            handler.post(messageRunnable)
            handler.post(gearRunnable)
        } catch (e: Exception) {
            android.util.Log.e("HeySary", "Error crítico en onCreate: ${e.message}")
            stopSelf()
        }
    }

    private fun setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    updateUIForListening(true)
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    voiceWave.updateAmplitude(rmsdB)
                    updateAura(rmsdB)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        // Reiniciar si no escuchó nada para mantener conversación continua
                        startListening()
                    } else {
                        isListening = false
                        updateUIForListening(false)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = data?.get(0) ?: ""
                    handleRecognitionResult(text)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val data = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = data?.get(0) ?: ""
                    if (text.isNotEmpty()) {
                        statusText.text = text
                        statusText.visibility = View.VISIBLE
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun startListening(forceHide: Boolean = false) {
        if (forceHide) hideResponse()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
        updateUIForListening(false)
    }

    private fun handleRecognitionResult(text: String) {
        statusText.text = text
        statusText.visibility = View.VISIBLE
        
        // Comandos de cierre
        val lowerText = text.lowercase()
        if (lowerText.contains("salir") || lowerText.contains("cerrar") || lowerText.contains("adiós")) {
            handler.postDelayed({ stopSelf() }, 1000)
            return
        }

        // Estado: Sary pensando...
        statusText.text = "Sary pensando..."
        isThinking = true
        voiceWave.setActive(true)
        // Animamos la onda de forma constante y suave mientras piensa
        handler.post(object : Runnable {
            override fun run() {
                if (statusText.text == "Sary pensando...") {
                    voiceWave.updateAmplitude(3f) // Pulso suave
                    handler.postDelayed(this, 100)
                }
            }
        })

        // Llamada a la IA en segundo plano
        serviceScope.launch {
            val response = groqClient.getResponse(text)
            
            withContext(Dispatchers.Main) {
                isThinking = false
                processAiResponse(response)
            }
        }
    }

    private fun processAiResponse(response: String) {
        var cleanText = response.replace(Regex("\\[.*?\\]"), "").trim()
        
        if (response.contains("[SEARCH_CONTACT:")) {
            val name = response.substringAfter("[SEARCH_CONTACT:").substringBefore("]").trim()
            val contact = handleContactSearch(name)
            if (contact != null) {
                cleanText = "He encontrado a ${contact.name} en tus contactos. ¿Deseas que inicie la llamada?"
            } else {
                cleanText = "Lo siento, no encontré a ningún contacto llamado $name."
            }
        } 
        else if (response.contains("[ACTION_CALL:")) {
            val name = response.substringAfter("[ACTION_CALL:").substringBefore("]").trim()
            val contact = handleCallAction(name)
            if (contact != null) {
                cleanText = "Entendido, llamando a ${contact.name}..."
            } else {
                cleanText = "No pude encontrar el número de $name para llamar."
            }
        }
        else if (response.contains("[CONFIRM_ACTION: TRUE]")) {
            if (pendingContact != null) {
                cleanText = "Llamando a ${pendingContact?.name}. Un gusto servirle señor, hasta luego."
                executeCall(pendingContact!!)
                speak(cleanText, "FAREWELL_AND_EXIT")
                return // Salimos para evitar el flujo normal
            } else {
                cleanText = "No tengo ninguna acción pendiente para confirmar."
            }
        }

        if (cleanText.isEmpty()) cleanText = "Hecho."

        statusText.text = "Sary responde"
        showResponse(cleanText)
        speak(cleanText)
    }

    private fun handleContactSearch(query: String): ContactInfo? {
        val results = contactHelper.findContact(query)
        return if (results.isNotEmpty()) {
            val contact = results[0]
            pendingContact = contact
            showContactCard(contact)
            contact
        } else {
            pendingContact = null
            null
        }
    }

    private fun handleCallAction(query: String): ContactInfo? {
        val results = contactHelper.findContact(query)
        return if (results.isNotEmpty()) {
            val contact = results[0]
            executeCall(contact)
            contact
        } else {
            null
        }
    }

    private fun showContactCard(contact: ContactInfo) {
        dynamicContainer.removeAllViews()
        val inflater = LayoutInflater.from(ContextThemeWrapper(this, R.style.Theme_HeySary))
        val cardView = inflater.inflate(R.layout.contact_card_layout, dynamicContainer, false)
        
        cardView.findViewById<TextView>(R.id.contact_name).text = contact.name
        cardView.findViewById<TextView>(R.id.contact_number).text = contact.phoneNumber
        
        if (contact.photoUri != null) {
            cardView.findViewById<ImageView>(R.id.contact_photo).setImageURI(Uri.parse(contact.photoUri))
        }

        cardView.findViewById<View>(R.id.call_action_button).setOnClickListener {
            executeCall(contact)
        }

        dynamicContainer.addView(cardView)
        dynamicContainer.visibility = View.VISIBLE
    }

    private fun executeCall(contact: ContactInfo) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${contact.phoneNumber}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            speak("No tengo permisos suficientes para realizar la llamada directa.")
        }
    }

    private fun showResponse(text: String) {
        responseText.text = text
        responseCard.visibility = View.VISIBLE
        responseCard.alpha = 0f
        responseCard.translationY = 50f
        responseCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .start()
    }

    private fun hideResponse() {
        if (responseCard.visibility == View.VISIBLE) {
            responseCard.animate()
                .alpha(0f)
                .translationY(50f)
                .setDuration(300)
                .withEndAction { responseCard.visibility = View.GONE }
                .start()
        }
    }

    private fun setupTts() {
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("es", "ES"))
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsInitialized = true
                
                // Intentar buscar voz femenina
                tts?.voices?.find { 
                    it.name.lowercase().contains("female") || 
                    it.name.lowercase().contains("femenina") ||
                    it.name.lowercase().contains("es-es-x-sfg") // Un nombre común de voz femenina de Google
                }?.let { tts?.voice = it }

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        handler.post {
                            voiceWave.visibility = View.VISIBLE
                            voiceWave.setActive(true)
                            isSarySpeaking = true
                            soundManager.startDucking()
                            startTtsAnimation()
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        handler.post {
                            isSarySpeaking = false
                            voiceWave.setActive(false)
                            voiceWave.visibility = View.GONE
                            soundManager.stopDucking()
                            
                            when (utteranceId) {
                                "SARY_RESPONSE" -> {
                                    handler.postDelayed({
                                        statusText.text = "¿Algo más en que te pueda ayudar?"
                                        startListening()
                                    }, 1000)
                                }
                                "PROTOCOL_REPORT" -> {
                                    // Eliminamos la frase fija para dejar que la IA maneje la despedida contextual.
                                    // El SoundManager completará su reproducción al 100% y cerrará el servicio.
                                    isRunningProtocol = false 
                                }
                                "NOTIFICATION_READ" -> {
                                    handler.postDelayed({
                                        stopSelf()
                                    }, 2500) // 2.5 seg para que el usuario lea el texto si desea
                                }
                                "FAREWELL_AND_EXIT" -> {
                                    handler.postDelayed({
                                        stopSelf()
                                    }, 500)
                                }
                            }
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        isSarySpeaking = false
                    }
                })
            }
        }
    }

    private var isSarySpeaking = false
    private fun startTtsAnimation() {
        handler.post(object : Runnable {
            override fun run() {
                if (isSarySpeaking) {
                    val amp = (5..12).random().toFloat()
                    voiceWave.updateAmplitude(amp) // Ondas simuladas mientras habla
                    updateAura(amp - 5f) // Sincronizamos el aura con la voz de Sary
                    handler.postDelayed(this, 100)
                }
            }
        })
    }

    private fun updateAura(rmsDb: Float) {
        if (!::coreGlow.isInitialized) return
        
        // Mapeamos la amplitud a escala (1.0 a 1.4) y opacidad (0.4 a 1.0)
        val normalized = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)
        val scale = 1.0f + (normalized * 0.4f)
        val alpha = 0.4f + (normalized * 0.6f)
        
        coreGlow.animate()
            .scaleX(scale)
            .scaleY(scale)
            .alpha(alpha)
            .setDuration(100)
            .start()
    }

    private fun speak(text: String, utteranceId: String = "SARY_RESPONSE") {
        if (isTtsInitialized) {
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } else {
            // Fallback si TTS falla
            handler.postDelayed({
                if (utteranceId == "FAREWELL_AND_EXIT") {
                    stopSelf()
                } else if (utteranceId != "PROTOCOL_REPORT" && utteranceId != "PROTOCOL_FINAL") {
                    // Solo abrimos micro en respuestas normales, no en protocolos
                    statusText.text = "¿Algo más en que te pueda ayudar?"
                    startListening()
                }
            }, 4000)
        }
    }

    private fun updateUIForListening(listening: Boolean) {
        if (listening) {
            voiceWave.visibility = View.VISIBLE
            voiceWave.setActive(true)
            statusText.text = "Escuchando..."
        } else {
            voiceWave.visibility = View.GONE
            voiceWave.setActive(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) {
            try {
                windowManager.removeView(overlayView)
            } catch (e: Exception) {
                android.util.Log.e("HeySary", "Error al limpiar overlay: ${e.message}")
            }
        }
        serviceScope.cancel()
        handler.removeCallbacks(messageRunnable)
        speechRecognizer?.destroy()
        tts?.shutdown()
        soundManager.release()
    }
}
