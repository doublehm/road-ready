package com.roadready.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadready.ui.theme.*

// ── Data model ──────────────────────────────────────────────────────────────────

data class IcbcItem(
    val id: String,
    val label: String,
)

data class IcbcCategory(
    val id: String,
    val icon: String,
    val label: String,
    val items: List<IcbcItem>,
)

enum class ObsState { NONE, NEEDS_WORK, ERROR }

// ── ICBC test template — all categories and items ───────────────────────────────

val ICBC_CATEGORIES: List<IcbcCategory> = listOf(

    IcbcCategory("speed_space", "🚗", "Speed & Space Management", listOf(
        IcbcItem("ss_following_close",   "Following distance too close"),
        IcbcItem("ss_speed_conditions",  "Speed inappropriate for conditions"),
        IcbcItem("ss_speeding",          "Exceeding posted speed limit"),
        IcbcItem("ss_speed_slow",        "Too slow / impeding traffic"),
        IcbcItem("ss_lane_space",        "Inadequate space in lane change"),
        IcbcItem("ss_lane_position",     "Incorrect lane position"),
        IcbcItem("ss_tailgating_hwy",    "Tailgating on highway"),
    )),

    IcbcCategory("observation", "👁", "Observation", listOf(
        IcbcItem("ob_mirror_before_brake",  "No mirror check before braking"),
        IcbcItem("ob_mirror_before_turn",   "No mirror check before turning"),
        IcbcItem("ob_shoulder_lane",        "No shoulder check — lane change"),
        IcbcItem("ob_shoulder_turn",        "No shoulder check — turning"),
        IcbcItem("ob_scan_intersection",    "Inadequate scan at intersection"),
        IcbcItem("ob_blind_spot",           "Blind spot not checked"),
        IcbcItem("ob_signal_missed",        "Failed to observe traffic signal"),
        IcbcItem("ob_sign_missed",          "Failed to observe stop / yield sign"),
        IcbcItem("ob_ped_crossing",         "Did not observe pedestrian crossing"),
    )),

    IcbcCategory("intersections", "🚦", "Intersection Procedures", listOf(
        IcbcItem("int_stop_incomplete",  "Did not stop completely at stop sign"),
        IcbcItem("int_stop_beyond_line", "Stopped beyond the stop line"),
        IcbcItem("int_yield_fail",       "Failed to yield right-of-way"),
        IcbcItem("int_red_light",        "Did not stop at red light"),
        IcbcItem("int_amber_unsafe",     "Ran amber light unsafely"),
        IcbcItem("int_wrong_lane",       "Incorrect lane approach"),
        IcbcItem("int_signal_late",      "Signal too late (< 30 m before)"),
        IcbcItem("int_signal_missing",   "No signal at intersection"),
        IcbcItem("int_yield_pedestrian", "Did not yield to pedestrian"),
    )),

    IcbcCategory("turning", "↩️", "Turning", listOf(
        IcbcItem("turn_right_wide",      "Right turn — turned too wide"),
        IcbcItem("turn_left_cut",        "Left turn — cut the corner"),
        IcbcItem("turn_no_signal",       "No signal before turning"),
        IcbcItem("turn_signal_late",     "Signal activated too late"),
        IcbcItem("turn_wrong_lane",      "Wrong lane after completing turn"),
        IcbcItem("turn_speed_curve",     "Improper speed entering curve"),
        IcbcItem("turn_u_turn_unsafe",   "Unsafe U-turn"),
    )),

    IcbcCategory("manoeuvres", "🅿️", "Special Manoeuvres", listOf(
        IcbcItem("man_lane_change",      "Lane change — unsafe / no signal"),
        IcbcItem("man_merge_speed",      "Highway merge — unsafe speed"),
        IcbcItem("man_exit_speed",       "Highway exit — too fast"),
        IcbcItem("man_3point",           "3-point turn — incomplete check"),
        IcbcItem("man_parallel_far",     "Parallel park — too far from curb"),
        IcbcItem("man_stall_lines",      "Stall parking — outside lines"),
        IcbcItem("man_reverse_check",    "Reversing — no check behind"),
        IcbcItem("man_roundabout",       "Roundabout — incorrect procedure"),
    )),

    IcbcCategory("vehicle_control", "🎛️", "Vehicle Control", listOf(
        IcbcItem("vc_harsh_brake",       "Harsh / sudden braking"),
        IcbcItem("vc_harsh_accel",       "Harsh acceleration"),
        IcbcItem("vc_harsh_steer",       "Harsh / jerky steering"),
        IcbcItem("vc_stall",             "Stalled engine"),
        IcbcItem("vc_coasting",          "Coasting in neutral / clutch riding"),
        IcbcItem("vc_wrong_gear",        "Incorrect gear for speed / grade"),
        IcbcItem("vc_smooth_stop",       "Failed smooth stop at lights"),
    )),

    IcbcCategory("hazards", "⚠️", "Hazards & Other", listOf(
        IcbcItem("haz_ped_right_of_way", "Pedestrian right-of-way not given"),
        IcbcItem("haz_school_zone",      "School zone speed violation"),
        IcbcItem("haz_rail_crossing",    "Railway crossing procedure wrong"),
        IcbcItem("haz_construction",     "Construction zone rules ignored"),
        IcbcItem("haz_distracted",       "Distracted driving"),
        IcbcItem("haz_aggressive",       "Aggressive / road rage behaviour"),
        IcbcItem("haz_predrive",         "Pre-drive check skipped"),
        IcbcItem("haz_seatbelt",         "Seatbelt not worn"),
    )),
)

