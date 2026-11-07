package com.kipu.app.feature.accounts.presentation.dashboard

import org.junit.Assert.*
import org.junit.Test

class DashboardInitialsTest {
    @Test fun missingNameUsesNeutralIdentity() {
        assertNull(dashboardInitials(null))
        assertNull(dashboardInitials("  "))
    }
    @Test fun initialsUseAtMostTwoTrimmedWords() {
        assertEquals("ÁP", dashboardInitials("  álvaro   pérez castro "))
        assertEquals("L", dashboardInitials("luis"))
        assertEquals("MC", dashboardInitials("María\nCastro"))
    }
}
