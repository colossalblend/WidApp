package app.widapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import app.widapp.ui.AppNav
import app.widapp.ui.theme.WidTheme

class MainActivity : ComponentActivity() {
    /** Text shared into the app (e.g. a widget JSON from a friend). */
    private val sharedText = mutableStateOf<String?>(null)
    private val openCreate = mutableStateOf(false)
    private val openUpdates = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handle(intent)
        val graph = (application as WidApp).graph
        (application as WidApp).updates.checkInBackground()
        setContent {
            val settings by graph.settings.flow.collectAsState()
            WidTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                AppNav(
                    sharedText = sharedText.value,
                    onSharedConsumed = { sharedText.value = null },
                    openCreate = openCreate.value,
                    onOpenCreateConsumed = { openCreate.value = false },
                    openUpdates = openUpdates.value,
                    onOpenUpdatesConsumed = { openUpdates.value = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) sharedText.value = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (intent?.getBooleanExtra(EXTRA_CREATE, false) == true) openCreate.value = true
        if (intent?.getBooleanExtra(EXTRA_UPDATES, false) == true) openUpdates.value = true
    }

    companion object {
        const val EXTRA_CREATE = "app.widapp.CREATE"
        const val EXTRA_UPDATES = "app.widapp.UPDATES"
    }
}
