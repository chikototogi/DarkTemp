package com.darktemp.app.core.util

object FormValidation {
    fun name(value: String, label: String): String? = when {
        value.isBlank() -> "Enter your $label."
        value.trim().length > 60 -> "$label must be 60 characters or fewer."
        else -> null
    }

    fun email(value: String): String? {
        val email = value.trim()
        return if (email.length > 254 || !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email)) "Enter a valid email address." else null
    }

    fun login(email: String, password: String): String? = email(email)
        ?: if (password.isEmpty()) "Enter your password." else null

    fun registration(firstName: String, lastName: String, email: String, password: String, confirmPassword: String): String? =
        name(firstName, "first name") ?: name(lastName, "last name") ?: email(email) ?: when {
            password.length < 6 -> "Use at least 6 characters for your password."
            password.length > 4096 -> "Password is too long."
            password != confirmPassword -> "Passwords do not match."
            else -> null
        }
}
