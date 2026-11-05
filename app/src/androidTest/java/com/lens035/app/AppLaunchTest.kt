package com.lens035.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The app opens straight into the home screen: no sign-in, all three entry points visible. */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeShowsEntryPoints() {
        rule.onNodeWithText("Fotoğraf düzenle").assertIsDisplayed()
        rule.onNodeWithText("Video").assertIsDisplayed()
        rule.onNodeWithText("Poz kamerası").assertIsDisplayed()
    }
}
