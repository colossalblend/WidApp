package app.widapp.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * Odometer / split-flap text: every character rolls on its own, staggered left to right.
 * Characters that do not change stay still, so 1.2.0 → 1.3.0 only turns one wheel.
 */
@Composable
fun RollingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, upward: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        text.forEachIndexed { i, ch ->
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    val dir = if (upward) 1 else -1
                    (slideInVertically(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) { it * dir } +
                        fadeIn(tween(160, delayMillis = i * 35)))
                        .togetherWith(slideOutVertically(tween(200, delayMillis = i * 35)) { -it * dir } + fadeOut(tween(140, delayMillis = i * 35)))
                        .using(SizeTransform(clip = true))
                },
                label = "roll$i",
            ) { c -> Text(c.toString(), style = style, color = color) }
        }
    }
}
