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
import app.recall.core.VisionInputs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
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

    private fun indexRequest(context: Context) = OneTimeWorkRequestBuilder<IndexWorker>().apply {
        if ((context.applicationContext as RecallApp).settings.chargingOnly) {
            setConstraints(Constraints.Builder().setRequiresCharging(true).build())
        }
    }.build()

    fun startIndexing(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(INDEX, ExistingWorkPolicy.KEEP, indexRequest(context))
    }

    /** Restarts indexing so changed settings (folders, dates, order, detail, charging) take effect immediately. */
    fun restartIndexing(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(INDEX, ExistingWorkPolicy.REPLACE, indexRequest(context))
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

/** "about 2 h 15 m", "about 4 m", "under a minute". */
fun formatEta(seconds: Long): String = when {
    seconds < 60 -> "under a minute"
    seconds < 3600 -> "about ${seconds / 60} m"
    else -> "about ${seconds / 3600} h ${(seconds % 3600) / 60} m"
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

/** How many photos go through the vision model per run. 1 until benchmarks show batching helps. */
const val VISION_BATCH = 1

/**
 * Near-identical shots: same folder and size, taken within 2 seconds (bursts, double taps of the shutter).
 * They share one embedding, so the model runs once per burst. Screenshots never count, since quick screenshots
 * of different screens look nothing alike.
 */
fun isBurst(a: Photo, b: Photo): Boolean =
    !a.isScreenshot && !b.isScreenshot && a.width > 0 &&
        a.bucketId == b.bucketId && a.width == b.width && a.height == b.height &&
        kotlin.math.abs(a.dateTaken - b.dateTaken) <= 2000

/** Groups consecutive near-identical photos; the first of each group is the one the model sees. */
fun burstGroups(photos: List<Photo>): List<List<Photo>> {
    val out = ArrayList<MutableList<Photo>>()
    for (p in photos) {
        val g = out.lastOrNull()
        if (g != null && isBurst(g.last(), p)) g.add(p) else out.add(mutableListOf(p))
    }
    return out
}

/**
 * Embeds every selected photo that is new or changed.
 *
 * Decoding and preparing photos runs on two IO threads ahead of the model, so the model never waits on storage.
 * Burst shots share one embedding. Progress reports `done`, `total`, `msPerPhoto` (over roughly the last 30 photos)
 * and `etaSec`.
 */
class IndexWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun getForegroundInfo() =
        Work.foregroundInfo(applicationContext, 2, "Getting photos ready to search", "Starting…", 0, 0)

    private class Prepared(val group: List<Photo>, val inputs: VisionInputs?)

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val app = applicationContext as RecallApp
        if (!app.models.isReady || !app.hasPhotoPermission()) return@withContext Result.success()
        runCatching { setForeground(getForegroundInfo()) }

        val photos = app.media.loadAll()
        app.db.deleteMissing(photos.mapTo(HashSet()) { it.id })
        val processed = app.db.processed()
        val selection = app.indexSelection(photos)
        val todo = selection.filter { processed[it.id] != it.modified }
        val total = selection.size
        var done = total - todo.size
        if (todo.isEmpty()) return@withContext Result.success()
        val groups = burstGroups(todo)

        val budget = app.settings.photoDetail
        val side = app.media.decodeSideFor(budget)
        val embedder = try { app.models.embedder() } catch (e: Exception) { return@withContext Result.retry() }

        // (time, photos done) samples for a rolling speed estimate
        val samples = ArrayDeque<Pair<Long, Int>>()
        samples.addLast(System.nanoTime() to done)

        suspend fun report() {
            val now = System.nanoTime()
            samples.addLast(now to done)
            while (samples.size > 2 && done - samples.first().second > 30) samples.removeFirst()
            val (t0, d0) = samples.first()
            val msPer = if (done > d0) (now - t0) / 1_000_000 / (done - d0) else 0L
            val eta = msPer * (total - done) / 1000
            setProgress(workDataOf("done" to done, "total" to total, "msPerPhoto" to msPer, "etaSec" to eta))
            runCatching {
                setForeground(
                    Work.foregroundInfo(
                        applicationContext, 2, "Getting photos ready to search",
                        "$done of $total · ${formatEta(eta)} left", done, total,
                    ),
                )
            }
        }

        fun save(group: List<Photo>, vec: FloatArray?) {
            for (p in group) {
                if (vec != null) app.db.put(p.id, p.modified, budget, vec) else app.db.markFailed(p.id, p.modified)
            }
            done += group.size
        }

        coroutineScope {
            val queue = Channel<Prepared>(capacity = 4 + VISION_BATCH)
            val producers = (0 until 2).map { lane ->
                launch(Dispatchers.IO) {
                    var i = lane
                    while (i < groups.size && isActive) {
                        val g = groups[i]
                        val inputs = try {
                            embedder.prepareImage(app.media.decodeForModel(g[0].uri, side), budget)
                        } catch (t: Throwable) { null } // includes OutOfMemoryError on huge images
                        queue.send(Prepared(g, inputs))
                        i += 2
                    }
                }
            }
            launch { producers.joinAll(); queue.close() }

            var sinceReport = 0
            for (first in queue) {
                if (isStopped) break
                // Take whatever else is already prepared, up to the batch size
                val batch = mutableListOf(first)
                while (batch.size < VISION_BATCH) batch.add(queue.tryReceive().getOrNull() ?: break)

                val ready = batch.filter { it.inputs != null }
                val vecs: List<FloatArray?> = if (ready.isEmpty()) emptyList() else try {
                    embedder.embedPreparedBatch(ready.map { it.inputs!! })
                } catch (t: Throwable) {
                    // Fall back to one at a time so one bad photo doesn't sink the batch
                    ready.map { r -> runCatching { embedder.embedPrepared(r.inputs!!) }.getOrNull() }
                }
                ready.forEachIndexed { i, r -> save(r.group, vecs[i]) }
                batch.filter { it.inputs == null }.forEach { save(it.group, null) }

                sinceReport += batch.size
                if (sinceReport >= 3 || done == total) { sinceReport = 0; report() }
            }
            coroutineContext.cancelChildren()
        }
        Result.success()
    }
}
