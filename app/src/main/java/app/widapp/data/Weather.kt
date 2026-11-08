package app.widapp.data

import kotlinx.serialization.Serializable

@Serializable
data class City(
    val name: String,
    val lat: Double,
    val lon: Double,
    val country: String? = null,
    val region: String? = null,
)

@Serializable
data class WeatherNow(
    val temp: Double,
    val feels: Double,
    val high: Double,
    val low: Double,
    val code: Int,
    val isDay: Boolean,
    val humidity: Int,
    val wind: Double,
    val precip: Int,
    val city: String,
    val units: String,
    val fetchedAt: Long,
)

object WeatherCodes {
    fun icon(code: Int, day: Boolean): String = when (code) {
        0 -> if (day) "sunny" else "bedtime"
        1, 2 -> if (day) "partly_cloudy_day" else "partly_cloudy_night"
        3 -> "cloud"
        45, 48 -> "foggy"
        51, 53, 55, 56, 57 -> "rainy_light"
        61, 63, 65, 66, 67, 80, 81, 82 -> "rainy"
        71, 73, 75, 77, 85, 86 -> "weather_snowy"
        95, 96, 99 -> "thunderstorm"
        else -> "cloud"
    }

    fun describe(code: Int, ru: Boolean): String = if (ru) when (code) {
        0 -> "Ясно"; 1 -> "Почти ясно"; 2 -> "Переменная облачность"; 3 -> "Пасмурно"
        45, 48 -> "Туман"; 51, 53, 55 -> "Морось"; 56, 57 -> "Ледяная морось"
        61 -> "Небольшой дождь"; 63 -> "Дождь"; 65 -> "Ливень"; 66, 67 -> "Ледяной дождь"
        71 -> "Небольшой снег"; 73 -> "Снег"; 75 -> "Снегопад"; 77 -> "Снежная крупа"
        80, 81 -> "Ливни"; 82 -> "Сильные ливни"; 85, 86 -> "Снегопад"
        95 -> "Гроза"; 96, 99 -> "Гроза с градом"; else -> "—"
    } else when (code) {
        0 -> "Clear"; 1 -> "Mostly clear"; 2 -> "Partly cloudy"; 3 -> "Overcast"
        45, 48 -> "Fog"; 51, 53, 55 -> "Drizzle"; 56, 57 -> "Freezing drizzle"
        61 -> "Light rain"; 63 -> "Rain"; 65 -> "Heavy rain"; 66, 67 -> "Freezing rain"
        71 -> "Light snow"; 73 -> "Snow"; 75 -> "Heavy snow"; 77 -> "Snow grains"
        80, 81 -> "Showers"; 82 -> "Heavy showers"; 85, 86 -> "Snow showers"
        95 -> "Thunderstorm"; 96, 99 -> "Hailstorm"; else -> "—"
    }
}
