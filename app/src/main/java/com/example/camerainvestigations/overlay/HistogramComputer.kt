package com.example.camerainvestigations.overlay

object HistogramComputer {

    private const val SAMPLE_STEP = 4   // sample every 4th pixel in X and Y

    /**
     * Computes a 256-bucket luminance histogram from a YUV_420_888 Y plane.
     * @param yPlane  raw Y plane bytes (unsigned, 0–255)
     * @param stride  row stride in bytes (may be wider than width)
     * @param width   frame width in pixels
     * @param height  frame height in pixels
     * @return IntArray(256) of pixel counts
     */
    fun compute(yPlane: ByteArray, stride: Int, width: Int, height: Int): IntArray {
        val histogram = IntArray(256)
        var row = 0
        while (row < height) {
            var col = 0
            while (col < width) {
                val index = row * stride + col
                if (index < yPlane.size) {
                    histogram[yPlane[index].toInt() and 0xFF]++
                }
                col += SAMPLE_STEP
            }
            row += SAMPLE_STEP
        }
        return histogram
    }

    /**
     * Returns true if more than [threshold] fraction of sampled pixels are at maximum brightness.
     */
    fun isClipping(histogram: IntArray, threshold: Float = 0.005f): Boolean {
        val total = histogram.sum()
        if (total == 0) return false
        return histogram[255].toFloat() / total > threshold
    }
}
