package com.roadready.data.repository

actual class PlatformLocationProvider actual constructor() {
    actual fun startTracking(onUpdate: (LocationUpdate) -> Unit) {
        println("Android GPS not yet implemented")
    }

    actual fun stopTracking() {
        println("Android GPS stopTracking not yet implemented")
    }
}
