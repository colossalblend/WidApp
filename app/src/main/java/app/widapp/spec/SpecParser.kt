package app.widapp.spec

import app.widapp.core.AppJson
import app.widapp.core.PrettyJson
import app.widapp.widget.render.WidgetIcons
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

class SpecParseException(message: String) : Exception(message)

object SpecParser {
    /** Pulls the first JSON object out of a model reply (code fences, prose around it, etc.). */
    fun extractJson(reply: String): String {
        var s = reply.trim()
        val fence = Regex("```(?:json|JSON)?\\s*([\\s\\S]*?)```").find(s)
        if (fence != null) s = fence.groupValues[1].trim()
        val start = s.indexOf('{')
        if (start < 0) throw SpecParseException("No JSON object in the reply")
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until s.length) {
            val c = s[i]
            if (inString) {
                if (escape) escape = false
                else if (c == '\\') escape = true
                else if (c == '"') inString = false
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return s.substring(start, i + 1) }
            }
        }
        throw SpecParseException("The JSON object is not closed (reply was cut off?)")
    }

    fun parse(text: String): WidgetSpec {
        val json = extractJson(text)
        val element = try {
            AppJson.parseToJsonElement(json)
        } catch (e: Exception) {
            throw SpecParseException("Invalid JSON: ${e.message?.take(200)}")
        }
        var obj = element.jsonObject
        // Some models wrap the result: {"widget": {...}}
        if (!obj.containsKey("root")) {
            val inner = obj.values.firstOrNull { it is JsonObject && it.jsonObject.containsKey("root") }
            if (inner != null) obj = inner.jsonObject
            else if (obj.containsKey("type")) obj = JsonObject(mapOf("root" to obj))
        }
        val spec = try {
            AppJson.decodeFromJsonElement<WidgetSpec>(obj)
        } catch (e: Exception) {
            throw SpecParseException("Does not match the widget schema: ${e.message?.take(200)}")
        }
        return normalize(spec)
    }

    fun normalize(spec: WidgetSpec): WidgetSpec = spec.copy(
        size = WidgetSize.of(spec.size).id,
        root = fix(spec.root),
    )

    private fun fix(n: WNode): WNode {
        val type = n.type.lowercase().let {
            when (it) {
                "col", "vstack", "vertical" -> "column"
                "hstack", "horizontal" -> "row"
                "stack", "zstack", "layer", "container", "card" -> "box"
                "label", "title" -> "text"
                "time", "textclock" -> "clock"
                "image", "symbol" -> "icon"
                "progressbar", "ring", "bar", "gauge", "meter" -> "progress"
                "checkbox", "todo", "toggle" -> "check"
                "pager", "carousel", "tabs" -> "pages"
                "indicator", "pagerdots" -> "dots"
                "space", "gap" -> "spacer"
                "line", "separator", "hr" -> "divider"
                else -> it
            }
        }
        val known = if (type in NodeTypes.all) type else if (n.children.isNotEmpty()) "box" else "spacer"
        val icon = n.icon?.trim()?.let { if (it.contains('{')) it else it.lowercase().replace(' ', '_').replace('-', '_') }
        return n.copy(
            type = known,
            icon = icon,
            style = if (type == "progress" && n.style == null && n.type.lowercase() == "ring") "ring" else n.style,
            children = n.children.map { fix(it) },
        )
    }

    fun toJson(spec: WidgetSpec): String = PrettyJson.encodeToString(WidgetSpec.serializer(), spec)

    /** Collects names used so the app can tell what data a widget needs (e.g. weather). */
    fun usesWeather(spec: WidgetSpec): Boolean = toJson(spec).contains("weather.")

    fun iconKnown(name: String?) = name != null && (WidgetIcons.filled.containsKey(name) || name.contains('{'))
}
