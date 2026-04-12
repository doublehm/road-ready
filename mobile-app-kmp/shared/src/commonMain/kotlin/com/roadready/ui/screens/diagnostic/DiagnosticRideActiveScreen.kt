package com.roadready.ui.screens.diagnostic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadready.data.remote.ApiClient
import com.roadready.data.repository.*
import com.roadready.ui.components.*
import com.roadready.ui.theme.*
import com.roadready.ui.util.pad2
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.compose.koinInject

// ── Tracked event during the ride ───────────────────────────────────────────────

private data class RideEvent(
    val timestamp: Long,
    val type: String,
    val description: String,
    val severity: String = "medium",
    val value: Double = 0.0,
)

// ── Screen ──────────────────────────────────────────────────────────────────────

@Composable
fun DiagnosticRideActiveScreen(
    rideType: String,
    supervisorName: String?,
    bookingId: Int?,
    onRideComplete: (rideId: Int?) -> Unit,
    onCancel: () -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    val scope = rememberCoroutineScope()

    // ── Sensor services (not in Koin — instantiate locally) ─────────────────
    val gpsService = remember {
        GPSTrackingService(scope, PlatformLocationProvider())
    }
    val motionService = remember {
        DeviceMotionService(PlatformMotionProvider())
    }
    val speedLimitService = remember {
        SpeedLimitService(apiClient)
    }

    // ── Collect sensor state flows ──────────────────────────────────────────
    val gpsState by gpsService.state.collectAsState()
    val motionState by motionService.state.collectAsState()
    val speedLimitState by speedLimitService.state.collectAsState()

    // ── Local UI state ──────────────────────────────────────────────────────
    var isActive by remember { mutableStateOf(true) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var showEndConfirm by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var gpsAvailable by remember { mutableStateOf(true) }
    var locationPermissionGranted by remember { mutableStateOf(false) }
    var events by remember { mutableStateOf<List<RideEvent>>(emptyList()) }
    var prevAcceleration by remember { mutableStateOf<Vec3?>(null) }
    var prevSpeed by remember { mutableStateOf(0.0) }

    // ── Request location permission, then start sensors ─────────────────────
    RequestLocationPermission { granted ->
        locationPermissionGranted = granted
        if (!granted) gpsAvailable = false
    }

    // ── Start services once permission is granted ───────────────────────────
    DisposableEffect(locationPermissionGranted) {
        if (locationPermissionGranted) {
            gpsService.startTracking()
        }
        motionService.startTracking()
        onDispose {
            gpsService.stopTracking()
            motionService.stopTracking()
        }
    }

    // ── Timer (1 s tick) ────────────────────────────────────────────────────
    LaunchedEffect(isActive) {
        while (isActive) {
            delay(1000)
            elapsedSeconds++
        }
    }

    // ── Track previous acceleration for jerk calculation ────────────────────
    LaunchedEffect(motionState.acceleration) {
        prevAcceleration = motionState.acceleration
    }

    // ── Track previous speed for braking detection ──────────────────────────
    LaunchedEffect(gpsState.speed) {
        prevSpeed = gpsState.speed
    }

    // ── Query speed limit when GPS location updates ─────────────────────────
    LaunchedEffect(gpsState.location) {
        val loc = gpsState.location ?: return@LaunchedEffect
        speedLimitService.onLocationChanged(loc.latitude, loc.longitude)
    }

    // ── Detect GPS availability (show warning after 5 s with no fix) ────────
    LaunchedEffect(Unit) {
        delay(5000)
        if (gpsState.location == null) gpsAvailable = false
    }
    // Clear warning once GPS starts working
    LaunchedEffect(gpsState.location) {
        if (gpsState.location != null) gpsAvailable = true
    }

    // ── End-ride confirmation dialog ────────────────────────────────────────
    if (showEndConfirm) {
        EndRideConfirmation(
            onConfirm = {
                showEndConfirm = false
                isActive = false
                isSubmitting = true

                val finalGps = gpsService.stopTracking()
                val finalMotion = motionService.stopTracking()
                val speedLimitData = speedLimitService.getSpeedLimitData()

                scope.launch {
                    val now = kotlinx.datetime.Clock.System.now().toString()
                    val startTimeStr = kotlinx.datetime.Instant.fromEpochMilliseconds(
                        finalGps.speedData.firstOrNull()?.timestamp
                            ?: (kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - elapsedSeconds * 1000L)
                    ).toString()

                    val routeJson = buildJsonArray {
                        finalGps.routeCoordinates.forEach {
                            add(buildJsonObject {
                                put("latitude", it.latitude)
                                put("longitude", it.longitude)
                            })
                        }
                    }.toString()

                    val speedJson = buildJsonArray {
                        finalGps.speedData.forEach {
                            add(buildJsonObject {
                                put("timestamp", it.timestamp.toDouble())
                                put("speed", it.speed)
                                put("latitude", it.latitude)
                                put("longitude", it.longitude)
                            })
                        }
                    }.toString()

                    val accelJson = buildJsonArray {
                        finalMotion.forEach {
                            add(buildJsonObject {
                                put("timestamp", it.timestamp.toDouble())
                                put("x", it.userAccelX)
                                put("y", it.userAccelY)
                                put("z", it.userAccelZ)
                            })
                        }
                    }.toString()

                    val rotationJson = buildJsonArray {
                        finalMotion.forEach {
                            add(buildJsonObject {
                                put("timestamp", it.timestamp.toDouble())
                                put("x", it.rotationX)
                                put("y", it.rotationY)
                                put("z", it.rotationZ)
                            })
                        }
                    }.toString()

                    val speedLimitJson = buildJsonArray {
                        speedLimitData.forEach {
                            add(buildJsonObject {
                                put("latitude", it.latitude)
                                put("longitude", it.longitude)
                                put("speed_limit", it.speedLimit)
                            })
                        }
                    }.toString()

                    val payload = buildJsonObject {
                        put("ride_type", rideType)
                        put("start_time", startTimeStr)
                        put("end_time", now)
                        put("duration_minutes", elapsedSeconds / 60.0)
                        put("distance_km", finalGps.distance)
                        if (bookingId != null) put("booking_id", bookingId)
                        put("route_coords", routeJson)
                        put("speed_data", speedJson)
                        put("acceleration_data", accelJson)
                        put("rotation_data", rotationJson)
                        put("speed_limit_data", speedLimitJson)
                        if (events.isNotEmpty()) {
                            put("evaluator_notes", events.joinToString("; ") { "${it.type}: ${it.description}" })
                        }
                    }
                    apiClient.completeRide(payload)
                        .onSuccess { created ->
                            // Trigger backend evaluation to compute scores
                            apiClient.evaluateRide(created.id)
                            onRideComplete(created.id)
                        }
                        .onFailure { onRideComplete(null) }
                }
            },
            onCancel = { showEndConfirm = false },
        )
        return
    }

    // ── Loading overlay while submitting ────────────────────────────────────
    if (isSubmitting) {
        Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Primary)
                Spacer(Modifier.height(16.dp))
                Text("Processing ride data…", color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium)
            }
        }
        return
    }

    val currentSpeed = gpsState.speed
    val currentAccel = motionState.acceleration
    val currentRotation = motionState.rotation
    val currentLimit = speedLimitState.currentSpeedLimit?.toInt()

    // ── Main content ────────────────────────────────────────────────────────
    Column(modifier = Modifier.fillMaxSize()) {
        // 1. Recording banner
        RecordingBanner(isActive, elapsedSeconds)

        // Scrollable body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // GPS warning
            if (!gpsAvailable) {
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Warning.copy(alpha = 0.15f))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("📡 GPS not available — waiting for signal…",
                        fontSize = 12.sp, color = Warning, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
            }

            // 2. Telemetry panel
            TelemetryPanel(
                acceleration = currentAccel,
                rotation = currentRotation,
                prevAcceleration = prevAcceleration,
                sampleIntervalMs = DeviceMotionService.STATE_THROTTLE_MS,
                speed = currentSpeed,
                prevSpeed = prevSpeed,
                isActive = isActive,
                duration = elapsedSeconds,
                distance = gpsState.distance,
                speedLimit = currentLimit,
                zoneType = speedLimitState.zoneType,
                roadName = speedLimitState.roadName,
                onThresholdExceeded = { gaugeEvent ->
                    val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                    // Debounce: skip if same gauge fired within last 3 s
                    val lastSame = events.lastOrNull { it.type == gaugeEvent.faultType }
                    if (lastSame == null || now - lastSame.timestamp > 3000) {
                        events = events + RideEvent(
                            timestamp = now,
                            type = gaugeEvent.faultType,
                            description = "${gaugeEvent.label}: ${gaugeEvent.value.let {
                                if (it < 10) "%.2f".let { _ ->
                                    val factor = 100.0
                                    val rounded = (gaugeEvent.value * factor).toInt() / factor
                                    rounded.toString()
                                } else gaugeEvent.value.toInt().toString()
                            }} ${gaugeEvent.unit}",
                            severity = "high",
                            value = gaugeEvent.value,
                        )
                    }
                },
            )

            Spacer(Modifier.height(8.dp))

            // 3. Live map
            LiveMapSection(
                gpsState,
                events,
                gpsState.location?.let { it.latitude to it.longitude },
            )

            Spacer(Modifier.height(8.dp))

            // Event log
            if (events.isNotEmpty()) {
                EventLogSection(events, elapsedSeconds)
                Spacer(Modifier.height(8.dp))
            }

            // 4. Supervisor feedback buttons
            if (supervisorName != null) {
                SupervisorFeedback(onEvent = { type ->
                    events = events + RideEvent(
                        timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                        type = type,
                        description = "Manual: $type",
                        severity = "medium",
                    )
                })
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(60.dp)) // room for bottom buttons
        }

        // 5. End ride / cancel buttons (pinned to bottom)
        BottomButtons(
            elapsedSeconds = elapsedSeconds,
            onEnd = { showEndConfirm = true },
            onCancel = onCancel,
        )
    }
}

