package app.recall

import android.content.Context
import app.recall.core.Embedder
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Downloads, stores and opens the on-device embedding model. */
class ModelManager(private val context: Context) {
    companion object {
        const val REPO = "onnx-community/embeddinggemma-2-ONNX"
        const val DTYPE = "q4"
        private const val BASE = "https://huggingface.co/$REPO/resolve/main/"
    }

    val dir: File = File(context.filesDir, "model")
    private val files = Embedder.requiredFiles(DTYPE)
    private val readyMarker = File(dir, ".ready")

    @Volatile private var embedder: Embedder? = null

    val isReady: Boolean get() = readyMarker.exists()

    /** Returns the embedder, opening it on first use. Must not be called on the main thread. */
    fun embedder(): Embedder {
        embedder?.let { return it }
        synchronized(this) {
            embedder?.let { return it }
            check(isReady) { "Model is not downloaded yet" }
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
            return Embedder(dir, DTYPE, threads).also { embedder = it }
        }
    }

    /**
     * Downloads every model file, resuming partial downloads. [onProgress] gets (bytesDone, bytesTotal);
     * the total is known after the first request for each file, so it grows briefly at the start.
     */
    fun download(isCancelled: () -> Boolean, onProgress: (Long, Long) -> Unit) {
        dir.mkdirs()
        val sizes = LongArray(files.size)
        // Learn the sizes first, so progress is meaningful from the start
        files.forEachIndexed { i, path -> sizes[i] = File(dir, path).takeIf { it.exists() }?.length() ?: headSize(path) }
        val total = sizes.sum()
        var doneBefore = 0L
        files.forEachIndexed { i, path ->
            if (isCancelled()) throw IOException("Cancelled")
            val target = File(dir, path)
            if (!target.exists()) fetch(path, target, isCancelled) { got -> onProgress(doneBefore + got, total) }
            doneBefore += target.length()
            onProgress(doneBefore, total)
        }
        readyMarker.writeText(System.currentTimeMillis().toString())
    }

    private fun open(url: String, rangeFrom: Long = 0, method: String = "GET"): HttpURLConnection {
        var current = URL(url)
        repeat(6) {
            val c = current.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 20_000
            c.readTimeout = 60_000
            c.requestMethod = method
            c.setRequestProperty("User-Agent", "RecallGallery/0.1 (Android)")
            if (rangeFrom > 0) c.setRequestProperty("Range", "bytes=$rangeFrom-")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw IOException("Redirect without location")
                c.disconnect()
                current = URL(current, loc)
            } else {
                return c
            }
        }
        throw IOException("Too many redirects for $url")
    }

    private fun headSize(path: String): Long {
        val c = open(BASE + path, method = "HEAD")
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode} for $path")
            return c.getHeaderField("Content-Length")?.toLongOrNull()
                ?: c.getHeaderField("X-Linked-Size")?.toLongOrNull() ?: 0L
        } finally {
            c.disconnect()
        }
    }

    private fun fetch(path: String, target: File, isCancelled: () -> Boolean, onBytes: (Long) -> Unit) {
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        var attempt = 0
        while (true) {
            try {
                val have = if (part.exists()) part.length() else 0L
                val c = open(BASE + path, rangeFrom = have)
                try {
                    val code = c.responseCode
                    val append = code == 206
                    if (code != 200 && code != 206) throw IOException("HTTP $code for $path")
                    var got = if (append) have else 0L
                    c.inputStream.use { input ->
                        java.io.FileOutputStream(part, append).use { out ->
                            val buf = ByteArray(1 shl 16)
                            var lastReport = 0L
                            while (true) {
                                if (isCancelled()) throw IOException("Cancelled")
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                got += n
                                if (got - lastReport > 512 * 1024) { onBytes(got); lastReport = got }
                            }
                        }
                    }
                } finally {
                    c.disconnect()
                }
                if (!part.renameTo(target)) throw IOException("Could not save $path")
                return
            } catch (e: IOException) {
                if (e.message == "Cancelled" || ++attempt >= 5) throw e
                Thread.sleep(2_000L * attempt)
            }
        }
    }

    fun delete() {
        synchronized(this) {
            embedder?.close(); embedder = null
            dir.deleteRecursively()
        }
    }
}
