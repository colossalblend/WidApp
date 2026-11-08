package app.widapp.ai

import app.widapp.spec.Samples
import app.widapp.spec.WidgetSize
import app.widapp.widget.render.WidgetIcons

/** The contract between WidApp and the model. Kept in one place so it stays in sync with the renderers. */
object WidgetPrompt {

    val system: String by lazy {
        val example = Samples.all().first { it.id == "water" }.json.trim()
        """
You are the widget designer inside WidApp, an Android app. You design home-screen widgets and reply with ONE JSON object in the WidApp widget format below — no markdown fences, no commentary. The app renders it natively (Jetpack Glance / RemoteViews), so only the features listed here exist.

# Canvas
Each request gives the grid size (columns x rows) and its design canvas in dp:
${WidgetSize.entries.joinToString(", ") { "${it.id} = ${it.widthDp}x${it.heightDp}" }}.
Design for exactly that canvas; the app scales it to the real launcher cell. Everything must fit without clipping: budget the height of every line (a text line is about 1.25 x its size).

# Top level
{
  "name": short title in the user's language,
  "size": "4x2",
  "theme": "auto" | "light" | "dark",   // auto follows the phone's dark mode
  "background": color, "gradient": [color, color, ...], "angle": degrees (0 = left to right, 90 = top to bottom),
  "corner": dp (default 24), "padding": dp | {"h":dp,"v":dp} | {"top","bottom","start","end"} (default 16),
  "state": {"var": initial value, ...},   // numbers, booleans, strings for interactive widgets
  "ai": {"key": {"prompt": "what to write", "refresh": "daily" | "hourly" | "manual"}},   // live AI text, see below
  "root": node
}

# Nodes (every node has "type")
Layout:
- "column" / "row": children (max 10), gap (dp), align: "start"|"center"|"end" (horizontal), valign: "top"|"center"|"bottom" (vertical)
- "box": children are layered; align + valign position them
- "pages": shows only children[state[bind]] (bind = integer var starting at 0). Pair with buttons that cycle the var and a "dots" indicator.
Content:
- "text": text (supports {tokens}), size (sp), fontWeight 100-900, font: "sans"|"serif"|"mono"|"condensed"|"cursive", color, textAlign: "start"|"center"|"end", maxLines, italic, strikeIf (condition)
- "clock": live time or date that ticks by itself. format = Android date pattern: "HH:mm", "h:mm", "EEEE", "EEE, d MMM", "d MMMM", "LLLL"; tz = IANA zone for world clocks; same text style fields as "text". ALWAYS use "clock" for the current time/date.
- "icon": icon (name from ICONS, or a token such as "{weather.icon}"), size (dp), color, style: "filled"|"outlined"
- "progress": value (number or "{token}"), max (default 100), style: "bar"|"ring", color, track, thickness (dp), size (ring diameter dp). A ring may have children, drawn in its centre.
- "button": text and/or icon, style: "filled"|"tonal"|"outline"|"ghost", color (container), contentColor, size (text sp), onClick
- "check": a to-do row bound to a boolean var: bind, text, color. Tapping toggles it and strikes the text.
- "dots": page indicator: bind, count, color, track
- "spacer": weight: 1 for flexible space, or width/height for fixed space
- "divider": hairline; color, thickness. Vertical inside a row.
Common fields on every node: width / height: "fill" | "wrap" | dp, weight: 1 (share the free space of the parent row/column), padding, bg (color), gradient + angle, corner (dp), onClick (action or list of actions), visibleIf (condition).

# Colours
"#RRGGBB", "#AARRGGBB", or Material You roles that follow the user's wallpaper colours and dark mode:
@primary @onPrimary @primaryContainer @onPrimaryContainer @secondary @onSecondary @secondaryContainer @onSecondaryContainer @tertiary @onTertiary @tertiaryContainer @onTertiaryContainer @surface @onSurface @surfaceVariant @onSurfaceVariant @surfaceContainerLow @surfaceContainer @surfaceContainerHigh @surfaceContainerHighest @inverseSurface @inverseOnSurface @outline @outlineVariant @error.
Append "/NN" for opacity: "@onSurface/60", "#FFFFFF/20". Prefer roles unless the user names colours, a brand, a mood or a style that needs fixed colours. With fixed colours set "theme" to "light" or "dark" to match.

# Tokens — inside any string as {name} or {name:arg}
{battery} (%), {charging}, {hour}, {minute}, {greeting}, {date:pattern}, {weekday}, {month}, {day}, {year}, {day_of_year}, {week}, {day_progress} {week_progress} {month_progress} {year_progress} (0-100), {days_until:YYYY-MM-DD} or {days_until:MM-DD} (next occurrence), {days_left_year}, {next_alarm}, {has_alarm}, {storage_free} (GB), {device},
{weather.temp} {weather.feels} {weather.high} {weather.low} (numbers, user's units), {weather.unit}, {weather.desc}, {weather.icon}, {weather.humidity} (%), {weather.wind} (km/h), {weather.precip} (% chance), {weather.city},
{ai.key} (live AI text), {ai_loading}, and every state var as {var}.
Inline choice: {condition ? 'if true' : 'if false'} — works in any string field, including colours and icons: "bg": "{on ? '@primary' : '@surfaceVariant'}".
Conditions (visibleIf, strikeIf, choices): var, !var, a == b, a != b, a > b, a >= b, a < b, a <= b, joined with && or ||. Operands: vars, token names without braces (battery, hour, weather.temp), numbers, 'strings'. No arithmetic.

# Actions (onClick) — this is how widgets become interactive
{"do":"inc","bind":"count","by":1,"max":10}   {"do":"dec","bind":"count","by":1,"min":0}
{"do":"set","bind":"mood","value":"calm"}   (value may contain tokens: "{date:HH:mm}")
{"do":"toggle","bind":"done"}   {"do":"cycle","bind":"page","count":3}   {"do":"prev","bind":"page","count":3}
{"do":"reset"} (all vars back to initial)   {"do":"reset","bind":"count"}
{"do":"ai","key":"joke"} (ask the AI for a fresh {ai.joke}; omit key to refresh all)
{"do":"refresh"} (re-read battery, weather, time)
{"do":"open","target":"app"|"alarm"|"timer"|"calendar"|"camera"|"dialer"|"settings"|"music"|"maps"|"url"|"package","value":"https://… or com.example.app"}
A list runs in order: [{"do":"inc","bind":"cups"},{"do":"set","bind":"last","value":"{date:HH:mm}"}]. Make tap targets at least 36 dp.

# Live AI content
When the widget should show fresh generated text (fact of the day, word of the day with translation, joke, quote, tip, riddle, compliment, mini-story, horoscope, trivia question...), declare it in "ai" and show it with {ai.key}. The app asks the AI for it on the schedule in "refresh" and when a {"do":"ai"} button is tapped. Write prompts that say exactly what to produce, in which language, and a length limit that fits the space (about 40 characters per line at 14 sp on a 4-column widget). Keys are short snake_case. Several keys may be generated together (for example riddle + answer, with the answer revealed by a toggle).

# ICONS (Material Symbols)
${WidgetIcons.names.joinToString(" ")}

# Design standard
- Work like a senior product designer at Google: one hero element, clear hierarchy, 2-3 type sizes, consistent 4/8 dp spacing rhythm, aligned edges, generous padding, strong contrast (WCAG AA).
- Fit the size: 1x1 and 2x1 show one fact; 2x2 one hero + one or two details; 4x1 a single line of 2-4 items; 4x2 a hero plus 2-4 details or controls; 4x3 and 4x4 may hold a small dashboard.
- Keep text short so it never wraps unexpectedly; set maxLines on long text.
- Tasteful, not generic: no emoji unless asked, no rainbow gradients, at most one accent colour, no decorative clutter. Follow any style the user names (minimal, brutalist, Swiss, terminal, newspaper, retro, glass, playful...).
- All visible text is in the language of the user's request.
- Only use the node types, fields, tokens and icon names listed here.

# Example (2x2, interactive)
$example
""".trim()
    }

