package app.widapp.ui.components

import android.graphics.Typeface
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.widapp.spec.Colors
import app.widapp.spec.DataSnapshot
import app.widapp.spec.Dim
import app.widapp.spec.Evaluator
import app.widapp.spec.Fonts
import app.widapp.spec.HAlign
import app.widapp.spec.Insets
import app.widapp.spec.VAlign
import app.widapp.spec.WAction
import app.widapp.spec.WNode
import app.widapp.spec.WidgetPalette
import app.widapp.spec.WidgetSize
import app.widapp.spec.WidgetSpec
import app.widapp.spec.parseActions
import app.widapp.spec.parseDim
import app.widapp.spec.parseH
import app.widapp.spec.parseInsets
import app.widapp.spec.parseV
import app.widapp.widget.ContentColors
import app.widapp.widget.render.WidgetIcons
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.sin

private data class PInk(val bg: String?, val content: String)

private class PreviewCtx(
    val ev: Evaluator,
    val palette: WidgetPalette,
    val dark: Boolean,
    val k: Float,
    val now: Long,
    val onAction: ((List<WAction>) -> Unit)?,
) {
    fun color(raw: String?): Color? = Colors.parse(ev.template(raw), palette)?.let { Color(it.pick(dark)) }
}

/**
 * Compose twin of the Glance renderer, used for the in-app canvas. It draws at the
 * widget's design aspect ratio and is fully interactive: buttons change preview state.
 */
@Composable
fun WidgetPreview(
    spec: WidgetSpec,
    state: Map<String, JsonPrimitive>,
    data: DataSnapshot,
    palette: WidgetPalette,
    systemDark: Boolean,
    modifier: Modifier = Modifier,
    onAction: ((List<WAction>) -> Unit)? = null,
) {
    val size = WidgetSize.of(spec.size)
    val dark = when (spec.theme.lowercase()) { "light" -> false; "dark" -> true; else -> systemDark }
    val needsClock = remember(spec) { spec.root.containsClock() }
    val now by produceState(System.currentTimeMillis(), needsClock) {
        while (needsClock) { delay(1000); value = System.currentTimeMillis() }
    }
    BoxWithConstraints(modifier) {
        val width = maxWidth
        val k = width.value / size.widthDp
        val height = (size.heightDp * k).dp
        val ev = Evaluator(state, data)
        val ctx = PreviewCtx(ev, palette, dark, k, now, onAction)
        val corner = ((spec.corner ?: 24f) * k).dp
        val bgRaw = spec.background?.let { ev.template(it) }
        val gradient = spec.gradient?.mapNotNull { ctx.color(it) }
        val shape = RoundedCornerShape(corner)
        var m = Modifier.width(width).height(height).clip(shape)
        m = when {
            !gradient.isNullOrEmpty() -> m.gradient(gradient, spec.angle ?: 90f)
            bgRaw != null -> m.background(ctx.color(bgRaw) ?: Color.Transparent)
            else -> m.background(ctx.color("@surfaceContainer")!!)
        }
        m = m.pad(parseInsets(spec.padding, 16f).scaled(k))
        val effectiveBg = bgRaw ?: spec.gradient?.firstOrNull() ?: "@surfaceContainer"
        val ink = PInk(effectiveBg, ContentColors.onColor(effectiveBg, palette, "@onSurface"))
        Box(m) {
            val root = spec.root
            val outer = Modifier
                .then(if (root.width == null) Modifier.fillMaxWidth() else Modifier)
                .then(if (root.height == null) Modifier.fillMaxHeight() else Modifier)
            PNode(root, outer, ParentKind.Box, ink, ctx)
        }
    }
}

private enum class ParentKind { Row, Column, Box }

private fun WNode.containsClock(): Boolean = type == "clock" || children.any { it.containsClock() }

private fun Modifier.pad(i: Insets) = padding(start = i.start.dp, top = i.top.dp, end = i.end.dp, bottom = i.bottom.dp)

private fun Modifier.gradient(colors: List<Color>, angle: Float): Modifier = drawBehind {
    val a = Math.toRadians(angle.toDouble())
    val c = Offset(size.width / 2, size.height / 2)
    val d = Offset((cos(a) * size.width / 2).toFloat(), (sin(a) * size.height / 2).toFloat())
    val list = if (colors.size == 1) listOf(colors[0], colors[0]) else colors
    drawRect(Brush.linearGradient(list, start = c - d, end = c + d))
}

