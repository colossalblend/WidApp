package app.widapp.data

import app.widapp.spec.WidgetSpec
import kotlinx.serialization.Serializable

@Serializable
data class DesignVersion(
    val spec: WidgetSpec,
    /** What the user asked for to get this version ("" for manual edits). */
    val prompt: String = "",
    val model: String? = null,
    val at: Long = System.currentTimeMillis(),
    val kind: String = "ai", // ai | manual | sample | import
)

@Serializable
data class WidgetDesign(
    val id: String,
    val versions: List<DesignVersion>,
    val current: Int = versions.lastIndex,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val favorite: Boolean = false,
) {
    val spec: WidgetSpec get() = versions[current.coerceIn(0, versions.lastIndex)].spec
    val name: String get() = spec.name
    val firstPrompt: String get() = versions.firstOrNull { it.prompt.isNotBlank() }?.prompt ?: ""
}

@Serializable
data class DesignLibrary(val designs: List<WidgetDesign> = emptyList())

enum class ProviderKind { OPENAI, ANTHROPIC, GEMINI }

@Serializable
data class ModelInfo(val id: String, val name: String? = null, val context: Int? = null, val vision: Boolean? = null)

@Serializable
data class ProviderConfig(
    val id: String,
    val presetId: String,
    val kind: ProviderKind,
    val name: String,
    val baseUrl: String,
    val apiKeyEnc: String = "",
    val model: String? = null,
    val models: List<ModelInfo> = emptyList(),
    val modelsFetchedAt: Long = 0,
)

@Serializable
data class ProviderList(val providers: List<ProviderConfig> = emptyList())

@Serializable
data class AppSettings(
    val themeMode: String = "system",
    val dynamicColor: Boolean = false,
    val widgetDynamicColor: Boolean = true,
    val activeProviderId: String? = null,
    val city: City? = null,
    val units: String = "c",
    val refreshMinutes: Int = 30,
    val aiLiveContent: Boolean = true,
    val updateAutoCheck: Boolean = true,
    val updateBeta: Boolean = false,
    val updateNotify: Boolean = false,
    val skippedVersion: String? = null,
    /** Version that was running last time; used to show "What's new" once after an update. */
    val lastSeenVersion: String? = null,
    /** We asked once for notification access (to report finished background generations). */
    val askedNotifications: Boolean = false,
)

/** Live AI text for widgets: designId -> key -> value. */
@Serializable
data class AiContentEntry(val text: String, val at: Long, val history: List<String> = emptyList())

@Serializable
data class AiContent(val byDesign: Map<String, Map<String, AiContentEntry>> = emptyMap(), val loading: Set<String> = emptySet())

@Serializable
data class WeatherCache(val now: WeatherNow? = null)
