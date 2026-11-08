package app.widapp.ui.editor

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.widapp.R
import app.widapp.data.WidgetDesign
import app.widapp.spec.SpecParser
import app.widapp.spec.WidgetSize
import app.widapp.ui.components.GeneratingStatus
import app.widapp.ui.components.Kicker
import app.widapp.ui.components.SizeTag
import app.widapp.ui.components.WidgetPreview
import app.widapp.ui.components.dotGrid
import app.widapp.ui.components.rememberApp
import app.widapp.ui.components.rememberImagePicker
import app.widapp.ui.components.rememberPalette
import app.widapp.ui.components.rememberPreviewData
import app.widapp.ui.components.rememberVoiceInput
import app.widapp.ui.theme.MonoBody
import app.widapp.ui.theme.MonoLabel
import app.widapp.widget.WidgetBinder
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(id: String, onBack: () -> Unit, onOpen: (String) -> Unit, onConnect: () -> Unit) {
    val app = rememberApp()
    val vm: EditorViewModel = viewModel(key = id) { EditorViewModel(app, id) }
    val design by vm.design.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var pinHelp by remember { mutableStateOf(false) }
    var placed by remember { mutableIntStateOf(0) }
    val c = MaterialTheme.colorScheme
    val pinAsk = stringResource(R.string.editor_pin_confirm)
    val copied = stringResource(R.string.editor_copied)

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(id) { vm.ensureLiveContent(); placed = WidgetBinder.countPlaced(context, id) }

    val d = design ?: run {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Scaffold(
        containerColor = c.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { renaming = true })
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back)) } },
                actions = {
                    IconButton(onClick = { vm.undo(context) }, enabled = d.current > 0 && !vm.busy) { Icon(painterResource(R.drawable.ic_undo), stringResource(R.string.editor_undo)) }
                    IconButton(onClick = { vm.toggleFavorite() }) {
                        Icon(painterResource(if (d.favorite) R.drawable.ic_star_fill else R.drawable.ic_star), stringResource(R.string.editor_favorite), tint = if (d.favorite) c.tertiary else c.onSurfaceVariant)
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.ic_more_vert), null) }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text(stringResource(R.string.editor_rename)) }, { menu = false; renaming = true }, leadingIcon = { Icon(painterResource(R.drawable.ic_edit), null) })
                            DropdownMenuItem({ Text(stringResource(R.string.editor_duplicate)) }, { menu = false; vm.duplicate()?.let(onOpen) }, leadingIcon = { Icon(painterResource(R.drawable.ic_content_copy), null) })
                            DropdownMenuItem({ Text(stringResource(R.string.editor_share)) }, {
                                menu = false
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, SpecParser.toJson(d.spec))
                                context.startActivity(Intent.createChooser(send, d.name))
                            }, leadingIcon = { Icon(painterResource(R.drawable.ic_share), null) })
                            DropdownMenuItem({ Text(stringResource(R.string.editor_copy_json)) }, {
                                menu = false
                                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("widget", SpecParser.toJson(d.spec)))
                                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                            }, leadingIcon = { Icon(painterResource(R.drawable.ic_data_object), null) })
                            DropdownMenuItem({ Text(stringResource(R.string.action_delete), color = c.error) }, { menu = false; deleting = true }, leadingIcon = { Icon(painterResource(R.drawable.ic_delete), null, tint = c.error) })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.surface),
            )
        },
        bottomBar = { if (tab == 0) RefineBar(vm, onConnect) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            Canvas(d, vm)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                // Meta on the left may shrink; the button never wraps.
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SizeTag(WidgetSize.of(d.spec.size), filled = true)
                        Spacer(Modifier.width(8.dp))
                        Text("v${d.current + 1}/${d.versions.size}", style = MonoLabel, color = c.onSurfaceVariant, maxLines = 1)
                    }
                    if (placed > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.editor_placed, placed), style = MonoLabel, color = c.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = {
                        if (WidgetBinder.canPin(context) && WidgetBinder.requestPin(context, d)) {
                            Toast.makeText(context, pinAsk, Toast.LENGTH_LONG).show()
                        } else pinHelp = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_mobile_arrow_up_right), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.editor_pin), maxLines = 1, softWrap = false)
                }
            }
            PrimaryTabRow(selectedTabIndex = tab, containerColor = c.surface) {
                listOf(R.string.editor_tab_refine, R.string.editor_tab_history, R.string.editor_tab_code).forEachIndexed { i, label ->
                    Tab(tab == i, { tab = i }, text = { Text(stringResource(label)) })
                }
            }
            when (tab) {
                0 -> RefineTab(d, vm)
                1 -> HistoryTab(d, vm)
                else -> CodeTab(d, vm)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(d.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.editor_rename)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.rename(name, context); renaming = false }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.editor_delete_q)) },
            text = { Text(stringResource(R.string.editor_delete_body)) },
            confirmButton = { TextButton(onClick = { deleting = false; vm.delete(context); onBack() }) { Text(stringResource(R.string.action_delete), color = c.error) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (pinHelp) {
        AlertDialog(
            onDismissRequest = { pinHelp = false },
            title = { Text(stringResource(R.string.settings_howto_title)) },
            text = { Text(stringResource(R.string.settings_howto)) },
            confirmButton = { TextButton(onClick = { pinHelp = false }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@Composable
private fun Canvas(d: WidgetDesign, vm: EditorViewModel) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val size = WidgetSize.of(d.spec.size)
    val systemDark = app.widapp.ui.components.appIsDark()
    val dark = vm.previewDark ?: systemDark
    val data = rememberPreviewData(d.id)
    val palette = rememberPalette()
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(32.dp))
            .background(if (dark) androidx.compose.ui.graphics.Color(0xFF151413) else c.surfaceContainerLow)
            .dotGrid(if (dark) androidx.compose.ui.graphics.Color(0x33FFFFFF) else c.outlineVariant.copy(alpha = 0.8f)),
    ) {
        val zoom = when { size.cols == 1 -> 1.7f; size.cols == 2 -> 1.35f; size.cols == 3 -> 1.1f; else -> 1f }
        val w = minOf(maxWidth.value - 40f, size.widthDp * zoom).dp
        Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                WidgetPreview(
                    spec = d.spec, state = d.spec.state + vm.previewState, data = data, palette = palette, systemDark = dark,
                    modifier = Modifier.width(w),
                    onAction = { vm.onPreviewAction(it, context) },
                )
                androidx.compose.animation.AnimatedVisibility(vm.busy, enter = fadeIn(), exit = fadeOut()) {
                    Box(Modifier.width(w).heightIn(min = 120.dp).clip(RoundedCornerShape(24.dp)).background(c.surface.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                        GeneratingStatus(null, Modifier.padding(16.dp), size = 64.dp, lines = R.array.refine_status)
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            IconButton(onClick = { vm.previewState.clear() }) { Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.editor_reset_state), tint = if (dark) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f) else c.onSurfaceVariant) }
            IconButton(onClick = { vm.previewDark = !dark }) {
                Icon(painterResource(if (dark) R.drawable.ic_light_mode else R.drawable.ic_dark_mode), stringResource(R.string.editor_toggle_theme), tint = if (dark) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f) else c.onSurfaceVariant)
            }
        }
        Text(
            stringResource(R.string.editor_tap_hint), style = MonoLabel,
            color = if (dark) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.45f) else c.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RefineTab(d: WidgetDesign, vm: EditorViewModel) {
    val app = rememberApp()
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    val ai by app.graph.aiContent.flow.collectAsState()
    val slots = remember(d.spec) { app.liveContent.slots(d) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        if (slots.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.tertiaryContainer).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Kicker(stringResource(R.string.editor_live_ai), color = c.onTertiaryContainer)
                    Spacer(Modifier.weight(1f))
                    if (d.id in ai.loading) LoadingIndicator(Modifier.size(28.dp), color = c.onTertiaryContainer)
                    else IconButton(onClick = { vm.refreshAi(null) }) { Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.action_refresh), tint = c.onTertiaryContainer) }
                }
                slots.forEach { s ->
                    Spacer(Modifier.height(6.dp))
                    Text("{ai.${s.key}} · ${s.refresh}", style = MonoLabel, color = c.onTertiaryContainer.copy(alpha = 0.7f))
                    Text(ai.byDesign[d.id]?.get(s.key)?.text ?: "…", style = MaterialTheme.typography.bodyMedium, color = c.onTertiaryContainer)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Kicker(stringResource(R.string.editor_quick))
        Spacer(Modifier.height(10.dp))
        val quick = listOf(
            R.string.quick_variant to null,
            R.string.quick_darker to R.string.quick_darker_p,
            R.string.quick_bigger to R.string.quick_bigger_p,
            R.string.quick_minimal to R.string.quick_minimal_p,
            R.string.quick_interactive to R.string.quick_interactive_p,
            R.string.quick_ai to R.string.quick_ai_p,
            R.string.quick_weather to R.string.quick_weather_p,
            R.string.quick_colors to R.string.quick_colors_p,
            R.string.quick_material to R.string.quick_material_p,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            quick.forEach { (label, prompt) ->
                val p = prompt?.let { stringResource(it) }
                AssistChip(
                    enabled = !vm.busy,
                    onClick = { if (p == null) vm.variant(context) else vm.refine(p, context) },
                    label = { Text(stringResource(label)) },
                    leadingIcon = if (p == null) ({ Icon(painterResource(R.drawable.ic_shuffle), null, Modifier.size(18.dp)) }) else null,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        val first = d.firstPrompt
        if (first.isNotBlank()) {
            Kicker(stringResource(R.string.editor_brief))
            Spacer(Modifier.height(6.dp))
            Text(first, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        }
    }
}

@Composable
private fun RefineBar(vm: EditorViewModel, onConnect: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val pick = rememberImagePicker { img, bmp -> vm.image = img; vm.thumb = bmp }
    val voice = rememberVoiceInput { t -> vm.input = (vm.input.trimEnd() + " " + t).trim() }
    Column(Modifier.fillMaxWidth().background(c.surfaceContainerLow).navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 10.dp)) {
        if (vm.needsProvider) {
            TextButton(onClick = onConnect) { Text(stringResource(R.string.connect_title)) }
        }
        androidx.compose.animation.AnimatedVisibility(vm.busy) {
            Text(
                stringResource(R.string.job_background_hint_short), style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
            )
        }
        vm.thumb?.let { bmp ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Image(bmp.asImageBitmap(), null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.create_reference), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.image = null; vm.thumb = null }) { Icon(painterResource(R.drawable.ic_close), null) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = pick) { Icon(painterResource(R.drawable.ic_add_photo_alternate), stringResource(R.string.create_image)) }
            TextField(
                vm.input, { vm.input = it }, Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.editor_refine_hint)) },
                maxLines = 4, shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContainerColor = c.surfaceContainerHigh, unfocusedContainerColor = c.surfaceContainerHigh,
                ),
                trailingIcon = { IconButton(onClick = voice) { Icon(painterResource(R.drawable.ic_mic), stringResource(R.string.create_voice)) } },
            )
            Spacer(Modifier.width(8.dp))
            if (vm.busy) {
                FilledIconButton(onClick = vm::cancel, colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.surfaceContainerHighest, contentColor = c.onSurface)) {
                    Icon(painterResource(R.drawable.ic_stop), stringResource(R.string.action_cancel))
                }
            } else {
                FilledIconButton(
                    onClick = { vm.refine(vm.input, context) }, enabled = vm.input.isNotBlank(),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.tertiary, contentColor = c.onTertiary),
                ) { Icon(painterResource(R.drawable.ic_arrow_upward), stringResource(R.string.action_send)) }
            }
        }
    }
}

