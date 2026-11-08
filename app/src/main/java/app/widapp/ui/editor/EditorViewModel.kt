package app.widapp.ui.editor

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.widapp.WidApp
import app.widapp.ai.ImageInput
import app.widapp.ai.NoProviderException
import app.widapp.core.L
import app.widapp.data.WidgetDesign
import app.widapp.spec.DataSnapshot
import app.widapp.spec.SpecParser
import app.widapp.spec.StateOps
import app.widapp.spec.WAction
import app.widapp.widget.WidgetBinder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

class EditorViewModel(private val app: WidApp, private val id: String) : ViewModel() {
    private val graph = app.graph
    val design: StateFlow<WidgetDesign?> = graph.designs.flow.map { lib -> lib.designs.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, graph.designs.get(id))

    val previewState = mutableStateMapOf<String, JsonPrimitive>()
    var previewDark by mutableStateOf<Boolean?>(null)
    /** True while a background generation job for this widget is queued or running. */
    var busy by mutableStateOf(false)
        private set
    var input by mutableStateOf("")
    var image by mutableStateOf<ImageInput?>(null)
    var thumb by mutableStateOf<Bitmap?>(null)
    var needsProvider by mutableStateOf(false)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    init {
        // AI edits run as background jobs; react to their outcome while this screen is open.
        viewModelScope.launch {
            app.jobs.flow.collect { list ->
                val mine = list.jobs.filter { it.designId == id }
                busy = mine.any { it.active }
                mine.filter { !it.seen && !it.active }.forEach { j ->
                    app.jobs.markSeen(j.id)
                    when (j.status) {
                        "done" -> previewState.clear()
                        "failed" -> { if (j.needsProvider) needsProvider = true; messages.emit(j.error.orEmpty()) }
                    }
                }
            }
        }
    }

    fun refine(text: String = input, @Suppress("UNUSED_PARAMETER") context: Context) {
        val instruction = text.trim()
        if (instruction.isEmpty() || busy) return
        app.jobs.enqueueRefine(id, instruction, image)
        input = ""; image = null; thumb = null
    }

    fun variant(@Suppress("UNUSED_PARAMETER") context: Context) {
        if (busy) return
        app.jobs.enqueueVariant(id)
    }

    fun cancel() { app.jobs.activeFor(id)?.let { app.jobs.cancel(it.id) } }

    fun select(index: Int, context: Context) { graph.designs.select(id, index); afterChange(context) }
    fun undo(context: Context) { design.value?.let { if (it.current > 0) select(it.current - 1, context) } }
    fun redo(context: Context) { design.value?.let { if (it.current < it.versions.lastIndex) select(it.current + 1, context) } }

    /** Returns an error message, or null when the JSON was applied. */
    fun applyJson(text: String, context: Context): String? {
        val spec = try { SpecParser.parse(text) } catch (e: Exception) { return e.message }
        graph.designs.addVersion(id, spec, "", null, kind = "manual")
        afterChange(context)
        return null
    }

    fun rename(name: String, context: Context) { if (name.isNotBlank()) { graph.designs.rename(id, name.trim()); WidgetBinder.refreshAll(context) } }
    fun toggleFavorite() = graph.designs.toggleFavorite(id)
    fun duplicate(): String? = graph.designs.duplicate(id)?.id
    fun delete(context: Context) { graph.designs.delete(id); graph.aiContent.clear(id); WidgetBinder.refreshAll(context) }

    fun refreshAi(keys: Set<String>? = null) {
        viewModelScope.launch {
            app.liveContent.refresh(id, keys).onFailure { e ->
                if (e is NoProviderException) needsProvider = true
                messages.emit(e.message ?: "AI")
            }
        }
    }

    fun onPreviewAction(actions: List<WAction>, context: Context) {
        val d = design.value ?: return
        actions.firstOrNull { it.op.equals("open", true) }?.let {
            messages.tryEmit(L.t("На рабочем столе откроется: ", "On the home screen this opens: ") + (it.target ?: "app")); return
        }
        val ai = actions.filter { it.op.equals("ai", true) }
        if (ai.isNotEmpty()) refreshAi(ai.mapNotNull { it.key }.toSet().ifEmpty { null })
        if (actions.any { it.op.equals("refresh", true) }) viewModelScope.launch { app.weatherService.refresh() }
        val ops = actions.filter { it.op.lowercase() !in setOf("ai", "refresh", "open") }
        if (ops.isNotEmpty()) {
            val data = DataSnapshot.capture(context, graph.weather.now, graph.aiContent.values(id))
            val next = StateOps.apply(d.spec.state + previewState, ops, data)
            previewState.clear(); previewState.putAll(next)
        }
    }

    private fun afterChange(context: Context) {
        previewState.clear()
        WidgetBinder.refreshAll(context)
        val d = design.value ?: return
        val stale = app.liveContent.stale(d)
        if (stale.isNotEmpty()) refreshAi(stale)
    }

    fun ensureLiveContent() {
        val d = design.value ?: return
        val stale = app.liveContent.stale(d)
        if (stale.isNotEmpty() && graph.activeProvider() != null) refreshAi(stale)
    }
}
