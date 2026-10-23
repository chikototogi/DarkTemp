package com.darktemp.app.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.darktemp.app.core.ui.*
import com.darktemp.app.feature.auth.AuthUiState
import com.darktemp.app.core.util.celsiusToFahrenheit
import com.darktemp.app.core.util.fahrenheitToCelsius
import com.darktemp.app.core.util.validateThresholds
import com.darktemp.app.data.model.*
import com.darktemp.app.feature.dashboard.MonitorUiState
import java.util.Locale

@Composable
fun SettingsScreen(
    state: MonitorUiState,
    securityState: AuthUiState,
    save: (Preferences) -> Unit,
    changePassword: (String, String, String) -> Unit,
    signOut: () -> Unit,
    retry: () -> Unit,
) {
    var confirmSignOut by remember { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordValidationError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(securityState.message) {
        if (securityState.message == "Password updated.") {
            currentPassword = ""
            newPassword = ""
            confirmPassword = ""
            passwordValidationError = null
        }
    }
    PageColumn {
        Text("Settings", style = MaterialTheme.typography.headlineLarge)
        val profile = (state.profile as? StreamState.Ready)?.value
        if (profile != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Account", color = Teal, style = MaterialTheme.typography.labelLarge)
                    Text("${profile.firstName} ${profile.lastName}", style = MaterialTheme.typography.titleLarge)
                    Text(profile.email, color = Muted)
                }
            }
        }
        when (val preferences = state.preferences) {
            StreamState.Loading -> LoadingContent()
            is StreamState.Error -> {
                MessageCard(preferences.message, true)
                OutlinedButton(retry) { Text("Retry") }
            }
            is StreamState.Ready -> PreferencesForm(preferences.value, state.busy, state.connected, save)
        }
        HorizontalDivider()
        Text("Change password", style = MaterialTheme.typography.titleLarge)
        Text("Confirm your current password before choosing a new one.", color = Muted, style = MaterialTheme.typography.bodySmall)
        securityState.error?.let { MessageCard(it, true) }
        securityState.message?.let { MessageCard(it) }
        FormField(currentPassword, { currentPassword = it }, "Current password", !securityState.busy, secret = true)
        FormField(newPassword, { newPassword = it }, "New password", !securityState.busy, secret = true)
        FormField(confirmPassword, { confirmPassword = it }, "Confirm new password", !securityState.busy, secret = true)
        passwordValidationError?.let { MessageCard(it, true) }
        Button(onClick = {
            passwordValidationError = when {
                currentPassword.isBlank() -> "Enter your current password."
                newPassword.length < 6 -> "The new password must contain at least 6 characters."
                newPassword != confirmPassword -> "The new passwords do not match."
                newPassword == currentPassword -> "Choose a new password different from the current one."
                else -> null
            }
            if (passwordValidationError == null) changePassword(currentPassword, newPassword, confirmPassword)
        }, enabled = !securityState.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            if (securityState.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Update password")
        }
        HorizontalDivider()
        OutlinedButton(
            { confirmSignOut = true },
            Modifier.fillMaxWidth().heightIn(min = 52.dp),
            enabled = !state.busy && !securityState.busy,
        ) { Text("Sign out") }
    }
    if (confirmSignOut) AlertDialog(onDismissRequest = { confirmSignOut = false },
        title = { Text("Sign out?") }, text = { Text("You can sign in again to view your readings and alerts.") },
        confirmButton = { TextButton({ confirmSignOut = false; signOut() }) { Text("Sign out") } },
        dismissButton = { TextButton({ confirmSignOut = false }) { Text("Cancel") } })
}

private fun editableTemperature(value: Double, unit: TemperatureUnit) =
    String.format(Locale.US, "%.2f", if (unit == TemperatureUnit.F) celsiusToFahrenheit(value) else value).trimEnd('0').trimEnd('.')

@Composable
private fun ColumnScope.PreferencesForm(prefs: Preferences, busy: Boolean, connected: Boolean, save: (Preferences) -> Unit) {
    var minimum by rememberSaveable { mutableStateOf(editableTemperature(prefs.minC, prefs.unit)) }
    var maximum by rememberSaveable { mutableStateOf(editableTemperature(prefs.maxC, prefs.unit)) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(prefs.minC, prefs.maxC, prefs.unit) {
        minimum = editableTemperature(prefs.minC, prefs.unit)
        maximum = editableTemperature(prefs.maxC, prefs.unit)
        error = null
    }
    Text("Temperature unit", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TemperatureUnit.entries.forEach { unit ->
            FilterChip(selected = prefs.unit == unit, onClick = { save(prefs.copy(unit = unit)) },
                enabled = !busy && connected,
                label = { Text(if (unit == TemperatureUnit.C) "Celsius · °C" else "Fahrenheit · °F") })
        }
    }
    HorizontalDivider()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Light-change alerts", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(
            checked = prefs.lightAlertsEnabled,
            onCheckedChange = { save(prefs.copy(lightAlertsEnabled = it)) },
            enabled = !busy && connected,
        )
    }
    Text("Alert when the sensor detects a change between LIGHT and DARK.", color = Muted, style = MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Temperature alerts", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(checked = prefs.alertsEnabled, onCheckedChange = { save(prefs.copy(alertsEnabled = it)) }, enabled = !busy && connected)
    }
    Text(
        "Alert when a reading goes below your minimum or above your maximum temperature.",
        color = Muted,
        style = MaterialTheme.typography.bodySmall,
    )
    FormField(minimum, { minimum = it }, "Minimum (°${prefs.unit.name})", !busy, KeyboardType.Decimal)
    FormField(maximum, { maximum = it }, "Maximum (°${prefs.unit.name})", !busy, KeyboardType.Decimal)
    error?.let { MessageCard(it, true) }
    if (!connected) MessageCard("Reconnect to save your preferences.")
    Button(onClick = {
        val low = minimum.replace(',', '.').toDoubleOrNull()
        val high = maximum.replace(',', '.').toDoubleOrNull()
        if (low == null || high == null || !low.isFinite() || !high.isFinite()) error = "Enter two valid temperatures."
        else {
            val lowC = if (prefs.unit == TemperatureUnit.F) fahrenheitToCelsius(low) else low
            val highC = if (prefs.unit == TemperatureUnit.F) fahrenheitToCelsius(high) else high
            error = validateThresholds(lowC, highC)
            if (error == null) save(prefs.copy(minC = lowC, maxC = highC))
        }
    }, enabled = !busy && connected, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Save limits")
    }
}
