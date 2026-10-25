package com.kipu.app.feature.budgets.data.remote

import com.kipu.app.feature.budgets.data.local.BudgetEntity
import com.kipu.app.feature.budgets.data.local.BudgetPeriodEntity
import com.kipu.app.feature.budgets.data.local.toDomain
import com.kipu.app.feature.budgets.domain.BudgetPeriodType
import com.kipu.app.feature.budgets.domain.BudgetSchedule
import com.kipu.app.feature.budgets.domain.BudgetScope
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetRemoteMappingsTest {
    private val owner = "11111111-1111-4111-8111-111111111111"
    private val budgetId = "22222222-2222-4222-8222-222222222222"
    private val food = "33333333-3333-4333-8333-333333333333"
    private val transport = "44444444-4444-4444-8444-444444444444"
    private val zone = ZoneId.of("UTC")

    @Test fun supabaseOffsetTimestampsPreserveTheInstantForBudgetsAndPeriods() {
        val timestamp = "2026-10-24T19:53:56.490936+00:00"
        val expected = java.time.Instant.parse("2026-10-24T19:53:56.490936Z").toEpochMilli()
        val row = budget().toRemoteDto().copy(createdAt = timestamp, updatedAt = "2026-10-24T14:53:56.490936-05:00").toLocalEntity(zone)
        assertEquals(expected, row.createdAt)
        assertEquals(expected, row.updatedAt)
        assertEquals(expected, period(food).toRemoteDto().copy(createdAt = timestamp).toLocalEntity().createdAt)
    }

    private fun budget(
        categoryId: String? = food,
        pendingScopeType: String? = null,
        pendingScopeCategoryId: String? = null,
        pendingScheduleType: String? = null,
        pendingRangeStartOn: String? = null,
        pendingRangeEndOn: String? = null,
        pendingEffectiveFrom: String? = null,
    ) = BudgetEntity(
        id = budgetId, userId = owner, name = "Comida", categoryId = categoryId, limitMinor = 50_000,
        currencyCode = "PEN", periodType = "MONTHLY", rangeStartOn = null, rangeEndOn = null,
        alertThresholdsBps = "5000,8000", status = "ACTIVE", revision = 3, createdOn = "2026-06-01",
        createdAt = 1_780_000_000_000, updatedAt = 1_780_000_100_000,
        pendingScopeType = pendingScopeType, pendingScopeCategoryId = pendingScopeCategoryId,
        pendingScheduleType = pendingScheduleType, pendingRangeStartOn = pendingRangeStartOn,
        pendingRangeEndOn = pendingRangeEndOn, pendingEffectiveFrom = pendingEffectiveFrom,
    )

    private fun period(scope: String?) = BudgetPeriodEntity(
        id = "$budgetId:2026-06-01:2026-07-01", userId = owner, budgetId = budgetId,
        startsOn = "2026-06-01", endsOn = "2026-07-01", limitMinor = 40_000,
        scopeCategoryId = scope, createdAt = 1_780_000_000_000,
    )

    @Test fun pendingChangeRoundTripsThroughTheRemoteDto() {
        val local = budget(
            pendingScopeType = "CATEGORY", pendingScopeCategoryId = transport,
            pendingScheduleType = "CUSTOM_RANGE", pendingRangeStartOn = "2026-07-01",
            pendingRangeEndOn = "2026-07-31", pendingEffectiveFrom = "2026-07-01",
        )

        val dto = local.toRemoteDto()
        assertEquals("CATEGORY", dto.pendingScopeType)
        assertEquals(transport, dto.pendingScopeCategoryId)
        assertEquals("CUSTOM_RANGE", dto.pendingScheduleType)
        assertEquals("2026-07-01", dto.pendingRangeStartOn)
        assertEquals("2026-07-31", dto.pendingRangeEndOn)
        assertEquals("2026-07-01", dto.pendingEffectiveFrom)

        val restored = dto.toLocalEntity(zone)
        assertEquals(local, restored.copy(createdOn = local.createdOn))
        val pending = restored.toDomain().pendingChange!!
        assertEquals(BudgetScope.Category(transport), pending.scope)
        assertEquals(BudgetSchedule.CustomRange(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31")), pending.schedule)
        assertEquals(LocalDate.parse("2026-07-01"), pending.effectiveFrom)
    }

    @Test fun budgetWithoutPendingChangeSendsAndReadsNoPendingFields() {
        val dto = budget().toRemoteDto()
        assertNull(dto.pendingScopeType)
        assertNull(dto.pendingEffectiveFrom)
        val restored = dto.toLocalEntity(zone)
        assertNull(restored.pendingEffectiveFrom)
        assertNull(restored.toDomain().pendingChange)
    }

    @Test fun periodScopeSnapshotIsSentToTheServer() {
        assertEquals(food, period(food).toRemoteDto().scopeCategoryId)
        assertNull(period(null).toRemoteDto().scopeCategoryId)
    }

    @Test fun hydrationUsesTheRemoteSnapshotInsteadOfTheCurrentBudgetScope() {
        // The budget is category-scoped today, but June was governed by TOTAL (null snapshot).
        val historicalTotal = period(null).toRemoteDto().toLocalEntity()
        assertNull(historicalTotal.scopeCategoryId)
        assertEquals(BudgetScope.Total, historicalTotal.toDomain().scope)

        val historicalTransport = period(transport).toRemoteDto().toLocalEntity()
        assertEquals(transport, historicalTransport.scopeCategoryId)
        assertEquals(BudgetScope.Category(transport), historicalTransport.toDomain().scope)
    }

    @Test fun hydratedPeriodKeepsDeterministicLocalIdAndIntervals() {
        val local = period(food)
        val restored = local.toRemoteDto().toLocalEntity()
        assertEquals(local.id, restored.id)
        assertEquals(local.copy(createdAt = restored.createdAt), restored)
        assertEquals(BudgetPeriodType.MONTHLY.name, budget().periodType)
    }

    @Test fun nullablePeriodScopeAndPendingFieldsAreWrittenAsExplicitNulls() {
        // The write path must send nulls so that returning to TOTAL or clearing a pending change reaches the server.
        val wireJson = Json { encodeDefaults = true; explicitNulls = true }
        val periodJson = wireJson.parseToJsonElement(wireJson.encodeToString(period(null).toRemoteDto())).jsonObject
        assertEquals(JsonNull, periodJson["scope_category_id"])
        val budgetJson = wireJson.parseToJsonElement(wireJson.encodeToString(budget().toRemoteDto())).jsonObject
        assertTrue(budgetJson.keys.containsAll(listOf("pending_scope_type", "pending_effective_from", "range_start_on")))
        assertEquals(JsonNull, budgetJson["pending_effective_from"])
    }

    @Test fun legacyRemoteRowsWithoutNewColumnsStillDecode() {
        val lenient = Json { ignoreUnknownKeys = true }
        val period = lenient.decodeFromString<BudgetPeriodRemoteDto>(
            """{"id":"$budgetId","user_id":"$owner","budget_id":"$budgetId","starts_on":"2026-06-01",
               "ends_on":"2026-07-01","limit_minor":10,"created_at":"2026-06-01T00:00:00Z"}""",
        )
        assertNull(period.scopeCategoryId)
    }
}
