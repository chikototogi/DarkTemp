package com.darktemp.app.core.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.darktemp.app.core.ui.*
import com.darktemp.app.data.model.*
import com.darktemp.app.data.repository.MonitoringRepository
import com.darktemp.app.feature.auth.*
import com.darktemp.app.feature.dashboard.*
import com.darktemp.app.feature.history.HistoryScreen
import com.darktemp.app.feature.notifications.NotificationsScreen
import com.darktemp.app.feature.settings.SettingsScreen
import com.darktemp.app.simpleFactory

private data class Destination(val route: String, val label: String, val icon: ImageVector)
private val destinations = listOf(
    Destination("dashboard", "Dashboard", Icons.Outlined.Dashboard),
    Destination("history", "History", Icons.Outlined.History),
    Destination("notifications", "Alerts", Icons.Outlined.Notifications),
    Destination("settings", "Settings", Icons.Outlined.Settings)
)

@Composable
fun SignedInApp(uid: String, repository: MonitoringRepository, auth: AuthViewModel) {
    // A fresh store per authenticated composition prevents user data surviving sign-out.
    val owner = remember(uid) { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    val vm: MonitorViewModel = viewModel(viewModelStoreOwner = owner, factory = simpleFactory { MonitorViewModel(uid, repository) })
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(vm, lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.monitor() } }
    val state by vm.state.collectAsStateWithLifecycle()
    when (val profile = state.profile) {
        StreamState.Loading -> AuthLayout { LoadingContent("Loading your profile…") }
        is StreamState.Error -> AuthLayout {
            MessageCard(profile.message, true)
            Button(vm::retry) { Text("Retry") }
            TextButton(auth::signOut) { Text("Sign out") }
        }
        is StreamState.Ready -> {
            if (profile.value == null) ProfileRecoveryScreen(auth)
            else MainNavigation(state, vm, auth)
        }
    }
}

@Composable
private fun MainNavigation(state: MonitorUiState, vm: MonitorViewModel, auth: AuthViewModel) {
    val nav = rememberNavController()
    val authState by auth.state.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.dismissMessage() }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        NavigationBar {
            destinations.forEach { destination ->
                NavigationBarItem(selected = route == destination.route,
                    onClick = {
                        nav.navigate(destination.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }, icon = { Icon(destination.icon, destination.label) }, label = { Text(destination.label) })
            }
        }
    }) { padding ->
        Column(Modifier.padding(padding).imePadding()) {
            state.actionError?.let { message ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    MessageCard(message, true)
                    Row { TextButton(vm::retry) { Text("Retry") }; TextButton(vm::dismissMessage) { Text("Dismiss") } }
                }
            }
            val unit = (state.preferences as? StreamState.Ready)?.value?.unit ?: TemperatureUnit.C
            NavHost(nav, startDestination = "dashboard", modifier = Modifier.weight(1f)) {
                composable("dashboard") { DashboardScreen(state, unit, vm::retry) }
                composable("history") { HistoryScreen(state.history, unit, vm::retry) }
                composable("notifications") {
                    NotificationsScreen(state.alerts, unit, state.busy, vm::markRead, vm::markAllRead, vm::clearAlerts, vm::retry)
                }
                composable("settings") {
                    SettingsScreen(state, authState, vm::savePreferences, auth::changePassword, auth::signOut, vm::retry)
                }
            }
        }
    }
}
