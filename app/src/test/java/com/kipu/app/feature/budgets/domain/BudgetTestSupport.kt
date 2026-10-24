package com.kipu.app.feature.budgets.domain

import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking

val TEST_ZONE: ZoneId = ZoneId.of("America/Lima")
const val OWNER = "u1"

fun day(s: String): LocalDate = LocalDate.parse(s)

class Env(premium: Boolean = false) {
    val categories = FakeCategoryLookup()
    val repo = FakeBudgetRepository()
    val plan = FakePlanAccess(premium)
    val reader = FakeConsumptionReader(categories)
    val ledger = FakeAlertLedger()
    val notifier = FakeNotifier()
    val validator = BudgetValidator(categories)
    val create = CreateBudget(repo, validator, plan)
    val renew = RenewBudgetPeriods(repo)
    val edit = EditBudget(repo, validator, renew)
    val finish = FinishBudget(repo)
    val delete = DeleteBudget(repo)
    val consumption = GetBudgetConsumption(repo, reader)
    val alerts = EvaluateBudgetAlerts(repo, reader, ledger, notifier)

    fun createBudget(
        id: String = "b1",
        limit: Long = 50_000,
        scope: BudgetScope = BudgetScope.Total,
        schedule: BudgetSchedule = BudgetSchedule.Recurring(BudgetPeriodType.MONTHLY),
        today: String = "2026-09-10",
        thresholds: List<Int> = Budget.DEFAULT_THRESHOLDS_BPS,
    ): Budget = (
        runBlocking {
            create(CreateBudgetCommand(id, OWNER, "Presupuesto $id", scope, limit, "PEN", schedule, thresholds, day(today)))
        } as BudgetOutcome.Success
        ).value

    fun expense(category: String, date: String, amount: Long, currency: String = "PEN") {
        reader.expenses += FakeConsumptionReader.Expense(category, day(date), amount, currency)
    }
}
