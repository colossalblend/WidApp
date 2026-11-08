package app.widapp.core

import java.util.Locale

/** Inline localisation for messages produced outside the UI layer (network errors, AI status). */
object L {
    val ru: Boolean get() = Locale.getDefault().language in setOf("ru", "uk", "be", "kk")
    fun t(ru: String, en: String): String = if (this.ru) ru else en
}
