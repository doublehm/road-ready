package com.roadready.android

import android.content.res.Configuration
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.roadready.App
import com.roadready.data.repository.AndroidContextHolder
import com.roadready.ui.components.PipStateHolder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidContextHolder.init(this)
        AndroidContextHolder.setActivity(this)
        WebView.setWebContentsDebuggingEnabled(true)
        enableEdgeToEdge()
        setContent {
            App()
        }
    }

    override fun onResume() {
        super.onResume()
        AndroidContextHolder.setActivity(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        AndroidContextHolder.setActivity(null)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipStateHolder.setInPipMode(isInPictureInPictureMode)
    }
}
