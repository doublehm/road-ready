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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
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

    // ── Sensor services ─────────────────
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

    RequestLocationPermission { granted ->
        locationPermissionGranted = granted
        if (!granted) gpsAvailable = false
    }

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

    LaunchedEffect(isActive) {
        while (isActive) {
            delay(1000)
            elapsedSeconds++
        }
    }

    LaunchedEffect(motionState.acceleration) {
        prevAcceleration = motionState.acceleration
    }

    LaunchedEffect(gpsState.speed) {
        prevSpeed = gpsState.speed
    }

    LaunchedEffect(gpsState.location) {
        val loc = gpsState.location ?: return@LaunchedEffect
        speedLimitService.onLocationChanged(loc.latitude, loc.longitude)
    }

    LaunchedEffect(Unit) {
        delay(5000)
        if (gpsState.location == null) gpsAvailable = false
    }
    LaunchedEffect(gpsState.location) {
        if (gpsState.location != null) gpsAvailable = true
    }

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

    if (isSubmitting) {
        Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Primary)
                Spacer(Modifier.height(16.dp))
                Text("Analysing your ride…", color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium)
            }
        }
        return
    }

    val currentSpeed = gpsState.speed
    val currentAccel = motionState.acceleration
    val currentRotation = motionState.rotation
    val currentLimit = speedLimitState.currentSpeedLimit?.toInt()

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Live Map as Background
        Box(modifier = Modifier.fillMaxSize()) {
            val mapCoords = if (gpsState.routeCoordinates.isEmpty() && gpsState.location != null) {
                listOf(Coordinate(gpsState.location!!.latitude, gpsState.location!!.longitude))
            } else {
                gpsState.routeCoordinates
            }
            
            LiveMapSection(
                gpsState = gpsState.copy(routeCoordinates = mapCoords),
                events = events,
                currentLocation = gpsState.location?.let { it.latitude to it.longitude },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Immersive Content (Floating UI)
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {

            Spacer(Modifier.height(24.dp))
            
            // Recording Status & Timer
            ImmersiveHeader(isActive, elapsedSeconds)

            Spacer(Modifier.height(20.dp))

            // GPS warning
            if (!gpsAvailable) {
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Warning.copy(alpha = 0.2f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("📡 Searching for GPS signal…",
                        fontSize = 13.sp, color = Warning, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))
            }

            // Telemetry Panel (Compact & Modern)
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
                    val lastSame = events.lastOrNull { it.type == gaugeEvent.faultType }
                    if (lastSame == null || now - lastSame.timestamp > 3000) {
                        events = events + RideEvent(
                            timestamp = now,
                            type = gaugeEvent.faultType,
                            description = "${gaugeEvent.label}: ${gaugeEvent.value}",
                            severity = "high",
                            value = gaugeEvent.value,
                        )
                    }
                },
            )

            Spacer(Modifier.weight(1f))

            // Event log badges
            if (events.isNotEmpty()) {
                EventBadges(events)
                Spacer(Modifier.height(20.dp))
            }

            // Quick Actions (Supervisor)
            if (supervisorName != null) {
                SupervisorActions(onEvent = { type ->
                    events = events + RideEvent(
                        timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                        type = type,
                        description = "Manual: $type",
                        severity = "medium",
                    )
                })
                Spacer(Modifier.height(20.dp))
            }

            // End ride button
            PrimaryButton(
                text = "End Ride",
                onClick = { showEndConfirm = true },
                color = Error,
                enabled = elapsedSeconds > 10,
                modifier = Modifier.padding(bottom = 40.dp)
            )
        }
    }
}

@Composable
private fun ImmersiveHeader(isActive: Boolean, elapsedSeconds: Int) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = Background.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isActive) Secondary else Warning)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isActive) "RECORDING" else "PAUSED",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    letterSpacing = 1.sp
                )
            }
            Text(
                "${pad2(elapsedSeconds / 60)}:${pad2(elapsedSeconds % 60)}",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
            )
        }
    }
}

@Composable
private fun LiveMapSection(
    gpsState: GPSTrackingState, 
    events: List<RideEvent>, 
    currentLocation: Pair<Double, Double>?,
    modifier: Modifier = Modifier
) {
    val coords = remember(gpsState.routeCoordinates) {
        gpsState.routeCoordinates.map { it.latitude to it.longitude }
    }
    val routeEvents = remember(events, currentLocation) {
        events.mapNotNull { e ->
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
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier.background(SurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text("Waiting for GPS signal…", color = TextMuted)
        }
    }
}

@Composable
private fun EventBadges(events: List<RideEvent>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
    ) {
        events.takeLast(3).forEach { event ->
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (event.severity == "high") Error.copy(alpha = 0.8f) else Warning.copy(alpha = 0.8f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(event.type.replace("_", " "), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SupervisorActions(onEvent: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(
            onClick = { onEvent("hard_brake") },
            modifier = Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(16.dp)).background(Surface)
        ) {
            Text("🛑 Brake")
        }
        IconButton(
            onClick = { onEvent("sharp_turn") },
            modifier = Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(16.dp)).background(Surface)
        ) {
            Text("↩️ Turn")
        }
        IconButton(
            onClick = { onEvent("speeding") },
            modifier = Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(16.dp)).background(Surface)
        ) {
            Text("🚦 Speed")
        }
    }
}

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
                PrimaryButton(text = "End & See Results", onClick = onConfirm, color = Secondary)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCancel) { Text("Keep Riding", color = Primary) }
            }
        }
    }
}
