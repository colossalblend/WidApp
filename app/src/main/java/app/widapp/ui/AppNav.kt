package app.widapp.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.widapp.R
import app.widapp.spec.SpecParser
import app.widapp.ui.components.rememberGraph
import app.widapp.ui.create.CreateScreen
import app.widapp.ui.editor.EditorScreen
import app.widapp.ui.ideas.IdeasTab
import app.widapp.ui.library.LibraryTab
import app.widapp.ui.providers.ModelsTab
import app.widapp.ui.providers.ProviderEditScreen
import app.widapp.ui.settings.SettingsTab
import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable data class CreateRoute(val prompt: String = "", val size: String = "", val surprise: Boolean = false)
@Serializable data class EditorRoute(val id: String)
@Serializable data class ProviderRoute(val id: String = "", val preset: String = "")

private data class Tab(val label: Int, val icon: Int, val iconSelected: Int)

private val tabs = listOf(
    Tab(R.string.tab_widgets, R.drawable.ic_widgets, R.drawable.ic_widgets_fill),
    Tab(R.string.tab_ideas, R.drawable.ic_interests, R.drawable.ic_interests_fill),
    Tab(R.string.tab_models, R.drawable.ic_hub, R.drawable.ic_hub_fill),
    Tab(R.string.tab_settings, R.drawable.ic_tune, R.drawable.ic_tune_fill),
)

@Composable
fun AppNav(sharedText: String?, onSharedConsumed: () -> Unit, openCreate: Boolean, onOpenCreateConsumed: () -> Unit) {
    val nav = rememberNavController()
    val graph = rememberGraph()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(sharedText) {
        val text = sharedText ?: return@LaunchedEffect
        onSharedConsumed()
        val spec = runCatching { SpecParser.parse(text) }.getOrNull()
        if (spec != null) {
            val d = graph.designs.create(spec, "", null, kind = "import")
            nav.navigate(EditorRoute(d.id))
        } else {
            nav.navigate(CreateRoute(prompt = text.take(2000)))
        }
    }
    LaunchedEffect(openCreate) {
        if (openCreate) { onOpenCreateConsumed(); nav.navigate(CreateRoute()) }
    }

    NavHost(nav, startDestination = HomeRoute) {
        composable<HomeRoute> {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                        tabs.forEachIndexed { i, t ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { Icon(painterResource(if (tab == i) t.iconSelected else t.icon), null) },
                                label = { Text(stringResource(t.label)) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                ),
                            )
                        }
                    }
                },
                floatingActionButton = {
                    if (tab <= 1) ExtendedFloatingActionButton(
                        onClick = { nav.navigate(CreateRoute()) },
                        icon = { Icon(painterResource(R.drawable.ic_add), null) },
                        text = { Text(stringResource(R.string.action_new_widget)) },
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    )
                },
            ) { pad ->
                AnimatedContent(tab, Modifier.padding(pad), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { t ->
                    when (t) {
                        0 -> LibraryTab(
                            onOpen = { nav.navigate(EditorRoute(it)) },
                            onCreate = { nav.navigate(CreateRoute()) },
                            onConnect = { tab = 2 },
                            onIdeas = { tab = 1 },
                        )
                        1 -> IdeasTab(
                            onPrompt = { p, s -> nav.navigate(CreateRoute(prompt = p, size = s)) },
                            onSurprise = { nav.navigate(CreateRoute(surprise = true)) },
                            onOpen = { nav.navigate(EditorRoute(it)) },
                        )
                        2 -> ModelsTab(
                            onEdit = { nav.navigate(ProviderRoute(id = it)) },
                            onAdd = { preset -> nav.navigate(ProviderRoute(preset = preset)) },
                        )
                        else -> SettingsTab(onImported = { nav.navigate(EditorRoute(it)) })
                    }
                }
            }
        }
        composable<CreateRoute> { entry ->
            val r = entry.toRoute<CreateRoute>()
            CreateScreen(
                initialPrompt = r.prompt,
                initialSize = r.size,
                surprise = r.surprise,
                onBack = { nav.popBackStack() },
                onCreated = { id ->
                    nav.navigate(EditorRoute(id)) { popUpTo<CreateRoute> { inclusive = true } }
                },
                onConnect = { nav.popBackStack(); tab = 2 },
            )
        }
        composable<EditorRoute> { entry ->
            val r = entry.toRoute<EditorRoute>()
            EditorScreen(
                id = r.id,
                onBack = { nav.popBackStack() },
                onOpen = { nav.navigate(EditorRoute(it)) { popUpTo<EditorRoute> { inclusive = true } } },
                onConnect = { nav.popBackStack(); tab = 2 },
            )
        }
        composable<ProviderRoute> { entry ->
            val r = entry.toRoute<ProviderRoute>()
            ProviderEditScreen(id = r.id.ifBlank { null }, presetId = r.preset.ifBlank { null }, onBack = { nav.popBackStack() })
        }
    }
}
