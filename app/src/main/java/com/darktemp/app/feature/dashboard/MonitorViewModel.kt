package com.darktemp.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darktemp.app.data.model.*
import com.darktemp.app.data.repository.MonitoringRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class MonitorUiState(
    val profile: StreamState<Profile?> = StreamState.Loading,
    val preferences: StreamState<Preferences> = StreamState.Loading,
    val latest: StreamState<SensorReading?> = StreamState.Loading,
    val history: StreamState<List<SensorReading>> = StreamState.Loading,
    val alerts: StreamState<List<SensorAlert>> = StreamState.Loading,
    val connected: Boolean = false,
    val now: Long = System.currentTimeMillis(),
    val busy: Boolean = false,
    val message: String? = null,
    val actionError: String? = null
)

class MonitorViewModel(private val uid: String, private val repository: MonitoringRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(MonitorUiState())
    val state = mutableState.asStateFlow()
    private val restart = MutableStateFlow(0)

    // One collector set belongs to the signed-in session, not to individual tabs.
    // The UI runs this only while STARTED. Cancellation removes all Firebase listeners.
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun monitor() = restart.collectLatest {
        coroutineScope {
            launch { repository.observeProfile(uid).collect { value -> mutableState.update { it.copy(profile = value) } } }
            launch { repository.observeHistory().collect { value -> mutableState.update { it.copy(history = value) } } }
            launch { repository.observeAlerts(uid).collect { value -> mutableState.update { it.copy(alerts = value) } } }
            launch {
                combine(repository.observeLatest(), repository.observePreferences(uid), repository.observeConnection()) { latest, prefs, connected ->
                    Triple(latest, prefs, connected)
                }.collectLatest { (latest, prefs, connected) ->
                    mutableState.update { it.copy(latest = latest, preferences = prefs, connected = connected) }
                    val reading = (latest as? StreamState.Ready)?.value
                    if (connected && reading != null && prefs is StreamState.Ready) {
                        repository.processReading(uid, reading).onFailure { failure ->
                            mutableState.update { it.copy(actionError = failure.message ?: "Could not save the alert. Tap retry.") }
                        }
                    }
                }
            }
            launch { while (isActive) { mutableState.update { it.copy(now = System.currentTimeMillis()) }; delay(5_000) } }
        }
    }

    fun retry() { dismissMessage(); restart.update { it + 1 } }
    fun dismissMessage() { mutableState.update { it.copy(message = null, actionError = null) } }
    fun savePreferences(value: Preferences) = operation("Preferences saved.") { repository.savePreferences(uid, value) }
    fun markAllRead() = operation { repository.markAllRead(uid) }
    fun markRead(id: String) = operation { repository.markAlertRead(uid, id) }
    fun clearAlerts() = operation("Notifications cleared.") { repository.clearAlerts(uid) }

    private fun operation(message: String? = null, action: suspend () -> Result<Unit>) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true, actionError = null, message = null) }
        viewModelScope.launch {
            val result = action()
            mutableState.update { it.copy(busy = false, message = if (result.isSuccess) message else null,
                actionError = result.exceptionOrNull()?.message) }
        }
    }
}
