package app.widapp.spec

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import app.widapp.ui.theme.DarkBrand
import app.widapp.ui.theme.LightBrand
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** A colour that may differ between light and dark mode. */
data class DayNight(val day: Int, val night: Int) {
    fun pick(dark: Boolean) = if (dark) night else day
}

/** Material colour roles available to widget specs as `@role`. */
class WidgetPalette(private val light: Map<String, Int>, private val dark: Map<String, Int>) {
    fun role(name: String): DayNight? {
        val key = name.lowercase().replace("_", "").replace("-", "")
        val d = light[key] ?: return null
        return DayNight(d, dark[key] ?: d)
    }

    companion object {
        private fun ColorScheme.roles(): Map<String, Int> = mapOf(
            "primary" to primary, "onprimary" to onPrimary,
            "primarycontainer" to primaryContainer, "onprimarycontainer" to onPrimaryContainer,
            "secondary" to secondary, "onsecondary" to onSecondary,
            "secondarycontainer" to secondaryContainer, "onsecondarycontainer" to onSecondaryContainer,
            "tertiary" to tertiary, "ontertiary" to onTertiary,
            "tertiarycontainer" to tertiaryContainer, "ontertiarycontainer" to onTertiaryContainer,
            "error" to error, "onerror" to onError, "errorcontainer" to errorContainer, "onerrorcontainer" to onErrorContainer,
            "background" to background, "onbackground" to onBackground,
            "surface" to surface, "onsurface" to onSurface,
            "surfacevariant" to surfaceVariant, "onsurfacevariant" to onSurfaceVariant,
            "surfacecontainerlowest" to surfaceContainerLowest, "surfacecontainerlow" to surfaceContainerLow,
            "surfacecontainer" to surfaceContainer, "surfacecontainerhigh" to surfaceContainerHigh,
            "surfacecontainerhighest" to surfaceContainerHighest,
            "widgetbackground" to surfaceContainer,
            "outline" to outline, "outlinevariant" to outlineVariant,
            "inversesurface" to inverseSurface, "inverseonsurface" to inverseOnSurface, "inverseprimary" to inversePrimary,
        ).mapValues { it.value.toArgb() }

        fun from(context: Context, dynamic: Boolean): WidgetPalette {
            val useDynamic = dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val l = if (useDynamic) dynamicLightColorScheme(context) else LightBrand
            val d = if (useDynamic) dynamicDarkColorScheme(context) else DarkBrand
            return WidgetPalette(l.roles(), d.roles())
        }
    }
}

object Colors {
    /** Parses "#RGB", "#RRGGBB", "#AARRGGBB", "rgb()/rgba()", named colours and "@role[/alpha]". */
    fun parse(raw: String?, palette: WidgetPalette): DayNight? {
        var s = raw?.trim()?.trim('\'', '"') ?: return null
        if (s.isEmpty()) return null
        var alpha: Float? = null
        val slash = s.lastIndexOf('/')
        if (slash > 0 && !s.lowercase().startsWith("rgb")) {
            alpha = s.substring(slash + 1).trim().removeSuffix("%").toFloatOrNull()?.let { if (it > 1f) it / 100f else it }
            s = s.substring(0, slash).trim()
        }
        val base = if (s.startsWith("@")) palette.role(s.removePrefix("@")) ?: return null
        else parseFixed(s)?.let { DayNight(it, it) } ?: return null
        return if (alpha != null) DayNight(withAlpha(base.day, alpha), withAlpha(base.night, alpha)) else base
    }

    private fun parseFixed(s: String): Int? {
        val lower = s.lowercase()
        if (lower == "transparent" || lower == "none") return 0
        if (lower.startsWith("rgb")) {
            val nums = lower.substringAfter('(').substringBefore(')').split(',', ' ', '/').filter { it.isNotBlank() }
                .mapNotNull { it.trim().removeSuffix("%").toFloatOrNull() }
            if (nums.size >= 3) {
                val a = if (nums.size >= 4) (if (nums[3] <= 1f) nums[3] * 255 else nums[3]) else 255f
                return android.graphics.Color.argb(a.toInt().coerceIn(0, 255), nums[0].toInt().coerceIn(0, 255), nums[1].toInt().coerceIn(0, 255), nums[2].toInt().coerceIn(0, 255))
            }
            return null
        }
        if (lower.startsWith("#")) {
            val hex = lower.substring(1)
            return when (hex.length) {
                3 -> ("ff" + hex.map { "$it$it" }.joinToString("")).toLongOrNull(16)?.toInt()
                4 -> hex.map { "$it$it" }.joinToString("").toLongOrNull(16)?.toInt()
                6 -> ("ff$hex").toLongOrNull(16)?.toInt()
                8 -> hex.toLongOrNull(16)?.toInt()
                else -> null
            }
        }
        return runCatching { android.graphics.Color.parseColor(lower) }.getOrNull()
    }

