package com.roadready.ml

import com.roadready.data.repository.DeviceMotionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Feeds a sliding window of IMU samples from [DeviceMotionService] into
 * [PlatformCoachingInference] and emits real-time [CoachingEvent]s.
 *
 * Sits entirely alongside the existing physics-based scoring pipeline —
 * it does not replace or modify it in any way.
 */
class RealTimeCoachingService(
    private val motionService: DeviceMotionService,
    private val inference: PlatformCoachingInference = PlatformCoachingInference(),
) {
    companion object {
        /** Number of motion samples required before inference runs. */
        const val WINDOW_SIZE = 50

        /** Minimum gap between two events of the same type (ms). */
        const val COOLDOWN_MS = 5_000L
    }

    private val _event = MutableStateFlow<CoachingEvent?>(null)
    val event: StateFlow<CoachingEvent?> = _event.asStateFlow()

    private val lastEventTs = mutableMapOf<CoachingEventType, Long>()
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job = scope.launch(Dispatchers.Default) {
            motionService.state.collect { state ->
                val window = state.data.takeLast(WINDOW_SIZE)
                if (window.size < WINDOW_SIZE) return@collect

                val coaching = inference.infer(window) ?: return@collect
                val now = Clock.System.now().toEpochMilliseconds()
                val last = lastEventTs[coaching.type] ?: 0L
                if (now - last >= COOLDOWN_MS) {
                    lastEventTs[coaching.type] = now
                    _event.value = coaching
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        lastEventTs.clear()
    }

    /** Call after the UI has consumed the event to reset the banner. */
    fun clearEvent() {
        _event.value = null
    }

    fun isModelLoaded(): Boolean = inference.isModelLoaded()
}
