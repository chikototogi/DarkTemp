package com.darktemp.app.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darktemp.app.core.ui.*
import com.darktemp.app.data.model.*

@Composable
fun NotificationsScreen(alerts: StreamState<List<SensorAlert>>, unit: TemperatureUnit, busy: Boolean,
    markRead: (String) -> Unit, markAll: () -> Unit, clear: () -> Unit, retry: () -> Unit) {
    var unread by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val entries = (alerts as? StreamState.Ready)?.value.orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Notifications", style = MaterialTheme.typography.headlineLarge) }
        item { Text("Alerts are detected while DARKTEMP is open.", color = Muted, style = MaterialTheme.typography.bodySmall) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(selected = !unread, onClick = { unread = false }, label = { Text("All") })
                FilterChip(selected = unread, onClick = { unread = true }, label = { Text("Unread") })
            }
        }
        if (entries.isNotEmpty()) item {
            Column {
                TextButton(markAll, enabled = !busy && entries.any { !it.isRead }) { Text("Mark all as read") }
                TextButton({ confirmClear = true }, enabled = !busy) { Text("Clear notifications") }
            }
        }
        when (alerts) {
            StreamState.Loading -> item { LoadingContent() }
            is StreamState.Error -> item {
                MessageCard(alerts.message, true)
                TextButton(retry) { Text("Retry") }
            }
            is StreamState.Ready -> {
                val filtered = entries.filter { !unread || !it.isRead }
                if (filtered.isEmpty()) item { MessageCard(if (unread && entries.isNotEmpty()) "No unread alerts" else "No alerts yet") }
                items(filtered, key = { it.id }) { alert ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (!alert.isRead) Box(Modifier.size(8.dp).background(Teal, CircleShape))
                                val heading = when (alert.type) {
                                    AlertType.TEMPERATURE_HIGH -> "Temperature above limit"
                                    AlertType.TEMPERATURE_LOW -> "Temperature below limit"
                                    AlertType.LIGHT_CHANGED_TO_DARK -> "Light level changed to DARK"
                                    AlertType.LIGHT_CHANGED_TO_LIGHT -> "Light level changed to LIGHT"
                                }
                                Text(heading, style = MaterialTheme.typography.titleMedium)
                            }
                            Text("Temperature · ${displayTemperature(alert.temperatureC, unit)}", style = MaterialTheme.typography.titleMedium)
                            alert.thresholdC?.let { Text("Limit · ${displayTemperature(it, unit)}", color = Muted) }
                            Text(displayTime(alert.timestamp), color = Muted, style = MaterialTheme.typography.bodySmall)
                            if (!alert.isRead) TextButton({ markRead(alert.id) }, enabled = !busy) { Text("Mark as read") }
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false },
        title = { Text("Clear notifications?") }, text = { Text("This removes all alerts from your account.") },
        confirmButton = { TextButton({ confirmClear = false; clear() }) { Text("Clear") } },
        dismissButton = { TextButton({ confirmClear = false }) { Text("Cancel") } })
}
