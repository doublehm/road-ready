package com.roadready.data.repository

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

actual class PlatformMotionProvider actual constructor() {

    private var sensorManager: SensorManager? = null
    private var accelListener: SensorEventListener? = null
    private var gyroListener: SensorEventListener? = null

    private var latestAccel: FloatArray? = null
    private var latestGyro: FloatArray? = null

    actual fun startTracking(onUpdate: (RawSensorData) -> Unit) {
        val context = AndroidContextHolder.applicationContext
        val sm = context.getSystemService(SensorManager::class.java) ?: return
        sensorManager = sm

        latestAccel = null
        latestGyro = null

        val accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyroscope = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        accelListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                latestAccel = event.values.copyOf()
                emitIfReady(onUpdate)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        gyroListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                latestGyro = event.values.copyOf()
                emitIfReady(onUpdate)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        accelerometer?.let {
            sm.registerListener(accelListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sm.registerListener(gyroListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun emitIfReady(onUpdate: (RawSensorData) -> Unit) {
        val acc = latestAccel ?: return
        val gyr = latestGyro ?: return
        onUpdate(
            RawSensorData(
                accX = acc[0],
                accY = acc[1],
                accZ = acc[2],
                gyroX = gyr[0],
                gyroY = gyr[1],
                gyroZ = gyr[2],
            )
        )
    }

    actual fun stopTracking() {
        accelListener?.let { sensorManager?.unregisterListener(it) }
        gyroListener?.let { sensorManager?.unregisterListener(it) }
        accelListener = null
        gyroListener = null
        sensorManager = null
        latestAccel = null
        latestGyro = null
    }
}