@Composable
private fun PNode(n: WNode, outer: Modifier, parent: ParentKind, ink: PInk, ctx: PreviewCtx) {
    if (n.visibleIf != null && !ctx.ev.cond(n.visibleIf)) return
    val bgRaw = n.bg?.let { ctx.ev.template(it) }?.takeIf { it.isNotBlank() }
    val childInk = if (bgRaw != null || n.gradient != null) {
        val b = bgRaw ?: n.gradient?.firstOrNull()
        PInk(b, ContentColors.onColor(b, ctx.palette, ink.content))
    } else ink
    when (n.type) {
        "column" -> PColumn(n, decorate(n, outer, ctx), childInk, ctx)
        "row" -> PRow(n, decorate(n, outer, ctx), childInk, ctx)
        "box" -> PBox(n, decorate(n, outer, ctx), childInk, ctx)
        "pages" -> {
            val idx = (Evaluator.toNumber(n.bind?.let { ctx.ev.lookup(it) }) ?: 0.0).toInt()
            val page = n.children.getOrNull(idx.coerceIn(0, (n.children.size - 1).coerceAtLeast(0)))
            Box(decorate(n, outer, ctx), contentAlignment = boxAlign(n)) { if (page != null) PNode(page, Modifier, ParentKind.Box, childInk, ctx) }
        }
        "text" -> {
            val strike = n.strikeIf != null && ctx.ev.cond(n.strikeIf)
            Text(
                ctx.ev.template(n.text), decorate(n, outer, ctx), style = textStyle(n, childInk, ctx, 14f, strike = strike),
                maxLines = n.maxLines ?: Int.MAX_VALUE, overflow = TextOverflow.Ellipsis,
            )
        }
        "clock" -> {
            val fmt = n.format?.takeIf { it.isNotBlank() } ?: if (ctx.ev.lookup("hour") != null && DateFormat.is24HourFormat(androidx.compose.ui.platform.LocalContext.current)) "HH:mm" else "h:mm"
            val cal = Calendar.getInstance().apply {
                timeInMillis = ctx.now
                n.tz?.takeIf { it.isNotBlank() }?.let { timeZone = TimeZone.getTimeZone(it) }
            }
            val text = runCatching { DateFormat.format(fmt, cal).toString() }.getOrDefault(fmt)
            Text(text, decorate(n, outer, ctx), style = textStyle(n, childInk, ctx, 32f, clock = true), maxLines = 1)
        }
        "icon" -> PIcon(n, decorate(n, outer, ctx), childInk, ctx)
        "progress" -> PProgress(n, outer, childInk, ctx)
        "button" -> PButton(n, outer, ink, ctx)
        "check" -> PCheck(n, decorate(n, outer, ctx), childInk, ctx)
        "dots" -> PDots(n, decorate(n, outer, ctx), childInk, ctx)
        "divider" -> {
            val t = ((n.thickness ?: 1f) * ctx.k).coerceAtLeast(0.5f).dp
            val c = ctx.color(n.color ?: "${childInk.content}/15") ?: Color.Gray
            val pad = parseInsets(n.padding, 0f).scaled(ctx.k)
            if (parent == ParentKind.Row) Box(outer.fillMaxHeight().pad(pad)) { Spacer(Modifier.width(t).fillMaxHeight().background(c)) }
            else Box(outer.fillMaxWidth().pad(pad)) { Spacer(Modifier.fillMaxWidth().height(t).background(c)) }
        }
        else -> Spacer(decorate(n, outer, ctx))
    }
}

