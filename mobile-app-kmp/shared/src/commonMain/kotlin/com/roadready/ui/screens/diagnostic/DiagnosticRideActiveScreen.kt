package com.roadready.ui.screens.diagnostic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

// ── Sensor fault code → human name ──────────────────────────────────────────────

private val FAULT_NAMES = mapOf(
    "C1" to "Left Turn",
    "C2" to "Right Turn",
    "C3" to "Grip",
    "C4" to "Turn Rate",
    "A1" to "Hard Brake",
    "A2" to "Hard Accel",
    "D1" to "Smoothness",
    "E1" to "Vertical G",
)

private fun badgeLabel(event: RideEvent): String {
    val name = FAULT_NAMES[event.type]
    return if (name != null) "${event.type} · $name" else event.description.take(28)
}

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

    val gpsService = remember { GPSTrackingService(scope, PlatformLocationProvider()) }
    val motionService = remember { DeviceMotionService(PlatformMotionProvider()) }
    val speedLimitService = remember { SpeedLimitService(apiClient) }

    val gpsState by gpsService.state.collectAsState()
    val motionState by motionService.state.collectAsState()
    val speedLimitState by speedLimitService.state.collectAsState()

    var isActive by remember { mutableStateOf(true) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var showEndConfirm by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var gpsAvailable by remember { mutableStateOf(true) }
    var locationPermissionGranted by remember { mutableStateOf(false) }
    var events by remember { mutableStateOf<List<RideEvent>>(emptyList()) }
    var prevAcceleration by remember { mutableStateOf<Vec3?>(null) }
    var prevSpeed by remember { mutableStateOf(0.0) }
    var showIcbcSheet by remember { mutableStateOf(false) }
    var icbcObsCount by remember { mutableIntStateOf(0) }

    RequestLocationPermission { granted ->
        locationPermissionGranted = granted
        if (!granted) gpsAvailable = false
    }

    DisposableEffect(locationPermissionGranted) {
        if (locationPermissionGranted) gpsService.startTracking()
        motionService.startTracking()
        onDispose {
            gpsService.stopTracking()
            motionService.stopTracking()
        }
    }

    LaunchedEffect(isActive) {
        while (isActive) { delay(1000); elapsedSeconds++ }
    }
    LaunchedEffect(motionState.acceleration) { prevAcceleration = motionState.acceleration }
    LaunchedEffect(gpsState.speed) { prevSpeed = gpsState.speed }
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
                            add(buildJsonObject { put("latitude", it.latitude); put("longitude", it.longitude) })
                        }
                    }.toString()
                    val speedJson = buildJsonArray {
                        finalGps.speedData.forEach {
                            add(buildJsonObject {
                                put("timestamp", it.timestamp)
                                put("speed", it.speed)
                                put("lat", it.latitude)
                                put("lon", it.longitude)
                            })
                        }
                    }.toString()
                    val accelJson = buildJsonArray {
                        finalMotion.forEach {
                            add(buildJsonObject {
                                put("timestamp", it.timestamp.toDouble())
                                put("x", it.userAccelX); put("y", it.userAccelY); put("z", it.userAccelZ)
                            })
                        }
                    }.toString()
                    val rotationJson = buildJsonArray {
                        finalMotion.forEach {
                            add(buildJsonObject {
                                put("timestamp", it.timestamp.toDouble())
                                put("x", it.rotationX); put("y", it.rotationY); put("z", it.rotationZ)
                            })
                        }
                    }.toString()
                    val speedLimitJson = buildJsonArray {
                        speedLimitData.forEach {
                            add(buildJsonObject {
                                put("lat", it.latitude)
                                put("lon", it.longitude)
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
                Text("Analysing your ride…", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            }
        }
        return
    }

    val currentSpeed = gpsState.speed
    val currentAccel = motionState.acceleration
    val currentRotation = motionState.rotation
    val currentLimit = speedLimitState.currentSpeedLimit?.toInt()

    Box(modifier = Modifier.fillMaxSize()) {

        // 1. Full-screen live map
        val mapCoords = if (gpsState.routeCoordinates.isEmpty() && gpsState.location != null) {
            listOf(Coordinate(gpsState.location!!.latitude, gpsState.location!!.longitude))
        } else {
            gpsState.routeCoordinates
        }
        LiveMapSection(
            gpsState = gpsState.copy(routeCoordinates = mapCoords),
            events = events,
            currentLocation = gpsState.location?.let { it.latitude to it.longitude },
            modifier = Modifier.fillMaxSize(),
        )

        // 2. Keep screen on while ride is active
        KeepScreenOn()

        // 3. Telemetry HUD — arc gauges positioned at screen edges
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
            modifier = Modifier.fillMaxSize(),
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

        // 4. Column overlay: header, GPS warning, events, supervisor actions, end button
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(24.dp))

            // Recording status + timer + speed all in one header card
            ImmersiveHeader(
                isActive = isActive,
                elapsedSeconds = elapsedSeconds,
                speed = currentSpeed,
                speedLimit = currentLimit,
                roadName = speedLimitState.roadName,
                distance = gpsState.distance,
            )

            Spacer(Modifier.height(8.dp))

            // ICBC observe chip — right-aligned, below header, above map
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IcbcObserveChip(
                    observationCount = icbcObsCount,
                    onClick = { showIcbcSheet = true },
                )
            }

            Spacer(Modifier.height(8.dp))

            if (!gpsAvailable) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Warning.copy(alpha = 0.2f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("📡 Searching for GPS signal…", fontSize = 13.sp, color = Warning, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.weight(1f))

            PrimaryButton(
                text = "End Ride",
                onClick = { showEndConfirm = true },
                color = Error,
                enabled = elapsedSeconds > 10,
                modifier = Modifier.padding(bottom = 32.dp),
            )
        }

        // ── Event badge stack — right edge, below Observe chip ──────────────
        if (events.isNotEmpty()) {
            EventBadges(
                events = events,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 152.dp, end = 16.dp),
            )
        }

        // ── ICBC bottom sheet ────────────────────────────────────────────────
        if (showIcbcSheet) {
            IcbcObservationSheet(
                elapsedSeconds = elapsedSeconds,
                onObservation = { type, label, severity ->
                    icbcObsCount++
                    val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                    events = events + RideEvent(
                        timestamp = now,
                        type = type,
                        description = label,
                        severity = severity,
                    )
                },
                onDismiss = { showIcbcSheet = false },
            )
        }
    }
}

