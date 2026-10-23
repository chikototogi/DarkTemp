package com.darktemp.app.data.repository

import com.darktemp.app.core.util.FormValidation
import com.darktemp.app.data.firebase.FirebaseMapper
import com.darktemp.app.data.firebase.repositoryResult
import com.darktemp.app.data.firebase.transact
import com.darktemp.app.data.model.AuthSession
import com.darktemp.app.data.model.Preferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

class AuthRepository internal constructor(private val auth: FirebaseAuth, private val database: FirebaseDatabase) {
    @Volatile private var registering = false

    val session: Flow<AuthSession> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebase ->
            val user = firebase.currentUser
            trySend(if (user == null || registering) AuthSession.SignedOut else AuthSession.SignedIn(user.uid, user.email.orEmpty()))
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    suspend fun login(email: String, password: String): Result<Unit> = repositoryResult {
        require(FormValidation.login(email, password) == null) { FormValidation.login(email, password).orEmpty() }
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        Unit
    }

    suspend fun register(firstName: String, lastName: String, email: String, password: String): Result<Unit> {
        val validation = FormValidation.registration(firstName, lastName, email, password, password)
        if (validation != null) return Result.failure(IllegalArgumentException(validation))
        if (registering) return Result.failure(IllegalStateException("Account creation is already in progress."))
        registering = true
        var accountCreated = false
        return try {
            val user = requireNotNull(auth.createUserWithEmailAndPassword(email.trim(), password).await().user)
            accountCreated = true
            database.getReference("users").child(user.uid).setValue(mapOf(
                "firstName" to firstName.trim(), "lastName" to lastName.trim(),
                "email" to requireNotNull(user.email), "createdAt" to ServerValue.TIMESTAMP,
                "preferences" to FirebaseMapper.preferencesMap(Preferences()),
            )).await()
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (accountCreated) Result.failure(IllegalStateException(
                "Your sign-in account was created, but profile setup failed. Sign in with the same email and password to finish your profile. Check Firebase rules if it fails again.", error))
            else Result.failure(error)
        } finally {
            // Registration intentionally ends at Login, including a recoverable profile-write failure.
            auth.signOut()
            registering = false
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = repositoryResult {
        require(FormValidation.email(email) == null) { FormValidation.email(email).orEmpty() }
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    suspend fun changePassword(currentPassword: String, newPassword: String, confirmation: String): Result<Unit> = repositoryResult {
        require(currentPassword.isNotBlank()) { "Enter your current password." }
        require(newPassword.length >= 6) { "The new password must contain at least 6 characters." }
        require(newPassword == confirmation) { "The new passwords do not match." }
        require(newPassword != currentPassword) { "Choose a new password different from the current one." }
        val user = requireNotNull(auth.currentUser) { "Sign in again to change your password." }
        val email = requireNotNull(user.email) { "This account does not have an email/password sign-in." }
        try {
            user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword)).await()
        } catch (error: FirebaseAuthException) {
            if (error.errorCode in setOf("ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS"))
                throw IllegalArgumentException("Current password is incorrect.", error)
            throw error
        }
        user.updatePassword(newPassword).await()
    }

    suspend fun recoverProfile(firstName: String, lastName: String): Result<Unit> = repositoryResult {
        require(FormValidation.name(firstName, "first name") == null) { "Enter your first name (up to 60 characters)." }
        require(FormValidation.name(lastName, "last name") == null) { "Enter your last name (up to 60 characters)." }
        val user = requireNotNull(auth.currentUser) { "Sign in again to finish your profile." }
        val reference = database.getReference("users").child(user.uid)
        reference.get().await()
        val committed = reference.transact { current ->
            check(auth.currentUser?.uid == user.uid) { "Your session has changed. Sign in again." }
            current.child("firstName").value = firstName.trim()
            current.child("lastName").value = lastName.trim()
            current.child("email").value = requireNotNull(user.email)
            if (current.child("createdAt").value == null) current.child("createdAt").value = ServerValue.TIMESTAMP
            if (runCatching { FirebaseMapper.preferences(current.child("preferences").value) }.isFailure)
                current.child("preferences").value = FirebaseMapper.preferencesMap(Preferences())
            Transaction.success(current)
        }
        check(committed) { "Profile setup was interrupted. Please try again." }
    }

    fun signOut() = auth.signOut()
}
