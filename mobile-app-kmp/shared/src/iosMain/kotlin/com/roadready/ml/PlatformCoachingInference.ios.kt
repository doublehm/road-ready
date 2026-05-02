package com.roadready.ml

import com.roadready.data.repository.MotionData
import platform.Foundation.NSDate
import kotlin.math.abs

/**
 * iOS inference backend — threshold-only for now.
 * CoreML integration is a future task once the Android model is trained.
 */
actual class PlatformCoachingInference actual constructor() {

    companion object {
        const val GRAVITY = 9.81f
        const val HARD_BRAKING_G = 0.6f
        const val HARD_ACCEL_G = 0.4f
        const val SHARP_TURN_G = 0.45f
    }

    actual fun isModelLoaded(): Boolean = false

    actual fun infer(window: List<MotionData>): CoachingEvent? {
        val recent = window.takeLast(10)
        val maxDecel    = recent.maxOf { -it.userAccelY.toFloat() }
        val maxFwdAccel = recent.maxOf {  it.userAccelY.toFloat() }
        val maxLateral  = recent.maxOf { abs(it.userAccelX.toFloat()) }

        val now = (NSDate().timeIntervalSince1970 * 1000).toLong()
        return when {
            maxDecel    > HARD_BRAKING_G * GRAVITY ->
                CoachingEvent(CoachingEventType.HARSH_BRAKING, now)
            maxFwdAccel > HARD_ACCEL_G   * GRAVITY ->
                CoachingEvent(CoachingEventType.HARSH_ACCELERATION, now)
            maxLateral  > SHARP_TURN_G   * GRAVITY ->
                CoachingEvent(CoachingEventType.SHARP_TURN, now)
            else -> null
        }
    }
}