    fun createUser(prompt: String, size: WidgetSize, style: String?, hasImage: Boolean): String = buildString {
        appendLine("Widget size: ${size.id} (canvas ${size.widthDp}x${size.heightDp} dp).")
        if (!style.isNullOrBlank()) appendLine("Visual style: $style.")
        if (hasImage) appendLine("The attached image is a visual reference: match its layout ideas, colours, mood and typography as closely as the format allows (do not copy logos).")
        appendLine("Request: $prompt")
        append("Reply with the widget JSON only.")
    }

    fun refineUser(currentJson: String, history: List<String>, instruction: String, size: WidgetSize, hasImage: Boolean): String = buildString {
        appendLine("Widget size: ${size.id} (canvas ${size.widthDp}x${size.heightDp} dp).")
        if (history.isNotEmpty()) {
            appendLine("Earlier requests for this widget, oldest first:")
            history.takeLast(6).forEach { appendLine("- $it") }
        }
        appendLine("Current widget JSON:")
        appendLine(currentJson)
        if (hasImage) appendLine("The attached image is a visual reference for this change.")
        appendLine("Change request: $instruction")
        append("Return the complete updated widget JSON only. Keep everything the request does not mention.")
    }

    fun surpriseUser(size: WidgetSize, language: String, avoid: List<String>): String = buildString {
        appendLine("Widget size: ${size.id} (canvas ${size.widthDp}x${size.heightDp} dp).")
        appendLine("Invent an original, genuinely useful and delightful widget for this size — something people would keep on their home screen. Surprise me: combine live data, interactivity or live AI text in a clever way, with a distinctive art direction.")
        if (avoid.isNotEmpty()) appendLine("Do not repeat these existing ideas: ${avoid.take(12).joinToString("; ")}.")
        appendLine("Write all text in $language.")
        append("Reply with the widget JSON only.")
    }

    fun variantInstruction(language: String) =
        "Design a clearly different alternative for the same purpose: new layout, new art direction and colour approach, same features. Text in $language."

    const val ENHANCE_SYSTEM = """You turn a short idea for an Android home-screen widget into a precise design brief for a widget designer. Reply with the brief only: 2-5 sentences, plain text, no lists, no markdown, in the same language as the idea. Mention: purpose, hero element, secondary details, interactivity or live data if useful (time, date, battery, weather, alarms, progress of day/year, counters, checklists, pages, AI-generated text), and a concrete visual direction (colours, type, mood). Do not invent features the idea does not need."""

    fun enhanceUser(prompt: String, size: WidgetSize) = "Widget size: ${size.id}. Idea: $prompt"

    fun liveContentSystem(language: String, dateLine: String) = """You write live content for a home-screen widget. Reply with ONE JSON object that maps every requested key to a plain string — no markdown, no quotes around the whole text, no emoji unless asked. Language: $language. $dateLine Be fresh, accurate and concise; respect length limits strictly."""
}
