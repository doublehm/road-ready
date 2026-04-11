package com.roadready.data.repository

actual class PlatformMotionProvider actual constructor() {
    actual fun startTracking(onUpdate: (RawSensorData) -> Unit) {
        println("Android motion sensors not yet implemented")
    }

    actual fun stopTracking() {
        println("Android motion stopTracking not yet implemented")
    }
}
