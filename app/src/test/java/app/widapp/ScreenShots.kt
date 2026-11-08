package app.widapp

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.widapp.data.ModelInfo
import app.widapp.data.ProviderConfig
import app.widapp.data.ProviderKind
import app.widapp.spec.Samples
import app.widapp.ui.AppNav
import app.widapp.ui.create.CreateScreen
import app.widapp.ui.editor.EditorScreen
import app.widapp.ui.providers.ProviderEditScreen
import app.widapp.ui.theme.WidTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-w411dp-h891dp-xxhdpi")
class ScreenShots {
    @get:Rule val compose = createComposeRule()

    private fun seed(withDesigns: Boolean = true): WidApp {
        val app = ApplicationProvider.getApplicationContext<WidApp>()
        if (withDesigns) Samples.parsed().forEach { (_, spec) -> app.graph.designs.create(spec, "Крупные часы с датой и будильником", "claude-opus-5-5") }
        app.graph.providers.upsert(
            ProviderConfig(
                "p1", "anthropic", ProviderKind.ANTHROPIC, "Anthropic", "https://api.anthropic.com", model = "claude-opus-5-5",
                models = listOf(ModelInfo("claude-opus-5-5", "Claude Opus 5.5", 1_000_000), ModelInfo("claude-sonnet-5-5", "Claude Sonnet 5.5", 1_000_000)),
            ),
        )
        app.graph.providers.upsert(ProviderConfig("p2", "openrouter", ProviderKind.OPENAI, "OpenRouter", "https://openrouter.ai/api/v1", model = "openai/gpt-5"))
        app.graph.settings.update { it.copy(activeProviderId = "p1") }
        return app
    }

    private fun shot(name: String) { compose.waitForIdle(); compose.onRoot().captureRoboImage("build/shots/screen_$name.png") }

    @Test fun home() {
        seed()
        compose.setContent { WidTheme { AppNav(null, {}, false, {}) } }
        shot("library")
        compose.onNodeWithText("Идеи").performClick(); shot("ideas")
        compose.onNodeWithText("Модели").performClick(); shot("models")
        compose.onNodeWithText("Настройки").performClick(); shot("settings")
    }

    @Test fun homeDark() {
        seed()
        compose.setContent { WidTheme(themeMode = "dark") { AppNav(null, {}, false, {}) } }
        shot("library_dark")
    }

    @Test fun empty() {
        val app = ApplicationProvider.getApplicationContext<WidApp>()
        app.graph.settings.update { it }
        compose.setContent { WidTheme { AppNav(null, {}, false, {}) } }
        shot("library_empty")
    }

    @Test fun create() {
        seed(false)
        compose.setContent { WidTheme { CreateScreen("", "4x2", false, {}, {}, {}) } }
        shot("create")
    }

    @Test fun editor() {
        val app = seed(false)
        val spec = Samples.parsed().first { it.first == "water" }.second
        val d = app.graph.designs.create(spec, "Трекер воды с кнопками", "claude-opus-5-5")
        compose.setContent { WidTheme { EditorScreen(d.id, {}, {}, {}) } }
        shot("editor")
        compose.onNodeWithText("Версии").performClick(); shot("editor_history")
    }

    /** Narrow English layout: the "Add to Home" button must stay on one line. */
    @Test @Config(qualifiers = "en-w360dp-h780dp-xxhdpi") fun editorEnglishNarrow() {
        val app = seed(false)
        val spec = Samples.parsed().first { it.first == "clock" }.second
        var d = app.graph.designs.create(spec, "Big clock", "cx/gpt-6.1-sol")
        repeat(11) { app.graph.designs.addVersion(d.id, spec, "tweak $it", "cx/gpt-6.1-sol") }
        compose.setContent { WidTheme(themeMode = "dark") { EditorScreen(d.id, {}, {}, {}) } }
        shot("editor_en_narrow")
    }

    @Test fun provider() {
        seed(false)
        compose.setContent { WidTheme { ProviderEditScreen(null, "anthropic", {}) } }
        shot("provider")
    }
}
