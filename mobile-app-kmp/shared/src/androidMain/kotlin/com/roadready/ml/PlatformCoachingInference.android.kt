package com.roadready.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.roadready.data.repository.AndroidContextHolder
import com.roadready.data.repository.MotionData
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Android inference backend using ONNX Runtime.
 *
 * Loads driving_coach.onnx from assets and runs the 1D-CNN on each
 * 50-sample IMU window.  Falls back to physics-mirrored thresholds when
 * the model file is absent so coaching is functional from day one.
 *
 * Model input : float32 [1, 50, 4]  (batch, window, features)
 * Model output: float32 [1, 4]      (raw logits — softmax applied here)
 * Classes     : 0=normal  1=harsh_braking  2=harsh_accel  3=sharp_turn
 */
actual class PlatformCoachingInference actual constructor() {

    companion object {
        const val MODEL_ASSET = "driving_coach.onnx"
        const val WINDOW_SIZE = 50
        const val NUM_FEATURES = 4
        const val NUM_CLASSES = 4

        // Normalisation divisors (must match train_coaching_model.py)
        const val ACCEL_NORM = 20.0f
        const val JERK_NORM  = 30.0f

        // Minimum softmax probability to fire a model-based event
        const val CONFIDENCE_THRESHOLD = 0.70f

        // Threshold fallback — mirrors DiagnosticEvaluator constants
        const val GRAVITY        = 9.81f
        const val HARD_BRAKING_G = 0.6f
        const val HARD_ACCEL_G   = 0.4f
        const val SHARP_TURN_G   = 0.45f
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null

    init {
        tryLoadModel()
    }

    private fun tryLoadModel() {
        try {
            val ctx = AndroidContextHolder.applicationContext
            val bytes = ctx.assets.open(MODEL_ASSET).use { it.readBytes() }
            session = env.createSession(bytes, OrtSession.SessionOptions())
        } catch (_: Exception) {
            session = null
        }
    }

    actual fun isModelLoaded(): Boolean = session != null

    actual fun infer(window: List<MotionData>): CoachingEvent? =
        if (session != null) inferWithModel(window) else inferWithThresholds(window)

    // ── ONNX Runtime inference ───────────────────────────────────────────────────

    private fun inferWithModel(window: List<MotionData>): CoachingEvent? {
        val sess = session ?: return inferWithThresholds(window)

        val samples = window.takeLast(WINDOW_SIZE)
        val flat = FloatArray(WINDOW_SIZE * NUM_FEATURES)
        var prevAx = 0f; var prevAy = 0f; var prevAz = 0f

        samples.forEachIndexed { i, m ->
            val ax = m.userAccelX.toFloat()
            val ay = m.userAccelY.toFloat()
            val az = m.userAccelZ.toFloat()
            val jerk = sqrt(
                (ax - prevAx) * (ax - prevAx) +
                (ay - prevAy) * (ay - prevAy) +
                (az - prevAz) * (az - prevAz)
            )
            val base = i * NUM_FEATURES
            flat[base + 0] = (ax / ACCEL_NORM).coerceIn(-1f, 1f)
            flat[base + 1] = (ay / ACCEL_NORM).coerceIn(-1f, 1f)
            flat[base + 2] = (az / ACCEL_NORM).coerceIn(-1f, 1f)
            flat[base + 3] = (jerk / JERK_NORM).coerceIn(-1f, 1f)
            prevAx = ax; prevAy = ay; prevAz = az
        }

        return try {
            val shape = longArrayOf(1, WINDOW_SIZE.toLong(), NUM_FEATURES.toLong())
            val tensor = OnnxTensor.createTensor(env, java.nio.FloatBuffer.wrap(flat), shape)
            val inputName = sess.inputNames.first()
            val results = sess.run(mapOf(inputName to tensor))
            val logits = (results[0].value as Array<*>)[0] as FloatArray
            tensor.close(); results.close()

            val probs = softmax(logits)
            val best = probs.indices.maxByOrNull { probs[it] } ?: 0
            if (best == 0 || probs[best] < CONFIDENCE_THRESHOLD) return null

            val now = System.currentTimeMillis()
            when (best) {
                1 -> CoachingEvent(CoachingEventType.HARSH_BRAKING,      now, probs[best], fromModel = true)
                2 -> CoachingEvent(CoachingEventType.HARSH_ACCELERATION,  now, probs[best], fromModel = true)
                3 -> CoachingEvent(CoachingEventType.SHARP_TURN,          now, probs[best], fromModel = true)
                else -> null
            }
        } catch (_: Exception) {
            inferWithThresholds(window)
        }
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.max()
        val exps = logits.map { Math.exp((it - max).toDouble()).toFloat() }
        val sum = exps.sum()
        return exps.map { it / sum }.toFloatArray()
    }

    // ── Threshold fallback (mirrors backend physics system) ──────────────────────

    private fun inferWithThresholds(window: List<MotionData>): CoachingEvent? {
        val recent = window.takeLast(10)
        val maxDecel    = recent.maxOf { -it.userAccelY.toFloat() }
        val maxFwdAccel = recent.maxOf {  it.userAccelY.toFloat() }
        val maxLateral  = recent.maxOf { abs(it.userAccelX.toFloat()) }

        val now = System.currentTimeMillis()
        return when {
            maxDecel    > HARD_BRAKING_G * GRAVITY ->
                CoachingEvent(CoachingEventType.HARSH_BRAKING,     now, fromModel = false)
            maxFwdAccel > HARD_ACCEL_G   * GRAVITY ->
                CoachingEvent(CoachingEventType.HARSH_ACCELERATION, now, fromModel = false)
            maxLateral  > SHARP_TURN_G   * GRAVITY ->
                CoachingEvent(CoachingEventType.SHARP_TURN,         now, fromModel = false)
            else -> null
        }
    }
}
