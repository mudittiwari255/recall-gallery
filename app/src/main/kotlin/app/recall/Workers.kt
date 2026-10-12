package app.recall

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object Work {
    const val DOWNLOAD = "model-download"
    const val INDEX = "photo-index"
    const val INDEX_PERIODIC = "photo-index-periodic"
    private const val CHANNEL = "background"

    fun startDownload(context: Context) {
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(DOWNLOAD, ExistingWorkPolicy.KEEP, req)
    }

    fun startIndexing(context: Context) {
        val req = OneTimeWorkRequestBuilder<IndexWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(INDEX, ExistingWorkPolicy.KEEP, req)
    }

    fun stopIndexing(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(INDEX)
    }

    /** Picks up new photos a few times a day while charging. */
    fun schedulePeriodic(context: Context) {
        val req = PeriodicWorkRequestBuilder<IndexWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresCharging(true).setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(INDEX_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun foregroundInfo(context: Context, id: Int, title: String, text: String, progress: Int, max: Int): ForegroundInfo {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Background work", NotificationManager.IMPORTANCE_LOW))
        }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(max, progress, max == 0)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, n)
        }
    }
}

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun getForegroundInfo() =
        Work.foregroundInfo(applicationContext, 1, "Downloading search model", "Starting…", 0, 0)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as RecallApp
        runCatching { setForeground(getForegroundInfo()) }
        var lastPct = -1
        try {
            app.models.download(isCancelled = { isStopped }) { done, total ->
                val pct = if (total > 0) (done * 100 / total).toInt() else 0
                if (pct != lastPct) {
                    lastPct = pct
                    setProgressAsync(workDataOf("done" to done, "total" to total))
                    runCatching {
                        setForegroundAsync(
                            Work.foregroundInfo(
                                applicationContext, 1, "Downloading search model",
                                "${done / 1_000_000} of ${total / 1_000_000} MB", pct, 100,
                            ),
                        )
                    }
                }
            }
            Work.startIndexing(applicationContext)
            Work.schedulePeriodic(applicationContext)
            Result.success()
        } catch (e: Exception) {
            if (isStopped) Result.failure() else Result.retry()
        }
    }
}

class IndexWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun getForegroundInfo() =
        Work.foregroundInfo(applicationContext, 2, "Indexing photos", "Preparing…", 0, 0)

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val app = applicationContext as RecallApp
        if (!app.models.isReady || !app.hasPhotoPermission()) return@withContext Result.success()
        runCatching { setForeground(getForegroundInfo()) }

        val photos = app.media.loadAll()
        app.db.deleteMissing(photos.mapTo(HashSet()) { it.id })
        val processed = app.db.processed()
        val todo = photos.filter { processed[it.id] != it.modified }
        val total = photos.size
        var done = total - todo.size
        val budget = app.settings.photoDetail
        val embedder = try { app.models.embedder() } catch (e: Exception) { return@withContext Result.retry() }

        for (photo in todo) {
            if (isStopped) break
            try {
                val image = app.media.decodeForModel(photo.uri)
                val vec = embedder.embedImage(image, budget)
                app.db.put(photo.id, photo.modified, budget, vec)
            } catch (e: OutOfMemoryError) {
                app.db.markFailed(photo.id, photo.modified)
                System.gc()
            } catch (e: Exception) {
                app.db.markFailed(photo.id, photo.modified)
            }
            done++
            setProgress(workDataOf("done" to done, "total" to total))
            if (done % 5 == 0 || done == total) {
                runCatching {
                    setForeground(Work.foregroundInfo(applicationContext, 2, "Indexing photos", "$done of $total", done, total))
                }
            }
        }
        Result.success()
    }
}
