package app.widapp.ai

import app.widapp.core.AppJson
import app.widapp.core.L
import app.widapp.data.AppGraph
import app.widapp.data.WidgetDesign
import app.widapp.spec.SpecParser
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class LiveSlot(val key: String, val prompt: String, val refresh: String)

/** Generates and refreshes the `{ai.key}` texts that make widgets feel alive. */
class AiContentService(private val graph: AppGraph, private val client: AiClient) {

    fun slots(design: WidgetDesign): List<LiveSlot> = design.spec.ai.mapNotNull { (key, el) ->
        when (el) {
            is JsonPrimitive -> el.contentOrNull?.let { LiveSlot(key, it, "daily") }
            is JsonObject -> {
                val prompt = (el["prompt"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                LiveSlot(key, prompt, (el["refresh"] as? JsonPrimitive)?.contentOrNull?.lowercase() ?: "daily")
            }
            else -> null
        }
    }

    /** Keys that are missing or stale according to their refresh policy. */
    fun stale(design: WidgetDesign, now: Long = System.currentTimeMillis()): Set<String> {
        val entries = graph.aiContent.entries(design.id)
        return slots(design).filter { slot ->
            val e = entries[slot.key] ?: return@filter true
            when (slot.refresh) {
                "hourly" -> now - e.at > 60 * 60 * 1000
                "manual", "never", "once" -> false
                else -> !sameDay(e.at, now)
            }
        }.map { it.key }.toSet()
    }

    suspend fun refresh(designId: String, keys: Set<String>? = null): Result<Unit> {
        val design = graph.designs.get(designId) ?: return Result.success(Unit)
        val slots = slots(design).filter { keys == null || it.key in keys }
        if (slots.isEmpty()) return Result.success(Unit)
        val provider = graph.activeProvider() ?: return Result.failure(NoProviderException())
        val model = provider.model ?: return Result.failure(AiException(L.t("Не выбрана модель", "No model selected")))
        graph.aiContent.setLoading(designId, true)
        return try {
            val entries = graph.aiContent.entries(designId)
            val locale = Locale.getDefault()
            val lang = locale.getDisplayLanguage(Locale.ENGLISH).ifBlank { "English" }
            val now = Date()
            val dateLine = "Today is " + SimpleDateFormat("EEEE, d MMMM yyyy, HH:mm", Locale.ENGLISH).format(now) + "."
            val user = buildString {
                appendLine("Widget: \"${design.name}\".")
                appendLine("Write these keys:")
                slots.forEach { appendLine("- ${it.key}: ${it.prompt}") }
                val prev = slots.flatMap { s -> (entries[s.key]?.history.orEmpty() + listOfNotNull(entries[s.key]?.text)).map { "${s.key}: $it" } }
                if (prev.isNotEmpty()) {
                    appendLine("Previously shown (never repeat these):")
                    prev.takeLast(16).forEach { appendLine("- $it") }
                }
                append("Reply with the JSON object only, keys: ${slots.joinToString(", ") { it.key }}.")
            }
            val reply = client.complete(
                provider, graph.providers.apiKey(provider), model,
                ChatRequest(WidgetPrompt.liveContentSystem(lang, dateLine), user, maxTokens = 4000),
            )
            val obj = AppJson.parseToJsonElement(SpecParser.extractJson(reply)).jsonObject
            val values = slots.associate { s ->
                s.key to ((obj[s.key] as? JsonPrimitive)?.contentOrNull ?: obj[s.key]?.toString() ?: "").trim()
            }.filterValues { it.isNotEmpty() }
            graph.aiContent.put(designId, values)
            Result.success(Unit)
        } catch (e: Exception) {
            graph.aiContent.setLoading(designId, false)
            Result.failure(e)
        }
    }

    private fun sameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }
}
