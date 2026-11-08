package app.widapp.widget

import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import app.widapp.R
import app.widapp.WidApp
import app.widapp.spec.DataSnapshot
import app.widapp.spec.WidgetPalette

class WidAppWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val graph = (context.applicationContext as WidApp).graph
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            val prefs = currentState<Preferences>()
            val library by graph.designs.flow.collectAsState()
            val settings by graph.settings.flow.collectAsState()
            val weather by graph.weather.flow.collectAsState()
            val ai by graph.aiContent.flow.collectAsState()
            val design = library.designs.firstOrNull { it.id == prefs[WidgetKeys.DESIGN] }
            if (design == null) {
                Unbound(context, appWidgetId)
            } else {
                val state = design.spec.state + WidgetKeys.readState(prefs)
                val data = DataSnapshot.capture(
                    context, weather.now,
                    ai.byDesign[design.id]?.mapValues { it.value.text } ?: emptyMap(),
                    design.id in ai.loading,
                )
                val palette = WidgetPalette.from(context, settings.widgetDynamicColor)
                GlanceRenderer(context, design.spec, state, data, palette, LocalSize.current).Root()
            }
        }
    }

    @Composable
    private fun Unbound(context: Context, appWidgetId: Int) {
        val ink = ColorProvider(day = Color(0xFF1C1B18), night = Color(0xFFEDE8DE))
        val intent = Intent(context, ConfigActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Column(
            GlanceModifier.fillMaxSize()
                .background(ColorProvider(day = Color(0xFFEAE5DA), night = Color(0xFF1F1E1B)))
                .cornerRadius(24.dp).padding(12.dp)
                .clickable(actionStartActivity(intent)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(ImageProvider(R.drawable.ic_widgets), null, GlanceModifier.size(28.dp), colorFilter = ColorFilter.tint(ink))
            Spacer(GlanceModifier.height(6.dp))
            Text(
                context.getString(R.string.widget_pick_design), maxLines = 2,
                style = TextStyle(color = ink, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
            )
        }
    }
}
