package com.roadready.ui.screens.diagnostic

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadready.data.remote.ApiClient
import com.roadready.data.repository.*
import com.roadready.ml.*
import com.roadready.ui.components.*
import com.roadready.ui.theme.*
import com.roadready.ui.util.pad2
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.compose.koinInject
import kotlin.math.*

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

// ── Road Hazard Constants ─────────────────────────────────────────────────────

private val HazardRed    = Color(0xFFDC2626)
private val HazardAmber  = Color(0xFFF59E0B)
private val HazardBg     = Color(0xFF120A00)

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
    val elevationService = remember { ElevationService() }
    val discrepancyService = remember { SpeedLimitDiscrepancyService(apiClient) }

    val gpsState by gpsService.state.collectAsState()
    val motionState by motionService.state.collectAsState()
    val speedLimitState by speedLimitService.state.collectAsState()
    val hazardApproach by hazardApproachDetector.approach.collectAsState()
    val elevationState by elevationService.state.collectAsState()
    val discrepancyState by discrepancyService.isOverSpeedVisible.collectAsState()
    val pulses by discrepancyService.pulses.collectAsState()

    var activeReportCategory by remember { mutableStateOf<ReportCategory?>(null) }
    var showReportingTab by remember { mutableStateOf(false) }
    var showObserveTab by remember { mutableStateOf(false) }
    var isActive by remember { mutableStateOf(true) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var showEndConfirm by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var gpsAvailable by remember { mutableStateOf(true) }
    var locationPermissionGranted by remember { mutableStateOf(false) }
    var events by remember { mutableStateOf<List<RideEvent>>(emptyList()) }
    var prevAcceleration by remember { mutableStateOf<Vec3?>(null) }
    var prevSpeed by remember { mutableStateOf(0.0) }
    var icbcObsCount by remember { mutableIntStateOf(0) }
    var itemStates by remember { mutableStateOf(mapOf<String, ObsState>()) }
    val coachingEvent by coachingService.event.collectAsState()
    var speedAlertVisible by remember { mutableStateOf(false) }
    var roadConditionEvents by remember { mutableStateOf<List<RoadConditionEvent>>(emptyList()) }
    var hazardPrefetched by remember { mutableStateOf(false) }
    var lastKnownLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var lastClassifiedDataSize by remember { mutableIntStateOf(0) }
    var elevationSegments by remember { mutableStateOf<List<ElevationSegment>>(emptyList()) }

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
    val roadIntelligence = remember { RoadIntelligenceService(apiClient, scope) }
    val activeHazard by roadIntelligence.activeWarning.collectAsState()

    LaunchedEffect(gpsState.location) {
        val loc = gpsState.location ?: return@LaunchedEffect
        gpsAvailable = true
        lastKnownLocation = loc.latitude to loc.longitude
        speedLimitService.onLocationChanged(loc.latitude, loc.longitude)
        elevationService.onLocationChanged(loc.latitude, loc.longitude, loc.altitudeM)
        
        val coord = Coordinate(loc.latitude, loc.longitude)
        roadIntelligence.maybeFetchHazards(coord)

        if (!hazardPrefetched) {
            hazardPrefetched = true
            scope.launch { roadConditionRepository.prefetchHazards(loc.latitude, loc.longitude) }
        }
        val coords = gpsState.routeCoordinates
        val heading = if (coords.size >= 2) {
            val prev = coords[coords.size - 2]
            val curr = coords.last()
            _bearingDeg(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
        } else 0.0
        
        hazardApproachDetector.onLocationUpdate(loc.latitude, loc.longitude, gpsState.speed, heading)
        roadIntelligence.onLocationUpdate(coord, heading.toFloat())
    }
    LaunchedEffect(Unit) {
        delay(5000)
        if (gpsState.location == null) gpsAvailable = false
    }

    LaunchedEffect(Unit) {
        snapshotFlow { motionState.data.size }.collect { size ->
            val newCount = size - lastClassifiedDataSize
            if (newCount >= RoadConditionClassifier.WINDOW_SIZE) {
                lastClassifiedDataSize = size
                val window = motionState.data.takeLast(RoadConditionClassifier.WINDOW_SIZE)
                val xWin = window.map { it.userAccelX.toFloat() }.toFloatArray()
                val yWin = window.map { it.userAccelY.toFloat() }.toFloatArray()
                val zWin = window.map { it.userAccelZ.toFloat() }.toFloatArray()
                
                val loc = lastKnownLocation ?: return@collect
                val label = roadConditionClassifier.classify(
                    xWin, yWin, zWin,
                    speedKmh = gpsState.speed.toFloat(),
                    altitudeM = (gpsState.location?.altitudeM ?: 0.0).toFloat(),
                    gradePct = (elevationState.gradePct ?: 0.0).toFloat()
                )
                
                if (label != RoadConditionLabel.SMOOTH) {
                    roadConditionEvents = roadConditionEvents + RoadConditionEvent(loc.first, loc.second, gpsState.speed.toFloat(), label.name.lowercase(), 0.8f, Clock.System.now().toEpochMilliseconds())
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        var speedingStartMs = 0L
        var lastAlertMs = 0L
        snapshotFlow { gpsState.speed to speedLimitState.currentSpeedLimit?.toInt() }.collect { (speed, limit) ->
            val now = Clock.System.now().toEpochMilliseconds()
            if (limit != null && speed > limit + 5) {
                if (speedingStartMs == 0L) speedingStartMs = now
                if (now - speedingStartMs >= 3_000 && now - lastAlertMs >= 30_000) {
                    lastAlertMs = now
                    speedAlertVisible = true
                }
            } else { speedingStartMs = 0L }
        }
    }
    LaunchedEffect(speedAlertVisible) { if (speedAlertVisible) { delay(4_000); speedAlertVisible = false } }
    LaunchedEffect(elevationState.terrainTip) { if (elevationState.terrainTip != null) { delay(5_000); elevationService.clearTip() } }

    LaunchedEffect(Unit) {
        var prevLoc: Pair<Double, Double>? = null
        snapshotFlow { gpsState.location?.latitude to gpsState.location?.longitude }.collect { (lat, lon) ->
            if (lat == null || lon == null) return@collect
            val cur = lat to lon
            val grade = elevationState.gradePct
            if (prevLoc != null && prevLoc != cur && grade != null) {
                elevationSegments = (elevationSegments + ElevationSegment(prevLoc!!, cur, grade)).takeLast(600)
            }
            prevLoc = cur
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { gpsState.speed to speedLimitState.currentSpeedLimit }.collect { (speed, limit) ->
            val loc = lastKnownLocation ?: return@collect
            val now = Clock.System.now().toEpochMilliseconds()
            speedLimitService.maybeForceRequery(speed, loc.first, loc.second, now)
            if (limit != null) discrepancyService.onSpeedAndLimit(speed, limit, loc.first, loc.second, now)
        }
    }

    if (showEndConfirm) {
        EndRideConfirmation(
            onConfirm = {
                showEndConfirm = false; isActive = false; isSubmitting = true
                val finalGps = gpsService.stopTracking(); val finalMotion = motionService.stopTracking()
                scope.launch {
                    val payload = buildJsonObject {
                        put("ride_type", rideType)
                        put("start_time", Instant.fromEpochMilliseconds(finalGps.speedData.firstOrNull()?.timestamp ?: (Clock.System.now().toEpochMilliseconds() - elapsedSeconds * 1000L)).toString())
                        put("end_time", Clock.System.now().toString())
                        put("duration_minutes", elapsedSeconds / 60.0)
                        put("distance_km", finalGps.distance)
                        if (bookingId != null) put("booking_id", bookingId)
                        put("route_coords", buildJsonArray { finalGps.routeCoordinates.forEach { add(buildJsonObject { put("latitude", it.latitude); put("longitude", it.longitude) }) } }.toString())
                        put("speed_data", buildJsonArray { finalGps.speedData.forEach { add(buildJsonObject { put("timestamp", it.timestamp); put("speed", it.speed); put("lat", it.latitude); put("lon", it.longitude) }) } }.toString())
                        put("acceleration_data", buildJsonArray { finalMotion.forEach { add(buildJsonObject { put("timestamp", it.timestamp.toDouble()); put("x", it.userAccelX); put("y", it.userAccelY); put("z", it.userAccelZ) }) } }.toString())
                        put("rotation_data", buildJsonArray { finalMotion.forEach { add(buildJsonObject { put("timestamp", it.timestamp.toDouble()); put("x", it.rotationX); put("y", it.rotationY); put("z", it.rotationZ) }) } }.toString())
                        put("speed_limit_data", buildJsonArray { speedLimitService.getSpeedLimitData().forEach { add(buildJsonObject { put("lat", it.latitude); put("lon", it.longitude); put("speed_limit", it.speedLimit) }) } }.toString())
                        speedLimitState.surface?.let { put("road_surface", it) }
                        speedLimitState.smoothness?.let { put("road_smoothness", it) }
                        if (events.isNotEmpty()) put("evaluator_notes", events.joinToString("; ") { "${it.type}: ${it.description}" })
                    }
                    val capturedConditionEvents = roadConditionEvents
                    apiClient.completeRide(payload).onSuccess { created ->
                        apiClient.evaluateRide(created.id)
                        if (capturedConditionEvents.isNotEmpty()) launch { roadConditionRepository.uploadTrip(created.id.toString(), capturedConditionEvents) }
                        onRideComplete(created.id)
                    }.onFailure { onRideComplete(null) }
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
    if (pip.isInPipMode) {
        PipMiniDashboard(gpsState.speed, speedLimitState.currentSpeedLimit, elapsedSeconds, gpsState.distance, motionState.acceleration, motionState.rotation, speedLimitState.currentSpeedLimit != null && gpsState.speed > speedLimitState.currentSpeedLimit!! + 5, elevationState.elevationM, elevationState.gradePct)
        return
    }

    val currentSpeed = gpsState.speed
    val currentAccel = motionState.acceleration
    val currentRotation = motionState.rotation
    val currentLimit = speedLimitState.currentSpeedLimit?.toInt()

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        val mapCoords = if (gpsState.routeCoordinates.isEmpty() && gpsState.location != null) listOf(Coordinate(gpsState.location!!.latitude, gpsState.location!!.longitude)) else gpsState.routeCoordinates
        LiveMapSection(gpsState.copy(routeCoordinates = mapCoords), events, gpsState.location?.let { it.latitude to it.longitude }, gpsState.speed, elevationSegments, elevationState.elevationM, elevationState.gradePct, Modifier.fillMaxSize())
        KeepScreenOn()

        if (pip.isSupported && gpsState.location != null) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 16.dp).clip(RoundedCornerShape(12.dp)).background(Background.copy(alpha = 0.82f)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).clickable { pip.enter() }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text("⊡  Float", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            }
        }

        // Top HUD
        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 16.dp, end = 16.dp).align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            val avgSpeed = remember(gpsState.speedData) { if (gpsState.speedData.isEmpty()) 0.0 else gpsState.speedData.map { it.speed }.average() }
            FloatingSessionCard(isActive, elapsedSeconds, gpsState.distance, avgSpeed, elevationState.elevationM, elevationState.gradePct)
            if (!gpsAvailable) { Spacer(Modifier.height(12.dp)); GpsWarningBanner() }
        }

        // Bottom HUD
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp, start = 16.dp, end = 16.dp).align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            RoadHazardAlert(hazardApproach)
            Spacer(Modifier.height(6.dp))
            SafetyAlertOverlay(speedLimitState.zoneType, speedLimitState.zoneType != "regular")
            Spacer(Modifier.height(8.dp))
            FloatingSpeedometer(gpsState.speed, speedLimitState.currentSpeedLimit, speedLimitState.roadName)
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IcbcObserveButton(icbcObsCount, Modifier.weight(1f)) { 
                    showObserveTab = !showObserveTab
                    showReportingTab = false
                    activeReportCategory = null
                }
                QuickReportButton(pulses.isNotEmpty(), Modifier.weight(1f)) { 
                    showReportingTab = !showReportingTab
                    showObserveTab = false
                    activeReportCategory = null
                }
                EndRideButton(elapsedSeconds > 10, Modifier.weight(1f)) { showEndConfirm = true }
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
            modifier = Modifier.fillMaxSize().padding(bottom = 120.dp).align(Alignment.BottomCenter),
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

        FloatingActionOverlay(
            showReporting = showReportingTab,
            showObserve = showObserveTab,
            activeCategory = activeReportCategory,
            pulses = pulses,
            elapsedSeconds = elapsedSeconds,
            icbcStates = itemStates,
            onCategoryClick = { cat -> 
                if (cat == ReportCategory.SpeedLimitSign) {
                    discrepancyService.showManualCorrection()
                    showReportingTab = false
                } else {
                    activeReportCategory = cat
                }
            },
            onSubtypeSubmit = { subtype, sev ->
                val categoryToReport = activeReportCategory
                lastKnownLocation?.let { loc ->
                    scope.launch {
                        if (categoryToReport != null) {
                            apiClient.reportRoadEvent(
                                category = categoryToReport,
                                subtypeId = subtype.id,
                                severity = sev,
                                lat = loc.first,
                                lon = loc.second,
                                rideId = bookingId
                            )
                        }
                    }
                }
                activeReportCategory = null
                showReportingTab = false
            },
            onObservation = { id, label, sev ->
                icbcObsCount++
                val now = Clock.System.now().toEpochMilliseconds()
                events = events + RideEvent(now, id, label, sev)
                val next = when (itemStates[id] ?: ObsState.NONE) {
                    ObsState.NONE       -> ObsState.NEEDS_WORK
                    ObsState.NEEDS_WORK -> ObsState.ERROR
                    ObsState.ERROR      -> ObsState.NONE
                }
                itemStates = itemStates + (id to next)
            },
            onDismiss = {
                showReportingTab = false
                showObserveTab = false
                activeReportCategory = null
            }
        )

        InsightRail(null, insightHistory, Modifier.align(Alignment.TopStart).padding(top = 40.dp, start = 16.dp).heightIn(max = 200.dp))

        Column(modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 16.dp).heightIn(max = 200.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
            if (speedAlertVisible) RideAdviceBanner("⚠️", "SPEEDING", Color(0xFFDC2626) to Color(0xFFB91C1C))
            activeHazard?.let { RideAdviceBanner(when(it.label) { "pothole" -> "🕳️"; "speed_bump" -> "🚧"; else -> "⚠️" }, "${it.label.uppercase()} AHEAD", Color(0xFFF59E0B) to Color(0xFFD97706)) }
            coachingEvent?.let { RideAdviceBanner("✨", it.type.shortMessage, Color(0xFF6366F1) to Color(0xFF4F46E5)) }
            elevationState.shortTerrainTip?.let { RideAdviceBanner(if (elevationState.category.contains("downhill")) "🏔️" else "⛰️", it, Color(0xFF0D9488) to Color(0xFF0F766E)) }
        }

        Box(modifier = Modifier.fillMaxSize().padding(bottom = 100.dp), contentAlignment = Alignment.BottomCenter) {
            SpeedLimitCorrectionStrip(speedLimitState.currentSpeedLimit ?: 0.0, gpsState.speed, discrepancyState, { reported -> scope.launch { discrepancyService.submitFlag(reported) } }, { discrepancyService.dismissOverSpeedPrompt() })
        }
    }
}

// ── HUD Components ─────────────────────────────────────────────────────────────

@Composable
private fun FloatingActionOverlay(
    showReporting: Boolean,
    showObserve: Boolean,
    activeCategory: ReportCategory?,
    pulses: Set<ReportCategory>,
    elapsedSeconds: Int,
    icbcStates: Map<String, ObsState>,
    onCategoryClick: (ReportCategory) -> Unit,
    onSubtypeSubmit: (ReportSubtype, String) -> Unit,
    onObservation: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    if (showReporting || showObserve || activeCategory != null) {
        Box(modifier = Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() })
    }
    Box(modifier = Modifier.fillMaxSize().padding(bottom = 100.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible = showReporting && activeCategory == null, enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom), exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)) {
            ReportingCategoriesPanel(pulses, onCategoryClick)
        }
        AnimatedVisibility(visible = activeCategory != null, enter = fadeIn() + scaleIn(initialScale = 0.95f), exit = fadeOut() + scaleOut(targetScale = 0.95f)) {
            activeCategory?.let { ReportSubtypePanel(it, onSubtypeSubmit) }
        }
        AnimatedVisibility(visible = showObserve, enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom), exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)) {
            FloatingObservePanel(elapsedSeconds, icbcStates, onObservation)
        }
    }
}

