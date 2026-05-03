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
import com.roadready.ml.CoachingEvent
import com.roadready.ml.RealTimeCoachingService
import com.roadready.ui.components.*
import com.roadready.ui.theme.*
import com.roadready.ui.util.pad2
import androidx.compose.runtime.snapshotFlow
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
    val coachingService = remember { RealTimeCoachingService(motionService) }

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
    val coachingEvent by coachingService.event.collectAsState()
    var speedAlertVisible by remember { mutableStateOf(false) }

    val insightHistory = remember(events) {
        events.groupBy { it.type }.map { (type, list) ->
            val rep = list.last()
            InsightItem.Fault(
                id = "fault-$type",
                timestamp = list.first().timestamp,
                label = badgeLabel(rep),
                type = type,
                count = list.size,
                severity = rep.severity
            )
        }.sortedBy { it.timestamp }
    }

    RequestLocationPermission { granted ->
        locationPermissionGranted = granted
        if (!granted) gpsAvailable = false
    }

    DisposableEffect(locationPermissionGranted) {
        if (locationPermissionGranted) gpsService.startTracking()
        motionService.startTracking()
        coachingService.start(scope)
        onDispose {
            gpsService.stopTracking()
            motionService.stopTracking()
            coachingService.stop()
        }
    }

    // Auto-dismiss coaching advice after 3.5 seconds
    LaunchedEffect(coachingEvent) {
        if (coachingEvent != null) {
            delay(3_500)
            coachingService.clearEvent()
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

    // Real-time speeding alert: fires after 3 s of sustained speeding, with a 30 s cooldown.
    LaunchedEffect(Unit) {
        var speedingStartMs = 0L
        var lastAlertMs = 0L
        snapshotFlow { gpsState.speed to speedLimitState.currentSpeedLimit?.toInt() }
            .collect { (speed, limit) ->
                val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                if (limit != null && speed > limit + 5) {
                    if (speedingStartMs == 0L) speedingStartMs = now
                    if (now - speedingStartMs >= 3_000 && now - lastAlertMs >= 30_000) {
                        lastAlertMs = now
                        speedAlertVisible = true
                    }
                } else {
                    speedingStartMs = 0L
                }
            }
    }
    LaunchedEffect(speedAlertVisible) {
        if (speedAlertVisible) {
            delay(4_000)
            speedAlertVisible = false
        }
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

    Box(modifier = Modifier.fillMaxSize().background(Background)) {

        // 1. Full-screen live map — THE BACKGROUND
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

        // 3. Top Floating HUD (Timer + Distance + Status)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val avgSpeed = remember(gpsState.speedData) {
                if (gpsState.speedData.isEmpty()) 0.0
                else gpsState.speedData.map { it.speed }.average()
            }

            FloatingSessionCard(
                isActive = isActive,
                elapsedSeconds = elapsedSeconds,
                distance = gpsState.distance,
                avgSpeed = avgSpeed
            )            
            if (!gpsAvailable) {
                Spacer(Modifier.height(12.dp))
                GpsWarningBanner()
            }
        }

        // 4. Bottom Floating HUD (Speedometer + Controls)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Large floating Speedometer
            FloatingSpeedometer(
                speed = currentSpeed,
                speedLimit = currentLimit,
                roadName = speedLimitState.roadName
            )
            
            Spacer(Modifier.height(20.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Secondary action: ICBC Observe
                IcbcObserveButton(
                    observationCount = icbcObsCount,
                    onClick = { showIcbcSheet = true }
                )
                
                // Primary action: End Ride
                EndRideButton(
                    enabled = elapsedSeconds > 10,
                    onClick = { showEndConfirm = true }
                )
            }
        }

        // 5. Side Telemetry (Tucked to edges, vertically centered)
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

        // 6. Physics Insight Rail — floating below top HUD on the left
        InsightRail(
            activeAdvice = null, // Logic moved to separate side popup
            history = insightHistory,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 100.dp, start = 16.dp),
        )

        // 7. Coaching & Speeding Alerts — floating below top HUD on the right
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 100.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (speedAlertVisible) SpeedAlertBanner(speedKmh = gpsState.speed.toInt(), limit = currentLimit ?: 0)
            coachingEvent?.let { CoachingBanner(it) }
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

// ── New HUD Components — Modern Glassmorphism ────────────────────────────────

@Composable
private fun FloatingSessionCard(
    isActive: Boolean,
    elapsedSeconds: Int,
    distance: Double,
    avgSpeed: Double
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Background.copy(alpha = 0.85f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // REC Dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isActive) Secondary else Warning)
            )
            Spacer(Modifier.width(12.dp))
            
            // Timer
            Text(
                "${pad2(elapsedSeconds / 60)}:${pad2(elapsedSeconds % 60)}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            
            Spacer(Modifier.width(14.dp))
            Box(Modifier.width(1.dp).height(16.dp).background(Color.White.copy(alpha = 0.2f)))
            Spacer(Modifier.width(14.dp))
            
            // Distance
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "%.1f".format(distance),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        " km",
                        fontSize = 10.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(start = 2.dp, bottom = 1.dp)
                    )
                }
                Text("DISTANCE", fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.width(14.dp))
            Box(Modifier.width(1.dp).height(16.dp).background(Color.White.copy(alpha = 0.2f)))
            Spacer(Modifier.width(14.dp))

            // Avg Speed
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        avgSpeed.toInt().toString(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        " avg",
                        fontSize = 10.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(start = 2.dp, bottom = 1.dp)
                    )
                }
                Text("KM/H", fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold)
            }
        }
    }
}


