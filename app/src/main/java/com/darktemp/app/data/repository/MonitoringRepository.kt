package com.darktemp.app.data.repository

import com.darktemp.app.core.config.MonitoringConfig
import com.darktemp.app.core.util.decideAlert
import com.darktemp.app.core.util.validateThresholds
import com.darktemp.app.data.firebase.FirebaseMapper
import com.darktemp.app.data.firebase.repositoryResult
import com.darktemp.app.data.firebase.transact
import com.darktemp.app.data.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class MonitoringRepository internal constructor(private val auth: FirebaseAuth, private val database: FirebaseDatabase) {
    private val device = database.getReference("devices").child(MonitoringConfig.DEVICE_ID)

    fun observeProfile(uid: String): Flow<StreamState<Profile?>> = observe(user(uid)) { FirebaseMapper.profile(it.value) }
    fun observePreferences(uid: String): Flow<StreamState<Preferences>> = observe(user(uid).child("preferences")) { FirebaseMapper.preferences(it.value) }
    fun observeLatest(): Flow<StreamState<SensorReading?>> = observe(device.child("latest")) { if (it.exists()) FirebaseMapper.sensor(it.value) else null }
    fun observeHistory(): Flow<StreamState<List<SensorReading>>> = observe(device.child("history").orderByChild("timestamp").limitToLast(MonitoringConfig.HISTORY_LIMIT)) {
        it.children.map { child -> FirebaseMapper.sensor(child.value, history = true) }.sortedByDescending { reading -> reading.timestamp }
    }
    fun observeAlerts(uid: String): Flow<StreamState<List<SensorAlert>>> = observe(user(uid).child("alerts").orderByChild("timestamp")) {
        it.children.map { child -> FirebaseMapper.alert(requireNotNull(child.key), child.value) }.sortedByDescending { alert -> alert.timestamp }
    }

    fun observeConnection(): Flow<Boolean> = callbackFlow {
        val reference = database.getReference(".info/connected")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { trySend(snapshot.value == true) }
            override fun onCancelled(error: DatabaseError) { trySend(false) }
        }
        reference.addValueEventListener(listener)
        awaitClose { reference.removeEventListener(listener) }
    }

    suspend fun savePreferences(uid: String, preferences: Preferences): Result<Unit> = repositoryResult {
        require(validateThresholds(preferences.minC, preferences.maxC) == null) { validateThresholds(preferences.minC, preferences.maxC).orEmpty() }
        user(uid).child("preferences").setValue(FirebaseMapper.preferencesMap(preferences)).await()
    }

    suspend fun processReading(uid: String, reading: SensorReading): Result<Unit> = repositoryResult {
        val reference = user(uid)
        // Warm the parent cache; transactions may initially run with a null local snapshot.
        reference.get().await()
        reference.transact { current ->
            check(auth.currentUser?.uid == uid) { "Your session has changed. Sign in again." }
            if (current.value == null) return@transact Transaction.success(current)
            val preferences = FirebaseMapper.preferences(current.child("preferences").value)
            val previous = FirebaseMapper.alertState(current.child("alertState").value)
            val decision = decideAlert(reading, preferences, previous, System.currentTimeMillis()) ?: return@transact Transaction.abort()
            current.child("alertState").value = mapOf(
                "lastProcessedReadingTimestamp" to decision.state.lastTimestamp,
                "lastTemperatureZone" to decision.state.zone.name,
                "lastLightStatus" to decision.state.lastLightStatus,
            )
            decision.alert?.let { current.child("alerts").child(it.id).value = FirebaseMapper.alertMap(it) }
            decision.lightAlert?.let { current.child("alerts").child(it.id).value = FirebaseMapper.alertMap(it) }
            Transaction.success(current)
        }
        Unit
    }

    suspend fun markAlertRead(uid: String, id: String): Result<Unit> = repositoryResult {
        require(Regex("^device001_[0-9]+(_light)?$").matches(id)) { "Invalid alert ID." }
        val reference = user(uid).child("alerts").child(id)
        reference.get().await()
        reference.transact { current ->
            check(auth.currentUser?.uid == uid) { "Your session has changed. Sign in again." }
            if (current.value != null) current.child("isRead").value = true
            Transaction.success(current)
        }
        Unit
    }

    suspend fun markAllRead(uid: String): Result<Unit> = repositoryResult {
        val reference = user(uid).child("alerts")
        reference.get().await()
        reference.transact { current ->
            check(auth.currentUser?.uid == uid) { "Your session has changed. Sign in again." }
            current.children.forEach { it.child("isRead").value = true }
            Transaction.success(current)
        }
        Unit
    }

    suspend fun clearAlerts(uid: String): Result<Unit> = repositoryResult {
        // Preserve alertState: reopening the app after clearing must not recreate the last alert.
        user(uid).child("alerts").removeValue().await()
    }

    private fun user(uid: String): DatabaseReference {
        check(auth.currentUser?.uid == uid) { "Your session has changed. Sign in again." }
        return database.getReference("users").child(uid)
    }

    private fun <T> observe(query: Query, map: (DataSnapshot) -> T): Flow<StreamState<T>> = callbackFlow {
        trySend(StreamState.Loading)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    trySend(StreamState.Ready(map(snapshot)))
                } catch (error: Exception) {
                    trySend(StreamState.Error(error.localizedMessage ?: "The database contains invalid data."))
                }
            }
            override fun onCancelled(error: DatabaseError) {
                trySend(StreamState.Error(if (error.code == DatabaseError.PERMISSION_DENIED)
                    "Database permission denied. Check the deployed rules and signed-in account."
                else "Database connection failed. ${error.message}"))
            }
        }
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }
}
