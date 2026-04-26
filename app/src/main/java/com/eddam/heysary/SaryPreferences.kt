package com.eddam.heysary

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SaryPreferences private constructor(context: Context) {

    private val sharedPreferences: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "sary_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("SaryPrefs", "Error al inicializar EncryptedSharedPreferences, usando fallback: ${e.message}")
            context.getSharedPreferences("sary_fallback_prefs", Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val KEY_GROQ_API = "groq_api_key"
        private const val KEY_GROQ_MODEL = "groq_model"
        private const val DEFAULT_MODEL = "llama-3.3-70b-versatile"
        
        // Claves para el motor de TTS
        private const val KEY_TTS_ENGINE = "tts_engine"
        const val ENGINE_NATIVE = "NATIVO"
        const val ENGINE_ELEVENLABS = "ELEVENLABS"
        const val ENGINE_RESEMBLE = "RESEMBLE_AI"
        
        private const val KEY_ELEVENLABS_API = "elevenlabs_api_key"
        private const val KEY_ELEVENLABS_VOICE = "elevenlabs_voice_id"
        
        private const val KEY_RESEMBLE_API = "resemble_api_key"
        private const val KEY_RESEMBLE_VOICE = "resemble_voice_name"
        private const val KEY_RESEMBLE_PROJECT = "resemble_project_uuid"
        
        private const val KEY_WHATSAPP_IP = "whatsapp_gateway_ip"
        private const val KEY_WHATSAPP_TOKEN = "whatsapp_gateway_token"
        private const val KEY_WHATSAPP_AUTOPILOT = "whatsapp_autopilot"
        private const val KEY_WHATSAPP_CONTEXT = "whatsapp_autopilot_context"
        
        @Volatile
        private var INSTANCE: SaryPreferences? = null

        fun getInstance(context: Context): SaryPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SaryPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    var groqApiKey: String?
        get() = sharedPreferences.getString(KEY_GROQ_API, null)
        set(value) = sharedPreferences.edit().putString(KEY_GROQ_API, value).apply()

    var groqModel: String
        get() = sharedPreferences.getString(KEY_GROQ_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = sharedPreferences.edit().putString(KEY_GROQ_MODEL, value).apply()

    var ambientVolume: Float
        get() = sharedPreferences.getFloat("ambient_volume", 0.5f)
        set(value) = sharedPreferences.edit().putFloat("ambient_volume", value).apply()

    fun hasApiKey(): Boolean = !groqApiKey.isNullOrBlank()

    // Configuración TTS Multi-Motor
    var ttsEngine: String
        get() = sharedPreferences.getString(KEY_TTS_ENGINE, ENGINE_NATIVE) ?: ENGINE_NATIVE
        set(value) = sharedPreferences.edit().putString(KEY_TTS_ENGINE, value).apply()
        
    var elevenLabsApiKey: String?
        get() = sharedPreferences.getString(KEY_ELEVENLABS_API, null)
        set(value) = sharedPreferences.edit().putString(KEY_ELEVENLABS_API, value).apply()
        
    var elevenLabsVoiceId: String?
        get() = sharedPreferences.getString(KEY_ELEVENLABS_VOICE, null)
        set(value) = sharedPreferences.edit().putString(KEY_ELEVENLABS_VOICE, value).apply()
        
    var resembleApiKey: String?
        get() = sharedPreferences.getString(KEY_RESEMBLE_API, null)
        set(value) = sharedPreferences.edit().putString(KEY_RESEMBLE_API, value).apply()
        
    var resembleVoiceUuid: String?
        get() = sharedPreferences.getString(KEY_RESEMBLE_VOICE, null)
        set(value) = sharedPreferences.edit().putString(KEY_RESEMBLE_VOICE, value).apply()
        
    var resembleProjectUuid: String?
        get() = sharedPreferences.getString(KEY_RESEMBLE_PROJECT, null)
        set(value) = sharedPreferences.edit().putString(KEY_RESEMBLE_PROJECT, value).apply()

    // WhatsApp Configuration
    var whatsappGatewayIp: String?
        get() = sharedPreferences.getString(KEY_WHATSAPP_IP, null)
        set(value) = sharedPreferences.edit().putString(KEY_WHATSAPP_IP, value).apply()

    var whatsappGatewayToken: String?
        get() = sharedPreferences.getString(KEY_WHATSAPP_TOKEN, null)
        set(value) = sharedPreferences.edit().putString(KEY_WHATSAPP_TOKEN, value).apply()

    var isAutoPilotWhatsapp: Boolean
        get() = sharedPreferences.getBoolean(KEY_WHATSAPP_AUTOPILOT, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_WHATSAPP_AUTOPILOT, value).apply()

    var whatsappAutoPilotContext: String
        get() = sharedPreferences.getString(KEY_WHATSAPP_CONTEXT, "Estoy conduciendo.") ?: "Estoy conduciendo."
        set(value) = sharedPreferences.edit().putString(KEY_WHATSAPP_CONTEXT, value).apply()

    // Gestión de Protocolos Programados
    fun getProtocols(): List<AutoProtocol> {
        val json = sharedPreferences.getString("auto_protocols", null)
        if (json == null) {
            return listOf(
                AutoProtocol("WAKE_UP", "Despertar con Estilo", "06:00", "06:00", false, "Clima y bienvenida Jarvis."),
                AutoProtocol("WORK", "Salida al Trabajo", "08:00", "08:00", false, "Estado batería y buena jornada."),
                AutoProtocol("LUNCH", "Hora de Almorzar", "13:00", "13:00", false, "Descanso y nutrición."),
                AutoProtocol("RETURN", "Regreso a Casa", "18:00", "18:00", false, "Bienvenida y resumen del día."),
                AutoProtocol("REST", "Protocolo de Descanso", "22:00", "22:00", false, "Silencio y despedida suave.")
            )
        }
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<AutoProtocol>>() {}.type
            com.google.gson.Gson().fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveProtocols(protocols: List<AutoProtocol>) {
        val json = com.google.gson.Gson().toJson(protocols)
        sharedPreferences.edit().putString("auto_protocols", json).apply()
    }

    // Gestión de Notificaciones
    var readNotificationsEnabled: Boolean
        get() = sharedPreferences.getBoolean("read_notifications_enabled", true)
        set(value) = sharedPreferences.edit().putBoolean("read_notifications_enabled", value).apply()

    var whitelistPackages: Set<String>
        get() {
            val json = sharedPreferences.getString("whitelist_json", null) ?: return emptySet()
            return try {
                val type = object : com.google.gson.reflect.TypeToken<Set<String>>() {}.type
                com.google.gson.Gson().fromJson(json, type)
            } catch (e: Exception) {
                emptySet()
            }
        }
        set(value) {
            val json = com.google.gson.Gson().toJson(value)
            sharedPreferences.edit().putString("whitelist_json", json).apply()
        }

    fun isPackageAllowed(packageName: String): Boolean {
        return readNotificationsEnabled && whitelistPackages.contains(packageName)
    }

    fun togglePackage(packageName: String) {
        val current = whitelistPackages.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        whitelistPackages = current
    }
}
