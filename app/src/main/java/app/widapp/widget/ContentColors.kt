package app.widapp.widget

import app.widapp.spec.Colors
import app.widapp.spec.WidgetPalette

/** Picks a readable default text colour for content placed on [bg]. */
object ContentColors {
    private val onRoles = mapOf(
        "primary" to "@onPrimary", "primarycontainer" to "@onPrimaryContainer",
        "secondary" to "@onSecondary", "secondarycontainer" to "@onSecondaryContainer",
        "tertiary" to "@onTertiary", "tertiarycontainer" to "@onTertiaryContainer",
        "error" to "@onError", "errorcontainer" to "@onErrorContainer",
        "inversesurface" to "@inverseOnSurface", "inverseprimary" to "@onPrimaryContainer",
        "background" to "@onBackground",
    )

    fun onColor(bg: String?, palette: WidgetPalette, fallback: String): String {
        if (bg.isNullOrBlank()) return fallback
        val s = bg.trim()
        if (s.startsWith("@")) {
            val role = s.removePrefix("@").substringBefore('/').lowercase().replace("_", "")
            onRoles[role]?.let { return it }
            if (role.startsWith("surface") || role == "widgetbackground") return "@onSurface"
            return fallback
        }
        val c = Colors.parse(s, palette) ?: return fallback
        if (android.graphics.Color.alpha(c.day) < 90) return fallback
        return if (Colors.luminance(c.day) > 0.45) "#FF1B1B18" else "#FFFFFFFF"
    }
}
