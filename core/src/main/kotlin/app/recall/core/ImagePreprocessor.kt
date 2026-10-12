package app.recall.core

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** An RGB image as packed 0xAARRGGBB pixels (alpha is ignored), row-major. */
class ArgbImage(val width: Int, val height: Int, val pixels: IntArray) {
    init { require(pixels.size == width * height) { "pixels.size != width*height" } }
}

/** What the vision encoder takes for one image. */
class VisionInputs(
    val pixelValues: FloatArray,   // [maxPatches, patchSize*patchSize*3], zero-padded
    val positionIds: LongArray,    // [maxPatches, 2] as (col, row), -1 padded
    val maxPatches: Int,
    val patchDim: Int,
    val numSoftTokens: Int,
    val resizedWidth: Int,
    val resizedHeight: Int,
)

/**
 * Port of Transformers.js `Gemma4ImageProcessor`: aspect-ratio-preserving resize to a multiple of
 * patch_size * pooling_kernel_size, rescale to [0, 1] (no mean/std normalization), and patchify in (dy, dx, c) order.
 */
class ImagePreprocessor(
    val patchSize: Int = 16,
    val poolingKernelSize: Int = 3,
) {
    fun targetSize(height: Int, width: Int, maxSoftTokens: Int): Pair<Int, Int> {
        val maxPatches = maxSoftTokens * poolingKernelSize * poolingKernelSize
        val targetPx = maxPatches.toDouble() * patchSize * patchSize
        val factor = sqrt(targetPx / (height.toDouble() * width))
        val sideMult = poolingKernelSize * patchSize
        var th = (floor(factor * height / sideMult) * sideMult).toInt()
        var tw = (floor(factor * width / sideMult) * sideMult).toInt()
        require(th != 0 || tw != 0) { "Image too small to resize" }
        val maxSide = (maxPatches / (poolingKernelSize * poolingKernelSize)) * sideMult
        if (th == 0) {
            th = sideMult
            tw = min((width / height) * sideMult, maxSide)
        } else if (tw == 0) {
            tw = sideMult
            th = min((height / width) * sideMult, maxSide)
        }
        return th to tw
    }

    fun process(image: ArgbImage, maxSoftTokens: Int): VisionInputs {
        val (th, tw) = targetSize(image.height, image.width, maxSoftTokens)
        val rgb = if (th == image.height && tw == image.width) toRgbFloat(image) else resizeBicubic(image, tw, th)
        val maxPatches = maxSoftTokens * poolingKernelSize * poolingKernelSize
        val ph = th / patchSize
        val pw = tw / patchSize
        val patchDim = patchSize * patchSize * 3
        val patches = FloatArray(maxPatches * patchDim)
        var o = 0
        for (py in 0 until ph) for (px in 0 until pw) for (dy in 0 until patchSize) {
            val row = (py * patchSize + dy) * tw * 3 + px * patchSize * 3
            for (dx in 0 until patchSize) {
                val src = row + dx * 3
                patches[o++] = rgb[src] / 255f
                patches[o++] = rgb[src + 1] / 255f
                patches[o++] = rgb[src + 2] / 255f
            }
        }
        val pos = LongArray(maxPatches * 2) { -1L }
        var i = 0
        for (r in 0 until ph) for (c in 0 until pw) { pos[i++] = c.toLong(); pos[i++] = r.toLong() }
        return VisionInputs(
            patches, pos, maxPatches, patchDim, (ph * pw) / (poolingKernelSize * poolingKernelSize), tw, th,
        )
    }

    private fun toRgbFloat(img: ArgbImage): FloatArray {
        val out = FloatArray(img.width * img.height * 3)
        for (i in img.pixels.indices) {
            val p = img.pixels[i]
            out[i * 3] = ((p shr 16) and 0xFF).toFloat()
            out[i * 3 + 1] = ((p shr 8) and 0xFF).toFloat()
            out[i * 3 + 2] = (p and 0xFF).toFloat()
        }
        return out
    }

    /**
     * Separable bicubic resampling (a = -0.5) with an antialiasing support that widens when downscaling, the same
     * scheme Pillow uses. Output values are clamped and rounded to 0..255 like an 8-bit image.
     */
    fun resizeBicubic(img: ArgbImage, outW: Int, outH: Int): FloatArray {
        val src = toRgbFloat(img)
        val horiz = resample1d(src, img.width, img.height, outW, horizontal = true)
        val out = resample1d(horiz, outW, img.height, outH, horizontal = false)
        for (k in out.indices) out[k] = Math.round(out[k].coerceIn(0f, 255f)).toFloat()
        return out
    }

    private fun cubic(x: Double): Double {
        val a = -0.5
        val ax = abs(x)
        return when {
            ax < 1.0 -> ((a + 2) * ax - (a + 3)) * ax * ax + 1
            ax < 2.0 -> (((ax - 5) * ax + 8) * ax - 4) * a
            else -> 0.0
        }
    }

    /** Resamples along one axis. [w] x [h] is the input size; returns RGB floats. */
    private fun resample1d(src: FloatArray, w: Int, h: Int, outLen: Int, horizontal: Boolean): FloatArray {
        val inLen = if (horizontal) w else h
        val scale = inLen.toDouble() / outLen
        val filterScale = max(scale, 1.0)
        val support = 2.0 * filterScale
        val outW = if (horizontal) outLen else w
        val outH = if (horizontal) h else outLen
        val out = FloatArray(outW * outH * 3)
        val maxTaps = ceil(support).toInt() * 2 + 1
        val weights = DoubleArray(maxTaps)
        for (o in 0 until outLen) {
            val center = (o + 0.5) * scale
            val xmin = max((center - support + 0.5).toInt(), 0)
            val xmax = min((center + support + 0.5).toInt(), inLen)
            val n = xmax - xmin
            var total = 0.0
            for (k in 0 until n) {
                val wgt = cubic((k + xmin - center + 0.5) / filterScale)
                weights[k] = wgt; total += wgt
            }
            if (total != 0.0) for (k in 0 until n) weights[k] /= total
            if (horizontal) {
                for (y in 0 until h) {
                    var r = 0.0; var g = 0.0; var b = 0.0
                    for (k in 0 until n) {
                        val s = (y * w + xmin + k) * 3
                        val wk = weights[k]
                        r += src[s] * wk; g += src[s + 1] * wk; b += src[s + 2] * wk
                    }
                    val d = (y * outW + o) * 3
                    out[d] = r.toFloat(); out[d + 1] = g.toFloat(); out[d + 2] = b.toFloat()
                }
            } else {
                for (x in 0 until w) {
                    var r = 0.0; var g = 0.0; var b = 0.0
                    for (k in 0 until n) {
                        val s = ((xmin + k) * w + x) * 3
                        val wk = weights[k]
                        r += src[s] * wk; g += src[s + 1] * wk; b += src[s + 2] * wk
                    }
                    val d = (o * w + x) * 3
                    out[d] = r.toFloat(); out[d + 1] = g.toFloat(); out[d + 2] = b.toFloat()
                }
            }
        }
        return out
    }
}
