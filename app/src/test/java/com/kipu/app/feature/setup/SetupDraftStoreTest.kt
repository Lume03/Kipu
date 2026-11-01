package com.kipu.app.feature.setup

import org.junit.Assert.*
import org.junit.Test

class SetupDraftStoreTest {
    @Test fun `verified owner keeps email and choices only until owner changes`() {
        val store = SetupDraftStore()
        store.begin(" new@example.com ")
        store.bindOwner("A")
        store.update { it.copy(account = it.account.copy(alias = "Mi cuenta")) }
        store.bindOwner("A")
        assertEquals("Mi cuenta", store.draft.value.account.alias)
        assertEquals("new@example.com", store.draft.value.email)
        store.bindOwner("B")
        assertEquals(SetupDraft(), store.draft.value)
    }
}
