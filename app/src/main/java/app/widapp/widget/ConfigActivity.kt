package app.widapp.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.widapp.MainActivity
import app.widapp.R
import app.widapp.WidApp
import app.widapp.spec.WidgetSize
import app.widapp.ui.components.ScreenHeader
import app.widapp.ui.library.DesignCard
import app.widapp.ui.theme.WidTheme
import kotlinx.coroutines.launch

/** Shown when a WidApp widget is dropped on the home screen: pick which design it shows. */
class ConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val graph = (application as WidApp).graph
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)
        val cols = info?.targetCellWidth ?: 0
        val rows = info?.targetCellHeight ?: 0

        setContent {
            val settings by graph.settings.flow.collectAsState()
            val library by graph.designs.flow.collectAsState()
            val scope = rememberCoroutineScope()
            WidTheme(settings.themeMode, settings.dynamicColor) {
                // Designs made for this cell size first.
                val sorted = library.designs.sortedByDescending { val s = WidgetSize.of(it.spec.size); s.cols == cols && s.rows == rows }
                Scaffold(containerColor = MaterialTheme.colorScheme.surface) { pad ->
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize().padding(pad),
                        contentPadding = PaddingValues(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalItemSpacing = 14.dp,
                    ) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            ScreenHeader(kicker = "WidApp", title = stringResource(R.string.config_title))
                        }
                        if (library.designs.isEmpty()) item(span = StaggeredGridItemSpan.FullLine) {
                            Text(stringResource(R.string.config_empty), style = MaterialTheme.typography.bodyLarge)
                        }
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Button(onClick = {
                                startActivity(Intent(this@ConfigActivity, MainActivity::class.java).putExtra(MainActivity.EXTRA_CREATE, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                finish()
                            }) { Text(stringResource(R.string.action_new_widget)) }
                        }
                        items(sorted, key = { it.id }, span = { if (WidgetSize.of(it.spec.size).isWide) StaggeredGridItemSpan.FullLine else StaggeredGridItemSpan.SingleLane }) { d ->
                            DesignCard(d, onClick = {
                                scope.launch {
                                    WidgetBinder.bind(this@ConfigActivity, widgetId, d.id)
                                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                                    finish()
                                }
                            })
                        }
                    }
                }
            }
        }
    }
}
