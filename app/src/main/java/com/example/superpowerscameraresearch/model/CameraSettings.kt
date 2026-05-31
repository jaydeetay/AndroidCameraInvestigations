package com.example.superpowerscameraresearch.model

data class CameraSettings(
    val isoAuto: Boolean = true,
    val iso: Int = 800,
    val shutterAuto: Boolean = true,
    val shutterNs: Long = 33_333_333L,   // ~1/30s in nanoseconds
    val wbAuto: Boolean = true,
    val whiteBalanceK: Int = 4000,
    val focusDistance: Float = 0f,       // 0 = infinity (Camera2 dioptre units)
    val zoom: Float = 1.0f,
    val oisEnabled: Boolean = true,
    val noiseReduction: Int = 1          // CaptureRequest.NOISE_REDUCTION_MODE_FAST
) {
    companion object {
        fun shutterNsToDisplay(ns: Long): String {
            val seconds = ns / 1_000_000_000.0
            return when {
                seconds >= 1.0 -> "${seconds.toLong()}s"
                else -> {
                    val denom = Math.round(1.0 / seconds)
                    "1/${denom}s"
                }
            }
        }

        fun focusDistanceToDisplay(dioptre: Float): String =
            if (dioptre == 0f) "∞" else "${"%.2f".format(dioptre)} D"
    }
}
