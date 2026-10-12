package app.recall.core

import java.util.PriorityQueue

/**
 * Exact nearest-neighbour search over unit vectors kept in one contiguous array.
 *
 * Brute force is the right tool at gallery scale: 20,000 photos x 768 dims is ~15M multiply-adds per query,
 * roughly 10-20 ms on a phone, with perfect recall. Approximate indexes only pay off around a million vectors.
 */
class VectorIndex(private val dim: Int = 768) {
    private var ids = LongArray(1024)
    private var data = FloatArray(1024 * dim)
    private var n = 0
    private val rowOf = HashMap<Long, Int>()

    val size: Int @Synchronized get() = n

    @Synchronized
    fun put(id: Long, v: FloatArray) {
        require(v.size == dim) { "Expected $dim dims, got ${v.size}" }
        val row = rowOf[id] ?: run {
            if (n == ids.size) {
                ids = ids.copyOf(n * 2)
                data = data.copyOf(n * 2 * dim)
            }
            val r = n++
            ids[r] = id
            rowOf[id] = r
            r
        }
        System.arraycopy(v, 0, data, row * dim, dim)
    }

    @Synchronized
    fun remove(id: Long) {
        val r = rowOf.remove(id) ?: return
        val last = n - 1
        if (r != last) {
            ids[r] = ids[last]
            System.arraycopy(data, last * dim, data, r * dim, dim)
            rowOf[ids[r]] = r
        }
        n--
    }

    @Synchronized
    fun clear() { n = 0; rowOf.clear() }

    @Synchronized
    fun get(id: Long): FloatArray? = rowOf[id]?.let { r -> data.copyOfRange(r * dim, (r + 1) * dim) }

    @Synchronized
    fun contains(id: Long): Boolean = rowOf.containsKey(id)

    /** Top [k] by dot product (cosine for unit vectors), optionally restricted by [filter]. */
    @Synchronized
    fun search(q: FloatArray, k: Int, filter: ((Long) -> Boolean)? = null): List<Pair<Long, Float>> {
        require(q.size == dim)
        val heap = PriorityQueue<Pair<Long, Float>>(k + 1, compareBy { it.second })
        for (r in 0 until n) {
            val id = ids[r]
            if (filter != null && !filter(id)) continue
            var s = 0f
            val base = r * dim
            for (j in 0 until dim) s += q[j] * data[base + j]
            if (heap.size < k) heap.add(id to s)
            else if (s > heap.peek().second) { heap.poll(); heap.add(id to s) }
        }
        return heap.sortedByDescending { it.second }
    }
}