@Composable
private fun HistoryTab(d: WidgetDesign, vm: EditorViewModel) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        d.versions.withIndex().reversed().forEach { (i, v) ->
            val current = i == d.current
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(if (current) c.secondaryContainer else c.surfaceContainerLow)
                    .clickable { vm.select(i, context) }.padding(14.dp),
            ) {
                Text("v${i + 1}", style = MonoLabel, color = if (current) c.onSecondaryContainer else c.onSurfaceVariant, modifier = Modifier.width(36.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        v.prompt.ifBlank {
                            stringResource(when (v.kind) { "manual" -> R.string.history_manual; "sample" -> R.string.history_sample; "import" -> R.string.history_import; else -> R.string.history_surprise })
                        },
                        style = MaterialTheme.typography.bodyMedium, color = c.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(listOfNotNull(v.model, fmt.format(Date(v.at))).joinToString(" · "), style = MonoLabel, color = c.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CodeTab(d: WidgetDesign, vm: EditorViewModel) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    var text by remember(d.current, d.versions.size) { mutableStateOf(SpecParser.toJson(d.spec)) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.editor_code_hint), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh).padding(14.dp)) {
            BasicTextField(text, { text = it; error = null }, Modifier.fillMaxWidth().heightIn(min = 240.dp), textStyle = MonoBody.copy(color = c.onSurface), cursorBrush = SolidColor(c.tertiary))
        }
        error?.let { Text(it, color = c.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { error = vm.applyJson(text, context) }) { Text(stringResource(R.string.editor_apply)) }
            TextButton(onClick = { text = SpecParser.toJson(d.spec); error = null }) { Text(stringResource(R.string.editor_revert)) }
        }
    }
}
