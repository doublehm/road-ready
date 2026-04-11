package com.roadready.ui.screens.instructor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.roadready.data.model.Booking
import com.roadready.data.remote.ApiClient
import com.roadready.ui.components.LoadingOverlay
import com.roadready.ui.theme.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun InstructorScheduleScreen(
    onSessionDetail: (Booking) -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    var isLoading by remember { mutableStateOf(true) }
    var bookings by remember { mutableStateOf<List<Booking>>(emptyList()) }
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Upcoming", "Completed", "Cancelled")

    LaunchedEffect(Unit) {
        apiClient.getBookings().onSuccess { bookings = it }
        isLoading = false
    }

    if (isLoading) { LoadingOverlay(); return }

    val filtered = when (selectedTab) {
        0 -> bookings.filter { it.status == "accepted" }
        1 -> bookings.filter { it.status == "completed" }
        2 -> bookings.filter { it.status == "cancelled" }
        else -> bookings
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Text(
            "Schedule",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(16.dp),
        )

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Surface,
            contentColor = Primary,
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) },
                    selectedContentColor = Primary,
                    unselectedContentColor = TextMuted,
                )
            }
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📅", style = MaterialTheme.typography.displayLarge)
                    Text("No ${tabs[selectedTab].lowercase()} bookings", style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered) { booking ->
                    ScheduleBookingCard(booking) { onSessionDetail(booking) }
                }
            }
        }
    }
}

@Composable
private fun ScheduleBookingCard(booking: Booking, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    booking.student?.fullName ?: "Student #${booking.studentId}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                booking.scheduledDate?.let {
                    Text("$it • ${booking.scheduledTime ?: ""}", style = MaterialTheme.typography.bodyMedium)
                }
                booking.pickupAddress?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val (color, label) = when (booking.status) {
                "accepted" -> AccentLight to "Upcoming"
                "completed" -> Primary to "Done"
                "cancelled" -> Error to "Cancelled"
                else -> TextMuted to booking.status
            }
            Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                Text(
                    label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium, color = color,
                )
            }
        }
    }
}
