package app.widapp.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.widapp.R
import app.widapp.ui.theme.MonoLabel
import kotlinx.coroutines.delay

/** Expressive morphing indicator with rotating, human status lines. */
@Composable
fun GeneratingStatus(model: String?, modifier: Modifier = Modifier, size: Dp = 96.dp, lines: Int = R.array.gen_status) {
    val steps = stringArrayResource(lines)
    val index by produceState(0) { while (true) { delay(2400); value = (value + 1) % steps.size } }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        LoadingIndicator(
            Modifier.size(size),
            color = MaterialTheme.colorScheme.tertiary,
            polygons = listOf(MaterialShapes.SoftBurst, MaterialShapes.Cookie9Sided, MaterialShapes.Pentagon, MaterialShapes.Pill, MaterialShapes.Sunny, MaterialShapes.Cookie4Sided, MaterialShapes.Oval),
        )
        Spacer(Modifier.height(18.dp))
        AnimatedContent(
            index,
            contentAlignment = Alignment.Center,
            transitionSpec = { fadeIn(tween(350, delayMillis = 150)) togetherWith fadeOut(tween(150)) },
            label = "status",
        ) { i ->
            Text(steps[i], style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        }
        if (model != null) {
            Spacer(Modifier.height(6.dp))
            Text(model, style = MonoLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
