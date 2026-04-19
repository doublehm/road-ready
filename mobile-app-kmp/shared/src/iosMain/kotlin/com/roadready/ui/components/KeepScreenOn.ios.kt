package com.roadready.ui.components

import androidx.compose.runtime.Composable
import platform.UIKit.UIApplication

@Composable
actual fun KeepScreenOn() {
    androidx.compose.runtime.DisposableEffect(Unit) {
        UIApplication.sharedApplication.idleTimerDisabled = true
        onDispose {
            UIApplication.sharedApplication.idleTimerDisabled = false
        }
    }
}
