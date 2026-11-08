package app.widapp.ui.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.widapp.BuildConfig
import app.widapp.R
import app.widapp.ui.components.Kicker
import app.widapp.ui.components.Markdown
import app.widapp.ui.components.RollingText
import app.widapp.ui.components.ShapeBadge
import app.widapp.ui.components.rememberApp
import app.widapp.ui.theme.MonoLabel

/** Slides in above the library when a newer release exists. */
@Composable
fun UpdateBanner(onOpen: () -> Unit) {
    val app = rememberApp()
    val available by app.updates.available.collectAsState()
    var dismissed by rememberSaveable { mutableStateOf<String?>(null) }
    val c = MaterialTheme.colorScheme
    val r = available
    AnimatedVisibility(
        r != null && dismissed != r.version,
        enter = fadeIn() + expandVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)),
        exit = fadeOut() + shrinkVertically(),
    ) {
        if (r == null) return@AnimatedVisibility
        Row(
            Modifier.fillMaxWidth().padding(bottom = 4.dp).clip(RoundedCornerShape(28.dp)).background(c.inverseSurface)
                .clickable(onClick = onOpen).padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShapeBadge(R.drawable.ic_download, shape = MaterialShapes.SoftBurst, container = c.tertiary, content = c.onTertiary, size = 46.dp, iconSize = 22.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Kicker(stringResource(R.string.update_k_available), color = c.inverseOnSurface.copy(alpha = 0.6f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(BuildConfig.VERSION_NAME + "  →  ", style = MonoLabel, color = c.inverseOnSurface.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 3.dp))
                    RollingText(r.version, MaterialTheme.typography.titleLarge, c.inverseOnSurface)
                }
            }
            Button(
                onClick = onOpen,
                colors = ButtonDefaults.buttonColors(containerColor = c.inverseOnSurface, contentColor = c.inverseSurface),
            ) { Text(stringResource(R.string.update_short)) }
            IconButton(onClick = { dismissed = r.version }) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close), tint = c.inverseOnSurface.copy(alpha = 0.6f)) }
        }
    }
}

/** Shown once after the app was updated. */
@Composable
fun WhatsNewSheet(version: String, notes: String, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeBadge(R.drawable.ic_check, shape = MaterialShapes.Cookie9Sided, container = c.tertiary, content = c.onTertiary, size = 52.dp, iconSize = 24.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Kicker(stringResource(R.string.update_updated))
                    RollingText(version, MaterialTheme.typography.headlineLarge, c.onSurface)
                }
            }
            Spacer(Modifier.height(20.dp))
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (notes.isBlank()) Text(stringResource(R.string.update_no_notes), color = c.onSurfaceVariant)
                else Markdown(notes)
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.update_great)) }
        }
    }
}

