package com.darktemp.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.darktemp.app.core.config.FirebaseProvider
import com.darktemp.app.core.config.FirebaseStatus
import com.darktemp.app.core.navigation.SignedInApp
import com.darktemp.app.core.ui.*
import com.darktemp.app.data.model.AuthSession
import com.darktemp.app.feature.auth.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val firebase = FirebaseProvider.initialize(applicationContext)
        setContent {
            DarkTempTheme {
                Surface {
                    when (firebase) {
                        is FirebaseStatus.SetupRequired -> AuthLayout {
                            Text("Firebase setup required", style = MaterialTheme.typography.headlineSmall)
                            MessageCard(firebase.message)
                            Text("Follow FIREBASE_SETUP.md, add app/google-services.json, then rebuild the app.", color = Muted)
                        }
                        is FirebaseStatus.Ready -> {
                            val auth: AuthViewModel = viewModel(factory = simpleFactory { AuthViewModel(firebase.auth) })
                            val session by auth.session.collectAsStateWithLifecycle()
                            when (val current = session) {
                                null -> AuthLayout { LoadingContent("Checking your session…") }
                                AuthSession.SignedOut -> AuthNavigation(auth)
                                is AuthSession.SignedIn -> key(current.uid) {
                                    SignedInApp(current.uid, firebase.monitoring, auth)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun <T : ViewModel> simpleFactory(create: () -> T): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
}
