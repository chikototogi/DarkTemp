package com.darktemp.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke test for the shipped, deliberately unconfigured build. No fake Firebase project. */
@RunWith(AndroidJUnit4::class)
class SetupLaunchTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun missingFirebaseShowsSetupInsteadOfCrashing() {
        val context = compose.activity
        val appId = context.resources.getIdentifier("google_app_id", "string", context.packageName)
        assumeTrue("Run this test on an unconfigured build", appId == 0)
        compose.onNodeWithText("Firebase setup required").assertIsDisplayed()
    }
}
