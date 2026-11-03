package com.kipu.app.feature.portability

import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

enum class DeletionRemote { PENDING, ERROR, COMPLETED }
enum class DeletionLocal { RETAINED, PENDING, SESSION_PENDING, CLEARED }
enum class DeletionStep { CLOSED, SUMMARY, REAUTH, CONFIRM, STATUS }
@Serializable
data class DeletionRequest(
    val owner: String,
    val requestId: String,
    val secret: String,
    val remote: DeletionRemote = DeletionRemote.PENDING,
    val local: DeletionLocal = DeletionLocal.RETAINED,
)
data class DeletionUiState(
    val step: DeletionStep = DeletionStep.CLOSED,
    val busy: Boolean = false,
    val pending: Int = 0,
    val request: DeletionRequest? = null,
    val message: String? = null,
)
interface DeletionRequestStore {
    suspend fun read(owner: String): DeletionRequest?
    suspend fun write(request: DeletionRequest)
}
interface AccountDeletionGateway {
    /** Verifies the current owner's password; never installs a different session. */
    suspend fun reauthenticate(owner: String, password: String)
    suspend fun delete(request: DeletionRequest): DeletionRemote
    fun clearReauthentication()
}
interface AccountDeletionCleanup {
    suspend fun clean(owner: String)
    suspend fun finish(owner: String) {}
}

/** Separate approval from execution. Cancellation before submission writes nothing. */
class AccountDeletion(
    private val store: DeletionRequestStore,
    private val gateway: AccountDeletionGateway,
    private val cleanup: AccountDeletionCleanup,
    private val currentOwner: () -> String?,
    private val pending: suspend (String) -> Int,
) {
    private val mutableState = MutableStateFlow(DeletionUiState())
    val state = mutableState.asStateFlow()
    private val mutex = Mutex()
    private var reauthenticated = false
    private var owner: String? = null

    suspend fun load() = mutex.withLock {
        owner = currentOwner()
        val request = owner?.let { store.read(it) }
        mutableState.value = DeletionUiState(request = request, step = if (request == null) DeletionStep.CLOSED else DeletionStep.STATUS)
    }
    suspend fun open() = mutex.withLock {
        val id = checkedOwner()
        mutableState.value = mutableState.value.copy(step = if (mutableState.value.request == null) DeletionStep.SUMMARY else DeletionStep.STATUS, pending = pending(id), message = null)
    }
    fun cancel() {
        if (mutableState.value.busy) return
        reauthenticated = false
        gateway.clearReauthentication()
        mutableState.value = mutableState.value.copy(step = DeletionStep.CLOSED, message = null)
    }
    fun continueToReauthentication() {
        if (!mutableState.value.busy) mutableState.value = mutableState.value.copy(step = DeletionStep.REAUTH, message = null)
    }
    suspend fun reauthenticate(password: String) = mutex.withLock {
        reauthenticated = false
        operation {
            gateway.reauthenticate(checkedOwner(), password)
            reauthenticated = true
            mutableState.value = mutableState.value.copy(step = DeletionStep.CONFIRM)
        }
    }
    suspend fun confirm() = mutex.withLock {
        if (!reauthenticated || mutableState.value.step != DeletionStep.CONFIRM) {
            mutableState.value = mutableState.value.copy(message = "Vuelve a autenticarte antes de confirmar.")
            return@withLock
        }
        operation {
            val id = checkedOwner()
            val request = mutableState.value.request ?: DeletionRequest(id, UUID.randomUUID().toString(),
                ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) })
            // Persist intent before the first remote call, including process-death/timeouts.
            store.write(request)
            mutableState.value = mutableState.value.copy(request = request, step = DeletionStep.STATUS)
            execute(request)
        }
    }
    suspend fun retry() = mutex.withLock {
        operation { mutableState.value.request?.let { checkedOwner(); execute(it) } }
    }
    private suspend fun execute(request: DeletionRequest) {
        var latest = request
        try {
            if (latest.remote != DeletionRemote.COMPLETED) {
                latest = latest.copy(remote = gateway.delete(latest))
                checkedOwner()
                store.write(latest)
                mutableState.value = mutableState.value.copy(request = latest)
            }
            if (latest.remote == DeletionRemote.COMPLETED) {
                withContext(NonCancellable) {
                    if (latest.local != DeletionLocal.CLEARED && latest.local != DeletionLocal.SESSION_PENDING) {
                        latest = latest.copy(local = DeletionLocal.PENDING)
                        store.write(latest)
                        mutableState.value = mutableState.value.copy(request = latest)
                        cleanup.clean(latest.owner)
                        latest = latest.copy(local = DeletionLocal.SESSION_PENDING, secret = "")
                        // Commit cleanup status before revoking the local owner.
                        store.write(latest)
                    }
                    cleanup.finish(latest.owner)
                    latest = latest.copy(local = DeletionLocal.CLEARED)
                    store.write(latest)
                    mutableState.value = mutableState.value.copy(request = latest, message = "Cuenta eliminada. Se cerró la sesión.")
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            // A lost response is ambiguous. Never infer remote completion or erase locals.
            latest = latest.copy(remote = if (latest.remote == DeletionRemote.COMPLETED) latest.remote else DeletionRemote.ERROR)
            store.write(latest)
            mutableState.value = mutableState.value.copy(request = latest, message = "No se pudo completar. Reintenta; el estado remoto y la limpieza local se muestran por separado.")
        }
        reauthenticated = false
        gateway.clearReauthentication()
    }
    private fun checkedOwner(): String = requireNotNull(owner).also {
        val active = currentOwner()
        check(active == it || (active == null && mutableState.value.request?.remote == DeletionRemote.COMPLETED))
    }
    private suspend fun operation(block: suspend () -> Unit) {
        mutableState.value = mutableState.value.copy(busy = true, message = null)
        try { block() } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(message = "No se pudo verificar o guardar la solicitud. Revisa tu contraseña y conexión.")
        } finally { mutableState.value = mutableState.value.copy(busy = false) }
    }
}
