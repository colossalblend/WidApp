package app.widapp.spec

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.text.format.DateFormat
import app.widapp.data.WeatherCodes
import app.widapp.data.WeatherNow
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Everything a widget can read at render time. Captured cheaply on every render. */
class DataSnapshot(
    val now: Calendar,
    val locale: Locale,
    val battery: Int,
    val charging: Boolean,
    val nextAlarmMillis: Long?,
    val storageFreeGb: Double,
    val weather: WeatherNow?,
    val use24h: Boolean,
    val ai: Map<String, String> = emptyMap(),
    val aiLoading: Boolean = false,
) {
    private val ru get() = locale.language == "ru"

    fun resolve(name: String, arg: String?): Any? {
        val n = name.trim().lowercase()
        return when (n) {
            "battery" -> battery
            "charging" -> charging
            "hour" -> now.get(Calendar.HOUR_OF_DAY)
            "minute" -> now.get(Calendar.MINUTE)
            "time" -> fmt(arg ?: if (use24h) "HH:mm" else "h:mm")
            "date" -> fmt(arg ?: "d MMMM")
            "weekday" -> fmt("EEEE")
            "month" -> fmt("LLLL")
            "year" -> now.get(Calendar.YEAR)
            "day" -> now.get(Calendar.DAY_OF_MONTH)
            "day_of_year" -> now.get(Calendar.DAY_OF_YEAR)
            "week" -> now.get(Calendar.WEEK_OF_YEAR)
            "greeting" -> greeting()
            "day_progress" -> pct((now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)) / 1440.0)
            "week_progress" -> {
                val dow = (now.get(Calendar.DAY_OF_WEEK) - now.firstDayOfWeek + 7) % 7
                pct((dow + (now.get(Calendar.HOUR_OF_DAY) / 24.0)) / 7.0)
            }
            "month_progress" -> pct((now.get(Calendar.DAY_OF_MONTH) - 1 + now.get(Calendar.HOUR_OF_DAY) / 24.0) / now.getActualMaximum(Calendar.DAY_OF_MONTH))
            "year_progress" -> pct((now.get(Calendar.DAY_OF_YEAR) - 1) / now.getActualMaximum(Calendar.DAY_OF_YEAR).toDouble())
            "days_left_year" -> now.getActualMaximum(Calendar.DAY_OF_YEAR) - now.get(Calendar.DAY_OF_YEAR)
            "days_until" -> daysUntil(arg)
            "next_alarm" -> nextAlarmMillis?.let {
                val c = Calendar.getInstance().apply { timeInMillis = it }
                DateFormat.format(if (use24h) "HH:mm" else "h:mm a", c).toString()
            } ?: "—"
            "has_alarm" -> nextAlarmMillis != null
            "storage_free" -> Math.round(storageFreeGb * 10) / 10.0
            "device" -> Build.MODEL
            "ai_loading" -> aiLoading
            else -> when {
                n.startsWith("weather.") -> weather(n.removePrefix("weather."))
                n.startsWith("ai.") -> ai[name.trim().substring(3)] ?: ai[n.substring(3)] ?: if (aiLoading) "…" else "…"
                else -> null
            }
        }
    }

    private fun weather(key: String): Any? {
        val w = weather ?: return when (key) { "icon" -> "cloud"; "unit" -> "°"; else -> null }
        return when (key) {
            "temp" -> Math.round(w.temp).toInt()
            "feels" -> Math.round(w.feels).toInt()
            "high" -> Math.round(w.high).toInt()
            "low" -> Math.round(w.low).toInt()
            "desc" -> WeatherCodes.describe(w.code, ru)
            "icon" -> WeatherCodes.icon(w.code, w.isDay)
            "code" -> w.code
            "humidity" -> w.humidity
            "wind" -> Math.round(w.wind).toInt()
            "precip" -> w.precip
            "city" -> w.city
            "unit" -> if (w.units == "f") "°F" else "°C"
            "is_day" -> w.isDay
            else -> null
        }
    }

    private fun fmt(pattern: String): String = try {
        DateFormat.format(pattern, now).toString()
    } catch (e: Exception) { pattern }

    private fun pct(f: Double) = (f.coerceIn(0.0, 1.0) * 100).toInt()

    private fun greeting(): String {
        val h = now.get(Calendar.HOUR_OF_DAY)
        return if (ru) when (h) {
            in 5..11 -> "Доброе утро"; in 12..16 -> "Добрый день"; in 17..22 -> "Добрый вечер"; else -> "Доброй ночи"
        } else when (h) {
            in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..22 -> "Good evening"; else -> "Good night"
        }
    }

    private fun daysUntil(arg: String?): Any? {
        val parts = arg?.trim()?.split("-")?.mapNotNull { it.toIntOrNull() } ?: return null
        val target = Calendar.getInstance().apply {
            clear()
            when (parts.size) {
                3 -> set(parts[0], parts[1] - 1, parts[2])
                2 -> { // MM-DD: next occurrence
                    set(now.get(Calendar.YEAR), parts[0] - 1, parts[1])
                    if (before(startOfToday())) add(Calendar.YEAR, 1)
                }
                else -> return null
            }
        }
        val diff = target.timeInMillis - startOfToday().timeInMillis
        return TimeUnit.MILLISECONDS.toDays(diff).coerceAtLeast(0)
    }

    private fun startOfToday(): Calendar = (now.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    companion object {
        fun capture(
            context: Context,
            weather: WeatherNow?,
            ai: Map<String, String> = emptyMap(),
            aiLoading: Boolean = false,
        ): DataSnapshot {
            val batt = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batt?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batt?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val status = batt?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) level * 100 / scale else 0
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val alarm = runCatching {
                (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).nextAlarmClock?.triggerTime
            }.getOrNull()
            val free = runCatching {
                val st = StatFs(Environment.getDataDirectory().path)
                st.availableBytes / 1_073_741_824.0
            }.getOrDefault(0.0)
            val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
            return DataSnapshot(
                now = Calendar.getInstance(locale),
                locale = locale,
                battery = pct,
                charging = charging,
                nextAlarmMillis = alarm,
                storageFreeGb = free,
                weather = weather,
                use24h = DateFormat.is24HourFormat(context),
                ai = ai,
                aiLoading = aiLoading,
            )
        }
    }
}
