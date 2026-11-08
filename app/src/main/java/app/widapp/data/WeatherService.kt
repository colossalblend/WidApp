package app.widapp.data

import app.widapp.core.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Open-Meteo: free, no key, no location permission (the user picks a city). */
class WeatherService(private val graph: AppGraph) {
    private val http = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()

    suspend fun searchCity(query: String): List<City> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", query.trim())
            .addQueryParameter("count", "8")
            .addQueryParameter("language", Locale.getDefault().language)
            .addQueryParameter("format", "json").build()
        val obj = getJson(url.toString())
        obj["results"]?.jsonArray?.mapNotNull { el ->
            val o = el.jsonObject
            City(
                name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                lat = o["latitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null,
                lon = o["longitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null,
                country = o["country"]?.jsonPrimitive?.contentOrNull,
                region = o["admin1"]?.jsonPrimitive?.contentOrNull,
            )
        } ?: emptyList()
    }

    suspend fun refresh(): Result<WeatherNow?> = withContext(Dispatchers.IO) {
        val s = graph.settings.value
        val city = s.city ?: return@withContext Result.success(null)
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
                .addQueryParameter("latitude", city.lat.toString())
                .addQueryParameter("longitude", city.lon.toString())
                .addQueryParameter("current", "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day")
                .addQueryParameter("daily", "temperature_2m_max,temperature_2m_min,precipitation_probability_max")
                .addQueryParameter("timezone", "auto")
                .addQueryParameter("forecast_days", "1")
                .apply { if (s.units == "f") addQueryParameter("temperature_unit", "fahrenheit") }
                .build()
            val o = getJson(url.toString())
            val cur = o["current"]!!.jsonObject
            val daily = o["daily"]?.jsonObject
            fun d(key: String) = daily?.get(key)?.jsonArray?.firstOrNull()?.jsonPrimitive?.doubleOrNull
            val w = WeatherNow(
                temp = cur["temperature_2m"]!!.jsonPrimitive.doubleOrNull ?: 0.0,
                feels = cur["apparent_temperature"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                high = d("temperature_2m_max") ?: 0.0,
                low = d("temperature_2m_min") ?: 0.0,
                code = cur["weather_code"]?.jsonPrimitive?.intOrNull ?: 0,
                isDay = (cur["is_day"]?.jsonPrimitive?.intOrNull ?: 1) == 1,
                humidity = cur["relative_humidity_2m"]?.jsonPrimitive?.intOrNull ?: 0,
                wind = cur["wind_speed_10m"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                precip = d("precipitation_probability_max")?.toInt() ?: 0,
                city = city.name,
                units = s.units,
                fetchedAt = System.currentTimeMillis(),
            )
            graph.weather.put(w)
            w
        }
    }

    private fun getJson(url: String): JsonObject {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            return AppJson.parseToJsonElement(r.body.string()).jsonObject
        }
    }
}
