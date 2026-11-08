package app.widapp.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.widapp.R
import app.widapp.data.WidgetDesign
import app.widapp.spec.WidgetSize
import app.widapp.ui.components.Kicker
import app.widapp.ui.components.ScreenHeader
import app.widapp.ui.components.ShapeBadge
import app.widapp.ui.components.SizeTag
import app.widapp.ui.components.WidgetThumb
import app.widapp.ui.components.dotGrid
import app.widapp.ui.components.rememberGraph
import app.widapp.ui.theme.MonoLabel

@Composable
fun LibraryTab(onOpen: (String) -> Unit, onCreate: () -> Unit, onConnect: () -> Unit, onIdeas: () -> Unit) {
    val graph = rememberGraph()
    val library by graph.designs.flow.collectAsState()
    val providers by graph.providers.flow.collectAsState()
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val designs = library.designs
        .filter { filter == null || (filter == "fav" && it.favorite) || it.spec.size == filter }
        .sortedWith(compareByDescending<WidgetDesign> { it.favorite }.thenByDescending { it.updatedAt })
    val sizesUsed = remember(library) { library.designs.map { it.spec.size }.distinct().sortedBy { WidgetSize.of(it).ordinal } }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 14.dp,
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            ScreenHeader(
                kicker = "WidApp · " + pluralStringResource(R.plurals.widgets_count, library.designs.size, library.designs.size),
                title = stringResource(R.string.library_title),
            )
        }
        if (providers.providers.isEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) { ConnectCard(onConnect) }
        }
        if (library.designs.size > 3) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(filter == null, { filter = null }, { Text(stringResource(R.string.filter_all)) })
                    FilterChip(filter == "fav", { filter = if (filter == "fav") null else "fav" }, { Text(stringResource(R.string.filter_favorites)) })
                    sizesUsed.take(4).forEach { s ->
                        FilterChip(filter == s, { filter = if (filter == s) null else s }, { Text(WidgetSize.of(s).label, style = MonoLabel) })
                    }
                }
            }
        }
        if (library.designs.isEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) { EmptyLibrary(onCreate, onIdeas) }
        }
        items(designs, key = { it.id }, span = { if (WidgetSize.of(it.spec.size).isWide) StaggeredGridItemSpan.FullLine else StaggeredGridItemSpan.SingleLane }) { d ->
            DesignCard(d, onClick = { onOpen(d.id) })
        }
    }
}

@Composable
fun DesignCard(d: WidgetDesign, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val size = WidgetSize.of(d.spec.size)
    Column(modifier.clip(RoundedCornerShape(28.dp)).clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.surfaceContainerLow)
                .dotGrid(c.outlineVariant.copy(alpha = 0.7f))
                .padding(if (size.isWide) 18.dp else 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val maxW = if (size.isWide) 360.dp else if (size.cols == 1) 96.dp else 200.dp
            Box(Modifier.widthIn(max = maxW)) { WidgetThumb(d.spec, d.id) }
        }
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (d.favorite) {
                Icon(painterResource(R.drawable.ic_star_fill), null, Modifier.size(14.dp), tint = c.tertiary)
                Spacer(Modifier.width(4.dp))
            }
            Text(d.name, style = MaterialTheme.typography.titleSmall, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            SizeTag(size)
        }
    }
}

@Composable
private fun ConnectCard(onConnect: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.tertiaryContainer).clickable(onClick = onConnect).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShapeBadge(R.drawable.ic_key, shape = MaterialShapes.Cookie9Sided, container = c.tertiary, content = c.onTertiary, size = 52.dp, iconSize = 24.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.connect_title), style = MaterialTheme.typography.titleMedium, color = c.onTertiaryContainer)
            Spacer(Modifier.height(2.dp))
            Text(stringResource(R.string.connect_body), style = MaterialTheme.typography.bodyMedium, color = c.onTertiaryContainer.copy(alpha = 0.8f))
        }
        Icon(painterResource(R.drawable.ic_chevron_right), null, tint = c.onTertiaryContainer)
    }
}

@Composable
private fun EmptyLibrary(onCreate: () -> Unit, onIdeas: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(c.surfaceContainerLow).dotGrid(c.outlineVariant.copy(alpha = 0.6f)).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            ShapeBadge(R.drawable.ic_widgets_fill, shape = MaterialShapes.Clover4Leaf, container = c.secondaryContainer, content = c.onSecondaryContainer, size = 112.dp, iconSize = 44.dp)
        }
        Spacer(Modifier.height(20.dp))
        Kicker(stringResource(R.string.empty_kicker))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineMedium, color = c.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.empty_body), style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onCreate, colors = ButtonDefaults.buttonColors(containerColor = c.primary)) { Text(stringResource(R.string.action_describe)) }
            OutlinedButton(onClick = onIdeas) { Text(stringResource(R.string.action_gallery)) }
        }
    }
}
