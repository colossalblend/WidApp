package app.widapp.ai

import app.widapp.core.L
import app.widapp.data.AppGraph
import app.widapp.data.ProviderConfig
import app.widapp.spec.SpecParseException
import app.widapp.spec.SpecParser
import app.widapp.spec.WidgetSize
import app.widapp.spec.WidgetSpec
import java.util.Locale

data class GenerationResult(val spec: WidgetSpec, val model: String)

class NoProviderException : AiException(L.t("Подключите ИИ-провайдера во вкладке «Модели»", "Connect an AI provider in the Models tab"))

/** High-level AI operations used by the UI. */
class WidgetGenerator(private val graph: AppGraph, private val client: AiClient) {

    private fun language(): String = when (Locale.getDefault().language) {
        "ru" -> "Russian"; "uk" -> "Ukrainian"; "en" -> "English"; "de" -> "German"; "es" -> "Spanish"
        else -> Locale.getDefault().getDisplayLanguage(Locale.ENGLISH)
    }

    private fun provider(): Pair<ProviderConfig, String> {
        val p = graph.activeProvider() ?: throw NoProviderException()
        val model = p.model ?: throw AiException(L.t("Выберите модель для «${p.name}» во вкладке «Модели»", "Pick a model for ${p.name} in the Models tab"))
        return p to model
    }

    suspend fun create(prompt: String, size: WidgetSize, style: String?, image: ImageInput?): GenerationResult =
        run(WidgetPrompt.createUser(prompt, size, style, image != null), size, image)

    suspend fun refine(current: WidgetSpec, history: List<String>, instruction: String, image: ImageInput?): GenerationResult {
        val size = WidgetSize.of(current.size)
        return run(WidgetPrompt.refineUser(SpecParser.toJson(current), history, instruction, size, image != null), size, image)
    }

    suspend fun variant(current: WidgetSpec, history: List<String>): GenerationResult =
        refine(current, history, WidgetPrompt.variantInstruction(language()), null)

    suspend fun surprise(size: WidgetSize): GenerationResult {
        val avoid = graph.designs.designs.map { it.name }
        return run(WidgetPrompt.surpriseUser(size, language(), avoid), size, null)
    }

    suspend fun enhance(prompt: String, size: WidgetSize): String {
        val (p, model) = provider()
        val text = client.complete(
            p, graph.providers.apiKey(p), model,
            ChatRequest(WidgetPrompt.ENHANCE_SYSTEM, WidgetPrompt.enhanceUser(prompt, size), maxTokens = 2000, json = false),
        )
        return text.trim().trim('"', '«', '»').ifBlank { prompt }
    }

    private suspend fun run(user: String, size: WidgetSize, image: ImageInput?): GenerationResult {
        val (p, model) = provider()
        val key = graph.providers.apiKey(p)
        var request = ChatRequest(WidgetPrompt.system, user, image)
        var lastError: Exception? = null
        repeat(2) { attempt ->
            val reply = client.complete(p, key, model, request)
            try {
                val spec = SpecParser.parse(reply).copy(size = size.id)
                return GenerationResult(spec, model)
            } catch (e: SpecParseException) {
                lastError = e
                if (attempt == 0) {
                    request = request.copy(
                        user = user + "\n\nYour previous reply could not be used (${e.message}). Reply again with ONLY one complete, valid JSON object.",
                    )
                }
            }
        }
        throw AiException(L.t("Модель вернула неподходящий ответ", "The model reply was not a valid widget") + ": ${lastError?.message}")
    }
}
