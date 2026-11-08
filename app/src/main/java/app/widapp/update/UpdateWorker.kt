package app.widapp.update

import android.Manifest
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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.widapp.MainActivity
import app.widapp.R
import app.widapp.WidApp
import java.util.concurrent.TimeUnit

/** Daily background check; posts a quiet notification only if the user opted in. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as WidApp
        val s = app.graph.settings.value
        if (!s.updateAutoCheck) return Result.success()
        val list = runCatching { app.updates.fetch() }.getOrElse { return Result.retry() }
        val newest = app.updates.newest(list, s.updateBeta) ?: return Result.success()
        if (newest.version == s.skippedVersion || !s.updateNotify) return Result.success()
        notify(applicationContext, newest)
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "updates"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("widapp-updates", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        fun notify(context: Context, r: ReleaseInfo) {
            if (!canNotify(context)) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.update_channel), NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(
                context, 7,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_UPDATES, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(context.getString(R.string.update_notif_title, r.version))
                .setContentText(r.notes.lineSequence().map { it.trim().trimStart('#', '-', '*', ' ') }.firstOrNull { it.isNotBlank() } ?: context.getString(R.string.update_notif_body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(42, n) }
        }
    }
}
