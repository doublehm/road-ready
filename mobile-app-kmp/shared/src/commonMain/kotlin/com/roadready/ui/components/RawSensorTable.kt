package com.roadready.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadready.ui.theme.*
import kotlin.math.abs
import kotlin.math.sqrt

// ── Row model ──────────────────────────────────────────────────────────────────

data class SensorRow(
    val relativeMs:  Long,
    val latitude:    Double?,
    val longitude:   Double?,
    val speedKmh:    Double?,
    val speedLimit:  Float?,
    val accX:        Double,   // lateral m/s²
    val accY:        Double,   // vertical m/s²
    val accZ:        Double,   // longitudinal m/s²
    val latG:        Double,
    val lonG:        Double,
    val vertG:       Double,
    val totalG:      Double,
    val gyroX:       Double,
    val gyroY:       Double,
    val gyroZ:       Double,
    val turnRate:    Double,
)

// ── Merge helper ───────────────────────────────────────────────────────────────

private const val G_VAL = 9.81

fun buildSensorRows(
    accelData:      List<AccelDataPoint>,
    rotationData:   List<RotationDataPoint>,
    speedData:      List<SpeedDataPoint>,
    speedLimitData: List<SpeedLimitPoint>,
): List<SensorRow> {
    if (accelData.isEmpty()) return emptyList()

    val startTs  = accelData.first().timestamp.toLong()
    val sortedSpd = speedData.sortedBy { it.timestamp }
    val sortedRot = rotationData.sortedBy { it.timestamp.toLong() }

    return accelData.map { accel ->
        val ts     = accel.timestamp.toLong()
        val relMs  = ts - startTs

        val sp  = nearestByTs(sortedSpd, ts)  { it.timestamp }
        val rot = nearestByTs(sortedRot, ts)  { it.timestamp.toLong() }

        val nearestLimit = sp?.let { nearestLimit(speedLimitData, it.lat, it.lon) }

        // Correct axis mapping (portrait phone, screen facing driver):
        //   X = lateral, Y = vertical, Z = longitudinal
        val latG  = abs(accel.x) / G_VAL
        val lonG  = abs(accel.z) / G_VAL
        val vertG = abs(accel.y) / G_VAL
        val totalG = sqrt(accel.x * accel.x + accel.y * accel.y + accel.z * accel.z) / G_VAL

        val tr = rot?.let { sqrt(it.x * it.x + it.y * it.y + it.z * it.z) } ?: 0.0

        SensorRow(
            relativeMs  = relMs,
            latitude    = sp?.lat,
            longitude   = sp?.lon,
            speedKmh    = sp?.speed?.toDouble(),
            speedLimit  = nearestLimit?.speed_limit,
            accX        = accel.x,
            accY        = accel.y,
            accZ        = accel.z,
            latG        = latG,
            lonG        = lonG,
            vertG       = vertG,
            totalG      = totalG,
            gyroX       = rot?.x ?: 0.0,
            gyroY       = rot?.y ?: 0.0,
            gyroZ       = rot?.z ?: 0.0,
            turnRate    = tr,
        )
    }
}

private fun <T> nearestByTs(sorted: List<T>, ts: Long, getTs: (T) -> Long): T? {
    if (sorted.isEmpty()) return null
    var lo = 0; var hi = sorted.lastIndex
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (getTs(sorted[mid]) < ts) lo = mid + 1 else hi = mid
    }
    return listOfNotNull(sorted.getOrNull(lo - 1), sorted.getOrNull(lo))
        .minByOrNull { abs(getTs(it) - ts) }
}

private fun nearestLimit(limits: List<SpeedLimitPoint>, lat: Double, lon: Double): SpeedLimitPoint? =
    limits.minByOrNull { abs(it.lat - lat) + abs(it.lon - lon) }

// ── Column spec ────────────────────────────────────────────────────────────────

private data class ColSpec(val header: String, val unit: String, val w: Dp)

