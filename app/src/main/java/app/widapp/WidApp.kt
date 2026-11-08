package app.widapp

import android.app.Application
import app.widapp.ai.AiClient
import app.widapp.ai.AiContentService
import app.widapp.ai.WidgetGenerator
import app.widapp.data.AppGraph
import app.widapp.data.WeatherService
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
    /** Work that should outlive a screen (e.g. filling live AI text after creation). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        RefreshWorker.schedule(this)
    }
}
