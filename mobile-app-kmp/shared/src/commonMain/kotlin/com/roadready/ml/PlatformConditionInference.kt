package com.roadready.ml

/**
 * Platform inference backend for the road condition classifier.
 *
 * Android actual: loads road_condition_classifier.onnx from assets.
 * iOS actual: threshold-only fallback (CoreML integration is a future task).
 */
expect class PlatformConditionInference() {
    /** Classify a 5-feature vector and return a [RoadConditionLabel]. */
    fun classify(features: FloatArray): RoadConditionLabel

    /** True once the ONNX model has been loaded successfully. */
    fun isModelLoaded(): Boolean
}
