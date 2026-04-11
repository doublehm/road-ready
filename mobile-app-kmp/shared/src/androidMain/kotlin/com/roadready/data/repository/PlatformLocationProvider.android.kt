package com.roadready.data.repository

import android.Manifest
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

actual class PlatformLocationProvider actual constructor() {

    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    actual fun startTracking(onUpdate: (LocationUpdate) -> Unit) {
        val context = AndroidContextHolder.applicationContext

        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            Log.w("PlatformLocationProvider", "Location permissions not granted — skipping GPS tracking")
            return
        }

        val client = LocationServices.getFusedLocationProviderClient(context)
        fusedClient = client

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                onUpdate(
                    LocationUpdate(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        speed = if (loc.hasSpeed()) loc.speed else null,
                        timestamp = loc.time,
                    )
                )
            }
        }
        locationCallback = callback

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    actual fun stopTracking() {
        locationCallback?.let { cb ->
            fusedClient?.removeLocationUpdates(cb)
        }
        locationCallback = null
        fusedClient = null
    }
}
