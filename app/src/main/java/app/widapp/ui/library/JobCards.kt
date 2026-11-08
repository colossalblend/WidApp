package app.widapp.ui.library

import androidx.compose.foundation.background
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.widapp.R
import app.widapp.jobs.GenJob
import app.widapp.spec.WidgetSize
import app.widapp.ui.components.Kicker
import app.widapp.ui.components.SizeTag
import app.widapp.ui.components.dotGrid
import app.widapp.ui.components.rememberApp

/** A widget that is still being generated in the background, or failed while the user was away. */
@Composable
fun JobCard(job: GenJob) {
    val app = rememberApp()
    val c = MaterialTheme.colorScheme
    if (job.active) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.surfaceContainerLow)
                .dotGrid(c.outlineVariant.copy(alpha = 0.6f)).padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LoadingIndicator(Modifier.size(48.dp), color = c.tertiary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Kicker(stringResource(if (job.kind == "surprise") R.string.job_k_surprise else R.string.job_k_building), color = c.tertiary)
                    Spacer(Modifier.width(8.dp))
                    SizeTag(WidgetSize.of(job.size))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    job.prompt.ifBlank { stringResource(R.string.job_surprise_text) },
                    style = MaterialTheme.typography.bodyMedium, color = c.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { app.jobs.cancel(job.id) }) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_cancel), tint = c.onSurfaceVariant)
            }
        }
    } else {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.errorContainer).padding(18.dp)) {
            Kicker(stringResource(R.string.job_failed), color = c.onErrorContainer)
            Spacer(Modifier.height(6.dp))
            if (job.prompt.isNotBlank()) {
                Text(job.prompt, style = MaterialTheme.typography.bodyMedium, color = c.onErrorContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
            }
            Text(job.error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = c.onErrorContainer.copy(alpha = 0.8f), maxLines = 4, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { app.jobs.retry(job.id) }) { Text(stringResource(R.string.update_retry)) }
                TextButton(onClick = { app.jobs.dismiss(job.id) }) { Text(stringResource(R.string.action_close), color = c.onErrorContainer) }
            }
        }
    }
}