private val COLS = listOf(
    ColSpec("TIME",     "mm:ss.t",  62.dp),
    ColSpec("LAT",      "°",        90.dp),
    ColSpec("LON",      "°",        90.dp),
    ColSpec("SPEED",    "km/h",     60.dp),
    ColSpec("LIMIT",    "km/h",     52.dp),
    ColSpec("ACC X",    "m/s²",     65.dp),
    ColSpec("ACC Y",    "m/s²",     65.dp),
    ColSpec("ACC Z",    "m/s²",     65.dp),
    ColSpec("LAT-G",    "G",        58.dp),
    ColSpec("LON-G",    "G",        58.dp),
    ColSpec("VRT-G",    "G",        58.dp),
    ColSpec("TTL-G",    "G",        58.dp),
    ColSpec("GYR X",    "rad/s",    64.dp),
    ColSpec("GYR Y",    "rad/s",    64.dp),
    ColSpec("GYR Z",    "rad/s",    64.dp),
    ColSpec("T-RATE",   "rad/s",    68.dp),
)

// ── Cell status ────────────────────────────────────────────────────────────────

private enum class CellStatus { OK, WARN, ALERT, NEUTRAL }

private fun gStatus(v: Double, warn: Double, alert: Double) = when {
    v >= alert -> CellStatus.ALERT
    v >= warn  -> CellStatus.WARN
    v > 0.005  -> CellStatus.OK
    else       -> CellStatus.NEUTRAL
}

private fun speedStatus(speed: Double?, limit: Float?) = when {
    speed == null || limit == null || limit <= 0f -> CellStatus.NEUTRAL
    speed > limit + 10.0 -> CellStatus.ALERT
    speed > limit        -> CellStatus.WARN
    else                 -> CellStatus.OK
}

private fun trStatus(v: Double) = when {
    v >= 0.80 -> CellStatus.ALERT
    v >= 0.50 -> CellStatus.WARN
    v > 0.005 -> CellStatus.OK
    else      -> CellStatus.NEUTRAL
}

private fun rawAccStatus(v: Double) = gStatus(abs(v) / G_VAL, 0.10, 0.35)

private fun textColorFor(s: CellStatus): Color = when (s) {
    CellStatus.ALERT   -> Color(0xFFF87171)
    CellStatus.WARN    -> Color(0xFFFBBF24)
    CellStatus.OK      -> Color(0xFF4ADE80)
    CellStatus.NEUTRAL -> Color(0xFF64748B)
}

private fun bgFor(s: CellStatus): Color = when (s) {
    CellStatus.ALERT   -> Color(0x20EF4444)
    CellStatus.WARN    -> Color(0x15F59E0B)
    CellStatus.OK      -> Color(0x0A22C55E)
    CellStatus.NEUTRAL -> Color.Transparent
}

// ── Cell composable ────────────────────────────────────────────────────────────

@Composable
private fun DataCell(text: String, width: Dp, status: CellStatus) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(bgFor(status)),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            text       = text,
            fontSize   = 10.sp,
            color      = textColorFor(status),
            textAlign  = TextAlign.End,
            maxLines   = 1,
            modifier   = Modifier.padding(horizontal = 5.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun HeaderCell(col: ColSpec) {
    Column(
        modifier = Modifier
            .width(col.w)
            .padding(horizontal = 5.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text       = col.header,
            fontSize   = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color      = Color(0xFF94A3B8),
            textAlign  = TextAlign.End,
            letterSpacing = 0.3.sp,
        )
        Text(
            text     = col.unit,
            fontSize = 7.5.sp,
            color    = Color(0xFF334155),
            textAlign = TextAlign.End,
        )
    }
}

// ── Row renderers ──────────────────────────────────────────────────────────────

@Composable
private fun HeaderRow(hScroll: ScrollState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D1826))
            .horizontalScroll(hScroll),
        verticalAlignment = Alignment.Bottom,
    ) {
        COLS.forEach { HeaderCell(it) }
    }
    HorizontalDivider(color = Color(0xFF1E3A5F), thickness = 1.dp)
}

