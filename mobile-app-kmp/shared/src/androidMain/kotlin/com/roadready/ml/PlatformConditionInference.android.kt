package com.roadready.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.roadready.data.repository.AndroidContextHolder

/**
 * Android actual: loads road_condition_classifier.onnx from assets and runs
 * the quantized Random Forest via ONNX Runtime.
 *
 * Model input : float32 [1, 5]  — [rmsa, peak_amp, variance, hp_energy, speed_kmh]
 * Model output: int64   [1]     — class index (zipmap=false)
 * Classes     : 0=smooth 1=rough 2=bump 3=pothole 4=speed_bump
 */
actual class PlatformConditionInference actual constructor() {

    companion object {
        const val MODEL_ASSET = "road_condition_classifier.onnx"
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

    actual fun classify(features: FloatArray): RoadConditionLabel {
        val sess = session ?: return classifyWithThresholds(features)
        return try {
            val shape = longArrayOf(1, 5)
            val tensor = OnnxTensor.createTensor(env, java.nio.FloatBuffer.wrap(features), shape)
            val inputName = sess.inputNames.first()
            val results = sess.run(mapOf(inputName to tensor))
            val labelIndex = ((results[0].value as LongArray)[0]).toInt()
            tensor.close()
            results.close()
            RoadConditionLabel.fromIndex(labelIndex)
        } catch (_: Exception) {
            classifyWithThresholds(features)
        }
    }

    // Threshold fallback mirrors seed-label logic in train_road_condition_model.py
    private fun classifyWithThresholds(features: FloatArray): RoadConditionLabel {
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
