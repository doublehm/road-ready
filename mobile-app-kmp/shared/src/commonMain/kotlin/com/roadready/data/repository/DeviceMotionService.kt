package com.roadready.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

// ── Data classes ────────────────────────────────────────────────────────────────

data class RawSensorData(
    val accX: Float,
    val accY: Float,
    val accZ: Float,
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float,
)

@Serializable
data class MotionData(
    val timestamp: Long,
    val userAccelX: Double,
    val userAccelY: Double,
    val userAccelZ: Double,
    val rotationX: Double,
    val rotationY: Double,
    val rotationZ: Double,
)

data class Vec3(val x: Double, val y: Double, val z: Double)

data class DeviceMotionState(
    val isTracking: Boolean = false,
    val acceleration: Vec3 = Vec3(0.0, 0.0, 0.0),
    val rotation: Vec3 = Vec3(0.0, 0.0, 0.0),
    val data: List<MotionData> = emptyList(),
)

// ── Platform expect ─────────────────────────────────────────────────────────────

expect class PlatformMotionProvider() {
    fun startTracking(onUpdate: (RawSensorData) -> Unit)
    fun stopTracking()
}

// ── Service ─────────────────────────────────────────────────────────────────────

/**
 * Kotlin port of useDeviceMotion.js.
 *
 * Processes raw accelerometer + gyroscope data, removes gravity via a seeded
 * high-pass filter, applies a 3-sample moving-average ring buffer for noise
 * pre-filtering, and exposes smoothed user-acceleration and rotation via
 * [StateFlow].
 *
 * All thresholds and constants match the React Native hook exactly.
 */
class DeviceMotionService(
    private val motionProvider: PlatformMotionProvider,
    private val currentTimeMillis: () -> Long = { kotlinx.datetime.Clock.System.now().toEpochMilliseconds() },
) {
    companion object {
        /** Max sensor samples held in memory (~60 s at 10 Hz). */
        const val DATA_WINDOW = 600
        /** Minimum ms between reactive state updates. */
        const val STATE_THROTTLE_MS = 150L
        /** High-pass filter coefficient (α = 0.98 → ~5 s time constant at 10 Hz). */
        const val GRAVITY_ALPHA = 0.98
        /** Ring buffer size for moving-average noise filter. */
        const val RING_BUFFER_SIZE = 3
    }

    // ── Reactive state ──────────────────────────────────────────────────────────
    private val _state = MutableStateFlow(DeviceMotionState())
    val state: StateFlow<DeviceMotionState> = _state.asStateFlow()

    // ── Internal accumulators ───────────────────────────────────────────────────
    private val dataRef = mutableListOf<MotionData>()
    private var gravity: Vec3? = null
    private var lastUserAccel = Vec3(0.0, 0.0, 0.0)
    private var lastGyro = Vec3(0.0, 0.0, 0.0)
    private var lastAccelStateUpdate = 0L
    private var lastGyroStateUpdate = 0L
    private val accelRing = mutableListOf<Vec3>()

    // ── Public API ──────────────────────────────────────────────────────────────

    fun startTracking() {
        clearData()
        motionProvider.startTracking { raw -> onSensorUpdate(raw) }
        _state.value = _state.value.copy(isTracking = true)
    }

    fun stopTracking(): List<MotionData> {
        motionProvider.stopTracking()
        _state.value = _state.value.copy(
            isTracking = false,
            data = dataRef.toList(),
        )
        return dataRef.toList()
    }

    fun clearData() {
        dataRef.clear()
        gravity = null
        lastUserAccel = Vec3(0.0, 0.0, 0.0)
        lastGyro = Vec3(0.0, 0.0, 0.0)
        lastAccelStateUpdate = 0L
        lastGyroStateUpdate = 0L
        accelRing.clear()
        _state.value = DeviceMotionState()
    }

    // ── Internal ────────────────────────────────────────────────────────────────

    private fun onSensorUpdate(raw: RawSensorData) {
        processAccelerometer(raw.accX.toDouble(), raw.accY.toDouble(), raw.accZ.toDouble())
        processGyroscope(raw.gyroX.toDouble(), raw.gyroY.toDouble(), raw.gyroZ.toDouble())
        syncData()
    }

    /**
     * High-pass filter to remove gravity, followed by 3-sample moving average.
     * Mirrors the JS Accelerometer listener exactly.
     */
    private fun processAccelerometer(ax: Double, ay: Double, az: Double) {
        // Seed gravity from first reading
        val g = gravity
        if (g == null) {
            gravity = Vec3(ax, ay, az)
        }

        // α-filter to track gravity
        val currentGravity = gravity!!
        val newGravity = Vec3(
            GRAVITY_ALPHA * currentGravity.x + (1 - GRAVITY_ALPHA) * ax,
            GRAVITY_ALPHA * currentGravity.y + (1 - GRAVITY_ALPHA) * ay,
            GRAVITY_ALPHA * currentGravity.z + (1 - GRAVITY_ALPHA) * az,
        )
        gravity = newGravity

        // User acceleration = total - gravity (already in m/s² on Android)
        val rawUserAccel = Vec3(
            ax - newGravity.x,
            ay - newGravity.y,
            az - newGravity.z,
        )

        // 3-sample moving average ring buffer
        accelRing.add(rawUserAccel)
        if (accelRing.size > RING_BUFFER_SIZE) accelRing.removeAt(0)

        val smoothed = Vec3(
            accelRing.sumOf { it.x } / accelRing.size,
            accelRing.sumOf { it.y } / accelRing.size,
            accelRing.sumOf { it.z } / accelRing.size,
        )
        lastUserAccel = smoothed

        // Throttle state updates
        val now = currentTimeMillis()
        if (now - lastAccelStateUpdate >= STATE_THROTTLE_MS) {
            _state.value = _state.value.copy(acceleration = lastUserAccel)
            lastAccelStateUpdate = now
        }
    }

    private fun processGyroscope(gx: Double, gy: Double, gz: Double) {
        lastGyro = Vec3(gx, gy, gz)

        val now = currentTimeMillis()
        if (now - lastGyroStateUpdate >= STATE_THROTTLE_MS) {
            _state.value = _state.value.copy(rotation = lastGyro)
            lastGyroStateUpdate = now
        }
    }

    /** Append a data point; keep a rolling window bounded at [DATA_WINDOW]. */
    private fun syncData() {
        val timestamp = currentTimeMillis()
        val point = MotionData(
            timestamp = timestamp,
            userAccelX = lastUserAccel.x,
            userAccelY = lastUserAccel.y,
            userAccelZ = lastUserAccel.z,
            rotationX = lastGyro.x,
            rotationY = lastGyro.y,
            rotationZ = lastGyro.z,
        )
        dataRef.add(point)

        if (dataRef.size > DATA_WINDOW) {
            val excess = dataRef.size - DATA_WINDOW
            repeat(excess) { dataRef.removeAt(0) }
        }

        // Update state every 10 samples
        if (dataRef.size % 10 == 0) {
            _state.value = _state.value.copy(data = dataRef.takeLast(100))
        }
    }
}
