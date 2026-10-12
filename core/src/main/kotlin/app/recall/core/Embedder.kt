package app.recall.core

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.google.gson.JsonParser
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.sqrt

/**
 * Runs EmbeddingGemma 2 (ONNX export from onnx-community/embeddinggemma-2-ONNX) for text and images.
 *
 * Expected files in [modelDir]: config.json, tokenizer.json, onnx/model_<dtype>.onnx(+_data) and
 * onnx/vision_encoder_<dtype>.onnx(+_data). Every embedding returned is 768-d and L2-normalized.
 */
class Embedder(
    private val modelDir: File,
    private val dtype: String = "q4",
    private val threads: Int = 4,
    private val sessionOptions: (OrtSession.SessionOptions) -> Unit = {},
) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    val tokenizer: HfTokenizer = HfTokenizer.load(File(modelDir, "tokenizer.json"))
    private val config = JsonParser.parseString(File(modelDir, "config.json").readText()).asJsonObject
    private val hiddenSize = config.getAsJsonObject("text_config").get("hidden_size").asInt
    val imageTokenId: Int = config.get("image_token_id").asInt
    private val preprocessor = ImagePreprocessor()

    private val textSession: OrtSession by lazy { open("model_$dtype.onnx") }
    private val visionSession: OrtSession by lazy { open("vision_encoder_$dtype.onnx") }
    // Fair, so a search waiting behind background indexing gets the next turn instead of starving
    private val lock = ReentrantLock(true)

    private fun open(name: String): OrtSession {
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(threads)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            sessionOptions(this)
        }
        return env.createSession(File(modelDir, "onnx/$name").absolutePath, opts)
    }

    /** Loads the text model now, so the first search does not pay for it. */
    fun warmUpText() { textSession }

    fun embedQuery(query: String): FloatArray = embedText("task: search result | query: $query")

    fun embedDocument(text: String, title: String = "none"): FloatArray = embedText("title: $title | text: $text")

    fun embedText(text: String): FloatArray = lock.withLock {
        val ids = tokenizer.encode(text)
        runText(ids, null)
    }

    /** The token ids used for one image: `<|image>` + `<|image|>` x n + `<image|>`, wrapped by the post-processor. */
    fun imageTokenIds(numSoftTokens: Int): IntArray {
        val boi = config.get("boi_token_id")?.asInt?.let(tokenizer::token) ?: "<|image>"
        val eoi = config.get("eoi_token_id")?.asInt?.let(tokenizer::token) ?: "<image|>"
        val image = tokenizer.token(imageTokenId) ?: "<|image|>"
        return tokenizer.encode(boi + image.repeat(numSoftTokens) + eoi)
    }

    fun prepareImage(image: ArgbImage, maxSoftTokens: Int = 280): VisionInputs = preprocessor.process(image, maxSoftTokens)

    fun embedImage(image: ArgbImage, maxSoftTokens: Int = 280): FloatArray = embedPrepared(prepareImage(image, maxSoftTokens))

    fun embedPrepared(v: VisionInputs): FloatArray = lock.withLock {
        val features = OnnxTensor.createTensor(
            env, FloatBuffer.wrap(v.pixelValues), longArrayOf(1, v.maxPatches.toLong(), v.patchDim.toLong()),
        ).use { pixels ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(v.positionIds), longArrayOf(1, v.maxPatches.toLong(), 2)).use { pos ->
                val inputs = HashMap<String, OnnxTensor>()
                for (name in visionSession.inputNames) {
                    inputs[name] = when (name) {
                        "pixel_values" -> pixels
                        "pixel_position_ids", "image_position_ids", "position_ids" -> pos
                        else -> error("Unexpected vision encoder input: $name")
                    }
                }
                visionSession.run(inputs).use { out ->
                    val t = (out.get("image_features").orElse(null) ?: out.get(0)) as OnnxTensor
                    val buf = t.floatBuffer
                    FloatArray(buf.remaining()).also { buf.get(it) }
                }
            }
        }
        val n = features.size / hiddenSize
        check(n == v.numSoftTokens) { "Vision encoder returned $n soft tokens, expected ${v.numSoftTokens}" }
        runText(imageTokenIds(n), features)
    }

    private fun runText(ids: IntArray, imageFeatures: FloatArray?): FloatArray {
        val len = ids.size.toLong()
        val tensors = ArrayList<OnnxTensor>()
        fun longs(data: LongArray, shape: LongArray) = OnnxTensor.createTensor(env, LongBuffer.wrap(data), shape).also(tensors::add)
        fun floats(data: FloatArray, rows: Long) =
            OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(rows, hiddenSize.toLong())).also(tensors::add)
        try {
            val inputs = HashMap<String, OnnxTensor>()
            for (name in textSession.inputNames) {
                inputs[name] = when (name) {
                    "input_ids" -> longs(LongArray(ids.size) { ids[it].toLong() }, longArrayOf(1, len))
                    "attention_mask" -> longs(LongArray(ids.size) { 1L }, longArrayOf(1, len))
                    "position_ids" -> longs(LongArray(ids.size) { it.toLong() }, longArrayOf(1, len))
                    "token_type_ids" -> longs(LongArray(ids.size), longArrayOf(1, len))
                    "image_features" -> imageFeatures?.let { floats(it, (it.size / hiddenSize).toLong()) } ?: floats(FloatArray(0), 0)
                    "video_features", "audio_features" -> floats(FloatArray(0), 0)
                    else -> error("Unexpected text model input: $name")
                }
            }
            textSession.run(inputs).use { out ->
                val t = out.get("sentence_embedding").orElseThrow { IllegalStateException("No sentence_embedding output") } as OnnxTensor
                val buf = t.floatBuffer
                return normalize(FloatArray(buf.remaining()).also { buf.get(it) })
            }
        } finally {
            tensors.forEach { it.close() }
        }
    }

    override fun close() {
        lock.withLock {
            runCatching { textSession.close() }
            runCatching { visionSession.close() }
        }
    }

    companion object {
        /** Files to download from the Hugging Face repo, relative to its root. */
        fun requiredFiles(dtype: String = "q4") = listOf(
            "config.json",
            "tokenizer.json",
            "onnx/model_$dtype.onnx",
            "onnx/model_$dtype.onnx_data",
            "onnx/vision_encoder_$dtype.onnx",
            "onnx/vision_encoder_$dtype.onnx_data",
        )

        fun normalize(v: FloatArray): FloatArray {
            var s = 0.0
            for (x in v) s += x * x
            val n = sqrt(s).toFloat().takeIf { it > 0f } ?: 1f
            for (i in v.indices) v[i] /= n
            return v
        }

        fun dot(a: FloatArray, b: FloatArray): Float {
            var s = 0f
            for (i in a.indices) s += a[i] * b[i]
            return s
        }
    }
}
