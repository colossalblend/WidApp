package app.widapp.widget.render

import app.widapp.R

/** TextClock layouts per system font (RemoteViews cannot change a TextClock typeface at runtime). */
object ClockLayouts {
    private val map = mapOf(
        "sans-serif|0" to R.layout.clock_sans_serif,
        "sans-serif|1" to R.layout.clock_sans_serif_bold,
        "sans-serif-thin|0" to R.layout.clock_sans_serif_thin,
        "sans-serif-light|0" to R.layout.clock_sans_serif_light,
        "sans-serif-medium|0" to R.layout.clock_sans_serif_medium,
        "sans-serif-black|0" to R.layout.clock_sans_serif_black,
        "sans-serif-condensed|0" to R.layout.clock_sans_serif_condensed,
        "sans-serif-condensed|1" to R.layout.clock_sans_serif_condensed_bold,
        "sans-serif-condensed-light|0" to R.layout.clock_sans_serif_condensed_light,
        "sans-serif-condensed-medium|0" to R.layout.clock_sans_serif_condensed_medium,
        "serif|0" to R.layout.clock_serif,
        "serif|1" to R.layout.clock_serif_bold,
        "monospace|0" to R.layout.clock_monospace,
        "monospace|1" to R.layout.clock_monospace_bold,
        "cursive|0" to R.layout.clock_cursive,
        "cursive|1" to R.layout.clock_cursive_bold,
        "casual|0" to R.layout.clock_casual,
    )

    fun forFont(family: String, bold: Boolean): Int =
        map["$family|${if (bold) 1 else 0}"] ?: map["$family|0"] ?: R.layout.clock_sans_serif
}
