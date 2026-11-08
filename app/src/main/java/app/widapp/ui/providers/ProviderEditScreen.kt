package app.widapp.ui.providers

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.widapp.R
import app.widapp.ai.ChatRequest
import app.widapp.ai.ProviderPresets
import app.widapp.core.SecretBox
import app.widapp.data.ModelInfo
import app.widapp.data.ProviderConfig
import app.widapp.ui.components.Kicker
import app.widapp.ui.components.rememberApp
import app.widapp.ui.theme.MonoBody
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ProviderEditScreen(id: String?, presetId: String?, onBack: () -> Unit) {
    val app = rememberApp()
    val graph = app.graph
    val context = LocalContext.current
    val existing = remember(id) { graph.providers.get(id) }
    var preset by remember { mutableStateOf(ProviderPresets.get(existing?.presetId ?: presetId ?: "openai")) }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: preset.name) }
    var baseUrl by rememberSaveable { mutableStateOf(existing?.baseUrl ?: preset.baseUrl) }
    var key by rememberSaveable { mutableStateOf(existing?.let { graph.providers.apiKey(it) } ?: "") }
    var showKey by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf(existing?.models ?: emptyList()) }
    var model by rememberSaveable { mutableStateOf(existing?.model) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme
    val foundText = stringResource(R.string.provider_found)
    val testOk = stringResource(R.string.provider_test_ok)

    suspend fun fetch() {
        if (baseUrl.length < 10 || (preset.needsKey && key.isBlank())) return
        loading = true; error = null
        runCatching { app.aiClient.listModels(preset.kind, baseUrl.trim(), key.trim()) }
            .onSuccess { list ->
                models = list
                status = foundText.format(list.size)
                if (model == null || list.none { it.id == model }) model = ProviderPresets.pickDefault(preset, list)
            }
            .onFailure { error = it.message; models = emptyList() }
        loading = false
    }

    // Auto-fetch models as soon as the key or address looks complete.
    LaunchedEffect(key, baseUrl, preset.id) {
        if (existing != null && models.isNotEmpty() && key == graph.providers.apiKey(existing) && baseUrl == existing.baseUrl) return@LaunchedEffect
        delay(700)
        fetch()
    }

    fun save() {
        val p = ProviderConfig(
            id = existing?.id ?: graph.providers.newId(),
            presetId = preset.id, kind = preset.kind, name = name.trim().ifBlank { preset.name },
            baseUrl = baseUrl.trim().trimEnd('/'), apiKeyEnc = SecretBox.encrypt(key.trim()),
            model = model, models = models, modelsFetchedAt = System.currentTimeMillis(),
        )
        graph.providers.upsert(p)
        if (graph.settings.value.activeProviderId == null || graph.providers.get(graph.settings.value.activeProviderId) == null) {
            graph.settings.update { it.copy(activeProviderId = p.id) }
        }
        onBack()
    }

    Scaffold(
        containerColor = c.surface,
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) stringResource(R.string.provider_new) else existing.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back)) } },
                actions = {
                    if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.action_delete)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.surface),
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(c.surfaceContainerLow).navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    enabled = model != null && !testing,
                    onClick = {
                        testing = true; error = null
                        scope.launch {
                            val tmp = ProviderConfig("tmp", preset.id, preset.kind, name, baseUrl.trim(), model = model)
                            runCatching {
                                app.aiClient.complete(tmp, key.trim(), model!!, ChatRequest("Reply with the single word OK.", "Ping", maxTokens = 400, json = false))
                            }.onSuccess { status = testOk + " · " + it.trim().take(40) }.onFailure { error = it.message }
                            testing = false
                        }
                    },
                ) { if (testing) LoadingIndicator(Modifier.size(24.dp)) else Text(stringResource(R.string.provider_test)) }
                Button(onClick = ::save, enabled = model != null, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_save)) }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Kicker(stringResource(R.string.provider_service))
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProviderPresets.all.forEach { p ->
                        FilterChip(p.id == preset.id, {
                            preset = p; name = p.name; baseUrl = p.baseUrl; models = emptyList(); model = null; status = null; error = null
                        }, { Text(p.name) })
                    }
                }
                preset.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
            }
            item {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.provider_name)) }, singleLine = true, shape = RoundedCornerShape(18.dp))
            }
            item {
                OutlinedTextField(
                    baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.provider_url)) },
                    singleLine = true, textStyle = MonoBody, shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
            item {
                OutlinedTextField(
                    key, { key = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(if (preset.needsKey) R.string.provider_key else R.string.provider_key_optional)) },
                    placeholder = { Text(preset.keyHint) },
                    singleLine = true, textStyle = MonoBody, shape = RoundedCornerShape(18.dp),
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(painterResource(if (showKey) R.drawable.ic_visibility_off else R.drawable.ic_visibility), null)
                        }
                    },
                )
                preset.keyUrl?.let { url ->
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                        Icon(painterResource(R.drawable.ic_open_in_new), null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.provider_get_key))
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Kicker(stringResource(R.string.provider_models))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when {
                                loading -> stringResource(R.string.provider_loading)
                                error != null -> error!!
                                status != null -> status!!
                                else -> stringResource(R.string.provider_models_hint)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (error != null) c.error else c.onSurfaceVariant,
                        )
                    }
                    if (loading) LoadingIndicator(Modifier.size(36.dp))
                    else IconButton(onClick = { scope.launch { fetch() } }) { Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.action_refresh)) }
                }
            }
            if (models.size > 8) item {
                OutlinedTextField(
                    query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(18.dp),
                    placeholder = { Text(stringResource(R.string.picker_search)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                )
            }
            val shown: List<ModelInfo> = models.filter { query.isBlank() || it.id.contains(query, true) || it.name?.contains(query, true) == true }
            items(shown, key = { it.id }) { m ->
                ModelRow(m, selected = model == m.id) { model = m.id }
            }
            if (models.isEmpty() && !loading) item {
                OutlinedTextField(
                    model ?: "", { model = it.ifBlank { null } }, Modifier.fillMaxWidth(), singleLine = true, textStyle = MonoBody,
                    label = { Text(stringResource(R.string.provider_model_manual)) }, shape = RoundedCornerShape(18.dp),
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.provider_delete_q, existing.name)) },
            confirmButton = { TextButton(onClick = { graph.providers.delete(existing.id); confirmDelete = false; onBack() }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
