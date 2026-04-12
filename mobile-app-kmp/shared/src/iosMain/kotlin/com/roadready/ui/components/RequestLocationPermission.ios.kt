package com.roadready.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
actual fun RequestLocationPermission(onResult: (granted: Boolean) -> Unit) {
    // iOS permission handling not yet implemented
    LaunchedEffect(Unit) {
        onResult(false)
    }
}
