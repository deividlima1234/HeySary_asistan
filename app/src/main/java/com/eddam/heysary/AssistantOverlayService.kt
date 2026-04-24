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
import android.widget.EditText
import android.widget.TextView
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.text.TextWatcher
import android.text.Editable
import java.util.Locale
import kotlinx.coroutines.*
import android.view.ContextThemeWrapper
import java.util.ArrayList

class AssistantOverlayService : Service(), TextToSpeech.OnInitListener {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var statusText: EditText
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
        "¿En qué puedo ayudarte?",
        "Status: Online",
        "Listening..."
    )

    private var isListening = false
    private var isThinking = false

    private val statusUpdateRunnable = object : Runnable {
        override fun run() {
            if (!isListening && !isThinking && statusText.text.toString() in messages || statusText.text.toString().isEmpty()) {
                statusText.setText(messages.random())
                handler.postDelayed(this, 10000)
            }
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
        } else {
            // Activación normal: Iniciar escucha inmediatamente
            handler.postDelayed({ startListening() }, 1000)
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
                        statusText.setText("Jarvis listo (Voz no disponible)")
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
            
            statusText = overlayView.findViewById(R.id.status_text) ?: throw IllegalStateException("status_text not found")
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

            micButton = overlayView.findViewById(R.id.mic_button)
            micButton.setOnClickListener {
                if (isListening) stopListening() else startListening()
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or 
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
                PixelFormat.TRANSLUCENT
            )
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN

            params.gravity = Gravity.TOP or Gravity.START

            // Cierre al tocar el fondo
            overlayView.findViewById<View>(R.id.overlay_root).setOnClickListener {
                stopSelf()
            }

            val activateKeyboard = {
                cancelListening() // Abortar mic agresivamente
                if (overlayView.isAttachedToWindow) {
                    params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                    windowManager.updateViewLayout(overlayView, params)
                }
                statusText.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(statusText, InputMethodManager.SHOW_IMPLICIT)
            }

            // Gestionar foco para el teclado y detectar clics directos en la caja de texto
            statusText.setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_UP) {
                    activateKeyboard()
                }
                false
            }
            
            statusText.setOnFocusChangeListener { _, hasFocus ->
                if (overlayView.isAttachedToWindow && !hasFocus) {
                    params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    windowManager.updateViewLayout(overlayView, params)
                }
            }

            statusText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    val query = statusText.text.toString().trim()
                    if (query.isNotEmpty()) {
                        handleUserQuery(query)
                        statusText.setText("")
                        statusText.clearFocus()
                        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(statusText.windowToken, 0)
                    }
                    true
                } else false
            }

            // Evitar que el clic en la píldora cierre el asistente
            overlayView.findViewById<View>(R.id.pill_container).setOnClickListener {
                activateKeyboard()
            }

            overlayView.findViewById<View>(R.id.close_response).setOnClickListener {
                hideResponse()
            }

            // Detectar escritura para cambiar ícono a botón Enviar
            micButton.tag = "MIC"
            statusText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val currentText = s?.toString()?.trim() ?: ""
                    // Si el usuario está escribiendo su propio texto (no del sistema)
                    if (statusText.hasFocus() && currentText.isNotEmpty() && !messages.contains(currentText) && currentText != "Escuchando...") {
                        micButton.setImageResource(android.R.drawable.ic_menu_send)
                        micButton.tag = "SEND"
                    } else {
                        micButton.setImageResource(android.R.drawable.ic_btn_speak_now)
                        micButton.tag = "MIC"
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            micButton.setOnClickListener {
                if (micButton.tag == "SEND") {
                    val query = statusText.text.toString().trim()
                    if (query.isNotEmpty()) {
                        handleUserQuery(query)
                        statusText.setText("")
                        statusText.clearFocus()
                        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(statusText.windowToken, 0)
                    }
                } else {
                    if (isListening) stopListening() else startListening()
                }
            }

            overlayView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

            windowManager.addView(overlayView, params)
            
            // Iniciar animaciones y rotaciones
            startAvatarAnimations()
            handler.post(statusUpdateRunnable)

        } catch (e: Exception) {
            e.printStackTrace()
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
                    handleUserQuery(text)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val data = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = data?.get(0) ?: ""
                    // Sólo mostrar resultados parciales si NO estamos escribiendo manualmente
                    if (text.isNotEmpty() && !statusText.hasFocus()) {
                        statusText.setText(text)
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

    private fun cancelListening() {
        speechRecognizer?.cancel() // Destruye inmediatamente el buffer de audio (no llama a onResults)
        isListening = false
        updateUIForListening(false)
        val textStr = statusText.text.toString()
        if (textStr == "Escuchando..." || textStr == "Sary a la orden...") {
            statusText.setText("")
        }
    }

    private fun handleUserQuery(text: String) {
        statusText.setText(text)
        statusText.visibility = View.VISIBLE
        
        // Comandos de cierre
        val lowerText = text.lowercase()
        if (lowerText.contains("salir") || lowerText.contains("cerrar") || lowerText.contains("adiós")) {
            handler.postDelayed({ stopSelf() }, 1000)
            return
        }

        // Estado: Sary pensando...
        statusText.setText("Sary pensando...")
        isThinking = true
        voiceWave.setActive(true)
        // Animamos la onda de forma constante y suave mientras piensa
        handler.post(object : Runnable {
            override fun run() {
                if (statusText.text.toString() == "Sary pensando...") {
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
        // Limpieza profunda de símbolos para TTS y visualización
        var cleanText = response.replace(Regex("\\[.*?\\]"), "")
            .replace("*", "")
            .replace("#", "")
            .replace("_", "")
            .trim()
        
        if (response.contains("[SEARCH_CONTACT:")) {
            val name = response.substringAfter("[SEARCH_CONTACT:").substringBefore("]").trim()
            val contacts = contactHelper.findContact(name)
            val contact = contacts.firstOrNull()
            if (contact != null) {
                pendingContact = contact
                cleanText += "\n\nHe encontrado a ${contact.name}. ¿Desea que lo llame?"
                speak(cleanText, "SARY_RESPONSE")
            } else {
                speak("No encontré a ningún contacto llamado $name.", "SARY_RESPONSE")
            }
        } else if (response.contains("[CALL_CONTACT:")) {
            pendingContact?.let {
                makePhoneCall(it.phoneNumber)
                pendingContact = null
                speak("Llamando a ${it.name} ahora.", "SARY_RESPONSE")
            } ?: run {
                statusText.setText("¿A quién desea llamar?")
                startListening()
            }
        } else if (response.contains("[WHATSAPP_CONTACT:")) {
            pendingContact?.let {
                openWhatsApp(it.phoneNumber)
                pendingContact = null
                speak("Abriendo chat con ${it.name}.", "SARY_RESPONSE")
            }
        } else {
            speak(cleanText, "SARY_RESPONSE")
        }
        
        showResponse(cleanText)
    }

    private fun makePhoneCall(number: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$number")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            statusText.setText("No tengo permisos suficientes para realizar la llamada directa.")
        }
    }

    private fun openWhatsApp(number: String) {
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$number")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private var typingJob: Job? = null

    private fun showResponse(text: String) {
        typingJob?.cancel()
        responseText.text = ""
        responseCard.visibility = View.VISIBLE
        responseCard.alpha = 0f
        responseCard.scaleX = 0.9f
        responseCard.scaleY = 0.9f
        
        responseCard.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(500)
            .setInterpolator(android.view.animation.OvershootInterpolator())
            .start()

        // Efecto Máquina de Escribir (Typewriter)
        typingJob = serviceScope.launch {
            val words = text.split(" ")
            val fullText = StringBuilder()
            for (word in words) {
                fullText.append(word).append(" ")
                withContext(Dispatchers.Main) {
                    responseText.text = fullText.toString()
                }
                delay(30) // Velocidad de "digitación"
            }
        }
    }

    private fun hideResponse() {
        if (responseCard.visibility == View.VISIBLE) {
            typingJob?.cancel()
            responseCard.animate()
                .alpha(0f)
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(400)
                .withEndAction { 
                    responseCard.visibility = View.GONE
                    responseText.text = ""
                }
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
                                        statusText.setText("¿Algo más en que te pueda ayudar?")
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
                    voiceWave.updateAmplitude(amp)
                    handler.postDelayed(this, 80)
                }
            }
        })
    }

    private fun speak(text: String, utteranceId: String = "SARY_RESPONSE") {
        if (isTtsInitialized) {
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }
    }

    private var isRunningProtocol = false 
    
    private fun updateUIForListening(listening: Boolean) {
        handler.post {
            if (listening) {
                if (!statusText.hasFocus()) {
                    statusText.setText("Escuchando...")
                }
                micButton.setColorFilter(android.graphics.Color.RED)
                voiceWave.visibility = View.VISIBLE
                voiceWave.setActive(true)
            } else {
                val currentText = statusText.text.toString()
                if (!statusText.hasFocus() && (currentText == "Escuchando..." || currentText.isEmpty())) {
                    statusText.setText("Sary a la orden...")
                }
                micButton.setColorFilter(android.graphics.Color.WHITE)
                voiceWave.visibility = View.GONE
                voiceWave.setActive(false)
            }
        }
    }

    private fun updateAura(amplitude: Float) {
        val scale = 1f + (amplitude / 30f)
        coreGlow.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(50)
            .start()
    }

    private fun startAvatarAnimations() {
        val outerAnim = android.view.animation.RotateAnimation(
            0f, 360f,
            android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
            android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 10000
            repeatCount = android.view.animation.Animation.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
        }

        val innerAnim = android.view.animation.RotateAnimation(
            360f, 0f,
            android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
            android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 15000
            repeatCount = android.view.animation.Animation.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
        }

        outerGear.startAnimation(outerAnim)
        innerGear.startAnimation(innerAnim)
    }

    private lateinit var micButton: ImageView

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        soundManager.release()
        serviceScope.cancel()
        handler.removeCallbacks(statusUpdateRunnable)
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }
}
