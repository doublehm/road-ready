package com.roadready.android

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.roadready.App
import com.roadready.data.repository.AndroidContextHolder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidContextHolder.init(this)
        WebView.setWebContentsDebuggingEnabled(true)
        enableEdgeToEdge()
        setContent {
            App()
        }
    }
}
