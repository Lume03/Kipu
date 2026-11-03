package com.kipu.app.feature.portability

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.work.WorkManager
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.core.security.KeystoreEncryptedSessionStorage
import com.kipu.app.core.security.StoredAuthSession
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.auth.data.remote.ApiResponse
import com.kipu.app.feature.auth.data.remote.AuthApi
import com.kipu.app.feature.auth.data.remote.LoginRequestDto
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** No Room schema change. Private no-backup DataStore holds the retry capability,
 * never the password or reauthentication JWT; completed entries erase the capability. */
@Singleton
class DataStoreDeletionRequests(private val data: DataStore<Preferences>) : DeletionRequestStore {
    @Inject constructor(@ApplicationContext context: Context) : this(
        PreferenceDataStoreFactory.create(produceFile = { File(context.noBackupFilesDir, "account-deletion.preferences_pb") })
    )
    override suspend fun read(owner: String): DeletionRequest? = data.data.first()[stringPreferencesKey(owner)]?.let { Json.decodeFromString<DeletionRequest>(it) }
    suspend fun unfinished(): List<DeletionRequest> = data.data.first().asMap().values.mapNotNull {
        (it as? String)?.let { value -> Json.decodeFromString<DeletionRequest>(value) }
    }.filter { it.local != DeletionLocal.CLEARED }
    override suspend fun write(request: DeletionRequest) {
        data.edit { it[stringPreferencesKey(request.owner)] = Json.encodeToString(DeletionRequest.serializer(), request) }
    }
}

@Singleton
class RemoteAccountDeletion @Inject constructor(
    private val api: AuthApi,
    private val storage: KeystoreEncryptedSessionStorage,
    private val client: SupabaseClient,
    private val sessions: AuthenticatedSessionProvider,
    private val http: HttpClient,
) : AccountDeletionGateway {
    private var proof: Pair<String, String>? = null
    private var recoveryEmail: String? = null
    fun recoveryEmail(email: String) { recoveryEmail = email.trim().takeIf { it.isNotBlank() } }
    override fun clearReauthentication() { proof = null; recoveryEmail = null }
    override suspend fun reauthenticate(owner: String, password: String) {
        proof = null
        val saved = storage.load()?.let(StoredAuthSession::parse)
        val email = client.auth.currentUserOrNull()?.takeIf { it.id == owner }?.email
            ?: saved?.takeIf { it.userId == owner }?.verifiedUser?.email
            ?: recoveryEmail
        require(!email.isNullOrBlank()) { "Inicia sesión de nuevo para verificar tu correo." }
        val response = api.login(LoginRequestDto(email, password))
        check(response is ApiResponse.Success && response.data.userId == owner) { "No se pudo reautenticar." }
        proof = owner to response.data.accessToken
    }
    override suspend fun delete(request: DeletionRequest): DeletionRemote {
        val token = proof?.takeIf { it.first == request.owner }?.second
            ?: sessions.currentSession()?.takeIf { it.userId.toString() == request.owner }?.accessToken
            // An expired token plus the private receipt capability can reconcile an
            // already removed Auth user. Edge never trusts this token for new work.
            ?: storage.load()?.let(StoredAuthSession::parse)?.takeIf { it.userId == request.owner }?.accessToken
            ?: "receipt-reconciliation"
        val response = http.post("functions/v1/delete-account") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            timeout { requestTimeoutMillis = 45000; connectTimeoutMillis = 10000; socketTimeoutMillis = 45000 }
            setBody(buildJsonObject {
                put("request_id", request.requestId); put("secret", request.secret); put("confirm", true)
            })
        }
        val status = response.body<JsonObject>()["status"]?.jsonPrimitive?.content
        return when {
            response.status.value == 200 && status == "completed" -> DeletionRemote.COMPLETED
            response.status.value == 202 && status == "pending" -> DeletionRemote.PENDING
            else -> DeletionRemote.ERROR
        }
    }
}

/** Deletes all owner-bearing Room tables in FK order, including owner_id capture
 * evidence. Installation permissions and global catalogs are deliberately retained. */
