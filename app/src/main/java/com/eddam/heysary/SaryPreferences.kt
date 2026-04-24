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
