package com.example.superpowerscameraresearch.overlay

/**
 * Strategy interface for detecting bright astronomical sources in a luma plane.
 * Swap implementations by passing a different SourceDetector to the overlay.
 *
 * cx/cy are the primary scientific output (source centroids in image coordinates).
 * radius is used only for drawing circles and may not be physically meaningful.
 */
interface SourceDetector {
    fun detect(
        luma: ByteArray,
        stride: Int,
        width: Int,
        height: Int,
        sensitivity: Int  // 1–100; callers must not call with 0
    ): List<DetectedSource>
}

data class DetectedSource(
    val cx: Float,     // centroid x, in image pixel coordinates
    val cy: Float,     // centroid y, in image pixel coordinates
    val radius: Float  // bounding radius in image pixels, for circle rendering
)
