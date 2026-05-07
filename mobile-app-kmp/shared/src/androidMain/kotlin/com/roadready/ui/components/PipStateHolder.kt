package com.roadready.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Updated by MainActivity.onPictureInPictureModeChanged. */
object PipStateHolder {
    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    fun setInPipMode(value: Boolean) {
        _isInPipMode.value = value
    }
}