// ── Observe chip — inline button placed below the header ────────────────────────

@Composable
fun IcbcObserveChip(
    observationCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xD01E3A5F))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("📋", fontSize = 14.sp)
        Text(
            if (observationCount > 0) "Observe  ·  $observationCount" else "Observe",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
        if (observationCount > 0) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444)),
            )
        }
    }
}

// ── Bottom sheet ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IcbcObservationSheet(
    elapsedSeconds: Int,
    onObservation: (type: String, label: String, severity: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var expandedCategoryId by remember { mutableStateOf<String?>(null) }

    // item id → current ObsState
    var itemStates by remember { mutableStateOf(mapOf<String, ObsState>()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F172A),
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF334155)),
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "📋  ICBC Observation Log",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    // Ride clock
                    val mins = elapsedSeconds / 60
                    val secs = elapsedSeconds % 60
                    Text(
                        "⏱ %02d:%02d".format(mins, secs),
                        fontSize = 12.sp,
                        color = TextMuted,
                    )
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            ICBC_CATEGORIES.forEach { category ->
                val isExpanded = expandedCategoryId == category.id
                val categoryHasFlags = category.items.any {
                    (itemStates[it.id] ?: ObsState.NONE) != ObsState.NONE
                }
                val flagCount = category.items.count {
                    (itemStates[it.id] ?: ObsState.NONE) != ObsState.NONE
                }

                // Category header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expandedCategoryId = if (isExpanded) null else category.id
                        }
                        .background(
                            if (isExpanded) Color(0xFF1E293B) else Color.Transparent
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(category.icon, fontSize = 18.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        category.label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    // Flag count badge
                    if (flagCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFEF4444).copy(alpha = 0.9f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                "$flagCount",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (isExpanded) "▲" else "▼",
                        fontSize = 11.sp,
                        color = TextMuted,
                    )
                }

                // Expandable items
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(tween(200)),
                    exit = shrinkVertically(tween(180)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0B1120)),
                    ) {
                        category.items.forEach { item ->
                            val state = itemStates[item.id] ?: ObsState.NONE
                            IcbcItemRow(
                                item = item,
                                state = state,
                                elapsedSeconds = elapsedSeconds,
                                onTap = {
                                    val next = when (state) {
                                        ObsState.NONE       -> ObsState.NEEDS_WORK
                                        ObsState.NEEDS_WORK -> ObsState.ERROR
                                        ObsState.ERROR      -> ObsState.NONE
                                    }
                                    itemStates = itemStates + (item.id to next)
                                    if (next != ObsState.NONE) {
                                        onObservation(
                                            item.id,
                                            item.label,
                                            if (next == ObsState.ERROR) "error" else "needs_work",
                                        )
                                    }
                                },
                            )
                            HorizontalDivider(
                                color = Color(0xFF1E293B),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 52.dp),
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
            }

            // Legend
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                LegendDot(Color(0xFF64748B), "Not flagged")
                LegendDot(Color(0xFFF59E0B), "Needs work")
                LegendDot(Color(0xFFEF4444), "Error")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap once = needs work  •  Tap twice = error  •  Tap again = clear",
                fontSize = 10.sp,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

// ── Item row ─────────────────────────────────────────────────────────────────────

@Composable
private fun IcbcItemRow(
    item: IcbcItem,
    state: ObsState,
    elapsedSeconds: Int,
    onTap: () -> Unit,
) {
    val dotColor by animateColorAsState(
        when (state) {
            ObsState.NONE       -> Color(0xFF334155)
            ObsState.NEEDS_WORK -> Color(0xFFF59E0B)
            ObsState.ERROR      -> Color(0xFFEF4444)
        },
        tween(200),
    )
    val rowBg by animateColorAsState(
        when (state) {
            ObsState.NONE       -> Color.Transparent
            ObsState.NEEDS_WORK -> Color(0xFFF59E0B).copy(alpha = 0.06f)
            ObsState.ERROR      -> Color(0xFFEF4444).copy(alpha = 0.08f)
        },
        tween(200),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable(onClick = onTap)
            .padding(start = 52.dp, end = 20.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.label,
            fontSize = 13.sp,
            color = when (state) {
                ObsState.NONE       -> TextSecondary
                ObsState.NEEDS_WORK -> Color(0xFFFBBF24)
                ObsState.ERROR      -> Color(0xFFF87171)
            },
            modifier = Modifier.weight(1f),
            lineHeight = 18.sp,
        )
        Spacer(Modifier.width(12.dp))
        // State indicator
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = 0.15f))
                .then(
                    if (state != ObsState.NONE)
                        Modifier.background(dotColor.copy(alpha = 0.15f), CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(if (state == ObsState.NONE) 8.dp else 12.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────────

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 10.sp, color = TextMuted)
    }
}
