package com.kipu.app.feature.movements.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MovementSyncReauthenticationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun reconnectExplainsPendingMovementsAndOpensLogin() {
        var reconnect = false
        compose.setContent {
            KipuTheme { MovementReauthenticationBanner { reconnect = true } }
        }
        compose.onNodeWithTag("banner_sync_reauthentication").assertIsDisplayed()
        compose.onNodeWithText("Reconectar cuenta").assertIsDisplayed().performClick()
        assertTrue(reconnect)
    }
}
