package app.widapp.jobs

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import app.widapp.MainActivity
import app.widapp.R
import app.widapp.WidApp
import java.util.UUID

object JobNotifications {
    const val PROGRESS_ID = 9100
    private const val CH_WORK = "generation"
    private const val CH_DONE = "generation_done"

    private fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_WORK, ctx.getString(R.string.job_channel_work), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_DONE, ctx.getString(R.string.job_channel_done), NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun allowed(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun open(ctx: Context, designId: String?, req: Int): PendingIntent = PendingIntent.getActivity(
        ctx, req,
        Intent(ctx, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            designId?.let { putExtra(MainActivity.EXTRA_OPEN_DESIGN, it) }
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun progress(ctx: Context, job: GenJob?, workId: UUID): Notification {
        channels(ctx)
        val title = ctx.getString(
            when (job?.kind) {
                "refine" -> R.string.job_refining
                "variant" -> R.string.job_variant
                "surprise" -> R.string.job_surprising
                else -> R.string.job_building
            },
        )
        return NotificationCompat.Builder(ctx, CH_WORK)
            .setSmallIcon(R.drawable.ic_widgets_fill)
            .setContentTitle(title)
            .setContentText(job?.prompt?.take(120)?.ifBlank { null } ?: ctx.getString(R.string.job_progress_hint))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open(ctx, job?.designId, 1))
            .addAction(0, ctx.getString(R.string.action_cancel), WorkManager.getInstance(ctx).createCancelPendingIntent(workId))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** Only when the user is not looking at the app — inside it, the screen itself shows the result. */
    fun done(app: WidApp, job: GenJob, name: String) {
        if (app.isInForeground || !allowed(app)) return
        channels(app)
        val n = NotificationCompat.Builder(app, CH_DONE)
            .setSmallIcon(R.drawable.ic_widgets_fill)
            .setContentTitle(app.getString(if (job.kind == "refine" || job.kind == "variant") R.string.job_done_refine else R.string.job_done_create))
            .setContentText(name)
            .setContentIntent(open(app, job.resultDesignId, job.id.hashCode()))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(job.id.hashCode(), n) }
    }

    fun failed(app: WidApp, job: GenJob) {
        if (app.isInForeground || !allowed(app)) return
        channels(app)
        val n = NotificationCompat.Builder(app, CH_DONE)
            .setSmallIcon(R.drawable.ic_widgets_fill)
            .setContentTitle(app.getString(R.string.job_failed))
            .setContentText(job.error?.take(160))
            .setStyle(NotificationCompat.BigTextStyle().bigText(job.error))
            .setContentIntent(open(app, job.designId, job.id.hashCode()))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(job.id.hashCode(), n) }
    }
}
