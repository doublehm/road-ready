package com.roadready.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * Controls Picture-in-Picture (floating) mode for the active diagnostic ride.
 * Android: uses native PiP API (no permission required).
 * iOS: not supported — isSupported will be false.
 */
@Stable
data class PipController(
    val isInPipMode: Boolean = false,
    val isSupported: Boolean = false,
    val enter: () -> Unit = {},
)

@Composable
expect fun rememberPipController(): PipController
