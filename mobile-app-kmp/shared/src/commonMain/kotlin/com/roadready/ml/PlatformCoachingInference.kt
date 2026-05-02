package com.roadready.ml

import com.roadready.data.repository.MotionData

/**
 * Platform inference backend.
 *
 * Android actual: tries to load driving_coach.tflite from assets and run the
 * 1D-CNN via TensorFlow Lite.  Falls back to physics-mirrored thresholds when
 * the model file is absent so coaching works immediately before training is done.
 *
 * iOS actual: threshold-only fallback (CoreML integration is a future task).
 */
expect class PlatformCoachingInference() {
    /** Returns a [CoachingEvent] if a driving fault is detected, null otherwise. */
    fun infer(window: List<MotionData>): CoachingEvent?

    /** True once a TFLite / CoreML model has been loaded successfully. */
    fun isModelLoaded(): Boolean
}
