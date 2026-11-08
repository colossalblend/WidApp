package app.widapp

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import app.widapp.ui.AppNav
import app.widapp.ui.theme.WidTheme
import app.widapp.ui.update.UpdateScreen
import app.widapp.update.ReleaseAsset
import app.widapp.update.ReleaseInfo
import app.widapp.update.UpdatePhase
import app.widapp.update.VerifyStep
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-w411dp-h891dp-xxhdpi")
class UpdateShots {
    @get:Rule val compose = createComposeRule()

    private val r120 = ReleaseInfo(
        "v1.2.0", "1.2.0", "WidApp 1.2.0",
        "## Что нового\n- **Живые виджеты**: кнопки теперь отвечают мгновенно\n- Новый стиль «Газета» и `4×3` размер\n- Исправлена погода для городов с апострофом\n\n**Full Changelog**: https://github.com/colossalblend/WidApp/compare/v1.1.0...v1.2.0",
        "2026-11-07T12:00:00Z", false, "https://github.com/colossalblend/WidApp/releases/tag/v1.2.0",
        ReleaseAsset("WidApp-1.2.0.apk", 18_640_112, "https://example.invalid/a.apk", "sha256:00"),
    )
    private val r100 = r120.copy(tag = "v1.0.0", version = "1.0.0", title = "WidApp 1.0.0", notes = "Первый релиз.", publishedAt = "2026-11-01T12:00:00Z")

    private fun app() = ApplicationProvider.getApplicationContext<WidApp>()
    private fun shot(name: String) { compose.waitForIdle(); compose.onRoot().captureRoboImage("build/shots/update_$name.png") }

    @Test fun available() {
        app().updates.seedForTest(listOf(r120, r100), UpdatePhase.Available(r120))
        compose.setContent { WidTheme { UpdateScreen {} } }
        compose.mainClock.advanceTimeBy(2000)
        shot("available")
    }

    @Test fun downloading() {
        app().updates.seedForTest(listOf(r120, r100), UpdatePhase.Downloading(r120, 7_400_000, 18_640_112, 2_300_000))
        compose.setContent { WidTheme { UpdateScreen {} } }
        compose.mainClock.advanceTimeBy(2000)
        shot("downloading")
    }

    @Test fun ready() {
        app().updates.seedForTest(listOf(r120, r100), UpdatePhase.Ready(r120, File("x.apk"), emptySet()))
        compose.setContent { WidTheme(themeMode = "dark") { UpdateScreen {} } }
        compose.mainClock.advanceTimeBy(2000)
        shot("ready_dark")
    }

    @Test fun verifying() {
        app().updates.seedForTest(listOf(r120, r100), UpdatePhase.Verifying(r120, setOf(VerifyStep.Size)))
        compose.setContent { WidTheme { UpdateScreen {} } }
        compose.mainClock.advanceTimeBy(2000)
        shot("verifying")
    }

    @Test fun banner() {
        app().updates.seedForTest(listOf(r120, r100), UpdatePhase.Available(r120))
        compose.setContent { WidTheme { AppNav(null, {}, false, {}) } }
        compose.mainClock.advanceTimeBy(2000)
        shot("banner")
    }
}