@Composable
private fun FloatingSpeedometer(
    speed: Double,
    speedLimit: Int?,
    roadName: String?
) {
    val speedColor = when {
        speedLimit == null -> TextPrimary
        speed > speedLimit + 5 -> Error
        speed > speedLimit -> Warning
        else -> Secondary
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // ── The Speedometer (Absolute Center) ──
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(Background.copy(alpha = 0.85f))
                    .border(2.dp, speedColor.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        speed.toInt().toString(),
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Black,
                        color = speedColor
                    )
                    Text(
                        "KM/H",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                }
            }

            // ── The Speed Limit (Regulatory Pillar) ──
            if (speedLimit != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = (-94).dp) // Offset to the left of the 140dp circle
                        .width(42.dp)
                        .height(58.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White)
                        .border(1.5.dp, Color(0xFF1F2937), RoundedCornerShape(6.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "MAXIMUM",
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                    Text(
                        speedLimit.toString(),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black,
                        lineHeight = 24.sp
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
        
        if (!roadName.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Background.copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    roadName.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}


@Composable
private fun GpsWarningBanner() {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Warning.copy(alpha = 0.15f))
            .border(1.dp, Warning.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            "📡 Searching for GPS signal…",
            fontSize = 13.sp,
            color = Warning,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun IcbcObserveButton(
    observationCount: Int,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariant.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) {
        Text("👁️", fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            "Observe",
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        if (observationCount > 0) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    observationCount.toString(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EndRideButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Error.copy(alpha = 0.9f),
            disabledContainerColor = Error.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
        Text(
            "End Ride",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            letterSpacing = 0.5.sp
        )
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

// ── Real-time speeding alert ──────────────────────────────────────────────────
// Shown for 4 seconds after 3 s of sustained speeding; 30 s cooldown.

@Composable
private fun SpeedAlertBanner(speedKmh: Int, limit: Int) {
    Box(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(Color(0xFFDC2626), Color(0xFFB91C1C))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("⚠", fontSize = 14.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "SPEEDING",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = "$speedKmh km/h in a $limit km/h zone",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

// ── Real-time coaching advice popup ──────────────────────────────────────────
// Shown for 3.5 seconds when the on-device model detects a driving fault.
// Uses Indigo theme to distinguish from Physics Faults.
// Positioned on the right side to avoid overlap.

@Composable
private fun CoachingBanner(event: CoachingEvent) {
    val Indigo500 = Color(0xFF6366F1)
    val Indigo600 = Color(0xFF4F46E5)

    Box(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(Indigo500, Indigo600)
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text("✨", fontSize = 14.sp)
            }
            
            Spacer(Modifier.width(10.dp))
            
            Column {
                Text(
                    text = event.type.label.uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    text = event.type.message,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
