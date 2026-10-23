package com.darktemp.app.core.util

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.database.DatabaseException

fun userError(error: Throwable): String = when (error) {
    is FirebaseNetworkException -> "Could not connect. Check your internet connection and try again."
    is FirebaseTooManyRequestsException -> "Too many attempts. Wait a moment and try again."
    is FirebaseAuthException -> when (error.errorCode) {
        "ERROR_INVALID_EMAIL" -> "Enter a valid email address."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "This email already has an account. Sign in or reset your password."
        "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND", "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS" -> "Email or password is incorrect."
        "ERROR_WEAK_PASSWORD", "ERROR_PASSWORD_DOES_NOT_MEET_REQUIREMENTS" -> "Use a password that meets this Firebase project's password policy."
        "ERROR_USER_DISABLED" -> "This account is disabled. Contact the project owner."
        "ERROR_OPERATION_NOT_ALLOWED" -> "Email/password sign-in is not enabled in Firebase."
        else -> "Authentication failed. ${error.localizedMessage ?: "Please try again."}"
    }
    is DatabaseException -> "Database access failed. Check the connection and deployed Firebase rules."
    else -> error.localizedMessage ?: "Something went wrong. Please try again."
}
