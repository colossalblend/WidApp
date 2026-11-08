package app.widapp.jobs

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.widapp.WidApp

/** Executes one [GenJob] as a foreground job so it keeps running when the app is closed. */
class GenerationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as WidApp
        val id = inputData.getString(KEY_JOB) ?: return Result.success()
        runCatching { setForeground(getForegroundInfo()) }
        app.jobs.run(id)
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val app = applicationContext as WidApp
        val job = app.jobs.get(inputData.getString(KEY_JOB))
        val n = JobNotifications.progress(applicationContext, job, id)
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(JobNotifications.PROGRESS_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(JobNotifications.PROGRESS_ID, n)
    }

    companion object {
        const val KEY_JOB = "job"
        const val TAG = "generation"
    }
}
