package com.darktemp.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darktemp.app.data.model.AuthSession
import com.darktemp.app.data.repository.AuthRepository
import com.darktemp.app.core.util.userError
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AuthUiState(val busy: Boolean = false, val error: String? = null, val message: String? = null,
    val registrationComplete: Boolean = false)

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    val session = repository.session.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val mutableState = MutableStateFlow(AuthUiState())
    val state = mutableState.asStateFlow()

    fun clearMessage() { mutableState.update { it.copy(error = null, message = null, registrationComplete = false) } }
    fun validationError(message: String) { mutableState.update { it.copy(error = message, message = null) } }
    fun login(email: String, password: String) = request { repository.login(email.trim(), password) }
    fun register(first: String, last: String, email: String, password: String) = request(
        success = "Account created. Sign in with your email and password.", registration = true
    ) { repository.register(first.trim(), last.trim(), email.trim(), password) }
    fun reset(email: String) = request(success = "If an account exists for this email, a reset link will arrive shortly.") {
        repository.sendPasswordReset(email.trim())
    }
    fun changePassword(current: String, new: String, confirmation: String) =
        request(success = "Password updated.") { repository.changePassword(current, new, confirmation) }
    fun recover(first: String, last: String) = request { repository.recoverProfile(first.trim(), last.trim()) }
    fun signOut() { repository.signOut(); mutableState.value = AuthUiState() }

    private fun request(success: String? = null, registration: Boolean = false, action: suspend () -> Result<Unit>) {
        if (state.value.busy) return
        mutableState.value = AuthUiState(busy = true)
        viewModelScope.launch {
            val result = action()
            mutableState.value = if (result.isSuccess) AuthUiState(message = success, registrationComplete = registration)
                else AuthUiState(error = result.exceptionOrNull()?.let(::userError) ?: "The request failed. Try again.")
        }
    }
}