// ── Recording banner ────────────────────────────────────────────────────────────

@Composable
private fun RecordingBanner(isActive: Boolean, elapsedSeconds: Int) {
    Surface(color = if (isActive) Accent else Surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (isActive) "🔴 RECORDING" else "⏸ PAUSED",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Text(
                "${pad2(elapsedSeconds / 60)}:${pad2(elapsedSeconds % 60)}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
            )
        }
    }
}

// ── Live map section ────────────────────────────────────────────────────────────

@Composable
private fun LiveMapSection(gpsState: GPSTrackingState, events: List<RideEvent>, currentLocation: Pair<Double, Double>?) {
    val coords = remember(gpsState.routeCoordinates) {
        gpsState.routeCoordinates.map { it.latitude to it.longitude }
    }
    val routeEvents = remember(events) {
        events.mapNotNull { e ->
            // Only include events that have valid coordinates
            val loc = currentLocation ?: return@mapNotNull null
            RouteEvent(
                type = e.type,
                lat = loc.first,
                lng = loc.second,
                severity = e.severity,
                description = e.description,
                timestamp = e.timestamp,
            )
        }
    }

    if (coords.isNotEmpty()) {
        PlatformOsmMap(
            coordinates = coords,
            events = routeEvents,
            height = 200.dp,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)),
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🗺️", fontSize = 32.sp)
                Spacer(Modifier.height(4.dp))
                Text("Waiting for route data…", fontSize = 13.sp, color = TextMuted)
            }
        }
    }
}