@Composable
private fun decorate(n: WNode, outer: Modifier, ctx: PreviewCtx, skipSize: Boolean = false): Modifier {
    var m = outer
    if (!skipSize) {
        when (val w = parseDim(n.width)) {
            Dim.Fill -> m = m.fillMaxWidth(); Dim.Wrap -> m = m.wrapContentWidth()
            is Dim.Fixed -> m = m.width((w.dp * ctx.k).dp); null -> Unit
        }
        when (val h = parseDim(n.height)) {
            Dim.Fill -> m = m.fillMaxHeight(); Dim.Wrap -> m = m.wrapContentHeight()
            is Dim.Fixed -> m = m.height((h.dp * ctx.k).dp); null -> Unit
        }
    }
    val corner = n.corner?.let { (it * ctx.k).dp }
    if (corner != null) m = m.clip(RoundedCornerShape(corner))
    val grad = n.gradient?.mapNotNull { ctx.color(it) }
    if (!grad.isNullOrEmpty()) m = m.gradient(grad, n.angle ?: 90f)
    else n.bg?.let { raw -> ctx.color(raw)?.let { m = m.background(it) } }
    clickFor(n.onClick, ctx)?.let { m = m.then(it) }
    val pad = parseInsets(n.padding, 0f).scaled(ctx.k)
    if (!pad.isZero) m = m.pad(pad)
    return m
}

@Composable
private fun clickFor(el: kotlinx.serialization.json.JsonElement?, ctx: PreviewCtx): Modifier? {
    val actions = parseActions(el)
    if (actions.isEmpty() || ctx.onAction == null) return null
    return Modifier.clickable(remember { MutableInteractionSource() }, ripple()) { ctx.onAction.invoke(actions) }
}

private fun visible(n: WNode, ctx: PreviewCtx) = n.children.filter { it.visibleIf == null || ctx.ev.cond(it.visibleIf) }.take(10)

private fun hAlign(h: HAlign?) = when (h) { HAlign.Center -> Alignment.CenterHorizontally; HAlign.End -> Alignment.End; else -> Alignment.Start }
private fun vAlign(v: VAlign?) = when (v) { VAlign.Center -> Alignment.CenterVertically; VAlign.Bottom -> Alignment.Bottom; else -> Alignment.Top }
private fun boxAlign(n: WNode): Alignment {
    val h = parseH(n.align); val v = parseV(n.valign)
    return when (v) {
        VAlign.Center -> when (h) { HAlign.Center -> Alignment.Center; HAlign.End -> Alignment.CenterEnd; else -> Alignment.CenterStart }
        VAlign.Bottom -> when (h) { HAlign.Center -> Alignment.BottomCenter; HAlign.End -> Alignment.BottomEnd; else -> Alignment.BottomStart }
        else -> when (h) { HAlign.Center -> Alignment.TopCenter; HAlign.End -> Alignment.TopEnd; else -> Alignment.TopStart }
    }
}

@Composable
private fun PColumn(n: WNode, mod: Modifier, ink: PInk, ctx: PreviewCtx) {
    val gap = ((n.gap ?: 0f) * ctx.k).dp
    val v = parseV(n.valign)
    val arrangement = if (gap > 0.dp) Arrangement.spacedBy(gap, vAlign(v)) else when (v) {
        VAlign.Center -> Arrangement.Center; VAlign.Bottom -> Arrangement.Bottom; else -> Arrangement.Top
    }
    Column(mod, verticalArrangement = arrangement, horizontalAlignment = hAlign(parseH(n.align))) {
        visible(n, ctx).forEach { c -> PNode(c, weightMod(c), ParentKind.Column, ink, ctx) }
    }
}

@Composable
private fun PRow(n: WNode, mod: Modifier, ink: PInk, ctx: PreviewCtx) {
    val gap = ((n.gap ?: 0f) * ctx.k).dp
    val h = parseH(n.align)
    val arrangement = if (gap > 0.dp) Arrangement.spacedBy(gap, hAlign(h)) else when (h) {
        HAlign.Center -> Arrangement.Center; HAlign.End -> Arrangement.End; else -> Arrangement.Start
    }
    Row(mod, horizontalArrangement = arrangement, verticalAlignment = vAlign(parseV(n.valign))) {
        visible(n, ctx).forEach { c -> PNode(c, weightMod(c), ParentKind.Row, ink, ctx) }
    }
}

private fun ColumnScope.weightMod(c: WNode): Modifier = if ((c.weight ?: 0f) > 0f) Modifier.weight(1f) else Modifier
private fun RowScope.weightMod(c: WNode): Modifier = if ((c.weight ?: 0f) > 0f) Modifier.weight(1f) else Modifier

@Composable
private fun PBox(n: WNode, mod: Modifier, ink: PInk, ctx: PreviewCtx) {
    Box(mod, contentAlignment = boxAlign(n)) { visible(n, ctx).forEach { PNode(it, Modifier, ParentKind.Box, ink, ctx) } }
}

