package app.widapp.ui.create

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.widapp.R
import app.widapp.WidApp
import app.widapp.ai.ImageInput
import app.widapp.ai.NoProviderException
import app.widapp.spec.WidgetSize
import app.widapp.ui.components.GeneratingStatus
import app.widapp.ui.components.Kicker
import app.widapp.ui.components.SizeGlyph
import app.widapp.ui.components.rememberApp
import app.widapp.ui.components.rememberImagePicker
import app.widapp.ui.components.rememberNotificationPermission
import app.widapp.ui.components.rememberVoiceInput
import app.widapp.ui.ideas.IdeaPrompts
import app.widapp.ui.providers.ModelPickerSheet
import app.widapp.ui.theme.MonoLabel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CreateViewModel(private val app: WidApp) : ViewModel() {
    var prompt by mutableStateOf("")
    var size by mutableStateOf(WidgetSize.S4x2)
    var style by mutableStateOf("")
    var image by mutableStateOf<ImageInput?>(null)
    var thumb by mutableStateOf<Bitmap?>(null)
    /** Only "enhance" runs here; widget generation is a background job ([jobId]). */
    var busy by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    var needsProvider by mutableStateOf(false)
    var jobId by mutableStateOf<String?>(null)
    private var job: Job? = null
    private var initialised = false

    fun init(p: String, s: String, surprise: Boolean) {
        if (initialised) return
        initialised = true
        if (p.isNotBlank()) prompt = p
        if (s.isNotBlank()) size = WidgetSize.of(s)
        if (surprise) surprise()
    }

    fun generate() {
        if (prompt.isBlank() || jobId != null) return
        error = null
        jobId = app.jobs.enqueueCreate(prompt.trim(), size, style.ifBlank { null }, image)
    }

    fun surprise() {
        if (jobId != null) return
        error = null
        jobId = app.jobs.enqueueSurprise(size)
    }

    fun enhance() {
        if (prompt.isBlank()) return
        job?.cancel()
        busy = "enhance"; error = null
        job = viewModelScope.launch {
            try { prompt = app.generator.enhance(prompt.trim(), size) }
            catch (e: NoProviderException) { needsProvider = true; error = e.message }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: e.javaClass.simpleName }
            finally { busy = null }
        }
    }

    fun cancel() {
        jobId?.let(app.jobs::cancel)
        jobId = null
    }

    /** Called when the job finished or failed and the screen reacted to it. */
    fun consumed() { jobId = null }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateScreen(
    initialPrompt: String,
    initialSize: String,
    surprise: Boolean,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    onConnect: () -> Unit,
) {
    val app = rememberApp()
    val vm: CreateViewModel = viewModel { CreateViewModel(app) }
    LaunchedEffect(Unit) { vm.init(initialPrompt, initialSize, surprise) }
    val jobs by app.jobs.flow.collectAsState()
    val job = jobs.jobs.firstOrNull { it.id == vm.jobId }
    LaunchedEffect(job?.status) {
        val j = job ?: return@LaunchedEffect
        when (j.status) {
            "done" -> { app.jobs.markSeen(j.id); vm.consumed(); j.resultDesignId?.let(onCreated) }
            "failed" -> { app.jobs.markSeen(j.id); vm.needsProvider = j.needsProvider; vm.error = j.error; vm.consumed() }
            "cancelled" -> vm.consumed()
        }
    }
    val askNotifications = rememberNotificationPermission()
    val providers by app.graph.providers.flow.collectAsState()
    val settings by app.graph.settings.flow.collectAsState()
    val active = app.graph.providers.get(settings.activeProviderId) ?: providers.providers.firstOrNull()
    var picker by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val c = MaterialTheme.colorScheme
    val pickImage = rememberImagePicker { img, bmp -> vm.image = img; vm.thumb = bmp }
    val voice = rememberVoiceInput { t -> vm.prompt = (vm.prompt.trimEnd() + " " + t).trim() }
    LaunchedEffect(vm.error) { vm.error?.let { snackbar.showSnackbar(it) } }

    Scaffold(
        containerColor = c.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close)) } },
                actions = {
                    TextButton(onClick = { askNotifications(); vm.surprise() }, enabled = vm.busy == null && vm.jobId == null) {
                        Icon(painterResource(R.drawable.ic_casino), null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.create_surprise))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.surface),
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(c.surface).navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                if (vm.needsProvider || active == null) {
                    OutlinedButton(onClick = onConnect, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.connect_title)) }
                } else {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { picker = true }.padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Kicker(stringResource(R.string.create_model))
                        Spacer(Modifier.width(8.dp))
                        Text("${active.model ?: "—"} · ${active.name}", style = MonoLabel, color = c.onSurface, maxLines = 1, modifier = Modifier.weight(1f))
                        Icon(painterResource(R.drawable.ic_keyboard_arrow_down), null, tint = c.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { askNotifications(); vm.generate() },
                        enabled = vm.prompt.isNotBlank() && vm.busy == null && vm.jobId == null,
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.tertiary, contentColor = c.onTertiary),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text(stringResource(R.string.create_build), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(10.dp))
                        Icon(painterResource(R.drawable.ic_arrow_forward), null)
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Kicker("01 — " + stringResource(R.string.create_step_prompt))
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.surfaceContainerLow).padding(20.dp),
                ) {
                    if (vm.prompt.isEmpty()) Text(stringResource(R.string.create_placeholder), style = MaterialTheme.typography.headlineSmall.copy(fontSize = 21.sp), color = c.onSurfaceVariant.copy(alpha = 0.7f))
                    BasicTextField(
                        vm.prompt, { vm.prompt = it },
                        Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        textStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = 21.sp, color = c.onSurface),
                        cursorBrush = SolidColor(c.tertiary),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(onClick = voice) { Icon(painterResource(R.drawable.ic_mic), stringResource(R.string.create_voice)) }
                    FilledTonalIconButton(onClick = pickImage) { Icon(painterResource(R.drawable.ic_add_photo_alternate), stringResource(R.string.create_image)) }
                    OutlinedButton(onClick = vm::enhance, enabled = vm.prompt.isNotBlank() && vm.busy == null) {
                        if (vm.busy == "enhance") LoadingIndicator(Modifier.size(22.dp))
                        else Icon(painterResource(R.drawable.ic_ink_pen), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.create_enhance))
                    }
                }
                vm.thumb?.let { bmp ->
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(bmp.asImageBitmap(), null, Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.create_reference), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.create_reference_hint), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.image = null; vm.thumb = null }) { Icon(painterResource(R.drawable.ic_close), null) }
                    }
                }

                Spacer(Modifier.height(28.dp))
                Kicker("02 — " + stringResource(R.string.create_step_size))
                Spacer(Modifier.height(12.dp))
                WidgetSize.entries.chunked(4).forEach { rowSizes ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowSizes.forEach { s ->
                            val sel = vm.size == s
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                                    .background(if (sel) c.secondaryContainer else c.surfaceContainerLow)
                                    .then(if (sel) Modifier.border(BorderStroke(1.5.dp, c.onSecondaryContainer), RoundedCornerShape(20.dp)) else Modifier)
                                    .clickable { vm.size = s }.padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                SizeGlyph(s, sel)
                                Spacer(Modifier.height(8.dp))
                                Text(s.label, style = MonoLabel, color = if (sel) c.onSecondaryContainer else c.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))
                Kicker("03 — " + stringResource(R.string.create_step_style))
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    IdeaPrompts.styles.forEach { (value, label) ->
                        FilterChip(vm.style == value, { vm.style = value }, { Text(label) })
                    }
                }

                Spacer(Modifier.height(28.dp))
                Kicker(stringResource(R.string.create_ideas))
                Spacer(Modifier.height(10.dp))
                IdeaPrompts.all.filter { WidgetSize.of(it.size) == vm.size }.ifEmpty { IdeaPrompts.all.take(4) }.take(4).forEach { idea ->
                    Text(
                        idea.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.onSurface,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp))
                            .background(c.surfaceContainer).clickable { vm.prompt = idea.text }.padding(14.dp),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }

            AnimatedVisibility(job?.active == true, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize().background(c.surface.copy(alpha = 0.96f)).clickable(enabled = true, onClick = {}), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
                        GeneratingStatus(active?.model)
                        Spacer(Modifier.height(20.dp))
                        Text(
                            stringResource(R.string.job_background_hint), style = MaterialTheme.typography.bodyMedium,
                            color = c.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = vm::cancel) { Text(stringResource(R.string.action_cancel)) }
                            Button(onClick = onBack) {
                                Icon(painterResource(R.drawable.ic_keyboard_arrow_down), null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.job_minimize))
                            }
                        }
                    }
                }
            }
        }
    }
    if (picker) ModelPickerSheet(onDismiss = { picker = false })
}
