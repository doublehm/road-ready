package com.roadready.ml

enum class CoachingEventType(val label: String, val message: String) {
    HARSH_BRAKING("Braking", "Try to brake smoother and earlier"),
    HARSH_ACCELERATION("Acceleration", "Ease into the throttle more gently"),
    SHARP_TURN("Cornering", "Slow down more before entering the turn"),
}

data class CoachingEvent(
    val type: CoachingEventType,
    val timestamp: Long,
    val confidence: Float = 1.0f,
    /** true = NN model fired; false = threshold fallback (model not yet loaded) */
    val fromModel: Boolean = false,
)
