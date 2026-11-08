package app.widapp.ui.providers

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import app.widapp.R
import app.widapp.ai.ProviderPresets
import app.widapp.data.ProviderConfig
import app.widapp.ui.components.Kicker
import app.widapp.ui.components.Monogram
import app.widapp.ui.components.ScreenHeader
import app.widapp.ui.components.ShapeBadge
import app.widapp.ui.components.rememberGraph
import app.widapp.ui.theme.MonoLabel

private val shapes: List<RoundedPolygon> get() = listOf(
    MaterialShapes.Cookie6Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Pentagon,
    MaterialShapes.Cookie4Sided, MaterialShapes.Gem, MaterialShapes.Flower, MaterialShapes.Puffy,
)

fun providerShape(id: String): RoundedPolygon = shapes[(id.hashCode() and 0x7fffffff) % shapes.size]

fun host(url: String): String = runCatching { Uri.parse(url).host }.getOrNull() ?: url

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelsTab(onEdit: (String) -> Unit, onAdd: (String) -> Unit) {
    val graph = rememberGraph()
    val list by graph.providers.flow.collectAsState()
    val settings by graph.settings.flow.collectAsState()
    val active = graph.providers.get(settings.activeProviderId) ?: list.providers.firstOrNull()
    var picker by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(kicker = stringResource(R.string.models_kicker), title = stringResource(R.string.models_title)) }
        if (active != null) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(c.inverseSurface).padding(22.dp)) {
                    Kicker(stringResource(R.string.models_active), color = c.inverseOnSurface.copy(alpha = 0.6f))
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Monogram(active.name, shape = providerShape(active.id), container = c.tertiary, content = c.onTertiary, size = 48.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(active.name, style = MaterialTheme.typography.titleLarge, color = c.inverseOnSurface)
                            Text(active.model ?: stringResource(R.string.models_no_model), style = MonoLabel, color = c.inverseOnSurface.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { picker = true }, colors = ButtonDefaults.buttonColors(containerColor = c.inverseOnSurface, contentColor = c.inverseSurface)) {
                            Text(stringResource(R.string.models_change))
                        }
                        OutlinedButton(onClick = { onEdit(active.id) }) { Text(stringResource(R.string.action_edit), color = c.inverseOnSurface) }
                    }
                }
            }
        } else {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(c.surfaceContainerLow).padding(24.dp)) {
                    ShapeBadge(R.drawable.ic_hub_fill, shape = MaterialShapes.Cookie12Sided, container = c.secondaryContainer, content = c.onSecondaryContainer, size = 72.dp, iconSize = 32.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.models_empty_title), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.models_empty_body), style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
                }
            }
        }
        if (list.providers.isNotEmpty()) {
            item { Spacer(Modifier.height(4.dp)); Kicker(stringResource(R.string.models_connected)) }
            items(list.providers, key = { it.id }) { p ->
                ProviderRow(p, selected = p.id == active?.id,
                    onSelect = { graph.settings.update { it.copy(activeProviderId = p.id) } },
                    onClick = { onEdit(p.id) })
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Kicker(stringResource(R.string.models_add))
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProviderPresets.all.forEach { preset ->
                    AssistChip(onClick = { onAdd(preset.id) }, label = { Text(preset.name) }, leadingIcon = {
                        Icon(painterResource(R.drawable.ic_add), null)
                    })
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.models_privacy), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        }
    }
    if (picker) ModelPickerSheet(onDismiss = { picker = false })
}

@Composable
private fun ProviderRow(p: ProviderConfig, selected: Boolean, onSelect: () -> Unit, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(if (selected) c.secondaryContainer else c.surfaceContainerLow)
            .clickable(onClick = onClick).padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monogram(p.name, shape = providerShape(p.id), container = if (selected) c.onSecondaryContainer else c.surfaceContainerHighest, content = if (selected) c.secondaryContainer else c.onSurface)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleMedium, color = c.onSurface)
            Text(
                listOfNotNull(host(p.baseUrl), p.model).joinToString(" · "),
                style = MonoLabel, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (p.models.isNotEmpty()) Text(stringResource(R.string.models_count, p.models.size), style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
        }
        RadioButton(selected = selected, onClick = onSelect)
    }
}
