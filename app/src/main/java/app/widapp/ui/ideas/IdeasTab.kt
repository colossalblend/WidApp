package app.widapp.ui.ideas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.widapp.R
import app.widapp.spec.Samples
import app.widapp.spec.StateOps
import app.widapp.spec.WidgetSize
import app.widapp.ui.components.Kicker
import app.widapp.ui.components.ScreenHeader
import app.widapp.ui.components.ShapeBadge
import app.widapp.ui.components.SizeTag
import app.widapp.ui.components.WidgetThumb
import app.widapp.ui.components.dotGrid
import app.widapp.ui.components.rememberGraph
import kotlinx.serialization.json.JsonPrimitive

@Composable
fun IdeasTab(onPrompt: (String, String) -> Unit, onSurprise: () -> Unit, onOpen: (String) -> Unit) {
    val graph = rememberGraph()
    val samples = remember { Samples.parsed() }
    var category by rememberSaveable { mutableStateOf("ai") }
    val c = MaterialTheme.colorScheme

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 14.dp,
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            ScreenHeader(kicker = stringResource(R.string.ideas_kicker), title = stringResource(R.string.ideas_title))
        }
        item(span = StaggeredGridItemSpan.FullLine) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.inverseSurface).clickable(onClick = onSurprise).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShapeBadge(R.drawable.ic_casino, shape = MaterialShapes.SoftBurst, container = c.tertiary, content = c.onTertiary, size = 56.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.surprise_title), style = MaterialTheme.typography.titleMedium, color = c.inverseOnSurface)
                    Text(stringResource(R.string.surprise_body), style = MaterialTheme.typography.bodyMedium, color = c.inverseOnSurface.copy(alpha = 0.75f))
                }
                Icon(painterResource(R.drawable.ic_arrow_forward), null, tint = c.inverseOnSurface)
            }
        }
        item(span = StaggeredGridItemSpan.FullLine) {
            Column {
                Spacer(Modifier.height(8.dp))
                Kicker(stringResource(R.string.ideas_ready))
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.ideas_ready_hint), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            }
        }
        items(samples, key = { it.first }, span = { if (WidgetSize.of(it.second.size).isWide) StaggeredGridItemSpan.FullLine else StaggeredGridItemSpan.SingleLane }) { (id, spec) ->
            val state = remember { mutableStateMapOf<String, JsonPrimitive>() }
            val size = WidgetSize.of(spec.size)
            Column {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.surfaceContainerLow)
                        .dotGrid(c.outlineVariant.copy(alpha = 0.7f)).padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.widthIn(max = if (size.isWide) 360.dp else 200.dp)) {
                        WidgetThumb(spec, "sample-$id", state = state, onAction = { acts ->
                            val next = StateOps.apply(spec.state + state, acts, null)
                            state.clear(); state.putAll(next)
                        })
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                        val d = graph.designs.create(spec, "", null, kind = "sample"); onOpen(d.id)
                    }.padding(horizontal = 6.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(spec.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1)
                    SizeTag(size)
                    Spacer(Modifier.width(8.dp))
                    Icon(painterResource(R.drawable.ic_add), stringResource(R.string.action_add), tint = c.tertiary)
                }
            }
        }
        item(span = StaggeredGridItemSpan.FullLine) {
            Column {
                Spacer(Modifier.height(12.dp))
                Kicker(stringResource(R.string.ideas_prompts))
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IdeaPrompts.categories.forEach { (key, label) ->
                        FilterChip(category == key, { category = key }, { Text(label) })
                    }
                }
            }
        }
        items(IdeaPrompts.all.filter { it.category == category }, span = { StaggeredGridItemSpan.FullLine }) { idea ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.surfaceContainer)
                    .clickable { onPrompt(idea.text, idea.size) }.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(idea.text, style = MaterialTheme.typography.bodyLarge, color = c.onSurface, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                SizeTag(WidgetSize.of(idea.size))
            }
        }
    }
}
