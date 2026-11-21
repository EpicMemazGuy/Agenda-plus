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
            "&hourly=precipitation_probability,precipitation" +
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

            val hourly = json.optJSONObject("hourly")
            val timeArr = hourly?.optJSONArray("time")
            val probArr = hourly?.optJSONArray("precipitation_probability")
            val mmArr = hourly?.optJSONArray("precipitation")
            val hourlyTimes = buildList {
                if (timeArr != null) {
                    for (i in 0 until timeArr.length()) {
                        add(timeArr.optString(i, ""))
                    }
                }
            }
            val hourlyProb = buildList {
                if (probArr != null) {
                    for (i in 0 until probArr.length()) {
                        add(probArr.optInt(i, 0))
                    }
                }
            }
            val hourlyMm = buildList {
                if (mmArr != null) {
                    for (i in 0 until mmArr.length()) {
                        add(mmArr.optDouble(i, 0.0))
                    }
                }
            }

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
                longitude = longitude,
                hourlyTimesIso = hourlyTimes,
                hourlyPrecipProb = hourlyProb,
                hourlyPrecipMm = hourlyMm
            )
        } catch (e: Exception) {
            RawConsoleLogger.warn("Weather", "Weer ophalen overgeslagen: ${e.message}")
            null
        }
    }

    companion object {
        fun evaluateRainForWindow(
            weather: WeatherInfo?,
            startMinutes: Int,
            durationMinutes: Int = 30
        ): com.example.model.RainForecastWindow {
            if (weather == null || weather.hourlyTimesIso.isEmpty()) {
                return com.example.model.RainForecastWindow()
            }
            val endMinutes = startMinutes + durationMinutes.coerceAtLeast(10)
            var maxProb = 0
            var maxMm = 0.0
            for (i in weather.hourlyTimesIso.indices) {
                val timeStr = weather.hourlyTimesIso[i]
                val tIdx = timeStr.indexOf('T')
                val hourInt = if (tIdx >= 0 && timeStr.length >= tIdx + 3) {
                    timeStr.substring(tIdx + 1, tIdx + 3).toIntOrNull()
                } else null
                if (hourInt == null) continue
                val bucketStart = hourInt * 60
                val bucketEnd = bucketStart + 59
                val overlaps = bucketStart <= endMinutes && bucketEnd >= startMinutes
                if (overlaps) {
                    val p = weather.hourlyPrecipProb.getOrNull(i) ?: 0
                    val mm = weather.hourlyPrecipMm.getOrNull(i) ?: 0.0
                    if (p > maxProb) maxProb = p
                    if (mm > maxMm) maxMm = mm
                }
            }
            val isRain = maxProb >= 40 || maxMm >= 0.2
            val summary = if (isRain) {
                val mmText = if (maxMm >= 0.1) String.format(java.util.Locale.US, "%.1f mm", maxMm) else "kans op bui"
                "Regen tijdens vertrek ($maxProb% • $mmText)"
            } else ""
            return com.example.model.RainForecastWindow(
                maxProbabilityPercent = maxProb,
                maxPrecipitationMm = maxMm,
                isRainExpected = isRain,
                summaryDutch = summary
            )
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
