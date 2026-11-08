package app.widapp.ui.settings

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.widapp.BuildConfig
import app.widapp.R
import app.widapp.data.City
import app.widapp.data.WeatherCodes
import app.widapp.spec.SpecParser
import app.widapp.ui.components.Kicker
import app.widapp.ui.components.ScreenHeader
import app.widapp.ui.components.rememberApp
import app.widapp.ui.theme.MonoLabel
import app.widapp.widget.RefreshWorker
import app.widapp.widget.WidgetBinder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsTab(onImported: (String) -> Unit, onUpdates: () -> Unit = {}) {
    val app = rememberApp()
    val graph = app.graph
    val s by graph.settings.flow.collectAsState()
    val weather by graph.weather.flow.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme

    var cityQuery by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<City>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(cityQuery) {
        if (cityQuery.length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(450); searching = true
        results = runCatching { app.weatherService.searchCity(cityQuery) }.getOrDefault(emptyList())
        searching = false
    }
    val available by app.updates.available.collectAsState()
    val importFail = stringResource(R.string.settings_import_fail)
    val refreshed = stringResource(R.string.settings_refreshed)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader(kicker = stringResource(R.string.settings_kicker), title = stringResource(R.string.settings_title)) }

        item {
            Group(stringResource(R.string.settings_look)) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                val modes = listOf("system" to R.string.theme_system, "light" to R.string.theme_light, "dark" to R.string.theme_dark)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { i, (m, label) ->
                        SegmentedButton(
                            selected = s.themeMode == m, onClick = { graph.settings.update { it.copy(themeMode = m) } },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                        ) { Text(stringResource(label)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ToggleRow(stringResource(R.string.settings_dynamic_app), stringResource(R.string.settings_dynamic_app_hint), s.dynamicColor) { v ->
                        graph.settings.update { it.copy(dynamicColor = v) }
                    }
                    ToggleRow(stringResource(R.string.settings_dynamic_widgets), stringResource(R.string.settings_dynamic_widgets_hint), s.widgetDynamicColor) { v ->
                        graph.settings.update { it.copy(widgetDynamicColor = v) }; WidgetBinder.refreshAll(context)
                    }
                }
            }
        }

        item {
            Group(stringResource(R.string.settings_weather)) {
                Text(
                    s.city?.let { "${it.name}${it.country?.let { c -> ", $c" } ?: ""}" } ?: stringResource(R.string.settings_city_none),
                    style = MaterialTheme.typography.titleMedium,
                )
                weather.now?.let { w ->
                    Text("${Math.round(w.temp)}° · ${WeatherCodes.describe(w.code, java.util.Locale.getDefault().language == "ru")}", style = MonoLabel, color = c.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    cityQuery, { cityQuery = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(18.dp),
                    placeholder = { Text(stringResource(R.string.settings_city_search)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_location_on), null) },
                    trailingIcon = { if (searching) LoadingIndicator(Modifier.size(28.dp)) },
                )
                results.forEach { city ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                            graph.settings.update { it.copy(city = city) }
                            cityQuery = ""; results = emptyList()
                            scope.launch { app.weatherService.refresh(); WidgetBinder.refreshAll(context) }
                        }.padding(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        Column {
                            Text(city.name, style = MaterialTheme.typography.bodyLarge)
                            Text(listOfNotNull(city.region, city.country).joinToString(", "), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.settings_units), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                val units = listOf("c" to "°C", "f" to "°F")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    units.forEachIndexed { i, (u, label) ->
                        SegmentedButton(
                            selected = s.units == u, shape = SegmentedButtonDefaults.itemShape(i, units.size),
                            onClick = {
                                graph.settings.update { it.copy(units = u) }
                                scope.launch { app.weatherService.refresh(); WidgetBinder.refreshAll(context) }
                            },
                        ) { Text(label) }
                    }
                }
            }
        }

        item {
            Group(stringResource(R.string.settings_widgets)) {
                Text(stringResource(R.string.settings_refresh_every), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                val opts = listOf(15, 30, 60, 180)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    opts.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = s.refreshMinutes == m, shape = SegmentedButtonDefaults.itemShape(i, opts.size),
                            onClick = { graph.settings.update { it.copy(refreshMinutes = m) }; RefreshWorker.schedule(context, m) },
                        ) { Text(if (m < 60) stringResource(R.string.minutes_short, m) else stringResource(R.string.hours_short, m / 60)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                ToggleRow(stringResource(R.string.settings_live_ai), stringResource(R.string.settings_live_ai_hint), s.aiLiveContent) { v ->
                    graph.settings.update { it.copy(aiLiveContent = v) }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = c.outlineVariant)
                ActionRow(R.drawable.ic_refresh, stringResource(R.string.settings_refresh_now)) {
                    RefreshWorker.enqueueNow(context); WidgetBinder.refreshAll(context)
                    Toast.makeText(context, refreshed, Toast.LENGTH_SHORT).show()
                }
                ActionRow(R.drawable.ic_content_paste, stringResource(R.string.settings_import)) {
                    val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                    val text = clip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    val spec = runCatching { SpecParser.parse(text) }.getOrNull()
                    if (spec == null) Toast.makeText(context, importFail, Toast.LENGTH_LONG).show()
                    else onImported(graph.designs.create(spec, "", null, kind = "import").id)
                }
            }
        }

        item {
            Group(stringResource(R.string.settings_about)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (available != null) c.tertiaryContainer else c.surfaceContainer)
                        .clickable(onClick = onUpdates).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_download), null, tint = if (available != null) c.onTertiaryContainer else c.onSurfaceVariant)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.update_title), style = MaterialTheme.typography.bodyLarge, color = if (available != null) c.onTertiaryContainer else c.onSurface)
                        Text(
                            available?.let { stringResource(R.string.update_row_available, it.version) } ?: stringResource(R.string.update_row_current, BuildConfig.VERSION_NAME),
                            style = MonoLabel, color = if (available != null) c.onTertiaryContainer else c.onSurfaceVariant,
                        )
                    }
                    Icon(painterResource(R.drawable.ic_chevron_right), null, tint = c.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.settings_howto_title), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.settings_howto), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.settings_credits), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Text("WidApp ${BuildConfig.VERSION_NAME}", style = MonoLabel, color = c.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(20.dp)) {
        Kicker(title)
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun ToggleRow(title: String, hint: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onChange(!value) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(value, onChange)
    }
}

@Composable
private fun ActionRow(icon: Int, title: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}
