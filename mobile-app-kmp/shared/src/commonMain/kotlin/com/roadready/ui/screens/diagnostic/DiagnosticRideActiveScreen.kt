package com.roadready.ui.screens.diagnostic

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadready.data.remote.ApiClient
import com.roadready.data.repository.*
import com.roadready.ml.*
import com.roadready.ui.components.*
import com.roadready.ui.theme.*
import com.roadready.ui.util.pad2
import kotlin.math.abs
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.compose.koinInject
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

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
    val roadConditionClassifier = remember { RoadConditionClassifier() }
    val roadConditionRepository = remember { RoadConditionRepository(apiClient) }
    val hazardApproachDetector = remember { HazardApproachDetector(roadConditionRepository) }
    val elevationService = remember { ElevationService(apiClient) }
    val discrepancyService = remember { SpeedLimitDiscrepancyService(apiClient) }

    val gpsState by gpsService.state.collectAsState()
    val motionState by motionService.state.collectAsState()
    val speedLimitState by speedLimitService.state.collectAsState()
    val hazardApproach by hazardApproachDetector.approach.collectAsState()
    val elevationState by elevationService.state.collectAsState()
    val discrepancyState by discrepancyService.discrepancy.collectAsState()

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
    var roadConditionEvents by remember { mutableStateOf<List<RoadConditionEvent>>(emptyList()) }
    var hazardPrefetched by remember { mutableStateOf(false) }
    var lastKnownLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var lastClassifiedDataSize by remember { mutableIntStateOf(0) }

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
            hazardApproachDetector.reset()
            roadConditionRepository.clear()
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
        lastKnownLocation = loc.latitude to loc.longitude
        speedLimitService.onLocationChanged(loc.latitude, loc.longitude)
        elevationService.onLocationChanged(loc.latitude, loc.longitude)

        // Prefetch hazard cache at first valid GPS fix
        if (!hazardPrefetched) {
            hazardPrefetched = true
            scope.launch { roadConditionRepository.prefetchHazards(loc.latitude, loc.longitude) }
        }

        // 1 Hz approach detection — derive heading from last two route coordinates
        val coords = gpsState.routeCoordinates
        val heading = if (coords.size >= 2) {
            val prev = coords[coords.size - 2]
            val curr = coords.last()
            _bearingDeg(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
        } else 0.0
        hazardApproachDetector.onLocationUpdate(
            lat = loc.latitude,
            lon = loc.longitude,
            speedKmh = gpsState.speed,
            headingDeg = heading,
        )
    }
    LaunchedEffect(Unit) {
        delay(5000)
        if (gpsState.location == null) gpsAvailable = false
    }
    LaunchedEffect(gpsState.location) {
        if (gpsState.location != null) gpsAvailable = true
    }

    // Classify Z-axis windows for road condition detection (every 20 samples = 2 s)
    LaunchedEffect(Unit) {
        snapshotFlow { motionState.data.size }
            .collect { size ->
                val newCount = size - lastClassifiedDataSize
                if (newCount >= RoadConditionClassifier.WINDOW_SIZE) {
                    lastClassifiedDataSize = size
                    val window = motionState.data
                        .takeLast(RoadConditionClassifier.WINDOW_SIZE)
                        .map { it.userAccelZ.toFloat() }
                        .toFloatArray()
                    val loc = lastKnownLocation ?: return@collect
                    val speed = gpsState.speed.toFloat()
                    val label = roadConditionClassifier.classify(window, speed)
                    if (label != RoadConditionLabel.SMOOTH) {
                        val nowMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                        roadConditionEvents = roadConditionEvents + RoadConditionEvent(
                            lat = loc.first,
                            lon = loc.second,
                            speedKmh = speed,
                            label = label.name.lowercase(),
                            confidence = 0.8f,
                            timestamp = nowMs,
                        )
                    }
                }
            }
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

    // Elevation tip auto-dismiss after 5 s
    LaunchedEffect(elevationState.terrainTip) {
        if (elevationState.terrainTip != null) {
            delay(5_000)
            elevationService.clearTip()
        }
    }

    // Speed limit discrepancy monitor — feeds speed + limit every second
    LaunchedEffect(Unit) {
        snapshotFlow { gpsState.speed to speedLimitState.currentSpeedLimit }
            .collect { (speed, limit) ->
                val loc = lastKnownLocation ?: return@collect
                if (limit != null) {
                    discrepancyService.onSpeedAndLimit(
                        speedKmh = speed,
                        osmLimitKmh = limit,
                        lat = loc.first,
                        lon = loc.second,
                        nowMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                    )
                }
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
                        speedLimitState.surface?.let { put("road_surface", it) }
                        speedLimitState.smoothness?.let { put("road_smoothness", it) }
                        if (events.isNotEmpty()) {
                            put("evaluator_notes", events.joinToString("; ") { "${it.type}: ${it.description}" })
                        }
                    }
                    val capturedConditionEvents = roadConditionEvents
                    apiClient.completeRide(payload)
                        .onSuccess { created ->
                            apiClient.evaluateRide(created.id)
                            // Upload classified road condition events in background
                            if (capturedConditionEvents.isNotEmpty()) {
                                launch {
                                    roadConditionRepository.uploadTrip(
                                        rideId = created.id.toString(),
                                        events = capturedConditionEvents,
                                    )
                                }
                            }
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

    val pip = rememberPipController()

    // In PiP (float) mode show only the essential gauges — full UI is hidden
    if (pip.isInPipMode) {
        val recentAlert = remember(events) {
            val cutoff = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - 10_000
            events.lastOrNull { it.timestamp >= cutoff }?.description
        }
        PipMiniDashboard(
            speedKmh = gpsState.speed,
            limitKmh = speedLimitState.currentSpeedLimit,
            distanceKm = gpsState.distance,
            activeAlert = recentAlert,
        )
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
            speedKmh = gpsState.speed,
            modifier = Modifier.fillMaxSize(),
        )

        // 2. Keep screen on while ride is active
        KeepScreenOn()

        // Float mode button — tap to shrink the ride into a PiP corner while navigating
        if (pip.isSupported && gpsState.location != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 18.dp, end = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Background.copy(alpha = 0.82f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .clickable { pip.enter() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("⊡  Float", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            }
        }

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

        // 4. Bottom Floating HUD (Safety Alert + Speedometer + Controls)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Road Hazard Alert — slides up when approaching a confirmed hazard
            RoadHazardAlert(approach = hazardApproach)
            Spacer(Modifier.height(6.dp))
            // Safety Zone Alert — above speedometer, clear of side panels
            SafetyAlertOverlay(
                zoneType = speedLimitState.zoneType,
                isActive = speedLimitState.zoneType != "regular",
            )
            Spacer(Modifier.height(8.dp))
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

        // 7. Coaching, Speed & Terrain Alerts — floating below top HUD on the right
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 100.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (speedAlertVisible) SpeedAlertBanner(speedKmh = gpsState.speed.toInt(), limit = currentLimit ?: 0)
            coachingEvent?.let { CoachingBanner(it) }
            elevationState.terrainTip?.let { TerrainTipBanner(it, elevationState.gradePct, elevationState.category) }
        }

        // 8. Speed limit discrepancy verification dialog
        if (discrepancyState.isVisible) {
            SpeedLimitVerificationDialog(
                osmSpeedKmh = discrepancyState.osmSpeedKmh,
                observedSpeedKmh = discrepancyState.observedSpeedKmh,
                onConfirm = { reportedSpeed ->
                    scope.launch { discrepancyService.submitFlag(reportedSpeed) }
                },
                onDismiss = { discrepancyService.dismiss() },
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
    val targetSpeedColor = when {
        speedLimit == null    -> TextPrimary
        speed > speedLimit + 5 -> Error
        speed > speedLimit    -> Warning
        else                  -> Secondary
    }
    val speedColor by animateColorAsState(
        targetValue = targetSpeedColor,
        animationSpec = tween(300),
        label = "speedColor"
    )

    val maxDisplay = if (speedLimit != null) (speedLimit * 1.6f).coerceAtLeast(80f) else 120f
    val fraction by animateFloatAsState(
        targetValue = (speed / maxDisplay).coerceIn(0.0, 1.0).toFloat(),
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "speedFraction"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // ── Arc-ring speedometer ──
            Box(
                modifier = Modifier.size(152.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 7.dp.toPx()
                    val inset = stroke / 2f
                    val arcRect = Size(size.width - stroke, size.height - stroke)
                    val arcOffset = Offset(inset, inset)

                    // Background track
                    drawArc(
                        color = Color.White.copy(alpha = 0.07f),
                        startAngle = 150f,
                        sweepAngle = 240f,
                        useCenter = false,
                        topLeft = arcOffset,
                        size = arcRect,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    // Speed fill
                    if (fraction > 0f) {
                        drawArc(
                            color = speedColor,
                            startAngle = 150f,
                            sweepAngle = 240f * fraction,
                            useCenter = false,
                            topLeft = arcOffset,
                            size = arcRect,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                }

                // Speed number
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(Background.copy(alpha = 0.92f))
                        .border(1.dp, Color.White.copy(alpha = 0.06f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            speed.toInt().toString(),
                            fontSize = 52.sp,
                            fontWeight = FontWeight.Black,
                            color = speedColor,
                            lineHeight = 52.sp
                        )
                        Text(
                            "KM/H",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.5.sp
                        )
                    }
                }
            }

            // ── Speed limit sign (left of dial) ──
            if (speedLimit != null) {
                SpeedLimitSign(
                    limit = speedLimit,
                    accentColor = speedColor,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = (-104).dp)
                )
            }
        }

        if (!roadName.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    roadName.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun SpeedLimitSign(
    limit: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(50.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Color.White)
            .border(1.5.dp, Color(0xFF1F2937), RoundedCornerShape(7.dp)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Red header band — authentic BC style
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFDC2626))
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "MAXIMUM",
                fontSize = 6.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 0.5.sp
            )
        }

        // Speed number
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = limit.toString(),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Color.Black,
                lineHeight = 28.sp
            )
        }

        // Live compliance stripe — color animates with the arc ring
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(accentColor)
        )
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
    speedKmh: Double,
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
        PlatformOsmMap(
            coordinates = coords,
            events = routeEvents,
            modifier = modifier,
            followCurrentLocation = true,
            speedKmh = speedKmh,
        )
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

// ── Terrain tip banner ────────────────────────────────────────────────────────
// Shows elevation grade and a driving tip for 5 s. Uses teal/earth tones to
// distinguish from coaching (indigo) and speed alerts (red).

private val TerrainTeal = Color(0xFF0D9488)
private val TerrainTeal2 = Color(0xFF0F766E)

@Composable
private fun TerrainTipBanner(tip: String, gradePct: Double?, category: String) {
    val icon = when {
        category.contains("uphill")   -> "⛰️"
        category.contains("downhill") -> "🏔️"
        else                           -> "📍"
    }
    val gradeText = gradePct?.let { " (${if (it > 0) "+" else ""}${"%.1f".format(it)}%)" } ?: ""

    Box(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(TerrainTeal, TerrainTeal2)
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
                Text(icon, fontSize = 14.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "TERRAIN$gradeText",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = tip,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

// ── Bearing helper ────────────────────────────────────────────────────────────
private fun _bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = PI / 180.0
    val dLon = (lon2 - lon1) * r
    val y = sin(dLon) * cos(lat2 * r)
    val x = cos(lat1 * r) * sin(lat2 * r) - sin(lat1 * r) * cos(lat2 * r) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}

// ── Road Hazard Alert ─────────────────────────────────────────────────────────
// Slides up from below the safety zone pill when approaching a confirmed hazard.

private val HazardRed    = Color(0xFFDC2626)
private val HazardAmber  = Color(0xFFF59E0B)
private val HazardBg     = Color(0xFF120A00).copy(alpha = 0.95f)

@Composable
private fun RoadHazardAlert(approach: HazardApproach?, modifier: Modifier = Modifier) {
    val isVisible = approach != null
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        ) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier,
    ) {
        val a = approach ?: return@AnimatedVisibility
        val isPothole = a.label == RoadConditionLabel.POTHOLE || a.label == RoadConditionLabel.SPEED_BUMP
        val accentColor = if (isPothole) HazardRed else HazardAmber

        val icon = when (a.label) {
            RoadConditionLabel.POTHOLE    -> "⚠️"
            RoadConditionLabel.SPEED_BUMP -> "🚧"
            RoadConditionLabel.BUMP       -> "〰️"
            else                          -> "⚠️"
        }
        val distText = if (a.distanceMetres < 100) "${a.distanceMetres.toInt()} m"
                       else "${(a.distanceMetres / 10).toInt() * 10} m"

        Row(
            modifier = Modifier
                .wrapContentWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(HazardBg)
                .border(1.dp, accentColor.copy(alpha = 0.8f), RoundedCornerShape(22.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(icon, fontSize = 16.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = a.label.displayName.uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = "Ahead · $distText",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
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

// ── PiP mini dashboard — shown when the ride floats over other apps ──────────────

@Composable
private fun PipMiniDashboard(
    speedKmh: Double,
    limitKmh: Double?,
    distanceKm: Double,
    activeAlert: String?,
) {
    val isSpeeding = limitKmh != null && speedKmh > limitKmh + 5
    val speedColor = if (isSpeeding) Error else Color.White

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080D1A)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Live speed — big and readable in a small window
                Text(
                    "${speedKmh.toInt()}",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    color = speedColor,
                )

                // Speed limit sign
                if (limitKmh != null) {
                    Box(
                        modifier = Modifier
                            .border(2.5.dp, Error, CircleShape)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${limitKmh.toInt()}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                        )
                    }
                }
            }

            Text("km/h", fontSize = 10.sp, color = Color.White.copy(alpha = 0.45f))

            Spacer(Modifier.height(2.dp))

            if (activeAlert != null) {
                Text(
                    "⚠ ${activeAlert.take(30)}",
                    fontSize = 11.sp,
                    color = Warning,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            } else {
                Text(
                    "● ${"%.1f".format(distanceKm)} km",
                    fontSize = 11.sp,
                    color = Color(0xFF22C55E),
                )
            }
        }
    }
}
