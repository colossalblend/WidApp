package app.widapp.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import app.widapp.WidApp
import app.widapp.core.AppJson
import app.widapp.spec.DataSnapshot
import app.widapp.spec.StateOps
import app.widapp.spec.parseActions

/** Runs a widget's onClick actions: state changes, AI refresh and data refresh. */
class WidgetActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val raw = parameters[KEY] ?: return
        val actions = parseActions(runCatching { AppJson.parseToJsonElement(raw) }.getOrNull())
        if (actions.isEmpty()) return
        val graph = (context.applicationContext as WidApp).graph
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val designId = prefs[WidgetKeys.DESIGN] ?: return
        val design = graph.designs.get(designId) ?: return

        val stateOps = actions.filter { it.op.lowercase() !in setOf("ai", "refresh", "open") }
        if (stateOps.isNotEmpty()) {
            val current = design.spec.state + WidgetKeys.readState(prefs)
            val data = DataSnapshot.capture(context, graph.weather.now, graph.aiContent.values(designId))
            val next = StateOps.apply(current, stateOps, data)
            updateAppWidgetState(context, glanceId) { p -> WidgetKeys.writeState(p, next) }
        }
        val aiActions = actions.filter { it.op.equals("ai", true) }
        if (aiActions.isNotEmpty()) {
            val keys = aiActions.mapNotNull { it.key }.toSet().ifEmpty { null }
            graph.aiContent.setLoading(designId, true)
            RefreshWorker.enqueueAi(context, designId, keys)
        }
        if (actions.any { it.op.equals("refresh", true) }) RefreshWorker.enqueueNow(context)
        WidAppWidget().update(context, glanceId)
    }

    companion object {
        val KEY = ActionParameters.Key<String>("actions")
    }
}
