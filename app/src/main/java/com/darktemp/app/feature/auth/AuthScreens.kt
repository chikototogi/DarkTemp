package com.darktemp.app.feature.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.darktemp.app.core.ui.*
import com.darktemp.app.core.util.FormValidation

@Composable
fun AuthNavigation(viewModel: AuthViewModel) {
    val nav = rememberNavController()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.registrationComplete) {
        if (state.registrationComplete) nav.navigate("login") { popUpTo("login") { inclusive = true } }
    }
    NavHost(nav, startDestination = "login") {
        composable("login") {
            LoginScreen(state, viewModel::validationError, viewModel::login,
                { viewModel.clearMessage(); nav.navigate("register") },
                { viewModel.clearMessage(); nav.navigate("reset") })
        }
        composable("register") {
            RegisterScreen(state, viewModel::validationError, viewModel::register) {
                viewModel.clearMessage(); nav.popBackStack()
            }
        }
        composable("reset") {
            ResetScreen(state, viewModel::validationError, viewModel::reset) {
                viewModel.clearMessage(); nav.popBackStack()
            }
        }
    }
}

@Composable
private fun AuthMessages(state: AuthUiState) {
    state.error?.let { MessageCard(it, true) }
    state.message?.let { MessageCard(it) }
}

@Composable
private fun LoginScreen(state: AuthUiState, error: (String) -> Unit, login: (String, String) -> Unit,
    register: () -> Unit, reset: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords stay in memory only; they are never placed in saved instance state.
    var password by remember { mutableStateOf("") }
    AuthLayout {
        Text("Sign in", style = MaterialTheme.typography.headlineMedium)
        Text("Monitor your room.", color = Muted)
        AuthMessages(state)
        FormField(email, { email = it }, "Email", !state.busy, KeyboardType.Email)
        FormField(password, { password = it }, "Password", !state.busy, secret = true)
        SubmitButton("Sign in", state.busy) {
            val problem = FormValidation.login(email, password)
            if (problem != null) error(problem) else login(email, password)
        }
        TextButton(reset, enabled = !state.busy) { Text("Forgot password?") }
        HorizontalDivider()
        TextButton(register, enabled = !state.busy) { Text("Create an account") }
    }
}

@Composable
private fun RegisterScreen(state: AuthUiState, error: (String) -> Unit,
    register: (String, String, String, String) -> Unit, back: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(1) }
    var first by rememberSaveable { mutableStateOf("") }
    var last by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    BackHandler { if (!state.busy) { if (step == 2) step = 1 else back() } }
    AuthLayout {
        Text("Create account", style = MaterialTheme.typography.headlineMedium)
        Text("Step $step of 2", color = Teal)
        Row(
            Modifier.fillMaxWidth().semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(step / 2f, 0f..1f)
            },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(2) { index ->
                Box(
                    Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50))
                        .background(if (index < step) Teal else MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
        AuthMessages(state)
        if (step == 1) {
            FormField(first, { first = it }, "First name", !state.busy)
            FormField(last, { last = it }, "Last name", !state.busy)
            SubmitButton("Next", state.busy) {
                val problem = FormValidation.name(first, "first name") ?: FormValidation.name(last, "last name")
                if (problem != null) error(problem) else step = 2
            }
        } else {
            FormField(email, { email = it }, "Email", !state.busy, KeyboardType.Email)
            FormField(password, { password = it }, "Password", !state.busy, secret = true)
            FormField(confirm, { confirm = it }, "Confirm password", !state.busy, secret = true)
            Text("Use at least 6 characters.", style = MaterialTheme.typography.bodySmall, color = Muted)
            SubmitButton("Create account", state.busy) {
                val problem = FormValidation.registration(first, last, email, password, confirm)
                if (problem != null) error(problem) else register(first, last, email, password)
            }
        }
        TextButton({ if (step == 2) step = 1 else back() }, enabled = !state.busy) { Text("Back") }
    }
}

@Composable
private fun ResetScreen(state: AuthUiState, error: (String) -> Unit, reset: (String) -> Unit, back: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    AuthLayout {
        Text("Reset password", style = MaterialTheme.typography.headlineMedium)
        AuthMessages(state)
        FormField(email, { email = it }, "Email", !state.busy, KeyboardType.Email)
        SubmitButton("Send reset email", state.busy) {
            val problem = FormValidation.email(email)
            if (problem != null) error(problem) else reset(email)
        }
        TextButton(back, enabled = !state.busy) { Text("Return to sign in") }
    }
}

@Composable
fun ProfileRecoveryScreen(viewModel: AuthViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var first by rememberSaveable { mutableStateOf("") }
    var last by rememberSaveable { mutableStateOf("") }
    AuthLayout {
        Text("Complete your profile", style = MaterialTheme.typography.headlineSmall)
        Text("Your account exists. Save your name to finish setup.", color = Muted)
        AuthMessages(state)
        FormField(first, { first = it }, "First name", !state.busy)
        FormField(last, { last = it }, "Last name", !state.busy)
        SubmitButton("Save profile", state.busy) {
            val problem = FormValidation.name(first, "first name") ?: FormValidation.name(last, "last name")
            if (problem != null) viewModel.validationError(problem) else viewModel.recover(first, last)
        }
        TextButton(viewModel::signOut, enabled = !state.busy) { Text("Sign out") }
    }
}
