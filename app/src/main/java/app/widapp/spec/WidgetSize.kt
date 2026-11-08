package app.widapp.spec

/** Home-screen grid sizes WidApp can place. Each maps to its own AppWidget provider. */
enum class WidgetSize(val id: String, val cols: Int, val rows: Int) {
    S1x1("1x1", 1, 1),
    S2x1("2x1", 2, 1),
    S2x2("2x2", 2, 2),
    S3x2("3x2", 3, 2),
    S4x1("4x1", 4, 1),
    S4x2("4x2", 4, 2),
    S4x3("4x3", 4, 3),
    S4x4("4x4", 4, 4);

    /** Design canvas in dp. The renderer scales sizes to the real launcher cell size. */
    val widthDp: Int get() = cols * CELL - GAP
    val heightDp: Int get() = rows * CELL - GAP
    val label: String get() = "$cols×$rows"
    val isWide: Boolean get() = cols >= 3

    companion object {
        const val CELL = 84
        const val GAP = 8
        fun of(id: String?): WidgetSize {
            val norm = id?.lowercase()?.replace("×", "x")?.replace(" ", "")
            return entries.firstOrNull { it.id == norm } ?: S2x2
        }
    }
}
