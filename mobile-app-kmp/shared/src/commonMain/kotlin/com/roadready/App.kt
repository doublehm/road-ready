package com.roadready

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.roadready.di.platformModule
import com.roadready.di.sharedModule
import com.roadready.ui.theme.Background
import com.roadready.ui.theme.RoadReadyTheme
import org.koin.compose.KoinApplication

@Composable
fun App() {
    KoinApplication(application = {
        modules(sharedModule, platformModule())
    }) {
        RoadReadyTheme {
            Surface(
                modifier = Modifier.fillMaxSize().systemBarsPadding(),
                color = Background,
            ) {
                RootContent()
            }
        }
    }
}
