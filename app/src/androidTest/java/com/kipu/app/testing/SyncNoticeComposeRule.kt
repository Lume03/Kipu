package com.kipu.app.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import com.kipu.app.ui.component.LocalSyncRecoveryViewModel
import com.kipu.app.ui.component.SyncRecoveryViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow

/** UI fixtures provide a ViewModel explicitly and never open the owner's persistent database. */
fun createSyncNoticeComposeRule(): ComposeContentTestRule = SyncNoticeComposeRule(createComposeRule())

private class SyncNoticeComposeRule(private val delegate: ComposeContentTestRule) : ComposeContentTestRule by delegate {
    private val viewModel = mockk<SyncRecoveryViewModel>(relaxed = true) {
        every { issues } returns MutableStateFlow(emptyList())
        every { busy } returns MutableStateFlow(false)
        every { error } returns MutableStateFlow(null)
    }
    override fun setContent(composable: @Composable () -> Unit) {
        delegate.setContent {
            CompositionLocalProvider(LocalSyncRecoveryViewModel provides viewModel, content = composable)
        }
    }
}

inline fun <reified A : androidx.activity.ComponentActivity> createSyncNoticeAndroidComposeRule(): SyncNoticeAndroidRule<A> =
    SyncNoticeAndroidRule(androidx.compose.ui.test.junit4.createAndroidComposeRule<A>())

class SyncNoticeAndroidRule<A : androidx.activity.ComponentActivity>(
    private val delegate: androidx.compose.ui.test.junit4.AndroidComposeTestRule<androidx.test.ext.junit.rules.ActivityScenarioRule<A>, A>,
) : ComposeContentTestRule by delegate {
    val activity: A get() = delegate.activity
    private val viewModel = mockk<SyncRecoveryViewModel>(relaxed = true) {
        every { issues } returns MutableStateFlow(emptyList())
        every { busy } returns MutableStateFlow(false)
        every { error } returns MutableStateFlow(null)
    }
    override fun setContent(composable: @Composable () -> Unit) {
        delegate.setContent {
            CompositionLocalProvider(LocalSyncRecoveryViewModel provides viewModel, content = composable)
        }
    }
}