private val typefaceCache = HashMap<String, FontFamily>()

private fun family(name: String, bold: Boolean): FontFamily = typefaceCache.getOrPut("$name|$bold") {
    FontFamily(Typeface.create(name, if (bold) Typeface.BOLD else Typeface.NORMAL))
}

private fun textStyle(n: WNode, ink: PInk, ctx: PreviewCtx, defaultSize: Float, colorOverride: String? = null, strike: Boolean = false, clock: Boolean = false): TextStyle {
    val f = Fonts.resolve(n.font, n.fontWeight, n.italic)
    val bold = f.weight >= 700
    return TextStyle(
        color = ctx.color(colorOverride ?: n.color) ?: ctx.color(ink.content) ?: Color.Black,
        fontSize = ((n.size ?: defaultSize) * ctx.k).sp,
        fontFamily = family(f.family, bold),
        fontWeight = if (bold) FontWeight.Bold else if (f.weight >= 500 && !clock) FontWeight.Medium else FontWeight.Normal,
        fontStyle = if (f.italic) FontStyle.Italic else FontStyle.Normal,
        textAlign = when (parseH(n.textAlign) ?: if (clock) parseH(n.align) else null) { HAlign.Center -> TextAlign.Center; HAlign.End -> TextAlign.End; else -> TextAlign.Start },
        textDecoration = if (strike) TextDecoration.LineThrough else TextDecoration.None,
        platformStyle = PlatformTextStyle(includeFontPadding = true),
    )
}

private fun iconRes(n: WNode, ctx: PreviewCtx): Int {
    val name = ctx.ev.template(n.icon).trim().lowercase()
    val map = if (n.style?.lowercase() == "outlined") WidgetIcons.outlined else WidgetIcons.filled
    return map[name] ?: WidgetIcons.filled[name] ?: WidgetIcons.filled["circle"]!!
}

@Composable
private fun PIcon(n: WNode, mod: Modifier, ink: PInk, ctx: PreviewCtx) {
    val s = ((n.size ?: 24f) * ctx.k).dp
    Icon(painterResource(iconRes(n, ctx)), null, mod.size(s), tint = ctx.color(n.color) ?: ctx.color(ink.content) ?: Color.Black)
}

private fun fraction(n: WNode, ctx: PreviewCtx): Float {
    val v = ctx.ev.number(n.value) ?: 0.0
    val max = (n.max ?: 100f).toDouble().takeIf { it != 0.0 } ?: 100.0
    return (v / max).toFloat().coerceIn(0f, 1f)
}

