package app.recall.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class VectorIndexTest {
    private fun unit(seed: Int, dim: Int = 768) = Random(seed).let { r -> Embedder.normalize(FloatArray(dim) { r.nextFloat() - 0.5f }) }

    @Test
    fun findsExactMatchFirstAndHandlesRemoval() {
        val idx = VectorIndex()
        for (i in 0 until 3000) idx.put(i.toLong(), unit(i))
        assertEquals(3000, idx.size)
        val top = idx.search(unit(1234), 5)
        assertEquals(1234L, top[0].first)
        assertEquals(1f, top[0].second, 1e-4f)

        idx.remove(1234)
        assertNull(idx.get(1234))
        assertEquals(2999, idx.size)
        // The vector moved into the freed row is still found correctly
        assertEquals(2999L, idx.search(unit(2999), 1)[0].first)
    }

    @Test
    fun filterRestrictsResults() {
        val idx = VectorIndex()
        for (i in 0 until 100) idx.put(i.toLong(), unit(i))
        val top = idx.search(unit(10), 3) { it % 2L == 1L }
        assert(top.all { it.first % 2L == 1L })
    }

    @Test
    fun twentyThousandPhotosSearchTiming() {
        val idx = VectorIndex()
        for (i in 0 until 20_000) idx.put(i.toLong(), unit(i))
        val q = unit(42)
        repeat(3) { idx.search(q, 200) } // warm up
        val t0 = System.nanoTime()
        val runs = 10
        repeat(runs) { idx.search(q, 200) }
        println("20k x 768 search: %.1f ms per query".format((System.nanoTime() - t0) / 1e6 / runs))
    }
}
