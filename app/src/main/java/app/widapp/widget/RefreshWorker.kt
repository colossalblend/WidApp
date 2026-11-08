package app.widapp.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.widapp.WidApp
import java.util.concurrent.TimeUnit

/** Refreshes weather and live AI text, then redraws every widget. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as WidApp
        val graph = app.graph
        val mode = inputData.getString(KEY_MODE) ?: MODE_PERIODIC
        if (mode == MODE_AI) {
            val designId = inputData.getString(KEY_DESIGN) ?: return Result.success()
            val keys = inputData.getStringArray(KEY_KEYS)?.toSet()
            app.liveContent.refresh(designId, keys)
            WidAppWidget().updateAll(applicationContext)
            return Result.success()
        }
        if (graph.settings.value.city != null) app.weatherService.refresh()
        if (graph.settings.value.aiLiveContent) {
            for (id in boundDesignIds(applicationContext)) {
                val design = graph.designs.get(id) ?: continue
                val stale = app.liveContent.stale(design)
                if (stale.isNotEmpty()) app.liveContent.refresh(id, stale)
            }
        }
        WidAppWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_DESIGN = "design"
        private const val KEY_KEYS = "keys"
        private const val MODE_PERIODIC = "periodic"
        private const val MODE_AI = "ai"
        private const val PERIODIC_NAME = "widapp-refresh"

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context, minutes: Int? = null) {
            val graph = (context.applicationContext as WidApp).graph
            val m = (minutes ?: graph.settings.value.refreshMinutes).coerceAtLeast(15).toLong()
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(m, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun enqueueNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("widapp-refresh-now", ExistingWorkPolicy.REPLACE, req)
        }

        fun enqueueAi(context: Context, designId: String, keys: Set<String>?) {
            val req = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(network)
                .setInputData(workDataOf(KEY_MODE to MODE_AI, KEY_DESIGN to designId, KEY_KEYS to keys?.toTypedArray()))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("widapp-ai-$designId", ExistingWorkPolicy.REPLACE, req)
        }

        suspend fun boundDesignIds(context: Context): Set<String> {
            val manager = GlanceAppWidgetManager(context)
            return manager.getGlanceIds(WidAppWidget::class.java).mapNotNull { id ->
                runCatching { getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[WidgetKeys.DESIGN] }.getOrNull()
            }.toSet()
        }
    }
}
