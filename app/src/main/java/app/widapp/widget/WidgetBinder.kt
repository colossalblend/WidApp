package app.widapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import app.widapp.data.WidgetDesign
import app.widapp.spec.WidgetSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object WidgetBinder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun bind(context: Context, appWidgetId: Int, designId: String) {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[WidgetKeys.DESIGN] = designId
            prefs.remove(WidgetKeys.STATE)
        }
        WidAppWidget().update(context, glanceId)
        RefreshWorker.schedule(context)
    }

    /** How many home-screen widgets currently show [designId]. */
    suspend fun countPlaced(context: Context, designId: String): Int {
        val m = GlanceAppWidgetManager(context)
        return m.getGlanceIds(WidAppWidget::class.java).count { id ->
            runCatching { getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[WidgetKeys.DESIGN] }.getOrNull() == designId
        }
    }

    fun refreshAll(context: Context) {
        scope.launch { runCatching { WidAppWidget().updateAll(context) } }
    }

    fun canPin(context: Context): Boolean = AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    /** Asks the launcher to place the widget; [PinResultReceiver] binds the design once it is placed. */
    fun requestPin(context: Context, design: WidgetDesign): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val provider = ComponentName(context, receiverFor(WidgetSize.of(design.spec.size)))
        val callback = PendingIntent.getBroadcast(
            context, design.id.hashCode(),
            Intent(context, PinResultReceiver::class.java).putExtra(EXTRA_DESIGN, design.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        return manager.requestPinAppWidget(provider, null, callback)
    }

    const val EXTRA_DESIGN = "app.widapp.DESIGN_ID"
}

class PinResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val designId = intent.getStringExtra(WidgetBinder.EXTRA_DESIGN) ?: return
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WidgetBinder.bind(context, widgetId, designId)
            } finally {
                pending.finish()
            }
        }
    }
}