// ── Immersive header (timer + speed + limit + road name) ────────────────────────

@Composable
private fun ImmersiveHeader(
    isActive: Boolean,
    elapsedSeconds: Int,
    speed: Double,
    speedLimit: Int?,
    roadName: String?,
    distance: Double,
) {
    val speedColor = when {
        speedLimit == null -> TextPrimary
        speed > speedLimit + 10 -> Color(0xFFEF4444)
        speed > speedLimit -> Color(0xFFF59E0B)
        else -> Color(0xFF22C55E)
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = Background.copy(alpha = 0.82f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Recording dot + label
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(if (isActive) Secondary else Warning),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isActive) "REC" else "PAUSED",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    letterSpacing = 1.sp,
                )
            }

            // Speed (center)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        speed.toInt().toString(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = speedColor,
                    )
                    Text(
                        " km/h",
                        fontSize = 12.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(bottom = 4.dp, start = 2.dp),
                    )
                }
                if (!roadName.isNullOrBlank()) {
                    Text(
                        roadName,
                        fontSize = 10.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }

            // Timer + distance + speed limit sign (right)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.weight(1f),
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${pad2(elapsedSeconds / 60)}:${pad2(elapsedSeconds % 60)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "%.1f".format(distance),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                        Text(
                            " km",
                            fontSize = 9.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(bottom = 1.dp),
                        )
                    }
                }
                if (speedLimit != null) {
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .border(2.5.dp, Color.Red, CircleShape)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            speedLimit.toString(),
                            fontSize = 13.sp,
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }
    }
}

// ── LiveMapSection ──────────────────────────────────────────────────────────────

@Composable
private fun LiveMapSection(
    gpsState: GPSTrackingState,
    events: List<RideEvent>,
    currentLocation: Pair<Double, Double>?,
    modifier: Modifier = Modifier,
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
        PlatformOsmMap(coordinates = coords, events = routeEvents, modifier = modifier, followCurrentLocation = true)
    } else {
        Box(modifier = modifier.background(SurfaceVariant), contentAlignment = Alignment.Center) {
            Text("Waiting for GPS signal…", color = TextMuted)
        }
    }
}

// ── Event badges — vertical stack on right side ──────────────────────────────────

@Composable
private fun EventBadges(events: List<RideEvent>, modifier: Modifier = Modifier) {
    // Group by type, preserving first-occurrence order
    val grouped = events
        .groupBy { it.type }
        .entries
        .sortedBy { (_, list) -> list.first().timestamp }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.End,
    ) {
        grouped.takeLast(5).forEach { (_, eventList) ->
            val representative = eventList.last()
            val count = eventList.size
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (representative.severity == "high" || representative.severity == "error")
                            Error.copy(alpha = 0.88f)
                        else Warning.copy(alpha = 0.88f),
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    if (count > 1) "${badgeLabel(representative)} ×$count"
                    else badgeLabel(representative),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ── End ride confirmation dialog ─────────────────────────────────────────────────

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
                Text(
                    "End Diagnostic Ride?",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "This will stop recording and process your results.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = TextMuted,
                )
                Spacer(Modifier.height(24.dp))
                PrimaryButton(text = "End & See Results", onClick = onConfirm, color = Secondary)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCancel) { Text("Keep Riding", color = Primary) }
            }
        }
    }
}
