package com.eddam.heysary

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class WeatherClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()

    suspend fun getCurrentWeather(lat: Double, lon: Double): WeatherData? = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
        
        val request = Request.Builder().url(url).build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                
                val body = response.body?.string() ?: return@withContext null
                val apiResponse = gson.fromJson(body, OpenMeteoResponse::class.java)
                
                val current = apiResponse.current_weather
                val condition = mapWeatherCode(current.weathercode)
                
                WeatherData(current.temperature, condition)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun mapWeatherCode(code: Int): String {
        return when (code) {
            0 -> "Cielo despejado"
            1, 2, 3 -> "Parcialmente nublado"
            45, 48 -> "Niebla"
            51, 53, 55 -> "Drizzle"
            61, 63, 65 -> "Lluvia"
            71, 73, 75 -> "Nieve"
            80, 81, 82 -> "Chubascos"
            95, 96, 99 -> "Tormenta"
            else -> "Condiciones variables"
        }
    }

    data class WeatherData(val temp: Double, val condition: String)
    
    private data class OpenMeteoResponse(val current_weather: CurrentWeather)
    private data class CurrentWeather(val temperature: Double, val weathercode: Int)
}
