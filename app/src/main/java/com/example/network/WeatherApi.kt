package com.example.network

import com.example.model.WeatherInfo
import com.example.util.RawConsoleLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class CitySearchResult(
    val name: String,
    val adminArea: String,
    val country: String,
    val latitude: Double,
    val longitude: Double
)

class WeatherClient {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun fetchCurrentWeather(
        cityName: String,
        latitude: Double,
        longitude: Double
    ): WeatherInfo? = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?" +
            "latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,weather_code,wind_speed_10m,is_day" +
            "&daily=temperature_2m_max,temperature_2m_min" +
            "&forecast_days=1&timezone=auto"
        try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            RawConsoleLogger.http("Weather", "GET Open-Meteo ($cityName) -> ${response.code}")
            if (!response.isSuccessful) return@withContext null

            val json = JSONObject(body)
            val current = json.optJSONObject("current") ?: return@withContext null
            val daily = json.optJSONObject("daily")
            val maxArr = daily?.optJSONArray("temperature_2m_max")
            val minArr = daily?.optJSONArray("temperature_2m_min")

            val temp = current.optDouble("temperature_2m", 0.0)
            val code = current.optInt("weather_code", 0)
            val wind = current.optDouble("wind_speed_10m", 0.0)
            val isDay = current.optInt("is_day", 1) == 1
            val maxT = maxArr?.optDouble(0, temp) ?: temp
            val minT = minArr?.optDouble(0, temp) ?: temp

            WeatherInfo(
                cityName = cityName,
                temperatureC = temp,
                minTempC = minT,
                maxTempC = maxT,
                windKmh = wind,
                conditionDutch = wmoCodeToDutch(code),
                weatherCode = code,
                isDay = isDay,
                latitude = latitude,
                longitude = longitude
            )
        } catch (e: Exception) {
            RawConsoleLogger.warn("Weather", "Weer ophalen overgeslagen: ${e.message}")
            null
        }
    }

    suspend fun searchCity(query: String): List<CitySearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=6&language=nl&format=json"
        try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val json = JSONObject(response.body?.string() ?: "{}")
            val results = json.optJSONArray("results") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    add(
                        CitySearchResult(
                            name = item.optString("name", trimmed),
                            adminArea = item.optString("admin1", ""),
                            country = item.optString("country", ""),
                            latitude = item.optDouble("latitude", 52.0907),
                            longitude = item.optDouble("longitude", 5.1214)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun wmoCodeToDutch(code: Int): String = when (code) {
        0 -> "Helder"
        1 -> "Licht bewolkt"
        2 -> "Half bewolkt"
        3 -> "Bewolkt"
        45, 48 -> "Mist"
        51, 53, 55, 56, 57 -> "Motregen"
        61, 63, 65, 66, 67 -> "Regen"
        71, 73, 75, 77 -> "Sneeuw"
        80, 81, 82 -> "Regenbuien"
        85, 86 -> "Sneeuwbuien"
        95, 96, 99 -> "Onweer"
        else -> "Wisselvallig"
    }
}