@Composable
private fun PProgress(n: WNode, outer: Modifier, ink: PInk, ctx: PreviewCtx) {
    val f = fraction(n, ctx)
    val color = ctx.color(n.color ?: "@primary") ?: Color.Gray
    val trackRaw = n.track ?: (ink.content + "/15").let { if (it.count { c -> c == '/' } > 1) "@onSurface/15" else it }
    val track = ctx.color(trackRaw) ?: Color.LightGray
    if (n.style?.lowercase() == "ring") {
        val d = ((n.size ?: 96f) * ctx.k).dp
        val t = (n.thickness ?: 8f) * ctx.k
        Box(decorate(n.copy(width = null, height = null), outer, ctx).size(d), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = t.dp.toPx()
                val inset = stroke / 2 + 1f
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                if (f > 0f) drawArc(color, -90f, 360f * f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            visible(n, ctx).forEach { PNode(it, Modifier, ParentKind.Box, ink, ctx) }
        }
    } else {
        val t = ((n.thickness ?: 6f) * ctx.k).dp
        val base = decorate(n.copy(height = null), outer, ctx)
        val m = (if (n.width == null) base.fillMaxWidth() else base).height(t)
        Box(m.clip(RoundedCornerShape(50)).background(track)) {
            if (f > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(RoundedCornerShape(50)).background(color))
        }
    }
}

@Composable
private fun PButton(n: WNode, outer: Modifier, ink: PInk, ctx: PreviewCtx) {
    val k = ctx.k
    val style = n.style?.lowercase() ?: "filled"
    val (container, content) = when (style) {
        "tonal" -> (n.color ?: "@secondaryContainer") to (n.contentColor ?: "@onSecondaryContainer")
        "outline", "outlined", "ghost", "text", "plain" -> null to (n.contentColor ?: n.color ?: ink.content)
        else -> (n.color ?: "@primary") to (n.contentColor ?: ContentColors.onColor(n.color ?: "@primary", ctx.palette, "@onPrimary"))
    }
    val contentColor = ctx.color(content) ?: Color.Black
    val hasText = !n.text.isNullOrBlank()
    val hasIcon = !n.icon.isNullOrBlank()
    val h = (36f * k).dp
    val radius = ((n.corner ?: 18f) * k).dp
    val shape = RoundedCornerShape(radius)
    var m = outer
    when (val w = parseDim(n.width)) { Dim.Fill -> m = m.fillMaxWidth(); is Dim.Fixed -> m = m.width((w.dp * k).dp); else -> Unit }
    m = when (val hh = parseDim(n.height)) { is Dim.Fixed -> m.height((hh.dp * k).dp); Dim.Fill -> m.fillMaxHeight(); else -> m.height(h) }
    if (!hasText && parseDim(n.width) == null && (n.weight ?: 0f) <= 0f) m = m.width(h)
    m = m.clip(shape)
    if (style.startsWith("outline")) m = m.border((1.5f).dp, contentColor.copy(alpha = contentColor.alpha * 0.4f), shape)
    container?.let { c -> ctx.color(c)?.let { m = m.background(it) } }
    clickFor(n.onClick, ctx)?.let { m = m.then(it) }
    Row(m.padding(horizontal = if (hasText) (14f * k).dp else 0.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (hasIcon) Icon(painterResource(iconRes(n, ctx)), null, Modifier.size((18f * k).dp), tint = contentColor)
        if (hasIcon && hasText) Spacer(Modifier.width((6f * k).dp))
        if (hasText) Text(
            ctx.ev.template(n.text), maxLines = 1,
            style = TextStyle(
                color = contentColor, fontSize = ((n.size ?: 14f) * k).sp, fontWeight = FontWeight.Medium,
                fontFamily = family(Fonts.resolve(n.font, n.fontWeight ?: JsonPrimitive(500), null).family, false),
                platformStyle = PlatformTextStyle(includeFontPadding = true),
            ),
        )
    }
}

@Composable
private fun PCheck(n: WNode, mod: Modifier, ink: PInk, ctx: PreviewCtx) {
    val key = n.bind
    val on = key != null && Evaluator.truthy(ctx.ev.lookup(key))
    val k = ctx.k
    val clickable = if (key != null && n.onClick == null && ctx.onAction != null) {
        Modifier.clickable { ctx.onAction.invoke(listOf(WAction(op = "toggle", bind = key))) }
    } else Modifier
    Row(mod.then(clickable).padding(vertical = (6f * k).dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(WidgetIcons.filled[if (on) "check_box" else "check_box_outline_blank"]!!), null,
            Modifier.size(((n.size ?: 14f) * 1.5f * k).dp),
            tint = ctx.color(if (on) n.color ?: "@primary" else "${ink.content}/60") ?: Color.Gray,
        )
        Spacer(Modifier.width((10f * k).dp))
        Text(
            ctx.ev.template(n.text), maxLines = n.maxLines ?: 1, overflow = TextOverflow.Ellipsis,
            style = textStyle(n, ink, ctx, 14f, colorOverride = if (on) "${n.contentColor ?: ink.content}/55" else n.contentColor, strike = on),
        )
    }
}

@Composable
private fun PDots(n: WNode, mod: Modifier, ink: PInk, ctx: PreviewCtx) {
    val count = (n.count ?: 3).coerceIn(1, 10)
    val cur = (Evaluator.toNumber(n.bind?.let { ctx.ev.lookup(it) }) ?: 0.0).toInt()
    val d: Dp = ((n.size ?: 6f) * ctx.k).dp
    Row(mod, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d * 0.8f)) {
        for (i in 0 until count) {
            val active = i == cur
            Box(
                Modifier.width(if (active) d * 2.6f else d).height(d).clip(RoundedCornerShape(50))
                    .background(ctx.color(if (active) n.color ?: ink.content else n.track ?: "${ink.content}/30") ?: Color.Gray),
            )
        }
    }
}
