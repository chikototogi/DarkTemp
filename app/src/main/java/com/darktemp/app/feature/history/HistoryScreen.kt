package com.darktemp.app.feature.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darktemp.app.core.ui.*
import com.darktemp.app.data.model.*

@Composable
fun HistoryScreen(history: StreamState<List<SensorReading>>, unit: TemperatureUnit, retry: () -> Unit) {
    var filter by remember { mutableStateOf("All readings") }
    var filterExpanded by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("History logs", style = MaterialTheme.typography.headlineLarge) }
        item { Text("Latest 100 readings", color = Muted) }
        item {
            Box {
                OutlinedButton(onClick = { filterExpanded = true }) {
                    Text(filter)
                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Choose history filter")
                }
                DropdownMenu(expanded = filterExpanded, onDismissRequest = { filterExpanded = false }) {
                    listOf("All readings", "LIGHT", "DARK").forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = { filter = option; filterExpanded = false },
                        )
                    }
                }
            }
        }
        when (history) {
            StreamState.Loading -> item { LoadingContent() }
            is StreamState.Error -> item {
                MessageCard(history.message, true)
                TextButton(retry) { Text("Retry") }
            }
            is StreamState.Ready -> {
                val filtered = history.value.filter { filter == "All readings" || it.lightStatus == filter }
                if (filtered.isEmpty()) item {
                    MessageCard(if (history.value.isEmpty()) "No readings recorded yet." else "No $filter readings in the latest 100.")
                }
                items(filtered) { reading ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(displayTime(reading.timestamp), style = MaterialTheme.typography.labelLarge, color = Muted)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(displayTemperature(reading.temperatureC, unit), style = MaterialTheme.typography.titleLarge)
                                Text(reading.lightStatus, color = Teal, style = MaterialTheme.typography.titleMedium)
                            }
                            Text("LDR · ${reading.lightValue}", style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                    }
                }
            }
        }
    }
}
