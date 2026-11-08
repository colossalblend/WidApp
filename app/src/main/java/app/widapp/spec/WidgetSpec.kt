package app.widapp.spec

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonPrimitive

/**
 * The widget description language the AI writes. Deliberately flat and forgiving:
 * every field is optional, unknown fields are ignored, and the renderers fall back to sane defaults.
 */
@Serializable
data class WidgetSpec(
    val name: String = "Widget",
    val size: String = "2x2",
    /** "auto" follows system dark mode, "light"/"dark" pin the palette. */
    val theme: String = "auto",
    @JsonNames("bg") val background: String? = null,
    val gradient: List<String>? = null,
    val angle: Float? = null,
    val corner: Float? = null,
    val padding: JsonElement? = null,
    val state: Map<String, JsonPrimitive> = emptyMap(),
    /** Live AI content: key -> prompt string or {"prompt": "...", "refresh": "daily|hourly|manual"}. */
    val ai: Map<String, JsonElement> = emptyMap(),
    val root: WNode = WNode(),
)

@Serializable
data class WNode(
    val type: String = "column",
    val children: List<WNode> = emptyList(),

    // Layout
    val width: JsonPrimitive? = null,
    val height: JsonPrimitive? = null,
    val weight: Float? = null,
    val padding: JsonElement? = null,
    val gap: Float? = null,
    val align: String? = null,
    @JsonNames("vAlign", "valign", "verticalAlign") val valign: String? = null,

    // Decoration
    val bg: String? = null,
    val gradient: List<String>? = null,
    val angle: Float? = null,
    val corner: Float? = null,

    // Text & clock
    val text: String? = null,
    val size: Float? = null,
    val fontWeight: JsonPrimitive? = null,
    val font: String? = null,
    val color: String? = null,
    val contentColor: String? = null,
    val textAlign: String? = null,
    val maxLines: Int? = null,
    val italic: Boolean? = null,
    val strikeIf: String? = null,
    val format: String? = null,
    val tz: String? = null,

    // Icon
    @JsonNames("name") val icon: String? = null,

    // Progress / button / dots
    val value: JsonPrimitive? = null,
    val max: Float? = null,
    val style: String? = null,
    val track: String? = null,
    val thickness: Float? = null,
    val count: Int? = null,

    // State binding & interaction
    @JsonNames("var", "variable") val bind: String? = null,
    val onClick: JsonElement? = null,
    val visibleIf: String? = null,
)

@Serializable
data class WAction(
    @SerialName("do") @JsonNames("action", "type", "op") val op: String = "",
    @JsonNames("var", "variable") val bind: String? = null,
    val value: JsonPrimitive? = null,
    val by: Double? = null,
    val min: Double? = null,
    val max: Double? = null,
    val count: Int? = null,
    val target: String? = null,
    /** For "ai": which live-content key to regenerate (all when null). */
    val key: String? = null,
)

object NodeTypes {
    val containers = setOf("column", "row", "box", "pages")
    val all = containers + setOf("text", "clock", "icon", "progress", "button", "check", "dots", "spacer", "divider")
}