@Composable
private fun DataRow(row: SensorRow, hScroll: ScrollState, even: Boolean) {
    val rowBg = if (even) Color(0xFF0B111E) else Color(0xFF0F172A)
    val sStatus = speedStatus(row.speedKmh, row.speedLimit)

    Row(
        modifier = Modifier
            .background(rowBg)
            .horizontalScroll(hScroll)
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // TIME
        val ms = row.relativeMs
        val tenths = (ms % 1000) / 100
        val secs   = (ms / 1000) % 60
        val mins   = ms / 60000
        DataCell("%02d:%02d.%d".format(mins, secs, tenths), COLS[0].w, CellStatus.NEUTRAL)

        // LAT / LON
        DataCell(row.latitude?.let  { "%.5f".format(it) } ?: "—", COLS[1].w, CellStatus.NEUTRAL)
        DataCell(row.longitude?.let { "%.5f".format(it) } ?: "—", COLS[2].w, CellStatus.NEUTRAL)

        // SPEED
        DataCell(row.speedKmh?.let { "%.1f".format(it) } ?: "—", COLS[3].w, sStatus)

        // LIMIT
        DataCell(
            row.speedLimit?.let { if (it > 0f) it.toInt().toString() else "—" } ?: "—",
            COLS[4].w, CellStatus.NEUTRAL,
        )

        // RAW ACCEL (m/s²)
        DataCell("%.2f".format(row.accX), COLS[5].w, rawAccStatus(row.accX))
        DataCell("%.2f".format(row.accY), COLS[6].w, rawAccStatus(row.accY))
        DataCell("%.2f".format(row.accZ), COLS[7].w, rawAccStatus(row.accZ))

        // G VALUES
        DataCell("%.3f".format(row.latG),   COLS[8].w,  gStatus(row.latG,  0.10, 0.35))
        DataCell("%.3f".format(row.lonG),   COLS[9].w,  gStatus(row.lonG,  0.08, 0.40))
        DataCell("%.3f".format(row.vertG),  COLS[10].w, gStatus(row.vertG, 0.05, 0.30))
        DataCell("%.3f".format(row.totalG), COLS[11].w, gStatus(row.totalG,0.15, 0.50))

        // GYROSCOPE
        DataCell("%.3f".format(row.gyroX),    COLS[12].w, trStatus(abs(row.gyroX)))
        DataCell("%.3f".format(row.gyroY),    COLS[13].w, trStatus(abs(row.gyroY)))
        DataCell("%.3f".format(row.gyroZ),    COLS[14].w, trStatus(abs(row.gyroZ)))
        DataCell("%.3f".format(row.turnRate), COLS[15].w, trStatus(row.turnRate))
    }
}

// ── Legend ─────────────────────────────────────────────────────────────────────

@Composable
private fun LegendRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D1826))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            Color(0xFF4ADE80) to "Within limits",
            Color(0xFFFBBF24) to "Approaching limit",
            Color(0xFFF87171) to "Exceeds limit",
            Color(0xFF64748B) to "Inactive / no data",
        ).forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(8.dp).background(color, androidx.compose.foundation.shape.CircleShape))
                Text(label, fontSize = 9.sp, color = Color(0xFF64748B))
            }
        }
    }
}

// ── Main composable ────────────────────────────────────────────────────────────

/**
 * Spreadsheet-style sensor data table.
 *
 * All 16 columns scroll together horizontally via a shared ScrollState.
 * The header row is sticky; data rows are rendered via LazyColumn.
 * Each cell is independently color-coded against physics thresholds.
 *
 * Must be placed inside a Box or Column with a defined height (e.g. 480.dp)
 * since it uses a LazyColumn internally.
 */
@Composable
fun RawSensorTable(
    rows: List<SensorRow>,
    modifier: Modifier = Modifier,
) {
    val hScroll = rememberScrollState()

    Column(
        modifier = modifier.background(Color(0xFF0B111E)),
    ) {
        // Sticky header
        HeaderRow(hScroll)

        if (rows.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text("No sensor data recorded", fontSize = 13.sp, color = TextMuted)
            }
        } else {
            // Row counter badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D1826))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${rows.size} samples  ·  scroll ↔ for all columns",
                    fontSize = 9.sp,
                    color = Color(0xFF334155),
                )
                Text(
                    "${(rows.last().relativeMs / 1000)} s captured",
                    fontSize = 9.sp,
                    color = Color(0xFF334155),
                )
            }
            HorizontalDivider(color = Color(0xFF0F1F33), thickness = 0.5.dp)

            // Data rows
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(rows) { idx, row ->
                    DataRow(row, hScroll, idx % 2 == 0)
                    HorizontalDivider(color = Color(0x08FFFFFF), thickness = 0.5.dp)
                }
            }

            // Legend footer
            HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
            LegendRow()
        }
    }
}
