package com.darktemp.app.core.config

import android.content.Context
import com.darktemp.app.data.repository.AuthRepository
import com.darktemp.app.data.repository.MonitoringRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

sealed interface FirebaseStatus {
    data class Ready(val auth: AuthRepository, val monitoring: MonitoringRepository) : FirebaseStatus
    data class SetupRequired(val message: String) : FirebaseStatus
}

object FirebaseProvider {
    @Volatile private var initialized: FirebaseStatus? = null

    @Synchronized fun initialize(context: Context): FirebaseStatus {
        initialized?.let { return it }
        val status = try {
            val app = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
                ?: FirebaseApp.initializeApp(context)
            if (app == null || app.options.databaseUrl.isNullOrBlank()) {
                FirebaseStatus.SetupRequired("Add your project's google-services.json to app/, after creating Realtime Database, then rebuild. See FIREBASE_SETUP.md.")
            } else {
                val firebaseAuth = FirebaseAuth.getInstance(app)
                val database = FirebaseDatabase.getInstance(app, requireNotNull(app.options.databaseUrl))
                // Default memory cache only: no disk persistence of another user's private inbox.
                FirebaseStatus.Ready(AuthRepository(firebaseAuth, database), MonitoringRepository(firebaseAuth, database))
            }
        } catch (_: Exception) {
            FirebaseStatus.SetupRequired("Firebase configuration could not be loaded. Check app/google-services.json, the Android package, and database URL, then rebuild.")
        }
        initialized = status
        return status
    }
}
