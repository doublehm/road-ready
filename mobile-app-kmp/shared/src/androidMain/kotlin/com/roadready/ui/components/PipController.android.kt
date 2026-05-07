package com.roadready.ui.components

import android.os.Build
import android.util.Rational
import android.app.PictureInPictureParams
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.roadready.data.repository.AndroidContextHolder

@Composable
actual fun rememberPipController(): PipController {
    val isInPipMode by PipStateHolder.isInPipMode.collectAsState()
    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    return PipController(
        isInPipMode = isInPipMode,
        isSupported = supported,
        enter = enter@{
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@enter
            val activity = AndroidContextHolder.activity ?: return@enter
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(2, 3))   // portrait — fits full gauge dashboard
                .build()
            activity.enterPictureInPictureMode(params)
        },
    )
}
