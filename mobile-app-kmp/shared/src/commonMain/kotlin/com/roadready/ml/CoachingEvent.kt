package com.roadready.ml

enum class CoachingEventType(val label: String, val message: String) {
    HARSH_BRAKING("Hard Brake", "Ease onto the brakes"),
    HARSH_ACCELERATION("Hard Accel", "Accelerate more gradually"),
    SHARP_TURN("Sharp Turn", "Slow down before turning"),
}

data class CoachingEvent(
    val type: CoachingEventType,
    val timestamp: Long,
    val confidence: Float = 1.0f,
    /** true = NN model fired; false = threshold fallback (model not yet loaded) */
    val fromModel: Boolean = false,
)
