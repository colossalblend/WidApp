package app.widapp.core

import kotlinx.serialization.json.Json

/** Lenient JSON shared by storage and AI parsing: models and old files may carry extra or odd fields. */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = false
    allowTrailingComma = true
    allowComments = true
}

val PrettyJson: Json = Json(AppJson) {
    prettyPrint = true
    prettyPrintIndent = "  "
}
