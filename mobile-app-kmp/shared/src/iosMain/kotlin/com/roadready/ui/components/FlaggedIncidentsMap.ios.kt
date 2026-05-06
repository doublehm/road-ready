package com.roadready.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.roadready.ui.theme.TextMuted

@Composable
actual fun FlaggedIncidentsMap(
    segments: List<SpeedSegment>,
    flags: List<SpeedFlag>,
    onFlagTapped: (SpeedFlag) -> Unit,
    modifier: Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Map not available on iOS yet", color = TextMuted)
    }
}
