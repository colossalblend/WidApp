package app.widapp

import android.app.Application
import app.widapp.ai.AiClient
import app.widapp.ai.AiContentService
import app.widapp.ai.WidgetGenerator
import app.widapp.data.AppGraph
import app.widapp.data.WeatherService
import app.widapp.update.UpdateManager
import app.widapp.update.UpdateWorker
import app.widapp.widget.RefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class WidApp : Application() {
    lateinit var graph: AppGraph
        private set
    val aiClient by lazy { AiClient() }
    val generator by lazy { WidgetGenerator(graph, aiClient) }
    val liveContent by lazy { AiContentService(graph, aiClient) }
    val weatherService by lazy { WeatherService(graph) }
    val updates by lazy { UpdateManager(this) }
    /** Work that should outlive a screen (e.g. filling live AI text after creation). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        // WorkManager may be unavailable in some test/instrumentation contexts.
        runCatching { RefreshWorker.schedule(this) }
        runCatching { UpdateWorker.schedule(this) }
        updates.cleanup()
    }
}
