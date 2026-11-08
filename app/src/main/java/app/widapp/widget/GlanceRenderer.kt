package app.widapp.widget

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.layout.wrapContentWidth
import androidx.glance.text.FontFamily
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.widapp.MainActivity
import app.widapp.R
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
import app.widapp.spec.scaleFor
import app.widapp.widget.render.Bitmaps
import app.widapp.widget.render.ClockLayouts
import app.widapp.widget.render.WidgetIcons
import androidx.glance.color.ColorProvider as DayNightProvider

private enum class Parent { Row, Column, Box }

/** Background and default content colour inherited down the tree. */
private data class Ink(val bg: String?, val content: String)

/** Renders a [WidgetSpec] with Glance. Mirrors [app.widapp.ui.components.WidgetPreview] for the in-app canvas. */
class GlanceRenderer(
    private val context: Context,
    private val spec: WidgetSpec,
    state: Map<String, kotlinx.serialization.json.JsonPrimitive>,
    data: DataSnapshot,
    private val palette: WidgetPalette,
    private val size: DpSize,
) {
    private val ev = Evaluator(state, data)
    private val wsize = WidgetSize.of(spec.size)
    private val k = scaleFor(wsize, size.width.value, size.height.value)
    private val fixedDark: Boolean? = when (spec.theme.lowercase()) { "light" -> false; "dark" -> true; else -> null }
    private val nowDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val density = context.resources.displayMetrics.density

    private fun provider(raw: String?): ColorProvider? {
        val dn = Colors.parse(ev.template(raw), palette) ?: return null
        return when (fixedDark) {
            null -> DayNightProvider(day = Color(dn.day), night = Color(dn.night))
            true -> ColorProvider(Color(dn.night))
            false -> ColorProvider(Color(dn.day))
        }
    }

    private fun concrete(raw: String?): Int? = Colors.parse(ev.template(raw), palette)?.pick(fixedDark ?: nowDark)
    private fun px(dp: Float) = (dp * density).toInt().coerceAtLeast(1)

    @Composable
    fun Root() {
        val bgRaw = spec.background?.let { ev.template(it) }
        val gradient = spec.gradient?.mapNotNull { concrete(it) }?.takeIf { it.isNotEmpty() }
        var mod = GlanceModifier.fillMaxSize()
        mod = when {
            gradient != null -> mod.background(
                ImageProvider(Bitmaps.gradient(gradient, spec.angle ?: 90f, px(size.width.value), px(size.height.value))),
                contentScale = ContentScale.FillBounds,
            )
            bgRaw != null -> provider(bgRaw)?.let { mod.background(it) } ?: mod
            else -> mod.background(provider("@surfaceContainer")!!)
        }
        mod = mod.cornerRadius((spec.corner ?: 24f).dp)
        mod = mod.pad(parseInsets(spec.padding, 16f).scaled(k))
        val effectiveBg = bgRaw ?: spec.gradient?.firstOrNull() ?: "@surfaceContainer"
        val ink = Ink(effectiveBg, ContentColors.onColor(effectiveBg, palette, "@onSurface"))
        Box(mod) {
            val root = spec.root
            val outer = GlanceModifier
                .let { if (root.width == null) it.fillMaxWidth() else it }
                .let { if (root.height == null) it.fillMaxHeight() else it }
            Node(root, outer, Parent.Box, ink)
        }
    }

    // ---------- Node dispatch ----------

    @Composable
    private fun Node(n: WNode, outer: GlanceModifier, parent: Parent, ink: Ink) {
        if (n.visibleIf != null && !ev.cond(n.visibleIf)) return
        val bgRaw = n.bg?.let { ev.template(it) }?.takeIf { it.isNotBlank() }
        val childInk = if (bgRaw != null || n.gradient != null) {
            val b = bgRaw ?: n.gradient?.firstOrNull()
            Ink(b, ContentColors.onColor(b, palette, ink.content))
        } else ink
        when (n.type) {
            "column" -> ColumnNode(n, decorate(n, outer, parent), childInk)
            "row" -> RowNode(n, decorate(n, outer, parent), childInk)
            "box" -> BoxNode(n, decorate(n, outer, parent), childInk)
            "pages" -> PagesNode(n, decorate(n, outer, parent), childInk)
            "text" -> TextNode(n, decorate(n, outer, parent), childInk)
            "clock" -> ClockNode(n, decorate(n, outer, parent), childInk)
            "icon" -> IconNode(n, decorate(n, outer, parent), childInk)
            "progress" -> ProgressNode(n, outer, parent, childInk)
            "button" -> ButtonNode(n, outer, parent, ink)
            "check" -> CheckNode(n, decorate(n, outer, parent), childInk)
            "dots" -> DotsNode(n, decorate(n, outer, parent), childInk)
            "divider" -> DividerNode(n, outer, parent, childInk)
            else -> Spacer(decorate(n, outer, parent))
        }
    }

    /** Size, background, corners, padding and click handling shared by every node. */
    private fun decorate(n: WNode, outer: GlanceModifier, parent: Parent, defaultPadding: Float = 0f): GlanceModifier {
        var m = outer
        when (val w = parseDim(n.width)) {
            Dim.Fill -> m = m.fillMaxWidth()
            Dim.Wrap -> m = m.wrapContentWidth()
            is Dim.Fixed -> m = m.width((w.dp * k).dp)
            null -> if (n.type == "spacer" && parent == Parent.Row && n.weight == null && n.width == null) Unit
        }
        when (val h = parseDim(n.height)) {
            Dim.Fill -> m = m.fillMaxHeight()
            Dim.Wrap -> m = m.wrapContentHeight()
            is Dim.Fixed -> m = m.height((h.dp * k).dp)
            null -> Unit
        }
        val grad = n.gradient?.mapNotNull { concrete(it) }?.takeIf { it.isNotEmpty() }
        if (grad != null) {
            m = m.background(ImageProvider(Bitmaps.gradient(grad, n.angle ?: 90f, 192, 192)), contentScale = ContentScale.FillBounds)
        } else n.bg?.let { raw -> provider(raw)?.let { m = m.background(it) } }
        n.corner?.let { m = m.cornerRadius((it * k).dp) }
        val pad = parseInsets(n.padding, defaultPadding).scaled(k)
        if (!pad.isZero) m = m.pad(pad)
        action(n.onClick)?.let { m = m.clickable(it) }
        return m
    }

    private fun GlanceModifier.pad(i: Insets) = padding(start = i.start.dp, top = i.top.dp, end = i.end.dp, bottom = i.bottom.dp)

    // ---------- Containers ----------

    private fun visibleChildren(n: WNode) = n.children.filter { it.visibleIf == null || ev.cond(it.visibleIf) }.take(10)

    @Composable
    private fun ColumnNode(n: WNode, mod: GlanceModifier, ink: Ink) {
        val kids = visibleChildren(n)
        val gap = (n.gap ?: 0f) * k
        val spacers = gap > 0 && kids.size * 2 - 1 <= 10
        Column(mod, verticalAlignment = vAlign(parseV(n.valign)), horizontalAlignment = hAlign(parseH(n.align))) {
            kids.forEachIndexed { i, c ->
                if (i > 0 && spacers) Spacer(GlanceModifier.height(gap.dp))
                val base = weightMod(c)
                if (i > 0 && gap > 0 && !spacers) {
                    Box(base.padding(top = gap.dp)) { Node(c, GlanceModifier, Parent.Column, ink) }
                } else Node(c, base, Parent.Column, ink)
            }
        }
    }

    @Composable
    private fun RowNode(n: WNode, mod: GlanceModifier, ink: Ink) {
        val kids = visibleChildren(n)
        val gap = (n.gap ?: 0f) * k
        val spacers = gap > 0 && kids.size * 2 - 1 <= 10
        Row(mod, horizontalAlignment = hAlign(parseH(n.align)), verticalAlignment = vAlign(parseV(n.valign))) {
            kids.forEachIndexed { i, c ->
                if (i > 0 && spacers) Spacer(GlanceModifier.width(gap.dp))
                val base = weightMod(c)
                if (i > 0 && gap > 0 && !spacers) {
                    Box(base.padding(start = gap.dp)) { Node(c, GlanceModifier, Parent.Row, ink) }
                } else Node(c, base, Parent.Row, ink)
            }
        }
    }

    @Composable
    private fun ColumnScope.weightMod(c: WNode): GlanceModifier =
        if ((c.weight ?: 0f) > 0f) GlanceModifier.defaultWeight() else GlanceModifier

    @Composable
    private fun RowScope.weightMod(c: WNode): GlanceModifier =
        if ((c.weight ?: 0f) > 0f) GlanceModifier.defaultWeight() else GlanceModifier

    @Composable
    private fun BoxNode(n: WNode, mod: GlanceModifier, ink: Ink) {
        Box(mod, contentAlignment = Alignment(hAlign(parseH(n.align)), vAlign(parseV(n.valign)))) {
            visibleChildren(n).forEach { Node(it, GlanceModifier, Parent.Box, ink) }
        }
    }

    @Composable
    private fun PagesNode(n: WNode, mod: GlanceModifier, ink: Ink) {
        val idx = (Evaluator.toNumber(n.bind?.let { ev.lookup(it) }) ?: 0.0).toInt()
        val page = n.children.getOrNull(idx.coerceIn(0, (n.children.size - 1).coerceAtLeast(0)))
        Box(mod, contentAlignment = Alignment(hAlign(parseH(n.align)), vAlign(parseV(n.valign)))) {
            if (page != null) Node(page, GlanceModifier, Parent.Box, ink)
        }
    }

    // ---------- Leaves ----------

    private fun textStyle(n: WNode, ink: Ink, defaultSize: Float, colorOverride: String? = null, strike: Boolean = false): TextStyle {
        val f = Fonts.resolve(n.font, n.fontWeight, n.italic)
        return TextStyle(
            color = provider(colorOverride ?: n.color) ?: provider(ink.content)!!,
            fontSize = ((n.size ?: defaultSize) * k).sp,
            fontWeight = if (f.weight >= 700) FontWeight.Bold else if (f.weight >= 500) FontWeight.Medium else FontWeight.Normal,
            fontStyle = if (f.italic) FontStyle.Italic else FontStyle.Normal,
            textAlign = when (parseH(n.textAlign)) { HAlign.Center -> TextAlign.Center; HAlign.End -> TextAlign.End; else -> TextAlign.Start },
            textDecoration = if (strike) TextDecoration.LineThrough else TextDecoration.None,
            fontFamily = FontFamily(f.family),
        )
    }

    @Composable
    private fun TextNode(n: WNode, mod: GlanceModifier, ink: Ink) {
        val strike = n.strikeIf != null && ev.cond(n.strikeIf)
        Text(ev.template(n.text), mod, textStyle(n, ink, 14f, strike = strike), maxLines = n.maxLines ?: Int.MAX_VALUE)
    }

    @Composable
    private fun ClockNode(n: WNode, mod: GlanceModifier, ink: Ink) {
        val f = Fonts.resolve(n.font, n.fontWeight, n.italic)
        val rv = RemoteViews(context.packageName, ClockLayouts.forFont(f.family, f.weight >= 700))
        rv.setTextViewTextSize(R.id.clock, TypedValue.COMPLEX_UNIT_SP, (n.size ?: 32f) * k)
        val dn = Colors.parse(ev.template(n.color ?: ink.content), palette)
        if (dn != null) {
            if (fixedDark == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) rv.setColorInt(R.id.clock, "setTextColor", dn.day, dn.night)
            else rv.setTextColor(R.id.clock, dn.pick(fixedDark ?: nowDark))
        }
        val fmt = n.format?.takeIf { it.isNotBlank() } ?: if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
        rv.setCharSequence(R.id.clock, "setFormat12Hour", fmt)
        rv.setCharSequence(R.id.clock, "setFormat24Hour", fmt)
        n.tz?.takeIf { it.isNotBlank() }?.let { rv.setString(R.id.clock, "setTimeZone", it) }
        val g = when (parseH(n.textAlign) ?: parseH(n.align)) { HAlign.Center -> Gravity.CENTER_HORIZONTAL; HAlign.End -> Gravity.END; else -> Gravity.START }
        rv.setInt(R.id.clock, "setGravity", g or Gravity.CENTER_VERTICAL)
        AndroidRemoteViews(rv, mod)
    }

    private fun iconRes(n: WNode): Int {
        val name = ev.template(n.icon).trim().lowercase()
        val map = if (n.style?.lowercase() == "outlined") WidgetIcons.outlined else WidgetIcons.filled
        return map[name] ?: WidgetIcons.filled[name] ?: WidgetIcons.filled["circle"]!!
    }

    @Composable
    private fun IconNode(n: WNode, mod: GlanceModifier, ink: Ink) {
        val s = (n.size ?: 24f) * k
        Image(
            ImageProvider(iconRes(n)), contentDescription = null,
            modifier = mod.size(s.dp),
            colorFilter = ColorFilter.tint(provider(n.color) ?: provider(ink.content)!!),
        )
    }

    private fun fraction(n: WNode): Float {
        val v = ev.number(n.value) ?: 0.0
        val max = (n.max ?: 100f).toDouble().takeIf { it != 0.0 } ?: 100.0
        return (v / max).toFloat().coerceIn(0f, 1f)
    }

    @Composable
    private fun ProgressNode(n: WNode, outer: GlanceModifier, parent: Parent, ink: Ink) {
        val f = fraction(n)
        val color = n.color ?: "@primary"
        val track = n.track ?: (ink.content + "/15").let { if (it.count { c -> c == '/' } > 1) "@onSurface/15" else it }
        if (n.style?.lowercase() == "ring") {
            val d = (n.size ?: 96f) * k
            val t = (n.thickness ?: 8f) * k
            val bmp = Bitmaps.ring(px(d), t * density, f, concrete(color) ?: 0xFF888888.toInt(), concrete(track) ?: 0x22888888)
            Box(decorate(n.copy(width = null, height = null), outer, parent).size(d.dp), contentAlignment = Alignment.Center) {
                Image(ImageProvider(bmp), contentDescription = null, modifier = GlanceModifier.fillMaxSize())
                visibleChildren(n).forEach { Node(it, GlanceModifier, Parent.Box, ink) }
            }
        } else {
            val t = (n.thickness ?: 6f) * k
            val rv = RemoteViews(context.packageName, R.layout.widget_bar)
            rv.setProgressBar(R.id.bar, 1000, (f * 1000).toInt(), false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val c = Colors.parse(ev.template(color), palette)
                val tr = Colors.parse(ev.template(track), palette)
                if (c != null) {
                    if (fixedDark == null) rv.setColorStateList(R.id.bar, "setProgressTintList", ColorStateList.valueOf(c.day), ColorStateList.valueOf(c.night))
                    else rv.setColorStateList(R.id.bar, "setProgressTintList", ColorStateList.valueOf(c.pick(fixedDark)))
                }
                if (tr != null) {
                    if (fixedDark == null) rv.setColorStateList(R.id.bar, "setProgressBackgroundTintList", ColorStateList.valueOf(tr.day), ColorStateList.valueOf(tr.night))
                    else rv.setColorStateList(R.id.bar, "setProgressBackgroundTintList", ColorStateList.valueOf(tr.pick(fixedDark)))
                }
            }
            val base = decorate(n.copy(height = null), outer, parent)
            val m = if (n.width == null) base.fillMaxWidth() else base
            AndroidRemoteViews(rv, m.height(t.dp))
        }
    }

    @Composable
    private fun ButtonNode(n: WNode, outer: GlanceModifier, parent: Parent, ink: Ink) {
        val style = n.style?.lowercase() ?: "filled"
        val (container, content) = when (style) {
            "tonal" -> (n.color ?: "@secondaryContainer") to (n.contentColor ?: "@onSecondaryContainer")
            "outline", "outlined" -> null to (n.contentColor ?: n.color ?: ink.content)
            "ghost", "text", "plain" -> null to (n.contentColor ?: n.color ?: ink.content)
            else -> (n.color ?: "@primary") to (n.contentColor ?: ContentColors.onColor(n.color ?: "@primary", palette, "@onPrimary"))
        }
        val hasText = !n.text.isNullOrBlank()
        val hasIcon = !n.icon.isNullOrBlank()
        val h = 36f * k
        val radius = (n.corner ?: 18f) * k
        var m = outer
        when (val w = parseDim(n.width)) { Dim.Fill -> m = m.fillMaxWidth(); is Dim.Fixed -> m = m.width((w.dp * k).dp); else -> Unit }
        m = when (val hh = parseDim(n.height)) { is Dim.Fixed -> m.height((hh.dp * k).dp); Dim.Fill -> m.fillMaxHeight(); else -> m.height(h.dp) }
        if (!hasText) m = if (parseDim(n.width) == null && (n.weight ?: 0f) <= 0f) m.width(h.dp) else m
        val outlineInset = style.startsWith("outline")
        if (outlineInset) {
            m = m.background(provider("$content/40")!!)
        } else container?.let { c -> provider(c)?.let { m = m.background(it) } }
        m = m.cornerRadius(radius.dp)
        action(n.onClick)?.let { m = m.clickable(it) }
        val inner = GlanceModifier.fillMaxSize().let {
            if (outlineInset) it.padding(1.5f.dp).let { x -> provider(ink.bg ?: "@surface")?.let { p -> x.background(p).cornerRadius((radius - 1.5f).dp) } ?: x } else it
        }
        Box(m, contentAlignment = Alignment.Center) {
            Row(inner.padding(horizontal = if (hasText) (14f * k).dp else 0.dp), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                if (hasIcon) {
                    Image(ImageProvider(iconRes(n)), null, GlanceModifier.size((18f * k).dp), colorFilter = ColorFilter.tint(provider(content)!!))
                }
                if (hasIcon && hasText) Spacer(GlanceModifier.width((6f * k).dp))
                if (hasText) {
                    Text(
                        ev.template(n.text), maxLines = 1,
                        style = TextStyle(
                            color = provider(content)!!, fontSize = ((n.size ?: 14f) * k).sp, fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily(Fonts.resolve(n.font, n.fontWeight ?: kotlinx.serialization.json.JsonPrimitive(500), null).family),
                        ),
                    )
                }
            }
        }
    }

    @Composable
    private fun CheckNode(n: WNode, mod: GlanceModifier, ink: Ink) {
        val key = n.bind
        val on = key != null && Evaluator.truthy(ev.lookup(key))
        val color = n.color ?: "@primary"
        val clickMod = if (key != null && n.onClick == null) mod.clickable(callback("""[{"do":"toggle","bind":"$key"}]""")) else mod
        Row(clickMod.padding(vertical = (6f * k).dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                ImageProvider(WidgetIcons.filled[if (on) "check_box" else "check_box_outline_blank"]!!), null,
                GlanceModifier.size(((n.size ?: 14f) * 1.5f * k).dp),
                colorFilter = ColorFilter.tint(provider(if (on) color else "${ink.content}/60") ?: provider(ink.content)!!),
            )
            Spacer(GlanceModifier.width((10f * k).dp))
            Text(
                ev.template(n.text), maxLines = n.maxLines ?: 1,
                style = textStyle(n, ink, 14f, colorOverride = if (on) "${n.contentColor ?: ink.content}/55" else n.contentColor, strike = on),
            )
        }
    }

    @Composable
    private fun DotsNode(n: WNode, mod: GlanceModifier, ink: Ink) {
        val count = (n.count ?: 3).coerceIn(1, 10)
        val cur = (Evaluator.toNumber(n.bind?.let { ev.lookup(it) }) ?: 0.0).toInt()
        val d = (n.size ?: 6f) * k
        Row(mod, verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until count) {
                val active = i == cur
                Box(GlanceModifier.padding(end = if (i < count - 1) (d * 0.8f).dp else 0.dp)) {
                    Spacer(
                        GlanceModifier.width((if (active) d * 2.6f else d).dp).height(d.dp)
                            .background(provider(if (active) n.color ?: ink.content else n.track ?: "${ink.content}/30")!!)
                            .cornerRadius((d / 2).dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun DividerNode(n: WNode, outer: GlanceModifier, parent: Parent, ink: Ink) {
        val t = ((n.thickness ?: 1f) * k).coerceAtLeast(0.5f)
        val c = provider(n.color ?: "${ink.content}/15") ?: provider("@outlineVariant")!!
        val pad = parseInsets(n.padding, 0f).scaled(k)
        val m = if (parent == Parent.Row) outer.fillMaxHeight().pad(pad).let { it } else outer.fillMaxWidth().pad(pad)
        Box(m) {
            Spacer(
                (if (parent == Parent.Row) GlanceModifier.width(t.dp).fillMaxHeight() else GlanceModifier.fillMaxWidth().height(t.dp)).background(c),
            )
        }
    }

    // ---------- Actions ----------

    private fun callback(json: String): Action =
        actionRunCallback<WidgetActionCallback>(actionParametersOf(WidgetActionCallback.KEY to json))

    private fun action(el: kotlinx.serialization.json.JsonElement?): Action? {
        val actions = parseActions(el)
        if (actions.isEmpty()) return null
        actions.firstOrNull { it.op.equals("open", true) }?.let { return openAction(it) }
        return callback(el.toString())
    }

    private fun openAction(a: WAction): Action {
        val value = a.value?.content?.let { ev.template(it) }?.trim().orEmpty()
        val intent: Intent? = when (a.target?.lowercase()) {
            "alarm", "alarms", "clock" -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
            "timer", "timers" -> Intent(AlarmClock.ACTION_SHOW_TIMERS)
            "calendar" -> Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build())
            "camera" -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            "dialer", "phone" -> Intent(Intent.ACTION_DIAL)
            "settings" -> Intent(Settings.ACTION_SETTINGS)
            "music" -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
            "maps", "map" -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(value)))
            "url", "web", "link" -> if (value.startsWith("http")) Intent(Intent.ACTION_VIEW, Uri.parse(value)) else null
            "package", "apps", "launch" -> if (value.isNotEmpty()) context.packageManager.getLaunchIntentForPackage(value) else null
            else -> null
        }
        val target = (intent ?: Intent(context, MainActivity::class.java)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return actionStartActivity(target)
    }

    private fun hAlign(h: HAlign?) = when (h) { HAlign.Center -> Alignment.Horizontal.CenterHorizontally; HAlign.End -> Alignment.Horizontal.End; else -> Alignment.Horizontal.Start }
    private fun vAlign(v: VAlign?) = when (v) { VAlign.Center -> Alignment.Vertical.CenterVertically; VAlign.Bottom -> Alignment.Vertical.Bottom; else -> Alignment.Vertical.Top }

    @Suppress("unused") private val keep: ActionParameters? = null
}
