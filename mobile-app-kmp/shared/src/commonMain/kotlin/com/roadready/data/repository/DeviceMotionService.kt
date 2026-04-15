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
    val isLinearAcceleration: Boolean = false,
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
 * Processes raw accelerometer + gyroscope data, exposes smoothed user-acceleration
 * and rotation via [StateFlow].
 *
 * Signal processing pipeline:
 * 1. Gravity removal: uses Android TYPE_LINEAR_ACCELERATION (hardware sensor fusion)
 *    when available, falling back to a high-pass α-filter otherwise.
 * 2. Noise rejection: 5-sample sliding median filter (rejects impulse spikes while
 *    preserving sustained forces during braking/turning).
 * 3. State throttle: UI updates capped at ~6.7 Hz (150 ms interval).
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
        /** Ring buffer size for sliding median noise filter. */
        const val RING_BUFFER_SIZE = 5
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
        processAccelerometer(raw.accX.toDouble(), raw.accY.toDouble(), raw.accZ.toDouble(), raw.isLinearAcceleration)
        processGyroscope(raw.gyroX.toDouble(), raw.gyroY.toDouble(), raw.gyroZ.toDouble())
        syncData()
    }

    /**
     * Processes accelerometer data with optional gravity removal and median filtering.
     *
     * When [isLinear] is true (TYPE_LINEAR_ACCELERATION), gravity is already removed
     * via Android's hardware sensor fusion (gyro + accel + magnetometer), which handles
     * sustained events correctly. When false, falls back to a high-pass α-filter.
     *
     * Uses a 5-sample sliding median filter to remove impulse noise while preserving
     * sustained force values (unlike a moving average which blurs them).
     */
    private fun processAccelerometer(ax: Double, ay: Double, az: Double, isLinear: Boolean) {
        val userAccel: Vec3
        if (isLinear) {
            // TYPE_LINEAR_ACCELERATION: gravity already removed via sensor fusion
            userAccel = Vec3(ax, ay, az)
        } else {
            // Fallback: high-pass gravity filter for devices without sensor fusion
            if (gravity == null) {
                gravity = Vec3(ax, ay, az)
            }
            val cg = gravity!!
            val ng = Vec3(
                GRAVITY_ALPHA * cg.x + (1 - GRAVITY_ALPHA) * ax,
                GRAVITY_ALPHA * cg.y + (1 - GRAVITY_ALPHA) * ay,
                GRAVITY_ALPHA * cg.z + (1 - GRAVITY_ALPHA) * az,
            )
            gravity = ng
            userAccel = Vec3(ax - ng.x, ay - ng.y, az - ng.z)
        }

        // 5-sample sliding median filter (component-wise).
        // Median rejects impulse spikes without attenuating sustained forces.
        accelRing.add(userAccel)
        if (accelRing.size > RING_BUFFER_SIZE) accelRing.removeAt(0)

        val smoothed = Vec3(
            medianOf(accelRing.map { it.x }),
            medianOf(accelRing.map { it.y }),
            medianOf(accelRing.map { it.z }),
        )
        lastUserAccel = smoothed

        // Throttle state updates
        val now = currentTimeMillis()
        if (now - lastAccelStateUpdate >= STATE_THROTTLE_MS) {
            _state.value = _state.value.copy(acceleration = lastUserAccel)
            lastAccelStateUpdate = now
        }
    }

    private fun medianOf(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0
        else sorted[mid]
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
