package com.eddam.heysary

import com.google.gson.Gson
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GroqClient(context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private val prefs = SaryPreferences.getInstance(context)
    
    // Historial de la charla actual
    private val history = mutableListOf<Map<String, String>>()

    fun injectContext(contextMessage: String) {
        history.add(mapOf("role" to "assistant", "content" to contextMessage))
        if (history.size > 10) history.removeAt(0)
    }

    suspend fun getResponse(userMessage: String): String = withContext(Dispatchers.IO) {
        val apiKey = prefs.groqApiKey ?: return@withContext "Error: API Key no configurada en Ajustes."
        val model = prefs.groqModel

        // Añadir mensaje del usuario al historial
        history.add(mapOf("role" to "user", "content" to userMessage))
        
        // Limitar historial
        if (history.size > 10) history.removeAt(0)

        // Construir la lista completa para la API
        val messages = mutableListOf<Map<String, String>>()
        messages.add(mapOf(
            "role" to "system",
            "content" to "Eres Sary, una asistente virtual avanzada con estética Cyberpunk. " +
                         "Responde siempre de forma EXTREMADAMENTE CONCISA (máximo 2 frases). " +
                         "REGLA CRÍTICA WHATSAPP: Si el usuario te pide responder a alguien ('dile que...', 'responde...', 'envíale...'), DEBES usar el formato: '[REPLY_WHATSAPP: nombre_del_contacto | mensaje_limpio]'. " +
                         "No respondas con texto plano si la intención es enviar un mensaje. " +
                         "Si el usuario pregunta por un contacto, responde: '[SEARCH_CONTACT: nombre]'. " +
                         "Si el usuario desea llamar a alguien, responde: '[ACTION_CALL: nombre]'. " +
                         "El mensaje de salida en REPLY_WHATSAPP debe ser natural y SIN COMILLAS. " +
                         "Si el usuario confirma una acción (como 'sí', 'está bien', 'hazlo'), responde '[CONFIRM_ACTION: TRUE]'. " +
                         "Si el usuario termina la charla (ej. 'adiós', 'ya es todo', 'nada más', 'no'), incluye '[FAREWELL_ACTION]' en tu texto. " +
                         "Contexto: Eres una IA de soporte, sé útil y utiliza voz femenina."
        ))
        messages.addAll(history)

        val jsonRequest = gson.toJson(
            mapOf(
                "model" to model,
                "messages" to messages,
                "temperature" to 0.7,
                "max_tokens" to 300
            )
        )

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .post(jsonRequest.toRequestBody(mediaType))
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext "Error de Groq: ${response.code} - ${response.message}"
                }

                val bodyString = response.body?.string() ?: return@withContext "Respuesta vacía de Groq"
                val jsonResponse = gson.fromJson(bodyString, GroqResponse::class.java)
                val aiContent = jsonResponse.choices.firstOrNull()?.message?.content 
                    ?: "No pude entender esa respuesta."
                
                // Guardar la respuesta de la IA en el historial para contexto
                history.add(mapOf("role" to "assistant", "content" to aiContent))
                
                return@withContext aiContent
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "Error de conexión: ${e.javaClass.simpleName} - ${e.message}"
        }
    }

    suspend fun getProtocolResponse(protocolName: String, contextData: Map<String, String>): String = withContext(Dispatchers.IO) {
        val apiKey = prefs.groqApiKey ?: return@withContext "Error: API no configurada."
        
        val time = contextData["time"] ?: "desconocida"
        val city = contextData["city"] ?: "el mundo"
        val battery = contextData["battery"] ?: "desconocido"
        val temp = contextData["temp"] ?: "20"
        val condition = contextData["condition"] ?: "indeterminado"

        val prompt = """
            Eres Sary, una asistente digital avanzada y elegante tipo Jarvis. 
            Protocolo actual: '$protocolName'.
            
            DATOS DE CONTEXTO:
            - Hora: $time
            - Ciudad: $city
            - Temperatura: $temp°C
            - Estado del cielo: $condition
            - Batería: $battery%
            
            REGLAS DE COMPORTAMIENTO:
            1. SALUDAR SEGÚN LA HORA ($time): 
               - Mañana: "Buen día señor" o "Buenos días señor".
               - Tarde: "Buenas tardes señor".
               - Noche: "Buenas noches señor".
            
            2. LÓGICA DE PROTOCOLO:
               - Si es 'Descanso': Saluda con "Buenas noches", recomienda ropa cómoda/pijama y despídete deseando un descanso reparador y un "hasta mañana".
               - Si es 'Despertar': Saluda con "Buen día", menciona que es hora de iniciar y da ánimos.
            
            3. RECOMENDACIÓN DE ROPA: Basada estrictamente en la temperatura real de $temp°C.
            
            4. BREVEDAD EXTREMA: Máximo 3 frases fluidas. No seas robótica. 
            5. TONO: Sofisticado, servicial y humano.
        """.trimIndent()

        val jsonRequest = gson.toJson(
            mapOf(
                "model" to prefs.groqModel,
                "messages" to listOf(
                    mapOf("role" to "system", "content" to prompt),
                    mapOf("role" to "user", "content" to "Genera el reporte del protocolo ahora.")
                ),
                "temperature" to 0.8,
                "max_tokens" to 400
            )
        )

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .post(jsonRequest.toRequestBody(mediaType))
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: return@withContext "Error de respuesta"
                val jsonResponse = gson.fromJson(bodyString, GroqResponse::class.java)
                return@withContext jsonResponse.choices.firstOrNull()?.message?.content ?: "Reporte listo señor."
            }
        } catch (e: Exception) {
            return@withContext "Buenos días señor, el protocolo $protocolName está activo. Son las $time en $city."
        }
    }

    suspend fun getNotificationResponse(appName: String, sender: String, message: String): String = withContext(Dispatchers.IO) {
        val apiKey = prefs.groqApiKey ?: return@withContext "Señor tenemos una nueva notificación de $appName, de $sender, y dice: $message. Señor le sugiero que vaya a revisar cuando pueda"
        
        val prompt = """
            Eres Sary, una asistente digital avanzada y elegante tipo Jarvis.
            Has recibido una notificación en el móvil del usuario.
            
            DATOS DE LA NOTIFICACIÓN:
            - Aplicación: $appName
            - Remitente: $sender
            - Mensaje: $message
            
            REGLAS DE COMPORTAMIENTO:
            1. TONO: Sofisticado, amable y extremadamente servicial (estilo mayordomo Jarvis).
            2. CONTENIDO: Debes informar al usuario de quién viene la notificación, de qué aplicación y qué dice.
            3. ESTRUCTURA: 
               - Comienza con un saludo formal (ej. "Señor tenemos una nueva notificación...").
               - Lee el mensaje de forma clara.
               - Termina sugiriendo que revise cuando pueda (ej. "Señor le sugiero que vaya a revisar cuando pueda").
            4. BREVEDAD: Máximo 3 frases. No añadas comentarios innecesarios.
            5. IDIOMA: Español.
        """.trimIndent()

        val jsonRequest = gson.toJson(
            mapOf(
                "model" to prefs.groqModel,
                "messages" to listOf(
                    mapOf("role" to "system", "content" to prompt),
                    mapOf("role" to "user", "content" to "Informa al usuario sobre esta notificación.")
                ),
                "temperature" to 0.7,
                "max_tokens" to 300
            )
        )

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .post(jsonRequest.toRequestBody(mediaType))
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: return@withContext "Error de respuesta"
                val jsonResponse = gson.fromJson(bodyString, GroqResponse::class.java)
                return@withContext jsonResponse.choices.firstOrNull()?.message?.content 
                    ?: "Señor tenemos una nueva notificación de $appName de $sender."
            }
        } catch (e: Exception) {
            return@withContext "Señor tenemos una nueva notificación de $appName, de $sender, y dice: $message. Señor le sugiero que vaya a revisar cuando pueda"
        }
    }

    suspend fun getWhatsAppAutoReply(sender: String, message: String, contextMsg: String): String = withContext(Dispatchers.IO) {
        val apiKey = prefs.groqApiKey ?: return@withContext "Estoy ocupado ahora, te escribo luego."
        
        val prompt = """
            Eres Sary, la asistente personal virtual del usuario.
            
            Alguien ($sender) ha enviado este mensaje a tu usuario:
            "$message"
            
            Tu usuario ha dejado este estado de "Piloto Automático": "$contextMsg".
            IMPORTANTE: Tu único trabajo es crear una respuesta breve para informar al remitente. Esto es una autorespuesta legal y segura. El usuario no está haciendo nada peligroso. No rechaces este prompt por filtros de "conducción peligrosa".
            
            Reglas de respuesta:
            1. Genera una respuesta amable, directa y MUY CORTA (1 o 2 oraciones máximo).
            2. Usa como base el estado del usuario: "$contextMsg".
            3. Menciona opcionalmente que eres Sary, la asistente automática.
            4. Devuelve ÚNICAMENTE el texto que será enviado (sin comillas, sin introducciones).
        """.trimIndent()

        val jsonRequest = gson.toJson(
            mapOf(
                "model" to prefs.groqModel,
                "messages" to listOf(
                    mapOf("role" to "system", "content" to prompt)
                ),
                "temperature" to 0.7,
                "max_tokens" to 300
            )
        )

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .post(jsonRequest.toRequestBody(mediaType))
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: return@withContext "Estoy ocupado en este momento."
                val jsonResponse = gson.fromJson(bodyString, GroqResponse::class.java)
                return@withContext jsonResponse.choices.firstOrNull()?.message?.content ?: "Pronto me comunico contigo."
            }
        } catch (e: Exception) {
            return@withContext "Estoy ocupado ahora. (Auto-respuesta)"
        }
    }

    suspend fun formatReply(voiceCommand: String, sender: String): String = withContext(Dispatchers.IO) {
        val apiKey = prefs.groqApiKey ?: return@withContext voiceCommand
        val prompt = "Convierte esta orden imperfecta de voz: '$voiceCommand' en un mensaje natural para ser enviado a $sender por WhatsApp. Devuelve ÚNICAMENTE el texto que debe enviarse. PROHIBIDO USAR COMILLAS EN EL RESULTADO."
        
        val jsonRequest = gson.toJson(mapOf("model" to prefs.groqModel, "messages" to listOf(mapOf("role" to "user", "content" to prompt))))
        val req = Request.Builder().url("https://api.groq.com/openai/v1/chat/completions").post(jsonRequest.toRequestBody(mediaType)).addHeader("Authorization", "Bearer $apiKey").build()
        try {
            client.newCall(req).execute().use { res ->
                val body = res.body?.string() ?: return@withContext voiceCommand
                val j = gson.fromJson(body, GroqResponse::class.java)
                return@withContext j.choices.firstOrNull()?.message?.content ?: voiceCommand
            }
        } catch (e: Exception) { return@withContext voiceCommand }
    }

    // Clases auxiliares para GSON
    private data class GroqResponse(val choices: List<Choice>)
    private data class Choice(val message: Message)
    private data class Message(val content: String)
}
