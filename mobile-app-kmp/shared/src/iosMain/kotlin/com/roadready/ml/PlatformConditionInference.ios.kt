package com.roadready.ml

/**
 * iOS inference backend — threshold-only for now.
 * CoreML via ONNX converter is a future task once the Android model is validated.
 */
actual class PlatformConditionInference actual constructor() {

    actual fun isModelLoaded(): Boolean = false

    actual fun classify(features: FloatArray): RoadConditionLabel {
        val peakAmp = features.getOrElse(1) { 0f }
        val variance = features.getOrElse(2) { 0f }
        val hpEnergy = features.getOrElse(3) { 0f }
        return when {
            peakAmp >= 4.0f && hpEnergy > 0.8f -> RoadConditionLabel.POTHOLE
            peakAmp >= 3.5f && hpEnergy > 0.5f -> RoadConditionLabel.BUMP
            peakAmp >= 4.0f && hpEnergy <= 0.5f -> RoadConditionLabel.SPEED_BUMP
            variance >= 0.8f -> RoadConditionLabel.ROUGH
            else -> RoadConditionLabel.SMOOTH
        }
    }
}
