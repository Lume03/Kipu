package com.kipu.app.feature.auth.data

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class PlanOnboardingStoreTest {
    @Test fun `registration binds durable onboarding only to the verified owner and completing clears it`() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("plan_onboarding", 0).edit().clear().commit()
        val store = PlanOnboardingStore(context)
        assertFalse(store.isPending("returning-owner"))
        store.registered("new@example.com")
        store.bindRegisteredOwner("other@example.com", "B")
        assertFalse(store.isPending("B"))
        store.bindRegisteredOwner("new@example.com", "A")
        assertTrue(PlanOnboardingStore(context).isPending("A"))
        assertFalse(store.isPending("B"))
        store.completed("A")
        assertFalse(PlanOnboardingStore(context).isPending("A"))
        store.bindRegisteredOwner("new@example.com", "A")
        assertFalse(store.isPending("A"))
    }
}
