package com.kipu.app.feature.portability

import androidx.room.withTransaction
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.categories.data.local.MerchantAliasRuleEntity
import com.kipu.app.feature.categories.data.local.MerchantCategoryPreferenceEntity
import com.kipu.app.feature.movements.data.local.TransactionRevisionEntity
import com.kipu.app.feature.movements.data.local.MovementOfficialRevisionEntity
import com.kipu.app.feature.movements.data.local.TransactionLinkEntity
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import javax.inject.Inject
import kotlinx.serialization.json.*
import java.time.OffsetDateTime

/** Owner-scoped, paginated reads for history and personal rules not carried by the feed. */
class SupplementaryHydration @Inject constructor(
    private val http: HttpClient,
    private val auth: AuthenticatedSessionProvider,
    private val session: SessionCoordinator,
    private val db: KipuDatabase,
) {
    private fun requireOwner(owner: String) = check(session.currentOwner?.verifiedUserId == owner)
    private suspend fun rows(owner: String, table: String, columns: String): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        var offset = 0
        while (true) {
            requireOwner(owner)
            val authenticated = requireNotNull(auth.currentSession())
            require(authenticated.userId.toString() == owner)
            val token = authenticated.accessToken
            val response = http.get("rest/v1/$table") {
                header("Authorization", "Bearer $token")
                url {
                    parameters.append("user_id", "eq.$owner")
                    parameters.append("select", columns)
                    parameters.append("order", if (table == "movement_official_revisions") "transaction_id,official_revision" else "id")
                    parameters.append("limit", "500")
                    parameters.append("offset", offset.toString())
                }
            }
            check(response.status.value in 200..299)
            val page = response.body<List<JsonObject>>()
            requireOwner(owner)
            require(page.all { it.text("user_id") == owner })
            result.addAll(page)
            if (page.size < 500) return result
            offset += page.size
        }
    }
    suspend fun merchantRules(owner: String): Boolean {
        val aliases = rows(owner, "merchant_alias_rules", "id,user_id,normalized_pattern,merchant_id,revision,deleted_at,updated_at")
        val preferences = rows(owner, "merchant_category_preferences", "id,user_id,merchant_id,category_id,revision,deleted_at,updated_at")
        db.withTransaction {
            requireOwner(owner)
            for (row in aliases) {
                val old = db.merchantRulesDao().getAliasRule(owner, row.text("id"))
                if (old != null && (old.syncState != "SYNCED" || old.remoteRevision > row.number("revision"))) continue
                db.merchantRulesDao().upsertAliasRule(MerchantAliasRuleEntity(
                    id = row.text("id"), userId = owner, normalizedPattern = row.text("normalized_pattern"),
                    merchantId = row.text("merchant_id"), remoteRevision = row.number("revision"),
                    deletedAt = row.timestamp("deleted_at"), updatedAt = requireNotNull(row.timestamp("updated_at")), syncState = "SYNCED",
                ))
            }
            for (row in preferences) {
                val old = db.merchantRulesDao().getPreference(owner, row.text("merchant_id"))
                if (old != null && (old.syncState != "SYNCED" || old.remoteRevision > row.number("revision"))) continue
                db.merchantRulesDao().upsertPreference(MerchantCategoryPreferenceEntity(
                    id = row.text("id"), userId = owner, merchantId = row.text("merchant_id"), categoryId = row.text("category_id"),
                    remoteRevision = row.number("revision"), deletedAt = row.timestamp("deleted_at"),
                    updatedAt = requireNotNull(row.timestamp("updated_at")), syncState = "SYNCED",
                ))
            }
            requireOwner(owner)
        }
        return true
    }
    suspend fun movementHistory(owner: String): Boolean {
        val revisions = rows(owner, "transaction_revisions", "id,user_id,transaction_id,command_id,command_type,base_revision,local_revision,previous_payload,new_payload,change_reason,provenance,created_at")
        val official = rows(owner, "movement_official_revisions", "user_id,transaction_id,official_revision,revision_id,official_revision_id,assigned_at")
        val links = rows(owner, "transaction_links", "id,user_id,source_transaction_id,target_transaction_id,link_type,created_at")
        db.withTransaction {
            requireOwner(owner)
            for (row in revisions) {
                if (db.movementDao().getRevision(owner, row.text("id")) != null) continue
                db.movementDao().insertRevision(TransactionRevisionEntity(
                    userId = owner, revisionId = row.text("id"), transactionId = row.text("transaction_id"), commandId = row.optional("command_id"),
                    commandType = row.text("command_type"), baseRevision = row.number("base_revision"), localRevision = row.number("local_revision"),
                    previousPayload = row["previous_payload"]?.takeUnless { it == JsonNull }?.toString(), newPayload = requireNotNull(row["new_payload"]).toString(),
                    changeReason = row.optional("change_reason"), provenance = row.text("provenance"), createdAt = requireNotNull(row.timestamp("created_at")),
                ))
            }
            for (row in official) {
                val tx = row.text("transaction_id"); val revision = row.number("official_revision")
                if (db.movementDao().getOfficialRevision(owner, tx, revision) != null) continue
                db.openHelper.writableDatabase.execSQL(
                    "INSERT OR IGNORE INTO movement_official_revisions(user_id,transaction_id,official_revision,revision_id,official_revision_id,assigned_at) VALUES(?,?,?,?,?,?)",
                    arrayOf<Any>(owner, tx, revision, row.text("revision_id"), row.text("official_revision_id"), requireNotNull(row.timestamp("assigned_at"))),
                )
                db.openHelper.writableDatabase.execSQL(
                    "UPDATE transactions SET current_revision_id=?, acknowledged_revision=? WHERE user_id=? AND id=? AND revision=? AND sync_status='SYNCED'",
                    arrayOf<Any>(row.text("revision_id"), revision, owner, tx, revision),
                )
            }
            for (row in links) db.transactionLinkDao().insertHydrated(TransactionLinkEntity(
                userId = owner, sourceTransactionId = row.text("source_transaction_id"), targetTransactionId = row.text("target_transaction_id"),
                linkType = row.text("link_type"), createdAt = requireNotNull(row.timestamp("created_at")),
            ))
            requireOwner(owner)
        }
        return true
    }

    suspend fun financialFacts(owner: String): Boolean {
        val facts = rows(owner, "financial_movements", "id,user_id,operation_id,operation_sequence,kind,amount_minor_units,currency,account_id,card_id,opening_account_id,effective_at,status,reverses_movement_id,adjusts_movement_id,category_id,merchant_id,merchant_provisional_text,created_at")
        db.withTransaction {
            requireOwner(owner)
            for (row in facts) {
                val dao = db.financialMovementDao()
                if (dao.getById(owner, row.text("id")) != null) continue
                val fact = com.kipu.app.feature.accounts.data.local.FinancialMovementEntity(
                    id = row.text("id"), userId = owner, operationId = row.text("operation_id"), operationSequence = row.number("operation_sequence").toInt(),
                    kind = row.text("kind"), amountMinorUnits = row.number("amount_minor_units"), currency = row.text("currency"),
                    accountId = row.optional("account_id"), cardId = row.optional("card_id"), openingAccountId = row.optional("opening_account_id"),
                    effectiveAt = requireNotNull(row.timestamp("effective_at")) * 1000L, status = row.text("status"),
                    reversesMovementId = row.optional("reverses_movement_id"), adjustsMovementId = row.optional("adjusts_movement_id"),
                    categoryId = row.optional("category_id"), merchantId = row.optional("merchant_id"), merchantProvisionalText = row.optional("merchant_provisional_text"),
                    createdAt = requireNotNull(row.timestamp("created_at")) * 1000L,
                )
                if (fact.kind in setOf("OPENING", "ADJUSTMENT", "REVERSAL") && fact.accountId != null) {
                    // The feed's account baseline is replaced by its auditable opening facts exactly once.
                    if (fact.kind == "OPENING") db.openHelper.writableDatabase.execSQL(
                        "DELETE FROM ledger_entries WHERE user_id=? AND id=?",
                        arrayOf(owner, "remote:opening:${fact.accountId}"),
                    )
                    dao.insert(fact)
                } else dao.insertRaw(fact) // Card postings are already represented by transaction ledger entries.
            }
            requireOwner(owner)
        }
        return true
    }
}

private fun JsonObject.text(key: String) = requireNotNull(optional(key))
private fun JsonObject.optional(key: String) = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.number(key: String) = requireNotNull(this[key]).jsonPrimitive.long
private fun JsonObject.timestamp(key: String) = optional(key)?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() }
