package com.roadready.ui.screens.shared

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
import com.roadready.data.model.Notification
import com.roadready.data.remote.ApiClient
import com.roadready.ui.components.LoadingOverlay
import com.roadready.ui.theme.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var notifications by remember { mutableStateOf<List<Notification>>(emptyList()) }
    var selectedNotification by remember { mutableStateOf<Notification?>(null) }

    LaunchedEffect(Unit) {
        apiClient.getNotifications().onSuccess { notifications = it.sortedByDescending { n -> n.createdAt } }
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Back", color = Primary) }
            Text("Notifications", style = MaterialTheme.typography.headlineMedium)
        }

        if (isLoading) { LoadingOverlay(); return }

        if (notifications.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔔", style = MaterialTheme.typography.displayLarge)
                    Text("No notifications", style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(notifications) { notif ->
                    NotificationItem(notif) {
                        selectedNotification = notif
                        if (!notif.isRead) {
                            scope.launch { apiClient.markNotificationRead(notif.id) }
                            notifications = notifications.map {
                                if (it.id == notif.id) it.copy(isRead = true) else it
                            }
                        }
                    }
                }
            }
        }
    }

    selectedNotification?.let { notif ->
        AlertDialog(
            onDismissRequest = { selectedNotification = null },
            title = { Text(notif.title) },
            text = {
                Column {
                    Text(notif.message, style = MaterialTheme.typography.bodyMedium)
                    notif.createdAt?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedNotification = null }) { Text("Close") }
            },
            containerColor = Surface,
        )
    }
}

@Composable
private fun NotificationItem(notif: Notification, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (!notif.isRead) Primary.copy(alpha = 0.05f) else Surface,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!notif.isRead) {
                Surface(modifier = Modifier.size(8.dp), color = Primary, shape = RoundedCornerShape(4.dp)) {}
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(notif.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(notif.message, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, color = TextMuted)
            }
            notif.createdAt?.let {
                Text(it.takeLast(8).take(5), style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
    }
}
