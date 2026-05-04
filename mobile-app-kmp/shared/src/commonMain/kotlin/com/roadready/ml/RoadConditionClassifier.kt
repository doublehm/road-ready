package com.roadready.ml

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Sliding 20-sample window feature extractor + platform ONNX inference wrapper.
 *
 * Features (5 total — must match train_road_condition_model.py):
 *   0. RMSA         — Root Mean Square Acceleration (correlated with IRI)
 *   1. peak_amp     — Peak-to-peak amplitude
 *   2. variance     — Z-axis variance
 *   3. hp_energy    — High-pass filtered energy (>2 Hz, removes suspension sway)
 *   4. speed_kmh    — Speed at window midpoint
 */
class RoadConditionClassifier(
    private val inference: PlatformConditionInference = PlatformConditionInference(),
) {
    companion object {
        const val WINDOW_SIZE = 20
        const val SAMPLE_HZ = 10.0f

        // 2nd-order Butterworth high-pass at 2 Hz, fs=10 Hz (bilinear transform)
        // Ω = tan(π·2/10) = 0.7265,  d = 1 + √2·Ω + Ω² = 2.5552
        private const val HP_B0 =  0.3914f   //  1/d
        private const val HP_B1 = -0.7828f   // -2/d
        private const val HP_B2 =  0.3914f   //  1/d
        private const val HP_A1 = -0.3699f   // 2(Ω²−1)/d
        private const val HP_A2 =  0.1959f   // (1−√2·Ω+Ω²)/d
    }

    fun isModelLoaded(): Boolean = inference.isModelLoaded()

    /**
     * Classify a 20-sample Z-axis window.
     *
     * @param zWindow  vertical accelerometer values in m/s² (length must be ≥ WINDOW_SIZE)
     * @param speedKmh GPS speed at the window midpoint
     */
    fun classify(zWindow: FloatArray, speedKmh: Float): RoadConditionLabel {
        require(zWindow.size >= WINDOW_SIZE) { "Window must have at least $WINDOW_SIZE samples" }
        val w = zWindow.takeLast(WINDOW_SIZE).toFloatArray()
        val features = extractFeatures(w, speedKmh)
        return inference.classify(features)
    }

    fun extractFeatures(w: FloatArray, speedKmh: Float): FloatArray {
        val rmsa = sqrt(w.map { it * it }.average()).toFloat()
        val peakAmp = w.max() - w.min()
        val mean = w.average().toFloat()
        val variance = w.map { (it - mean) * (it - mean) }.average().toFloat()
        val hpEnergy = highPassEnergy(w)
        return floatArrayOf(rmsa, peakAmp, variance, hpEnergy, speedKmh)
    }

    private fun highPassEnergy(x: FloatArray): Float {
        val n = x.size
        if (n < 3) return 0f

        val y = FloatArray(n)
        // Causal 2nd-order IIR high-pass; zero initial conditions
        y[0] = HP_B0 * x[0]
        if (n > 1) y[1] = HP_B0 * x[1] + HP_B1 * x[0] - HP_A1 * y[0]
        for (i in 2 until n) {
            y[i] = HP_B0 * x[i] + HP_B1 * x[i - 1] + HP_B2 * x[i - 2] -
                HP_A1 * y[i - 1] - HP_A2 * y[i - 2]
        }
        return y.map { it * it }.average().toFloat()
    }
}
