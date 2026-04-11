package com.roadready.data.repository

actual class PlatformMotionProvider actual constructor() {
    actual fun startTracking(onUpdate: (RawSensorData) -> Unit) {
        println("iOS motion sensors not yet implemented")
    }

    actual fun stopTracking() {
        println("iOS motion stopTracking not yet implemented")
    }
}
