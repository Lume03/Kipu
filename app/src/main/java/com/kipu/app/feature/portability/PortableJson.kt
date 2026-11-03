package com.kipu.app.feature.portability

import kotlinx.serialization.json.*

/** Version 1 uses Room column names, epoch timestamps and integer minor units with currency. */
object PortableJson {
    const val SCHEMA_VERSION = 1
    val columns = linkedMapOf(
        "user_profiles" to "user_id,display_name,currency_code,time_zone,month_start,hide_balances,theme_mode,remote_revision,remote_updated_at",
        "accounts" to "id,user_id,alias,type,currency,preset_id,color,icon,initial_balance_minor_units,opened_at,is_archived,remote_revision,created_at,updated_at,linked_account_id",
        "cards" to "id,user_id,account_id,alias,type,currency,network,issuer,last_four_digits,credit_limit_minor_units,billing_day,due_day,preset_id,style_preset_id,color,icon,is_archived,remote_revision,created_at,updated_at,personal_tea_bps,closing_day_inclusive,cycle_date_basis",
        "financial_movements" to "id,operation_sequence,user_id,kind,amount_minor_units,currency,account_id,card_id,opening_account_id,effective_at,status,reverses_movement_id,adjusts_movement_id,category_id,merchant_id,merchant_provisional_text,created_at",
        "categories" to "id,user_id,parent_id,origin,is_active,remote_revision,created_at,updated_at,category_type",
        "category_presentations" to "category_id,user_id,name,icon,color,remote_revision,updated_at",
        "merchant_catalog_cache" to "id,name,normalized_name,is_active,version,default_category_id,priority,logo_key,brand_color,logo_path,logo_dark_path,logo_source,user_id",
        "merchant_alias_rules" to "id,user_id,normalized_pattern,merchant_id,remote_revision,deleted_at,updated_at",
        "merchant_category_preferences" to "user_id,merchant_id,id,category_id,remote_revision,deleted_at,updated_at",
        "transactions" to "id,user_id,type,amount_minor,currency_code,source_account_id,destination_account_id,category_id,merchant_id,merchant_provisional_text,legacy_kind,occurred_at,note,status,created_at,updated_at,card_id,operation_kind,installment_count,revision,acknowledged_revision,current_revision_id",
        "transaction_links" to "user_id,source_transaction_id,target_transaction_id,link_type,created_at",
        "debts" to "id,user_id,obligation_type,counterparty_name,total_minor,currency_code,opened_on,opening_mode,due_date,reminder_lead_days,notes,status,revision,deleted_at,created_at,updated_at",
        "debt_installments" to "id,user_id,debt_id,installment_number,due_date,amount_minor,status,revision,deleted_at,created_at,updated_at",
        "debt_events" to "id,user_id,debt_id,transaction_id,interest_transaction_id,installment_id,event_type,amount_minor,principal_delta_minor,occurred_at,created_at",
        "goals" to "id,user_id,name,target_minor,currency_code,target_date,linked_account_id,icon_key,color_argb,status,revision,created_at,updated_at",
        "goal_events" to "id,user_id,goal_id,transaction_id,event_type,delta_minor,currency_code,backing_account_id,occurred_at,created_at",
        "transaction_revisions" to "user_id,revision_id,transaction_id,command_type,base_revision,local_revision,previous_payload,new_payload,change_reason,created_at",
        "movement_official_revisions" to "user_id,transaction_id,official_revision,revision_id,official_revision_id,assigned_at",
        "ledger_entries" to "id,user_id,transaction_id,account_id,role,signed_amount_minor,currency_code,created_at",
        "credit_installments" to "id,user_id,transaction_id,installment_number,due_date,principal_minor,interest_minor,status,revision,created_at,updated_at,deleted_at,purchase_type,financing,installments_count,tea_bps,rate_source,rounding_policy,billing_closing_date",
        "credit_payment_allocations" to "id,user_id,payment_transaction_id,installment_id,allocated_minor,created_at",
        "recurrences" to "id,user_id,template_transaction,amount_mode,frequency,interval_count,custom_interval_unit,start_date,end_date,next_due_date,reminder_lead_days,status,is_active,revision,deleted_at,created_at,updated_at",
        "budgets" to "id,user_id,name,category_id,limit_minor,currency_code,period_type,range_start_on,range_end_on,alert_thresholds_bps,status,revision,created_on,created_at,updated_at,pending_scope_type,pending_scope_category_id,pending_schedule_type,pending_range_start_on,pending_range_end_on,pending_effective_from",
        "budget_periods" to "id,user_id,budget_id,starts_on,ends_on,limit_minor,scope_category_id,created_at",
    )
    fun encode(owner: String, rows: Map<String, List<JsonObject>>): String {
        require(owner.isNotBlank())
        return buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            put("ownerId", owner)
            put("exportedAt", java.time.Instant.now().toString())
            put("moneyEncoding", "integer_minor_units")
            putJsonObject("entities") {
                columns.forEach { (table, names) ->
                    val allowed = names.split(',').toSet()
                    put(table, JsonArray(rows[table].orEmpty().filter {
                        it["user_id"]?.jsonPrimitive?.content == owner
                    }.map { row -> JsonObject(row.filterKeys { it in allowed }.mapValues { (key, value) ->
                        if (key in setOf("previous_payload", "new_payload", "template_transaction") && value is JsonPrimitive && value.isString) {
                            val parsed = Json.parseToJsonElement(value.content)
                            sanitize(owner, parsed)
                        } else value
                    }) }))
                }
            }
        }.toString()
    }

    private fun sanitize(owner: String, value: JsonElement): JsonElement = when (value) {
        is JsonObject -> {
            if (value["user_id"]?.jsonPrimitive?.content?.let { it != owner } == true) JsonNull else {
                val financialKeys = columns.values.flatMap { it.split(',') }.toSet() + setOf("ledger_entries", "role", "signed_amount_minor", "entry_role", "ledger_account_id", "concept")
                JsonObject(value.filterKeys { it in financialKeys && it !in setOf("previous_payload", "new_payload", "template_transaction") }.mapValues { sanitize(owner, it.value) })
            }
        }
        is JsonArray -> JsonArray(value.map { sanitize(owner, it) })
        else -> value
    }
}
