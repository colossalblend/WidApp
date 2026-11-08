package app.widapp.ai

import app.widapp.core.L
import app.widapp.data.ModelInfo
import app.widapp.data.ProviderKind

data class ProviderPreset(
    val id: String,
    val name: String,
    val kind: ProviderKind,
    val baseUrl: String,
    val needsKey: Boolean = true,
    val keyHint: String = "",
    val keyUrl: String? = null,
    val note: String? = null,
    /** Substrings tried in order to pick a sensible default model from the fetched list. */
    val preferred: List<String> = emptyList(),
)

object ProviderPresets {
    val all get() = listOf(
        ProviderPreset("openai", "OpenAI", ProviderKind.OPENAI, "https://api.openai.com/v1", keyHint = "sk-…",
            keyUrl = "https://platform.openai.com/api-keys", preferred = listOf("gpt-5", "gpt-4.1", "gpt-4o")),
        ProviderPreset("anthropic", "Anthropic", ProviderKind.ANTHROPIC, "https://api.anthropic.com", keyHint = "sk-ant-…",
            keyUrl = "https://platform.claude.com/settings/keys",
            preferred = listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-opus", "claude-sonnet")),
        ProviderPreset("gemini", "Google Gemini", ProviderKind.GEMINI, "https://generativelanguage.googleapis.com/v1beta", keyHint = "AIza…",
            keyUrl = "https://aistudio.google.com/apikey", preferred = listOf("gemini-3-pro", "gemini-2.5-pro", "gemini-pro", "gemini-flash")),
        ProviderPreset("openrouter", "OpenRouter", ProviderKind.OPENAI, "https://openrouter.ai/api/v1", keyHint = "sk-or-…",
            keyUrl = "https://openrouter.ai/keys", note = L.t("Сотни моделей через один ключ", "Hundreds of models, one key"),
            preferred = listOf("anthropic/claude-opus-5-5", "anthropic/claude-sonnet-5-5", "openai/gpt-5", "google/gemini")),
        ProviderPreset("deepseek", "DeepSeek", ProviderKind.OPENAI, "https://api.deepseek.com/v1", keyHint = "sk-…",
            keyUrl = "https://platform.deepseek.com/api_keys", preferred = listOf("deepseek-chat")),
        ProviderPreset("groq", "Groq", ProviderKind.OPENAI, "https://api.groq.com/openai/v1", keyHint = "gsk_…",
            keyUrl = "https://console.groq.com/keys", preferred = listOf("llama-3.3-70b", "llama")),
        ProviderPreset("mistral", "Mistral", ProviderKind.OPENAI, "https://api.mistral.ai/v1",
            keyUrl = "https://console.mistral.ai/api-keys", preferred = listOf("mistral-large-latest", "mistral-medium")),
        ProviderPreset("xai", "xAI", ProviderKind.OPENAI, "https://api.x.ai/v1", keyHint = "xai-…",
            keyUrl = "https://console.x.ai", preferred = listOf("grok-4", "grok")),
        ProviderPreset("together", "Together AI", ProviderKind.OPENAI, "https://api.together.xyz/v1",
            keyUrl = "https://api.together.ai/settings/api-keys"),
        ProviderPreset("ollama", "Ollama", ProviderKind.OPENAI, "http://192.168.0.10:11434/v1", needsKey = false,
            note = L.t("Локальные модели в вашей сети", "Local models on your network")),
        ProviderPreset("lmstudio", "LM Studio", ProviderKind.OPENAI, "http://192.168.0.10:1234/v1", needsKey = false,
            note = L.t("Локальный сервер LM Studio", "Local LM Studio server")),
        ProviderPreset("custom", L.t("Свой сервер", "Custom server"), ProviderKind.OPENAI, "https://", needsKey = false,
            note = L.t("Любой OpenAI-совместимый API", "Any OpenAI-compatible API")),
        ProviderPreset("custom-anthropic", L.t("Anthropic-совместимый", "Anthropic-compatible"), ProviderKind.ANTHROPIC, "https://", needsKey = true,
            note = L.t("Прокси или сервис с API Anthropic", "A proxy or service speaking the Anthropic API")),
    )

    fun get(id: String) = all.firstOrNull { it.id == id } ?: all.first { it.id == "custom" }

    fun pickDefault(preset: ProviderPreset, models: List<ModelInfo>): String? {
        if (models.isEmpty()) return null
        for (p in preset.preferred) {
            val hits = models.filter { it.id.contains(p, ignoreCase = true) }
            if (hits.isNotEmpty()) return hits.minBy { it.id.length }.id
        }
        return models.first().id
    }
}