// ── Event log ───────────────────────────────────────────────────────────────────

@Composable
private fun EventLogSection(events: List<RideEvent>, elapsedSeconds: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariant.copy(alpha = 0.5f))
            .padding(10.dp),
    ) {
        Text("⚡ ${events.size} Events Detected", fontSize = 12.sp,
            fontWeight = FontWeight.Bold, color = Warning)
        Spacer(Modifier.height(4.dp))
        for (event in events.takeLast(5).reversed()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(event.description, fontSize = 10.sp, color = TextSecondary,
                    modifier = Modifier.weight(1f), maxLines = 1)
                val severityColor = when (event.severity) {
                    "high" -> Color(0xFFEF4444)
                    "medium" -> Color(0xFFF59E0B)
                    else -> Color(0xFF64748B)
                }
                Text(event.type, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = severityColor)
            }
        }
    }
}

// ── Supervisor feedback buttons ─────────────────────────────────────────────────

@Composable
private fun SupervisorFeedback(onEvent: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Quick Events", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickEventButton("🛑 Hard Brake", Modifier.weight(1f)) { onEvent("hard_brake") }
                QuickEventButton("↩️ Sharp Turn", Modifier.weight(1f)) { onEvent("sharp_turn") }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickEventButton("🚦 Speed", Modifier.weight(1f)) { onEvent("speeding") }
                QuickEventButton("⚠️ Other", Modifier.weight(1f)) { onEvent("other_event") }
            }
        }
    }
}

@Composable
private fun QuickEventButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

// ── Bottom buttons ──────────────────────────────────────────────────────────────

@Composable
private fun BottomButtons(elapsedSeconds: Int, onEnd: () -> Unit, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
        ) { Text("Cancel") }
        Button(
            onClick = onEnd,
            modifier = Modifier.weight(2f),
            colors = ButtonDefaults.buttonColors(containerColor = Error),
            shape = RoundedCornerShape(12.dp),
            enabled = elapsedSeconds > 30,
        ) {
            Text("End Ride", style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ── End ride confirmation ───────────────────────────────────────────────────────

@Composable
private fun EndRideConfirmation(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🏁", style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(12.dp))
                Text("End Diagnostic Ride?",
                    style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("This will stop recording and process your results.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center, color = TextMuted)
                Spacer(Modifier.height(24.dp))
                PrimaryButton(text = "End & See Results", onClick = onConfirm, color = Accent)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCancel) { Text("Keep Riding", color = Primary) }
            }
        }
    }
}
