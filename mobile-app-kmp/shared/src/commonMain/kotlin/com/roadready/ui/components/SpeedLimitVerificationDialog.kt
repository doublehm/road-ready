package com.roadready.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadready.ui.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Dialog shown when the ML discrepancy detector suspects the OSM speed limit
 * may be wrong.  Presents the 4 standard limits closest to the driver's current
 * speed plus a free-text field for custom input.
 */
@Composable
fun SpeedLimitVerificationDialog(
    osmSpeedKmh: Double,
    observedSpeedKmh: Double,
    onConfirm: (Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    val allLimits = listOf(30, 40, 50, 60, 70, 80, 90, 100, 110, 120)
    val suggestedLimits = allLimits
        .sortedBy { abs(it - observedSpeedKmh) }
        .take(4)
        .sorted()

    var selected by remember { mutableStateOf<Int?>(null) }
    var custom by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("⚠️", fontSize = 36.sp)
                Spacer(Modifier.height(12.dp))

                Text(
                    "Speed Limit Discrepancy Detected",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))

                Text(
                    "You've been driving at ${observedSpeedKmh.roundToInt()} km/h " +
                    "in a map-marked ${osmSpeedKmh.roundToInt()} km/h zone. " +
                    "What is the actual speed limit here? Your report helps correct map data for everyone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))

                Text(
                    "SELECT THE ACTUAL SPEED LIMIT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestedLimits.forEach { limit ->
                        val isSelected = selected == limit
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Primary else SurfaceVariant)
                                .border(
                                    width = 1.5.dp,
                                    color = if (isSelected) Primary else Color.White.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable { selected = limit; custom = "" }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "$limit",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = if (isSelected) Color.White else TextPrimary,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "OR ENTER A CUSTOM LIMIT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = custom,
                    onValueChange = { v ->
                        if (v.length <= 3 && v.all { it.isDigit() }) {
                            custom = v
                            selected = null
                        }
                    },
                    label = { Text("Speed limit (km/h)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = Primary,
                        unfocusedLabelColor = TextMuted,
                        cursorColor = Primary,
                    ),
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { onConfirm(null); onDismiss() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("Skip", color = TextMuted)
                    }
                    Button(
                        onClick = {
                            val speed = selected?.toDouble() ?: custom.toDoubleOrNull()
                            onConfirm(speed)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        enabled = selected != null || custom.isNotBlank(),
                    ) {
                        Text("Report", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
