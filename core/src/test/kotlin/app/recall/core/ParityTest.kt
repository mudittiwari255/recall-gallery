package app.recall.core

import com.google.gson.JsonParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * Compares the Kotlin pipeline against Transformers.js reference outputs (tools/reference/reference.mjs).
 * Runs only when MODEL_DIR and REFERENCE_JSON are set (CI downloads the model and produces the reference).
 */
class ParityTest {
    companion object {
        private val modelDir = System.getenv("MODEL_DIR")?.let(::File)
        private val refFile = System.getenv("REFERENCE_JSON")?.let(::File)
        private lateinit var embedder: Embedder
        private val ref by lazy { JsonParser.parseString(refFile!!.readText()).asJsonObject }

        @BeforeClass @JvmStatic
        fun setUp() {
            assumeTrue("MODEL_DIR / REFERENCE_JSON not set", modelDir?.isDirectory == true && refFile?.isFile == true)
            val t0 = System.nanoTime()
            embedder = Embedder(modelDir!!)
            println("Tokenizer loaded in ${(System.nanoTime() - t0) / 1_000_000} ms")
            println(embedder.tokenizer.describe())
        }

        fun cos(a: FloatArray, b: FloatArray) = Embedder.dot(Embedder.normalize(a.copyOf()), Embedder.normalize(b.copyOf()))
        fun floats(e: com.google.gson.JsonElement) = e.asJsonArray.let { a -> FloatArray(a.size()) { a[it].asFloat } }
        fun ints(e: com.google.gson.JsonElement) = e.asJsonArray.let { a -> IntArray(a.size()) { a[it].asInt } }
    }

    @Test
    fun textTokenIdsMatch() {
        for (t in ref.getAsJsonArray("texts")) {
            val o = t.asJsonObject
            val text = o.get("text").asString
            val expected = ints(o.get("ids"))
            val actual = embedder.tokenizer.encode(text)
            if (!expected.contentEquals(actual)) {
                println("MISMATCH for \"$text\"\n  expected ${expected.toList()}\n  actual   ${actual.toList()}")
            }
            assertArrayEquals("token ids for \"$text\"", expected, actual)
        }
    }

    @Test
    fun textEmbeddingsMatch() {
        for (t in ref.getAsJsonArray("texts")) {
            val o = t.asJsonObject
            val t0 = System.nanoTime()
            val actual = embedder.embedText(o.get("text").asString)
            val ms = (System.nanoTime() - t0) / 1_000_000
            val c = cos(floats(o.get("embedding")), actual)
            println("text cos=%.5f (%d ms) %s".format(c, ms, o.get("text").asString))
            assertTrue("text embedding cosine $c too low", c > 0.995f)
        }
    }

    @Test
    fun imagePreprocessingAndEmbeddingsMatch() {
        for (e in ref.getAsJsonArray("images")) {
            val o = e.asJsonObject
            val buffered = ImageIO.read(File(o.get("path").asString))
            val w = buffered.width; val h = buffered.height
            val img = ArgbImage(w, h, buffered.getRGB(0, 0, w, h, null, 0, w))
            val budget = o.get("budget").asInt
            val v = embedder.prepareImage(img, budget)

            assertEquals("soft tokens", o.get("numSoftTokens").asInt, v.numSoftTokens)
            var sum = 0.0; var count = 0; var real = 0
            for (p in 0 until v.maxPatches) {
                if (v.positionIds[p * 2] < 0) continue
                real++
                for (k in 0 until v.patchDim) { sum += v.pixelValues[p * v.patchDim + k]; count++ }
            }
            assertEquals("real patches", o.get("realPatches").asInt, real)
            val mean = sum / count
            println("image ${o.get("path").asString} budget=$budget resized=${v.resizedWidth}x${v.resizedHeight} mean=%.4f ref=%.4f".format(mean, o.get("meanPixel").asDouble))
            assertEquals("mean pixel", o.get("meanPixel").asDouble, mean, 0.01)
            assertArrayEquals("image token ids", ints(o.get("ids")), embedder.imageTokenIds(v.numSoftTokens))

            val t0 = System.nanoTime()
            val actual = embedder.embedPrepared(v)
            val ms = (System.nanoTime() - t0) / 1_000_000
            val c = cos(floats(o.get("embedding")), actual)
            println("image cos=%.5f (%d ms)".format(c, ms))
            assertTrue("image embedding cosine $c too low", c > 0.97f)
        }
    }

    @Test
    fun batchedVisionMatchesSingleAndReportsSpeed() {
        val paths = ref.getAsJsonArray("images").map { it.asJsonObject.get("path").asString }.distinct()
        val images = paths.map { path ->
            val bi = ImageIO.read(File(path))
            ArgbImage(bi.width, bi.height, bi.getRGB(0, 0, bi.width, bi.height, null, 0, bi.width))
        }
        // Four different inputs: each image and a mirrored copy
        val inputs = images.flatMap { img ->
            val mirrored = IntArray(img.pixels.size) { i -> val y = i / img.width; val x = i % img.width; img.pixels[y * img.width + (img.width - 1 - x)] }
            listOf(img, ArgbImage(img.width, img.height, mirrored))
        }.take(4).map { embedder.prepareImage(it, 70) }

        embedder.embedPrepared(inputs[0]) // warm up
        val singles: List<FloatArray>
        val t0 = System.nanoTime()
        singles = inputs.map { embedder.embedPrepared(it) }
        val singleMs = (System.nanoTime() - t0) / 1e6
        val batched: List<FloatArray>
        val t1 = System.nanoTime()
        batched = embedder.embedPreparedBatch(inputs)
        val batchMs = (System.nanoTime() - t1) / 1e6
        for (i in inputs.indices) {
            val c = Embedder.dot(singles[i], batched[i])
            assertTrue("batched result $i differs (cos=$c)", c > 0.999f)
        }
        println("BENCH vision batch=${inputs.size}: one-by-one %.0f ms, batched %.0f ms, speedup %.2fx".format(singleMs, batchMs, singleMs / batchMs))
    }

    @Test
    fun searchRanksTheRightImage() {
        val cats = embedder.embedQuery("cats sleeping on a couch")
        val turtle = embedder.embedQuery("a turtle swimming in the ocean")
        val first = ref.getAsJsonArray("images")[0].asJsonObject
        val img = floats(first.get("embedding"))
        println("cats=%.3f turtle=%.3f".format(Embedder.dot(cats, img), Embedder.dot(turtle, img)))
        assertTrue(Embedder.dot(cats, img) > Embedder.dot(turtle, img))
    }
}
