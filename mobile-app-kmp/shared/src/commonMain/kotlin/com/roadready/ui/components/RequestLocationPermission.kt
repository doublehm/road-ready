package com.roadready.ui.components

import androidx.compose.runtime.Composable

/**
 * Platform composable that requests location permission at runtime.
 * [onResult] is called with `true` if granted, `false` if denied.
 * The request is triggered once when this composable enters composition.
 */
@Composable
expect fun RequestLocationPermission(onResult: (granted: Boolean) -> Unit)
