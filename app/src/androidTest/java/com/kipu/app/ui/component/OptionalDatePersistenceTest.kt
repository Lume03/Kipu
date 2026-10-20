package com.kipu.app.ui.component

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.debts.data.local.DebtEntity
import com.kipu.app.feature.debts.data.local.DebtLocalDataSource
import com.kipu.app.feature.debts.domain.DebtCommandHasher
import com.kipu.app.feature.debts.domain.model.DebtCommandIdentity
import com.kipu.app.feature.debts.domain.model.DebtDescriptionPatch
import com.kipu.app.feature.debts.domain.model.DebtDetailsEditResult
import com.kipu.app.feature.goals.data.OfflineFirstGoalRepository
import com.kipu.app.feature.goals.data.local.GoalEntity
import com.kipu.app.feature.goals.data.local.GoalLocalDataSource
import com.kipu.app.feature.goals.data.sync.GoalsSyncScheduler
import com.kipu.app.feature.goals.domain.model.GoalCommandResult
import com.kipu.app.feature.goals.domain.usecase.UpdateGoalCommand
import io.mockk.mockk
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Existing persistence paths, isolated Room database and no network/scheduler side effects. */
class OptionalDatePersistenceTest {
    private lateinit var db: KipuDatabase
    private val owner = "issue-71-fixture"

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, KipuDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    @Test fun payableDateAndClearPersistWithoutChangingOtherDatesOrMoney() = checkDebt("PAYABLE")
    @Test fun receivableDateAndClearPersistWithoutChangingOtherDatesOrMoney() = checkDebt("RECEIVABLE")

    private fun checkDebt(type: String) = runBlocking {
        val source = DebtLocalDataSource(db, db.debtDao(), db.debtOutboxDao(), db.movementDao(),
            db.accountDao(), db.financialMovementDao(), db.categoryDao())
        val original = DebtEntity(UUID.randomUUID().toString(), owner, type, "Persona de ejemplo",
            5_000L, "PEN", "2026-10-01", dueDate = "2026-11-10", notes = "Datos ficticios")
        val control = original.copy(id = UUID.randomUUID().toString(), dueDate = "2027-06-30")
        db.debtDao().insertDebt(original)
        db.debtDao().insertDebt(control)
        for ((index, date) in listOf("2028-02-29", null).withIndex()) {
            val iso = date?.let { optionalDatePickerIso(optionalDatePickerInitialMillis(it)!!) }
            val identity = DebtCommandIdentity(UUID.randomUUID().toString(), DebtCommandHasher.sha256("$index:$date"))
            val result = source.editDebtDetails(owner, original.id,
                DebtDescriptionPatch(original.counterpartyName, iso?.let(LocalDate::parse), original.notes, index + 1L), identity)
            assertTrue("Unexpected edit: $result", result is DebtDetailsEditResult.Applied)
            val reopened = db.debtDao().getDebt(owner, original.id)!!
            assertEquals(date, reopened.dueDate)
            assertEquals(original.totalMinor, reopened.totalMinor)
            assertEquals(original.openedOn, reopened.openedOn)
            assertEquals(control, db.debtDao().getDebt(owner, control.id))
            val payload = Json.parseToJsonElement(db.debtOutboxDao().get(owner, identity.operationId)!!.payload).jsonObject
            assertEquals(date ?: "", payload["due_date"]!!.jsonPrimitive.content)
            assertNoFinancialWrites()
        }
    }

    @Test fun goalDateAndClearPersistWithoutChangingOtherGoalsOrReserves() = runBlocking {
        val local = GoalLocalDataSource(db, db.goalDao(), db.goalOutboxDao(), db.movementDao())
        val repository = OfflineFirstGoalRepository(local, mockk<GoalsSyncScheduler>(relaxed = true))
        val original = GoalEntity(UUID.randomUUID().toString(), owner, "Meta de ejemplo", 10_000L, "PEN",
            targetDate = "2026-11-10", revision = 1L, createdAt = 1L, updatedAt = 1L)
        val control = original.copy(id = UUID.randomUUID().toString(), targetDate = "2027-06-30")
        db.goalDao().insertGoal(original)
        db.goalDao().insertGoal(control)
        for ((index, date) in listOf("2028-02-29", null).withIndex()) {
            val iso = date?.let { optionalDatePickerIso(optionalDatePickerInitialMillis(it)!!) }
            val result = repository.updateGoal(owner, original.id, UpdateGoalCommand(
                expectedRevision = index + 1L, name = original.name, target = Money(original.targetMinor, Currency.PEN),
                targetDate = iso?.let(LocalDate::parse), linkedAccountId = null,
            ))
            assertTrue("Unexpected update: $result", result is GoalCommandResult.Applied)
            val reopened = db.goalDao().getGoal(owner, original.id)!!
            assertEquals(date, reopened.targetDate)
            assertEquals(original.targetMinor, reopened.targetMinor)
            assertEquals(control, db.goalDao().getGoal(owner, control.id))
            val command = db.goalOutboxDao().getPendingForUser(owner).single { it.expectedRevision == index + 1L }
            val payload = Json.parseToJsonElement(command.payload).jsonObject.getValue("payload").jsonObject
            if (date == null) assertEquals(JsonNull, payload["target_date"])
            else assertEquals(date, payload["target_date"]!!.jsonPrimitive.content)
            assertEquals(0, count("goal_events"))
            assertNoFinancialWrites()
        }
    }

    private fun assertNoFinancialWrites() {
        assertEquals(0, count("transactions"))
        assertEquals(0, count("ledger_entries"))
    }

    private fun count(table: String) = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
        check(it.moveToFirst()); it.getInt(0)
    }
}
