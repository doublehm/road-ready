package com.roadready.data.repository

actual class PlatformLocationProvider actual constructor() {
    actual fun startTracking(onUpdate: (LocationUpdate) -> Unit) {
        println("iOS GPS not yet implemented")
    }

    actual fun stopTracking() {
        println("iOS GPS stopTracking not yet implemented")
    }
}
