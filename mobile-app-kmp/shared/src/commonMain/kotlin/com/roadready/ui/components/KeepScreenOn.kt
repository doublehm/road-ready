package com.roadready.ui.components

import androidx.compose.runtime.Composable

/** Prevents the display from sleeping while the composable is in the composition. */
@Composable
expect fun KeepScreenOn()