    fun withAlpha(color: Int, alpha: Float): Int {
        val a = (android.graphics.Color.alpha(color) * alpha.coerceIn(0f, 1f)).toInt()
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    fun luminance(color: Int): Double {
        fun ch(v: Int): Double { val c = v / 255.0; return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(android.graphics.Color.red(color)) + 0.7152 * ch(android.graphics.Color.green(color)) + 0.0722 * ch(android.graphics.Color.blue(color))
    }
}

sealed interface Dim {
    data object Fill : Dim
    data object Wrap : Dim
    data class Fixed(val dp: Float) : Dim
}

fun parseDim(p: JsonPrimitive?): Dim? {
    if (p == null) return null
    val s = p.content.trim().lowercase()
    return when {
        s == "fill" || s == "match" || s == "max" || s == "100%" || s == "match_parent" -> Dim.Fill
        s == "wrap" || s == "auto" || s == "wrap_content" -> Dim.Wrap
        else -> s.removeSuffix("dp").toFloatOrNull()?.let { Dim.Fixed(it) }
    }
}

data class Insets(val top: Float = 0f, val end: Float = 0f, val bottom: Float = 0f, val start: Float = 0f) {
    val isZero get() = top == 0f && end == 0f && bottom == 0f && start == 0f
    fun scaled(k: Float) = Insets(top * k, end * k, bottom * k, start * k)
}

fun parseInsets(el: JsonElement?, default: Float = 0f): Insets {
    when (el) {
        null -> return Insets(default, default, default, default)
        is JsonPrimitive -> {
            val v = el.doubleOrNull?.toFloat() ?: el.content.removeSuffix("dp").toFloatOrNull() ?: default
            return Insets(v, v, v, v)
        }
        is JsonArray -> {
            val n = el.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull?.toFloat() }
            return when (n.size) {
                1 -> Insets(n[0], n[0], n[0], n[0])
                2 -> Insets(n[0], n[1], n[0], n[1])
                3 -> Insets(n[0], n[1], n[2], n[1])
                4 -> Insets(n[0], n[1], n[2], n[3])
                else -> Insets(default, default, default, default)
            }
        }
        is JsonObject -> {
            fun f(vararg keys: String): Float? = keys.firstNotNullOfOrNull { k -> el[k]?.let { runCatching { it.jsonPrimitive.doubleOrNull?.toFloat() }.getOrNull() } }
            val all = f("all")
            val h = f("h", "horizontal", "x") ?: all ?: default
            val v = f("v", "vertical", "y") ?: all ?: default
            return Insets(
                top = f("top", "t") ?: v,
                end = f("end", "right", "r") ?: h,
                bottom = f("bottom", "b") ?: v,
                start = f("start", "left", "l") ?: h,
            )
        }
    }
}

/** System font family + the three weights RemoteViews can express. */
data class FontSpec(val family: String, val weight: Int, val italic: Boolean)

object Fonts {
    fun numericWeight(p: JsonPrimitive?): Int {
        if (p == null) return 400
        p.content.toIntOrNull()?.let { return it.coerceIn(100, 900) }
        return when (p.content.lowercase()) {
            "thin", "hairline" -> 100
            "extralight", "ultralight" -> 200
            "light" -> 300
            "regular", "normal", "book" -> 400
            "medium" -> 500
            "semibold", "demibold" -> 600
            "bold" -> 700
            "extrabold", "heavy" -> 800
            "black" -> 900
            else -> 400
        }
    }

    fun resolve(font: String?, weightRaw: JsonPrimitive?, italic: Boolean?): FontSpec {
        val w = numericWeight(weightRaw)
        val it = italic == true
        return when (font?.lowercase()?.trim()) {
            "serif", "editorial", "classic" -> FontSpec("serif", if (w >= 600) 700 else 400, it)
            "mono", "monospace", "code" -> FontSpec("monospace", if (w >= 600) 700 else 400, it)
            "condensed", "narrow", "compressed" -> when {
                w <= 300 -> FontSpec("sans-serif-condensed-light", 400, it)
                w >= 600 -> FontSpec("sans-serif-condensed", 700, it)
                w == 500 -> FontSpec("sans-serif-condensed-medium", 400, it)
                else -> FontSpec("sans-serif-condensed", 400, it)
            }
            "cursive", "handwriting", "script" -> FontSpec("cursive", if (w >= 600) 700 else 400, it)
            "casual" -> FontSpec("casual", 400, it)
            else -> when {
                w <= 200 -> FontSpec("sans-serif-thin", 400, it)
                w <= 300 -> FontSpec("sans-serif-light", 400, it)
                w <= 450 -> FontSpec("sans-serif", 400, it)
                w <= 550 -> FontSpec("sans-serif-medium", 400, it)
                w <= 750 -> FontSpec("sans-serif", 700, it)
                else -> FontSpec("sans-serif-black", 400, it)
            }
        }
    }
}

enum class HAlign { Start, Center, End }
enum class VAlign { Top, Center, Bottom }

fun parseH(s: String?): HAlign? = when (s?.lowercase()?.trim()) {
    "start", "left" -> HAlign.Start
    "center", "centre", "middle" -> HAlign.Center
    "end", "right" -> HAlign.End
    else -> null
}

fun parseV(s: String?): VAlign? = when (s?.lowercase()?.trim()) {
    "top", "start" -> VAlign.Top
    "center", "centre", "middle" -> VAlign.Center
    "bottom", "end" -> VAlign.Bottom
    else -> null
}

/** Scale factor between the design canvas and the size the launcher actually gives us. */
fun scaleFor(size: WidgetSize, widthDp: Float, heightDp: Float): Float {
    if (widthDp <= 0f || heightDp <= 0f) return 1f
    val k = minOf(widthDp / size.widthDp, heightDp / size.heightDp)
    return k.coerceIn(0.6f, 1.6f)
}
