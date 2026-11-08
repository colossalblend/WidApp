package app.widapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import app.widapp.spec.WidgetSize
import app.widapp.ui.theme.MonoLabel

/** Design-tool canvas: tiny dots on a 14 dp grid. */
fun Modifier.dotGrid(color: Color, step: Dp = 14.dp, radius: Dp = 1.dp): Modifier = drawBehind {
    val s = step.toPx(); val r = radius.toPx()
    var y = s / 2
    while (y < size.height) {
        var x = s / 2
        while (x < size.width) { drawCircle(color, r, Offset(x, y)); x += s }
        y += s
    }
}

/** "4×2" in a hairline pill, set in mono. */
@Composable
fun SizeTag(size: WidgetSize, modifier: Modifier = Modifier, filled: Boolean = false) {
    val c = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .then(if (filled) Modifier.background(c.onSurface) else Modifier.border(1.dp, c.outlineVariant, RoundedCornerShape(50)))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(size.label, style = MonoLabel, color = if (filled) c.surface else c.onSurfaceVariant)
    }
}

/** Kicker in mono caps: "01 — PROMPT". */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), modifier, style = MonoLabel.copy(fontSize = 11.sp, letterSpacing = 0.12.em), color = color)
}

@Composable
fun ScreenHeader(kicker: String, title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Kicker(kicker)
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

/** An icon sitting inside one of Material's expressive shapes. */
@Composable
fun ShapeBadge(
    icon: Int,
    modifier: Modifier = Modifier,
    shape: RoundedPolygon = MaterialShapes.Cookie9Sided,
    container: Color = MaterialTheme.colorScheme.tertiaryContainer,
    content: Color = MaterialTheme.colorScheme.onTertiaryContainer,
    size: Dp = 56.dp,
    iconSize: Dp = 26.dp,
) {
    Box(modifier.size(size).clip(shape.toShape()).background(container), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, Modifier.size(iconSize), tint = content)
    }
}

/** Monogram in a shape — used for providers instead of third-party logos. */
@Composable
fun Monogram(text: String, modifier: Modifier = Modifier, shape: RoundedPolygon = MaterialShapes.Cookie6Sided, container: Color, content: Color, size: Dp = 44.dp) {
    Box(modifier.size(size).clip(shape.toShape()).background(container), contentAlignment = Alignment.Center) {
        Text(text.take(2), color = content, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W700))
    }
}

@Composable
fun SizeGlyph(size: WidgetSize, selected: Boolean, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val cell = 9.dp; val gap = 2.dp
    Box(
        modifier
            .width(cell * 4 + gap * 3)
            .height(cell * 4 + gap * 3),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (r in 0 until 4) Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (col in 0 until 4) {
                    val inside = col < size.cols && r >= 4 - size.rows
                    Box(
                        Modifier.size(cell).clip(RoundedCornerShape(2.5.dp)).background(
                            when {
                                inside && selected -> c.onSecondaryContainer
                                inside -> c.onSurface
                                else -> c.outlineVariant.copy(alpha = 0.5f)
                            }
                        )
                    )
                }
            }
        }
    }
}

fun Int.compact(): String = when {
    this >= 1_000_000 -> "${this / 1_000_000}M"
    this >= 1000 -> "${this / 1000}K"
    else -> toString()
}
