package app.widapp.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.widapp.core.AppJson
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonPrimitive

/** Per-widget-instance state kept in Glance's preferences store. */
object WidgetKeys {
    val DESIGN = stringPreferencesKey("design_id")
    val STATE = stringPreferencesKey("state")
    private val serializer = MapSerializer(String.serializer(), JsonPrimitive.serializer())

    fun readState(prefs: Preferences): Map<String, JsonPrimitive> =
        prefs[STATE]?.let { runCatching { AppJson.decodeFromString(serializer, it) }.getOrNull() } ?: emptyMap()

    fun writeState(prefs: MutablePreferences, state: Map<String, JsonPrimitive>) {
        prefs[STATE] = AppJson.encodeToString(serializer, state)
    }
}
