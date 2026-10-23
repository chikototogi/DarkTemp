package com.darktemp.app.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darktemp.app.core.config.MonitoringConfig
import com.darktemp.app.core.util.isFreshReading
import com.darktemp.app.core.ui.*
import com.darktemp.app.data.model.*

@Composable
fun DashboardScreen(state: MonitorUiState, unit: TemperatureUnit, retry: () -> Unit) {
    PageColumn {
        Text("DARKTEMP", style = MaterialTheme.typography.labelLarge, color = Teal, letterSpacing = 3.sp)
        Text("Room overview", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        if (!state.connected) MessageCard("Database disconnected. Waiting to reconnect.")
        when (val latest = state.latest) {
            StreamState.Loading -> LoadingContent("Waiting for sensor readings…")
            is StreamState.Error -> {
                MessageCard(latest.message, true)
                OutlinedButton(retry) { Text("Retry") }
            }
            is StreamState.Ready -> {
                val reading = latest.value
                if (reading == null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(8.dp).background(Muted, CircleShape))
                        Text("Waiting for sensor data", style = MaterialTheme.typography.labelLarge, color = Muted)
                    }
                    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFF0A4053), Panel))).padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Outlined.Thermostat, null, tint = Teal)
                                Text("Ambient temperature", color = Muted)
                            }
                            Text(
                                "-- ${if (unit == TemperatureUnit.C) "°C" else "°F"}",
                                fontSize = 48.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Muted,
                            )
                            Text("Unavailable until the device sends a reading", color = Muted)
                        }
                    }
                    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            Icon(Icons.Outlined.Sensors, null, Modifier.size(40.dp), tint = Teal)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Light status", color = Muted)
                                Text("--", style = MaterialTheme.typography.headlineMedium, color = Muted)
                                Text("Unavailable until the device sends a reading", color = Muted)
                            }
                        }
                    }
                    HorizontalDivider()
                    Text("Last successful update", color = Muted, style = MaterialTheme.typography.labelLarge)
                    Text("No readings yet", color = Muted)
                    Text("Device · ${MonitoringConfig.DEVICE_ID}", color = Muted, style = MaterialTheme.typography.bodySmall)
                } else {
                    val age = state.now - reading.timestamp
                    val future = age < -MonitoringConfig.FUTURE_TOLERANCE_MS
                    val fresh = isFreshReading(reading, state.now)
                    val status = when { future -> "Device clock mismatch"; !fresh -> "Reading is stale"; !state.connected -> "Last received reading"; else -> "Recent sensor data" }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(8.dp).background(if (fresh && state.connected) Teal else MaterialTheme.colorScheme.error, CircleShape))
                        Text(status, style = MaterialTheme.typography.labelLarge, color = Muted)
                    }
                    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFF0A4053), Panel))).padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Outlined.Thermostat, null, tint = Teal)
                                Text("Ambient temperature", color = Muted)
                            }
                            Text(displayTemperature(reading.temperatureC, unit), fontSize = 48.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (fresh) "Room temperature" else "Last recorded temperature", color = Muted)
                        }
                    }
                    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            Icon(if (reading.lightStatus == "LIGHT") Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                null, Modifier.size(40.dp), tint = Teal)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Light status", color = Muted)
                                Text(reading.lightStatus, style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                    }
                    HorizontalDivider()
                    Text("Last successful update", color = Muted, style = MaterialTheme.typography.labelLarge)
                    Text(displayTime(reading.timestamp))
                    Text("Device · ${reading.deviceId}", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (state.preferences is StreamState.Error) MessageCard(state.preferences.message, true)
    }
}
