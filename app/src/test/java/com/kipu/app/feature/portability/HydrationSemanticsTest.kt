package com.kipu.app.feature.portability
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.session.*
import com.kipu.app.feature.accounts.data.remote.*
import com.kipu.app.feature.debts.data.local.DebtDao
import com.kipu.app.feature.debts.data.sync.*
import com.kipu.app.feature.goals.data.sync.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application=android.app.Application::class,sdk=[34])
class HydrationSemanticsTest {
    @Test fun normalSyncStopsRetryingConflictsButRestoreReportsFailure() = runTest {
        val session = mockk<SessionCoordinator> { every { currentOwner } returns LocalOwner("owner") }
        val api = mockk<FinancialInstrumentsApi>()
        val page = PullChangesResponseDto(listOf(SyncChangeItemDto(1,"GOAL","g",1,"UPSERT",kotlinx.serialization.json.buildJsonObject {})),1)
        coEvery { api.pullChanges(any(),any()) } returns FinancialApiResponse.Success(page)
        val debtDao = mockk<DebtDao>(relaxed=true)
        val debtApplier = mockk<DebtChangeFeedApplier>()
        coEvery { debtApplier.applyPage(any(),any()) } returns DebtChangeApplyResult.Conflict("debt",2,1)
        val debt = DebtHydration(debtDao,api,debtApplier,session)
        assertTrue(debt.pullChanges("owner")); assertFalse(debt.pullChanges("owner",full=true))
        val db = mockk<KipuDatabase>(relaxed=true)
        val goalApplier = mockk<GoalChangeFeedApplier>()
        coEvery { goalApplier.applyPage(any(),any()) } returns GoalChangeApplyResult.Conflict("goal",2,1)
        val goal = GoalHydration(api,goalApplier,db,mockk(relaxed=true),session)
        assertTrue(goal.pullChanges("owner")); assertFalse(goal.pullChanges("owner",full=true))
        coEvery { api.pullChanges(any(),any()) } returns FinancialApiResponse.Error(403,null)
        assertTrue(goal.pullChanges("owner")); assertFalse(goal.pullChanges("owner",full=true))
    }
}