@Composable
private fun ReportingCategoriesPanel(pulses: Set<ReportCategory>, onCategoryClick: (ReportCategory) -> Unit) {
    Surface(color = Color.Black.copy(alpha = 0.85f), shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, GlassStroke), modifier = Modifier.width(300.dp)) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("REPORT ROAD EVENT", fontSize = 11.sp, fontWeight = FontWeight.Black, color = TextMuted, letterSpacing = 1.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(ReportCategory.Construction to "🚧", ReportCategory.Traffic to "🚗", ReportCategory.Hazard to "⚠️", ReportCategory.RoadCondition to "🕳️", ReportCategory.SpeedLimitSign to "🪧").forEach { (cat, icon) ->
                    OrbitButton(cat, icon, pulses.contains(cat), { onCategoryClick(cat) })
                }
            }
        }
    }
}

@Composable
private fun ReportSubtypePanel(category: ReportCategory, onSubmit: (ReportSubtype, String) -> Unit) {
    var selectedSeverity by remember { mutableStateOf("Medium") }
    var selectedSubtype by remember { mutableStateOf<ReportSubtype?>(null) }
    val subtypes = remember(category) { getSubtypesForCategory(category) }
    Surface(color = Color.Black.copy(alpha = 0.9f), shape = RoundedCornerShape(28.dp), border = BorderStroke(1.2.dp, Color(0xFF6366F1).copy(alpha = 0.5f)), modifier = Modifier.width(320.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("REPORT ${category.name.uppercase()}", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 1.sp)
            Spacer(Modifier.height(16.dp))
            LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 240.dp)) {
                items(subtypes) { subtype ->
                    val isSelected = selectedSubtype == subtype
                    Surface(onClick = { selectedSubtype = subtype }, shape = RoundedCornerShape(14.dp), color = if (isSelected) Color(0xFF6366F1).copy(alpha = 0.15f) else Color(0xFF1E293B), border = BorderStroke(1.dp, if (isSelected) Color(0xFF6366F1) else Color.White.copy(alpha = 0.05f))) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(subtype.icon, fontSize = 16.sp); Spacer(Modifier.width(8.dp))
                            Text(subtype.label, color = if (isSelected) Color.White else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (category != ReportCategory.SpeedLimitSign) {
                Spacer(Modifier.height(20.dp)); Text("SEVERITY", fontSize = 10.sp, fontWeight = FontWeight.Black, color = TextMuted, letterSpacing = 1.sp); Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Low", "Medium", "High").forEach { sev ->
                        val isSel = selectedSeverity == sev
                        Surface(onClick = { selectedSeverity = sev }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), color = if (isSel) Color.White.copy(alpha = 0.1f) else Color.Transparent, border = BorderStroke(1.dp, if (isSel) Color.White else Color.White.copy(alpha = 0.1f))) {
                            Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) { Text(sev.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Black, color = if (isSel) Color.White else TextMuted) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = { selectedSubtype?.let { onSubmit(it, selectedSeverity) } }, enabled = selectedSubtype != null, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                Text("SUBMIT REPORT", fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 0.5.sp)
            }
        }
    }
}

@Composable
private fun FloatingObservePanel(elapsedSeconds: Int, icbcStates: Map<String, ObsState>, onObservation: (String, String, String) -> Unit) {
    Surface(color = Color.Black.copy(alpha = 0.85f), shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, GlassStroke), modifier = Modifier.width(320.dp).heightIn(max = 440.dp)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("OBSERVATION LOG", fontSize = 11.sp, fontWeight = FontWeight.Black, color = TextMuted, letterSpacing = 1.sp)
                Surface(color = Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                    Text("%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60), fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            IcbcObservationList(itemStates = icbcStates, onItemTap = { item, state ->
                val nextSeverity = when (state) { ObsState.NONE -> "needs_work"; ObsState.NEEDS_WORK -> "error"; else -> "none" }
                onObservation(item.id, item.label, nextSeverity)
            }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun FloatingSessionCard(
    isActive: Boolean,
    elapsedSeconds: Int,
    distance: Double,
    avgSpeed: Double,
    elevationM: Double? = null,
    gradePct: Double? = null
) {
    Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, GlassStroke), modifier = Modifier.wrapContentSize()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (isActive) Secondary else Warning))
                Spacer(Modifier.width(12.dp))
                Text("${pad2(elapsedSeconds / 60)}:${pad2(elapsedSeconds % 60)}", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
                Spacer(Modifier.width(16.dp)); Box(Modifier.width(1.dp).height(16.dp).background(Color.White.copy(alpha = 0.2f))); Spacer(Modifier.width(16.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("%.1f".format(distance), fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
                        Text(" km", fontSize = 10.sp, color = TextMuted, modifier = Modifier.padding(start = 2.dp, bottom = 1.dp))
                    }
                    Text("DISTANCE", fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(16.dp)); Box(Modifier.width(1.dp).height(16.dp).background(Color.White.copy(alpha = 0.2f))); Spacer(Modifier.width(16.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(avgSpeed.toInt().toString(), fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
                        Text(" avg", fontSize = 10.sp, color = TextMuted, modifier = Modifier.padding(start = 2.dp, bottom = 1.dp))
                    }
                    Text("KM/H", fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Black)
                }
            }
            if (elevationM != null) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = Color.White.copy(alpha = 0.1f))
                Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⛰ ${elevationM.toInt()}m", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    if (gradePct != null) {
                        Spacer(Modifier.width(12.dp))
                        val gradeColor = when {
                            gradePct > 10 -> Color(0xFFEF4444)
                            gradePct > 5 -> Color(0xFFF97316)
                            gradePct > 2 -> Color(0xFFF59E0B)
                            gradePct > -2 -> Color(0xFF94A3B8)
                            gradePct > -5 -> Color(0xFF38BDF8)
                            else -> Color(0xFF636666)
                        }
                        // Changed color to a more neutral gray for better contrast
                        // Removed the explicit color assignment to use the default color
                    }
                }
            }
        }
    }