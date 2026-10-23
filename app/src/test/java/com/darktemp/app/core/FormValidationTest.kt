package com.darktemp.app.core

import com.darktemp.app.core.util.FormValidation
import org.junit.Assert.*
import org.junit.Test

class FormValidationTest {
    @Test fun acceptsValidRegistration() {
        assertNull(FormValidation.registration("Ada", "Lovelace", " ada@example.test ", "secret123", "secret123"))
        assertNull(FormValidation.registration("María", "De la Cruz", "maria@example.test", "abcdef", "abcdef"))
    }
    @Test fun rejectsMissingNamesMalformedEmailAndMismatch() {
        assertNotNull(FormValidation.registration(" ", "User", "a@example.test", "abcdef", "abcdef"))
        assertNotNull(FormValidation.registration("Test", "", "a@example.test", "abcdef", "abcdef"))
        assertNotNull(FormValidation.registration("Test", "User", "bad email", "abcdef", "abcdef"))
        assertNotNull(FormValidation.registration("Test", "User", "a@example.test", "abcde", "abcde"))
        assertNotNull(FormValidation.registration("Test", "User", "a@example.test", "abcdef", "ABCDEF"))
        assertNotNull(FormValidation.registration("a".repeat(61), "User", "a@example.test", "abcdef", "abcdef"))
    }
    @Test fun loginDoesNotApplyNewPasswordRulesToExistingAccounts() {
        assertNull(FormValidation.login("a@example.test", "123"))
        assertNotNull(FormValidation.login("a@example.test", ""))
        assertNotNull(FormValidation.login("a@", "abcdef"))
    }
}
