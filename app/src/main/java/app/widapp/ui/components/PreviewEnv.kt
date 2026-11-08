package app.widapp.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.widapp.WidApp
import app.widapp.data.AppGraph
import app.widapp.spec.DataSnapshot
import app.widapp.spec.WidgetPalette
import app.widapp.spec.WidgetSpec
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import app.widapp.spec.WAction

@Composable
fun rememberGraph(): AppGraph {
    val ctx = LocalContext.current
    return remember { (ctx.applicationContext as WidApp).graph }
}

@Composable
fun rememberApp(): WidApp {
    val ctx = LocalContext.current
    return remember { ctx.applicationContext as WidApp }
}

@Composable
fun rememberPreviewData(designId: String?): DataSnapshot {
    val ctx = LocalContext.current
    val graph = rememberGraph()
    val weather by graph.weather.flow.collectAsState()
    val ai by graph.aiContent.flow.collectAsState()
    val tick by produceState(0) { while (true) { delay(30_000); value++ } }
    return remember(weather, ai, tick, designId) {
        DataSnapshot.capture(
            ctx, weather.now,
            designId?.let { id -> ai.byDesign[id]?.mapValues { it.value.text } } ?: emptyMap(),
            designId != null && designId in ai.loading,
        )
    }
}

@Composable
fun rememberPalette(): WidgetPalette {
    val ctx = LocalContext.current
    val graph = rememberGraph()
    val settings by graph.settings.flow.collectAsState()
    return remember(settings.widgetDynamicColor) { WidgetPalette.from(ctx, settings.widgetDynamicColor) }
}

/** Static (non-interactive) thumbnail of a spec, used in grids. */
@Composable
fun WidgetThumb(spec: WidgetSpec, designId: String?, modifier: Modifier = Modifier, dark: Boolean = isSystemInDarkTheme(), state: Map<String, JsonPrimitive> = emptyMap(), onAction: ((List<WAction>) -> Unit)? = null) {
    WidgetPreview(
        spec = spec,
        state = spec.state + state,
        data = rememberPreviewData(designId),
        palette = rememberPalette(),
        systemDark = dark,
        modifier = modifier.fillMaxWidth(),
        onAction = onAction,
    )
}
