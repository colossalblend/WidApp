package app.widapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.widapp.ui.theme.Mono
import app.widapp.ui.theme.MonoBody

private sealed interface Block {
    data class Heading(val level: Int, val text: String) : Block
    data class Bullet(val text: String, val marker: String) : Block
    data class Para(val text: String) : Block
    data class Code(val text: String) : Block
    data class Quote(val text: String) : Block
}

private fun parse(md: String): List<Block> {
    val out = mutableListOf<Block>()
    val para = StringBuilder()
    fun flush() { if (para.isNotBlank()) out += Block.Para(para.toString().trim()); para.clear() }
    val lines = md.replace("\r\n", "\n").lines()
    var i = 0
    while (i < lines.size) {
        val raw = lines[i]; val t = raw.trim()
        when {
            t.startsWith("```") -> {
                flush()
                val code = StringBuilder(); i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) { code.appendLine(lines[i]); i++ }
                out += Block.Code(code.toString().trimEnd())
            }
            t.startsWith("#") -> { flush(); val lvl = t.takeWhile { it == '#' }.length; out += Block.Heading(lvl, t.drop(lvl).trim()) }
            t.matches(Regex("^[-*+]\\s+.*")) -> { flush(); out += Block.Bullet(t.drop(1).trim().removePrefix("[ ] ").removePrefix("[x] "), "•") }
            t.matches(Regex("^\\d+[.)]\\s+.*")) -> { flush(); out += Block.Bullet(t.substringAfter(' ').trim(), t.substringBefore(' ')) }
            t.startsWith(">") -> { flush(); out += Block.Quote(t.trimStart('>', ' ')) }
            t.isEmpty() -> flush()
            else -> { if (para.isNotEmpty()) para.append(' '); para.append(t) }
        }
        i++
    }
    flush()
    return out
}

private val inline = Regex("""\*\*(.+?)\*\*|__(.+?)__|`([^`]+)`|\[([^\]]+)]\(([^)\s]+)\)|(https?://[^\s)]+)|(?<![*\w])\*(?!\s)(.+?)\*|(?<!\w)_(?!\s)(.+?)_(?!\w)""")

private fun inlineText(text: String, link: Color, codeBg: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in inline.findAll(text)) {
        append(text.substring(last, m.range.first))
        val g = m.groupValues
        val linkStyle = TextLinkStyles(SpanStyle(color = link, textDecoration = TextDecoration.Underline))
        when {
            g[1].isNotEmpty() || g[2].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(g[1].ifEmpty { g[2] }) }
            g[3].isNotEmpty() -> withStyle(SpanStyle(fontFamily = Mono, background = codeBg)) { append(" ${g[3]} ") }
            g[4].isNotEmpty() -> withLink(LinkAnnotation.Url(g[5], linkStyle)) { append(g[4]) }
            g[6].isNotEmpty() -> withLink(LinkAnnotation.Url(g[6], linkStyle)) {
                // GitHub compare links are long; show them compactly.
                append(g[6].removePrefix("https://").removePrefix("github.com/").let { if (it.length > 42) it.take(40) + "…" else it })
            }
            g[7].isNotEmpty() || g[8].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[7].ifEmpty { g[8] }) }
        }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

/** Small Markdown renderer for release notes (headings, lists, quotes, code, links, emphasis). */
@Composable
fun Markdown(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    val blocks = remember(text) { parse(text) }
    val c = MaterialTheme.colorScheme
    val t = MaterialTheme.typography
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { b ->
            when (b) {
                is Block.Heading -> Text(
                    inlineText(b.text, c.tertiary, c.surfaceContainerHighest),
                    style = if (b.level <= 2) t.titleLarge else t.titleMedium, color = color,
                    modifier = Modifier.padding(top = 6.dp),
                )
                is Block.Para -> Text(inlineText(b.text, c.tertiary, c.surfaceContainerHighest), style = t.bodyLarge, color = color.copy(alpha = 0.9f))
                is Block.Bullet -> Row {
                    if (b.marker == "•") Box(Modifier.padding(top = 10.dp, end = 12.dp).size(6.dp).clip(CircleShape).background(c.tertiary))
                    else Text(b.marker, style = MonoBody, color = c.tertiary, modifier = Modifier.width(26.dp))
                    Text(inlineText(b.text, c.tertiary, c.surfaceContainerHighest), style = t.bodyLarge, color = color.copy(alpha = 0.9f))
                }
                is Block.Quote -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(3.dp).padding(vertical = 2.dp).background(c.outlineVariant))
                    Spacer(Modifier.width(12.dp))
                    Text(inlineText(b.text, c.tertiary, c.surfaceContainerHighest), style = t.bodyMedium.copy(fontStyle = FontStyle.Italic), color = c.onSurfaceVariant)
                }
                is Block.Code -> Text(
                    b.text, style = MonoBody, color = color,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surfaceContainerHighest).padding(12.dp),
                )
            }
        }
    }
}
