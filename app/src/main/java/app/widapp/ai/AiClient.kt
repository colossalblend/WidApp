package app.widapp.ai

import app.widapp.core.AppJson
import app.widapp.core.L
import app.widapp.data.ModelInfo
import app.widapp.data.ProviderConfig
import app.widapp.data.ProviderKind
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

open class AiException(message: String, val status: Int? = null) : Exception(message)

data class ImageInput(val base64: String, val mime: String = "image/jpeg")

data class ChatRequest(
    val system: String,
    val user: String,
    val image: ImageInput? = null,
    val maxTokens: Int = 16000,
    /** Ask for JSON where the API supports a JSON response mode without restricting the model. */
    val json: Boolean = true,
)

/**
 * One small HTTP client for three wire formats: OpenAI-compatible chat completions,
 * the Anthropic Messages API and the Gemini generateContent API.
 */
class AiClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(330, TimeUnit.SECONDS)
        .build(),
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    // ---------- Models ----------

    suspend fun listModels(kind: ProviderKind, baseUrl: String, apiKey: String): List<ModelInfo> = when (kind) {
        ProviderKind.OPENAI -> listOpenAi(baseUrl, apiKey)
        ProviderKind.ANTHROPIC -> listAnthropic(baseUrl, apiKey)
        ProviderKind.GEMINI -> listGemini(baseUrl, apiKey)
    }

    private val nonChat = Regex("embed|whisper|tts|dall-e|davinci|babbage|moderation|transcri|audio|realtime|sora|rerank|guard", RegexOption.IGNORE_CASE)

    private suspend fun listOpenAi(base: String, key: String): List<ModelInfo> {
        val res = get(url(base, "models"), openAiHeaders(key))
        val data = (res as? JsonObject)?.get("data")?.jsonArray ?: (res as? JsonArray) ?: JsonArray(emptyList())
        return data.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.str("id") ?: return@mapNotNull null
            val modality = o["architecture"]?.let { (it as? JsonObject)?.str("input_modalities") ?: it.toString() }
            ModelInfo(
                id = id,
                name = o.str("name")?.takeIf { it != id },
                context = o["context_length"]?.jsonPrimitive?.intOrNull,
                vision = modality?.contains("image"),
            )
        }.filterNot { nonChat.containsMatchIn(it.id) }.sortedBy { it.id.lowercase() }
    }

    private suspend fun listAnthropic(base: String, key: String): List<ModelInfo> {
        val out = mutableListOf<ModelInfo>()
        var after: String? = null
        repeat(10) {
            val u = url(anthropicBase(base), "v1/models").toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("limit", "1000").apply { after?.let { addQueryParameter("after_id", it) } }.build().toString()
            val res = get(u, anthropicHeaders(key)).jsonObject
            res["data"]?.jsonArray?.forEach { el ->
                val o = el.jsonObject
                val id = o.str("id") ?: return@forEach
                out += ModelInfo(id, o.str("display_name"), o["max_input_tokens"]?.jsonPrimitive?.intOrNull, vision = true)
            }
            val more = res["has_more"]?.jsonPrimitive?.contentOrNull == "true"
            after = res.str("last_id")
            if (!more || after == null) return out
        }
        return out
    }

    private suspend fun listGemini(base: String, key: String): List<ModelInfo> {
        val out = mutableListOf<ModelInfo>()
        var token: String? = null
        repeat(10) {
            val u = url(base, "models").toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("pageSize", "1000").apply { token?.let { addQueryParameter("pageToken", it) } }.build().toString()
            val res = get(u, mapOf("x-goog-api-key" to key)).jsonObject
            res["models"]?.jsonArray?.forEach { el ->
                val o = el.jsonObject
                val methods = o["supportedGenerationMethods"]?.toString() ?: ""
                if (!methods.contains("generateContent")) return@forEach
                val id = o.str("name")?.removePrefix("models/") ?: return@forEach
                if (nonChat.containsMatchIn(id)) return@forEach
                out += ModelInfo(id, o.str("displayName"), o["inputTokenLimit"]?.jsonPrimitive?.intOrNull, vision = true)
            }
            token = res.str("nextPageToken")
            if (token == null) return out.sortedByDescending { it.id }
        }
        return out
    }

    // ---------- Completion ----------

    suspend fun complete(p: ProviderConfig, apiKey: String, model: String, req: ChatRequest): String = when (p.kind) {
        ProviderKind.OPENAI -> completeOpenAi(p.baseUrl, apiKey, model, req)
        ProviderKind.ANTHROPIC -> completeAnthropic(p.baseUrl, apiKey, model, req, req.maxTokens)
        ProviderKind.GEMINI -> completeGemini(p.baseUrl, apiKey, model, req, withSystem = true)
    }

    private suspend fun completeOpenAi(base: String, key: String, model: String, req: ChatRequest): String {
        val body = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", req.system) }
                addJsonObject {
                    put("role", "user")
                    if (req.image == null) put("content", req.user)
                    else putJsonArray("content") {
                        addJsonObject { put("type", "text"); put("text", req.user) }
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", "data:${req.image.mime};base64,${req.image.base64}") }
                        }
                    }
                }
            }
        }
        val res = post(url(base, "chat/completions"), openAiHeaders(key), body).jsonObject
        val msg = res["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?: throw AiException(res.errorMessage() ?: L.t("Пустой ответ модели", "Empty model response"))
        return when (val c = msg["content"]) {
            is JsonPrimitive -> c.contentOrNull.orEmpty()
            is JsonArray -> c.joinToString("") { (it as? JsonObject)?.str("text").orEmpty() }
            else -> ""
        }.ifBlank { throw AiException(L.t("Модель вернула пустой ответ", "The model returned an empty reply")) }
    }

    private suspend fun completeAnthropic(base: String, key: String, model: String, req: ChatRequest, maxTokens: Int): String {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            put("system", req.system)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        req.image?.let { img ->
                            addJsonObject {
                                put("type", "image")
                                putJsonObject("source") { put("type", "base64"); put("media_type", img.mime); put("data", img.base64) }
                            }
                        }
                        addJsonObject { put("type", "text"); put("text", req.user) }
                    }
                }
            }
        }
        val res = try {
            post(url(anthropicBase(base), "v1/messages"), anthropicHeaders(key), body).jsonObject
        } catch (e: AiException) {
            // Older models cap output lower; step down instead of failing.
            if (e.status == 400 && e.message.orEmpty().contains("max_tokens") && maxTokens > 4096) {
                return completeAnthropic(base, key, model, req, if (maxTokens > 8192) 8192 else 4096)
            }
            throw e
        }
        if (res.str("stop_reason") == "refusal") throw AiException(L.t("Модель отказалась выполнять этот запрос. Попробуйте переформулировать.", "The model declined this request. Try rephrasing it."))
        val text = res["content"]?.jsonArray?.filter { (it as? JsonObject)?.str("type") == "text" }
            ?.joinToString("") { it.jsonObject.str("text").orEmpty() }.orEmpty()
        if (text.isBlank()) throw AiException(res.errorMessage() ?: L.t("Модель вернула пустой ответ", "The model returned an empty reply"))
        return text
    }

    private suspend fun completeGemini(base: String, key: String, model: String, req: ChatRequest, withSystem: Boolean): String {
        val body = buildJsonObject {
            if (withSystem) putJsonObject("systemInstruction") { putJsonArray("parts") { addJsonObject { put("text", req.system) } } }
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        req.image?.let { img ->
                            addJsonObject { putJsonObject("inline_data") { put("mime_type", img.mime); put("data", img.base64) } }
                        }
                        addJsonObject { put("text", if (withSystem) req.user else req.system + "\n\n" + req.user) }
                    }
                }
            }
            if (withSystem && req.json) putJsonObject("generationConfig") { put("responseMimeType", "application/json") }
        }
        val res = try {
            post(url(base, "models/$model:generateContent"), mapOf("x-goog-api-key" to key), body).jsonObject
        } catch (e: AiException) {
            // Some models (e.g. Gemma) reject system instructions or JSON mode.
            if (withSystem && e.status == 400) return completeGemini(base, key, model, req, withSystem = false)
            throw e
        }
        val parts = res["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
        val text = parts?.filter { (it as? JsonObject)?.get("thought")?.jsonPrimitive?.contentOrNull != "true" }
            ?.joinToString("") { (it as? JsonObject)?.str("text").orEmpty() }.orEmpty()
        if (text.isBlank()) {
            val reason = res["promptFeedback"]?.jsonObject?.str("blockReason")
                ?: res["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.str("finishReason")
            throw AiException(if (reason != null) L.t("Ответ заблокирован: $reason", "Reply blocked: $reason") else L.t("Модель вернула пустой ответ", "The model returned an empty reply"))
        }
        return text
    }

    // ---------- HTTP ----------

    private fun openAiHeaders(key: String) = buildMap {
        if (key.isNotBlank()) put("Authorization", "Bearer $key")
        put("HTTP-Referer", "https://github.com/colossalblend/WidApp")
        put("X-Title", "WidApp")
    }

    private fun anthropicHeaders(key: String) = mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01")

    private fun anthropicBase(base: String) = base.trimEnd('/').removeSuffix("/v1")

    private fun url(base: String, path: String): String {
        val b = base.trim().trimEnd('/')
        if (b.toHttpUrlOrNull() == null) throw AiException(L.t("Неверный адрес сервера: $base", "Invalid server address: $base"))
        return "$b/$path"
    }

    private suspend fun get(url: String, headers: Map<String, String>): JsonElement =
        execute(Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.get().build())

    private suspend fun post(url: String, headers: Map<String, String>, body: JsonObject): JsonElement =
        execute(Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }
            .post(body.toString().toRequestBody(jsonType)).build())

    private suspend fun execute(request: Request): JsonElement {
        val response = try {
            http.newCall(request).await()
        } catch (e: IOException) {
            throw AiException(L.t("Нет связи с сервером", "Cannot reach the server") + ": ${e.message ?: e.javaClass.simpleName}")
        }
        response.use { r ->
            val text = r.body.string()
            val parsed = runCatching { AppJson.parseToJsonElement(text) }.getOrNull()
            if (!r.isSuccessful) {
                val msg = (parsed as? JsonObject)?.errorMessage() ?: text.take(300).ifBlank { r.message }
                val hint = when (r.code) {
                    401, 403 -> L.t("Ключ не подошёл", "Key rejected")
                    404 -> L.t("Не найдено (проверьте адрес и модель)", "Not found (check the address and model)")
                    429 -> L.t("Слишком много запросов или закончился баланс", "Rate limited or out of credit")
                    in 500..599 -> L.t("Сервер провайдера недоступен", "Provider server error")
                    else -> L.t("Ошибка", "Error")
                }
                throw AiException("$hint (HTTP ${r.code}): $msg", r.code)
            }
            return parsed ?: throw AiException(L.t("Сервер вернул не JSON", "Server did not return JSON") + ": ${text.take(200)}")
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (!cont.isCancelled) cont.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) { cont.resume(response) }
        })
        cont.invokeOnCancellation { runCatching { cancel() } }
    }
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.errorMessage(): String? {
    val e = this["error"]
    return when (e) {
        is JsonObject -> e.str("message") ?: e.toString()
        is JsonPrimitive -> e.contentOrNull
        else -> str("message") ?: str("detail")
    }
}

@Suppress("unused")
private val keepImports = buildJsonArray { }
