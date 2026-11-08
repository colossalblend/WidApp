package app.widapp.data

import android.content.Context
import app.widapp.core.JsonFileStore
import app.widapp.core.SecretBox
import app.widapp.spec.WidgetSpec
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.UUID

class DesignRepository(dir: File) {
    private val store = JsonFileStore(File(dir, "designs.json"), DesignLibrary.serializer()) { DesignLibrary() }
    val flow: StateFlow<DesignLibrary> get() = store.flow
    val designs: List<WidgetDesign> get() = store.value.designs

    fun get(id: String?): WidgetDesign? = designs.firstOrNull { it.id == id }

    fun create(spec: WidgetSpec, prompt: String, model: String?, kind: String = "ai"): WidgetDesign {
        val d = WidgetDesign(
            id = UUID.randomUUID().toString().take(12),
            versions = listOf(DesignVersion(spec, prompt, model, kind = kind)),
        )
        store.update { it.copy(designs = listOf(d) + it.designs) }
        return d
    }

    /** Adds a new version after the current one (drops any "redo" branch). */
    fun addVersion(id: String, spec: WidgetSpec, prompt: String, model: String?, kind: String = "ai") = mutate(id) { d ->
        val kept = d.versions.take(d.current + 1)
        val versions = (kept + DesignVersion(spec, prompt, model, kind = kind)).takeLast(40)
        d.copy(versions = versions, current = versions.lastIndex, updatedAt = System.currentTimeMillis())
    }

    fun select(id: String, index: Int) = mutate(id) { it.copy(current = index.coerceIn(0, it.versions.lastIndex), updatedAt = System.currentTimeMillis()) }

    fun rename(id: String, name: String) = mutate(id) { d ->
        val v = d.versions.toMutableList()
        val cur = v[d.current]
        v[d.current] = cur.copy(spec = cur.spec.copy(name = name))
        d.copy(versions = v, updatedAt = System.currentTimeMillis())
    }

    fun toggleFavorite(id: String) = mutate(id) { it.copy(favorite = !it.favorite) }

    fun duplicate(id: String): WidgetDesign? {
        val src = get(id) ?: return null
        val copy = src.copy(
            id = UUID.randomUUID().toString().take(12),
            versions = listOf(src.versions[src.current].copy(kind = "manual")),
            current = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        store.update { lib -> lib.copy(designs = listOf(copy) + lib.designs) }
        return copy
    }

    fun delete(id: String) = store.update { lib -> lib.copy(designs = lib.designs.filterNot { it.id == id }) }

    private fun mutate(id: String, f: (WidgetDesign) -> WidgetDesign) = store.update { lib ->
        lib.copy(designs = lib.designs.map { if (it.id == id) f(it) else it })
    }
}

class ProviderRepository(dir: File) {
    private val store = JsonFileStore(File(dir, "providers.json"), ProviderList.serializer()) { ProviderList() }
    val flow: StateFlow<ProviderList> get() = store.flow
    val providers: List<ProviderConfig> get() = store.value.providers

    fun get(id: String?) = providers.firstOrNull { it.id == id }
    fun apiKey(p: ProviderConfig): String = SecretBox.decrypt(p.apiKeyEnc)

    fun upsert(p: ProviderConfig) = store.update { list ->
        val exists = list.providers.any { it.id == p.id }
        list.copy(providers = if (exists) list.providers.map { if (it.id == p.id) p else it } else list.providers + p)
    }

    fun delete(id: String) = store.update { list -> list.copy(providers = list.providers.filterNot { it.id == id }) }

    fun newId() = UUID.randomUUID().toString().take(10)
}

class SettingsRepository(dir: File) {
    private val store = JsonFileStore(File(dir, "settings.json"), AppSettings.serializer()) { AppSettings() }
    val flow: StateFlow<AppSettings> get() = store.flow
    val value: AppSettings get() = store.value
    fun update(f: (AppSettings) -> AppSettings) = store.update(f)
}

class AiContentRepository(dir: File) {
    private val store = JsonFileStore(File(dir, "ai_content.json"), AiContent.serializer()) { AiContent() }
    val flow: StateFlow<AiContent> get() = store.flow
    val value: AiContent get() = store.value

    fun values(designId: String): Map<String, String> = store.value.byDesign[designId]?.mapValues { it.value.text } ?: emptyMap()
    fun entries(designId: String): Map<String, AiContentEntry> = store.value.byDesign[designId] ?: emptyMap()
    fun isLoading(designId: String) = designId in store.value.loading

    fun setLoading(designId: String, loading: Boolean) = store.update {
        it.copy(loading = if (loading) it.loading + designId else it.loading - designId)
    }

    fun put(designId: String, values: Map<String, String>) = store.update { c ->
        val now = System.currentTimeMillis()
        val old = c.byDesign[designId] ?: emptyMap()
        val merged = old.toMutableMap()
        values.forEach { (k, v) ->
            val prev = old[k]
            merged[k] = AiContentEntry(v, now, ((prev?.history ?: emptyList()) + listOfNotNull(prev?.text)).takeLast(8))
        }
        c.copy(byDesign = c.byDesign + (designId to merged), loading = c.loading - designId)
    }

    fun clear(designId: String) = store.update { it.copy(byDesign = it.byDesign - designId) }
}

class WeatherRepository(dir: File) {
    private val store = JsonFileStore(File(dir, "weather.json"), WeatherCache.serializer()) { WeatherCache() }
    val flow: StateFlow<WeatherCache> get() = store.flow
    val now: WeatherNow? get() = store.value.now
    fun put(w: WeatherNow?) = store.update { it.copy(now = w) }
}

/** Manual dependency graph, created once by [app.widapp.WidApp]. */
class AppGraph(context: Context) {
    private val dir = File(context.filesDir, "store")
    val designs = DesignRepository(dir)
    val providers = ProviderRepository(dir)
    val settings = SettingsRepository(dir)
    val aiContent = AiContentRepository(dir)
    val weather = WeatherRepository(dir)

    fun activeProvider(): ProviderConfig? {
        val s = settings.value
        return providers.get(s.activeProviderId) ?: providers.providers.firstOrNull()
    }
}
