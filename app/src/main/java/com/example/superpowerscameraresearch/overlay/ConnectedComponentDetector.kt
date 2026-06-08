package com.example.superpowerscameraresearch.overlay

class ConnectedComponentDetector : SourceDetector {

    companion object {
        // Blobs smaller than this pixel count are treated as hot-pixel noise and discarded.
        private const val MIN_BLOB_PIXELS = 2

        // Blobs covering more than this fraction of the frame are treated as sky/fog and discarded.
        private const val MAX_BLOB_AREA_FRACTION = 0.30f
    }

    override fun detect(
        luma: ByteArray,
        stride: Int,
        width: Int,
        height: Int,
        sensitivity: Int
    ): List<DetectedSource> {
        // threshold: sensitivity=100 → T≈0 (detect everything); sensitivity=1 → T≈252 (only very bright)
        val threshold = ((1f - sensitivity / 100f) * 255f).toInt()
        val maxBlobPixels = (width * height * MAX_BLOB_AREA_FRACTION).toInt()

        // Union-Find parent array indexed by pixel index (y*stride + x, but we use y*width + x for
        // the label array since stride may include padding we don't care about).
        val labels = IntArray(width * height) { -1 }  // -1 = below threshold
        val parent = IntArray(width * height) { it }

        fun root(i: Int): Int {
            var r = i
            while (parent[r] != r) r = parent[r]
            // Path compression
            var j = i
            while (parent[j] != r) {
                val next = parent[j]
                parent[j] = r
                j = next
            }
            return r
        }

        fun union(a: Int, b: Int) {
            val ra = root(a)
            val rb = root(b)
            if (ra != rb) parent[ra] = rb
        }

        // Single-pass labeling (4-connectivity: left and above neighbours)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val lumaVal = luma[y * stride + x].toInt() and 0xFF
                if (lumaVal < threshold) continue
                val idx = y * width + x
                labels[idx] = idx  // mark as above-threshold; parent already = idx
                if (x > 0 && labels[idx - 1] >= 0) union(idx, idx - 1)
                if (y > 0 && labels[(y - 1) * width + x] >= 0) union(idx, (y - 1) * width + x)
            }
        }

        // Accumulate per-root statistics
        data class BlobStats(
            var count: Int = 0,
            var sumX: Long = 0, var sumY: Long = 0,
            var minX: Int = Int.MAX_VALUE, var maxX: Int = Int.MIN_VALUE,
            var minY: Int = Int.MAX_VALUE, var maxY: Int = Int.MIN_VALUE
        )
        val stats = HashMap<Int, BlobStats>()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (labels[idx] < 0) continue
                val r = root(idx)
                val s = stats.getOrPut(r) { BlobStats() }
                s.count++
                s.sumX += x
                s.sumY += y
                if (x < s.minX) s.minX = x
                if (x > s.maxX) s.maxX = x
                if (y < s.minY) s.minY = y
                if (y > s.maxY) s.maxY = y
            }
        }

        val result = mutableListOf<DetectedSource>()
        val maxRadius = width / 4f  // upper clamp: prevents full-sky fog blobs

        for ((_, s) in stats) {
            if (s.count < MIN_BLOB_PIXELS) continue   // noise filter
            if (s.count > maxBlobPixels) continue      // sky/fog filter
            val cx = s.sumX.toFloat() / s.count
            val cy = s.sumY.toFloat() / s.count
            val radius = (maxOf(s.maxX - s.minX, s.maxY - s.minY) / 2f).coerceIn(2f, maxRadius)
            result += DetectedSource(cx, cy, radius)
        }

        return result
    }
}
