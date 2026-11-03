package com.kipu.app.feature.portability

import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.movements.data.sync.MovementHydration
import com.kipu.app.feature.goals.data.sync.GoalHydration
import com.kipu.app.feature.debts.data.sync.DebtHydration
import com.kipu.app.feature.recurrences.data.sync.RecurrenceHydration
import com.kipu.app.feature.budgets.data.sync.BudgetHydration
import com.kipu.app.feature.categories.data.sync.CategoryHydration
import com.kipu.app.feature.settings.domain.ProfilePreferencesRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class RestoreStatus { COMPLETED, PARTIAL, ERROR }
data class RestoreResult(val owner: String, val modules: Map<String, Boolean>) {
    val status: RestoreStatus get() = when {
        modules.isEmpty() || modules.values.none { it } -> RestoreStatus.ERROR
        modules.values.all { it } -> RestoreStatus.COMPLETED
        else -> RestoreStatus.PARTIAL
    }
}

/** A failed module is retried independently; successful modules keep their actual result. */
class RestoreRunner(private val operations: Map<String, suspend (String) -> Boolean>) {
    suspend fun run(owner: String, previous: RestoreResult? = null): RestoreResult {
        require(previous == null || previous.owner == owner)
        val results = linkedMapOf<String, Boolean>()
        for ((name, operation) in operations) {
            results[name] = if (previous?.modules?.get(name) == true) true else try {
                operation(owner)
            } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
        }
        return RestoreResult(owner, results)
    }
}

@Singleton
class RestoreUserData @Inject constructor(
    private val session: SessionCoordinator,
    movements: MovementHydration,
    goals: GoalHydration,
    debts: DebtHydration,
    recurrences: RecurrenceHydration,
    budgets: BudgetHydration,
    categories: CategoryHydration,
    supplements: SupplementaryHydration,
    profile: ProfilePreferencesRepository,
    pending: PendingLocalChanges,
) {
    private val mutex = Mutex()
    private val runner = RestoreRunner(linkedMapOf<String, suspend (String) -> Boolean>(
        "Categorías y comercios" to { owner -> categories.hydrateFully(owner) && supplements.merchantRules(owner) },
        "Cuentas, tarjetas y movimientos" to { owner -> movements.hydrateFully(owner) && supplements.movementHistory(owner) && supplements.financialFacts(owner) },
        "Deudas y cuotas" to { owner -> debts.hydrateFully(owner) },
        "Metas y reservas" to { owner -> goals.hydrateFully(owner) },
        "Presupuestos" to { owner -> budgets.hydrateFully(owner) },
        "Recurrencias" to { owner -> recurrences.hydrateFully(owner) },
        "Perfil y preferencias" to { owner ->
            pending(owner).byModule["profile_preference_outbox"] == 0 && profile.refreshProfile(UUID.fromString(owner)).isSuccess
        },
    ).mapValues { (_, operation) ->
        val guarded: suspend (String) -> Boolean = { owner ->
            check(session.currentOwner?.verifiedUserId == owner)
            operation(owner).also { check(session.currentOwner?.verifiedUserId == owner) }
        }
        guarded
    })

    suspend operator fun invoke(owner: String, previous: RestoreResult? = null): RestoreResult = mutex.withLock {
        runner.run(owner, previous)
    }
}
