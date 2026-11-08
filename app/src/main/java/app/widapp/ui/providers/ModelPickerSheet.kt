package app.widapp.ui.providers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.widapp.R
import app.widapp.data.ModelInfo
import app.widapp.ui.components.compact
import app.widapp.ui.components.rememberApp
import app.widapp.ui.theme.MonoBody
import app.widapp.ui.theme.MonoLabel
import kotlinx.coroutines.launch

/** Quick switcher: pick any model of any connected provider. */
@Composable
fun ModelPickerSheet(onDismiss: () -> Unit) {
    val app = rememberApp()
    val graph = app.graph
    val list by graph.providers.flow.collectAsState()
    val settings by graph.settings.flow.collectAsState()
    var providerId by remember { mutableStateOf(settings.activeProviderId ?: list.providers.firstOrNull()?.id) }
    val provider = list.providers.firstOrNull { it.id == providerId } ?: list.providers.firstOrNull()
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme

    fun reload() {
        val p = provider ?: return
        loading = true; error = null
        scope.launch {
            runCatching { app.aiClient.listModels(p.kind, p.baseUrl, graph.providers.apiKey(p)) }
                .onSuccess { models -> graph.providers.upsert(p.copy(models = models, modelsFetchedAt = System.currentTimeMillis())) }
                .onFailure { error = it.message }
            loading = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.picker_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                list.providers.forEach { p -> FilterChip(p.id == provider?.id, { providerId = p.id }, { Text(p.name) }) }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text(stringResource(R.string.picker_search)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                trailingIcon = {
                    if (loading) LoadingIndicator(Modifier.size(28.dp))
                    else TextButton(onClick = ::reload) { Text(stringResource(R.string.action_refresh)) }
                },
                shape = RoundedCornerShape(20.dp),
            )
            error?.let { Text(it, color = c.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
            Spacer(Modifier.height(8.dp))
            val models = provider?.models.orEmpty().filter { query.isBlank() || it.id.contains(query, true) || it.name?.contains(query, true) == true }
            if (provider != null && provider.models.isEmpty() && !loading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    TextButton(onClick = ::reload) { Text(stringResource(R.string.picker_load)) }
                }
            }
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(models, key = { it.id }) { m ->
                    ModelRow(m, selected = provider?.model == m.id) {
                        val p = provider ?: return@ModelRow
                        graph.providers.upsert(p.copy(model = m.id))
                        graph.settings.update { it.copy(activeProviderId = p.id) }
                        onDismiss()
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun ModelRow(m: ModelInfo, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (selected) c.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(m.id, style = MonoBody, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            m.name?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        m.context?.let { Text(it.compact(), style = MonoLabel, color = c.onSurfaceVariant) }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(R.drawable.ic_check), null, tint = c.onSecondaryContainer)
        }
    }
}