class DeleteLocalAccountRows @Inject constructor(private val database: KipuDatabase) {
    suspend operator fun invoke(owner: String) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val db = database.openHelper.writableDatabase
            val tables = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }
            val owners = tables.mapNotNull { table ->
                val column = db.query("PRAGMA table_info(`$table`)").use { c ->
                    buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
                }.firstOrNull { it == "user_id" || it == "owner_id" }
                column?.let { table to it }
            }.toMap()
            val parents = owners.keys.associateWith { table ->
                db.query("PRAGMA foreign_key_list(`$table`)").use { c -> buildSet {
                    while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("table")))
                } } - table
            }
            val remaining = owners.keys.toMutableSet()
            while (remaining.isNotEmpty()) {
                val leaves = remaining.filter { parent -> remaining.none { child -> parent in parents.getValue(child) } }
                check(leaves.isNotEmpty()) { "Unresolved account foreign key cycle" }
                leaves.forEach { table ->
                    db.execSQL("DELETE FROM `$table` WHERE `${owners.getValue(table)}` = ?", arrayOf(owner))
                    remaining.remove(table)
                }
            }
            owners.forEach { (table, column) ->
                db.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM `$table` WHERE `$column`=?", arrayOf(owner))).use {
                    check(it.moveToFirst() && it.getInt(0) == 0) { "Local account rows remain" }
                }
            }
        }
    }
}

@Singleton
class LocalAccountDeletionCleanup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rows: DeleteLocalAccountRows,
    private val session: SessionCoordinator,
    private val storage: KeystoreEncryptedSessionStorage,
    private val client: SupabaseClient,
) : AccountDeletionCleanup {
    override suspend fun clean(owner: String) = withContext(NonCancellable + Dispatchers.IO) {
        check(session.currentOwner?.verifiedUserId.let { it == null || it == owner })
        val manager = WorkManager.getInstance(context)
        val names = listOf("sync_profile_preferences_$owner", "sync_notifications_$owner", "goal-sync_$owner",
            "movement-sync_$owner", "instrument-sync_$owner", "debt-sync_$owner", "sync_categories_work_$owner",
            "sync_plan_quota_work_$owner", "budget-sync_$owner", "recurrence-sync:$owner", "recurrence-sync-rescue:$owner",
            "plan-selection-sync-$owner", "plan-entitlement-refresh-$owner", "budget-maintenance-now_$owner")
        names.forEach { manager.cancelUniqueWork(it).result.get() }
        listOf("debt-reminder-user:$owner", "recurrence-reminder-owner:$owner").forEach { manager.cancelAllWorkByTag(it).result.get() }
        // Revoke ownership before deleting rows: in-flight responses must fail
        // their owner guards. The login recovery screen can finish after a crash.
        if (session.currentOwner?.verifiedUserId == owner) session.clearActiveOwner(explicit = true)
        rows(owner)
        for (name in listOf("plan_onboarding", "goal_display", "budget_appearance_v1", "movement_receipts_v1", "instrument_contract_recovery", "movement_contract_recovery")) {
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            prefs.all.keys.filter { it.split(':').contains(owner) }.forEach { key ->
                if (name == "movement_receipts_v1") {
                    prefs.getString(key, null)?.let { value ->
                        runCatching { context.contentResolver.releasePersistableUriPermission(android.net.Uri.parse(value), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    }
                }
                editor.remove(key)
            }
            check(editor.commit())
        }
        // Current OCR/notification evidence is stored in Room. Owner directories are
        // reserved for temporary files; never delete another owner's or original SAF file.
        listOf(context.cacheDir, context.filesDir).forEach { root ->
            listOf("ocr", "evidence", "receipts", "portability").forEach { name ->
                val dir = File(File(root, name), owner)
                check(!dir.exists() || dir.deleteRecursively()) { "Temporary evidence cleanup failed" }
            }
        }
    }
    override suspend fun finish(owner: String) = withContext(NonCancellable + Dispatchers.IO) {
        check(session.currentOwner?.verifiedUserId.let { it == null || it == owner })
        check(client.auth.currentUserOrNull()?.id.let { it == null || it == owner })
        client.auth.clearSession()
        if (storage.load()?.let(StoredAuthSession::parse)?.userId == owner) {
            storage.delete()
            check(storage.load() == null)
        }
        val temporarySession = File(context.filesDir, "kipu_session.enc.tmp")
        check(!temporarySession.exists() || temporarySession.delete()) { "Temporary session cleanup failed" }
        session.updateRemoteSession(RemoteSession.Absent)
        if (session.currentOwner?.verifiedUserId == owner) session.clearActiveOwner(explicit = true)
    }
}
