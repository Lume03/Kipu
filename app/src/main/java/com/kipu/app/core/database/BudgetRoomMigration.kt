package com.kipu.app.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** EP-PRE migration kept separate so parallel 25→26 migrations can be renumbered at integration. */
val MIGRATION_BUDGETS_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `budgets` (
            `id` TEXT NOT NULL,
            `user_id` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            `category_id` TEXT,
            `limit_minor` INTEGER NOT NULL,
            `currency_code` TEXT NOT NULL,
            `period_type` TEXT NOT NULL,
            `range_start_on` TEXT,
            `range_end_on` TEXT,
            `alert_thresholds_bps` TEXT NOT NULL,
            `status` TEXT NOT NULL,
            `revision` INTEGER NOT NULL,
            `created_on` TEXT NOT NULL,
            `created_at` INTEGER NOT NULL,
            `updated_at` INTEGER NOT NULL,
            `pending_scope_type` TEXT,
            `pending_scope_category_id` TEXT,
            `pending_schedule_type` TEXT,
            `pending_range_start_on` TEXT,
            `pending_range_end_on` TEXT,
            `pending_effective_from` TEXT,
            PRIMARY KEY(`user_id`, `id`)
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `budget_owner_status_idx` ON `budgets` (`user_id`, `status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `budget_owner_created_idx` ON `budgets` (`user_id`, `created_on`)")

        db.execSQL("""CREATE TABLE IF NOT EXISTS `budget_periods` (
            `id` TEXT NOT NULL,
            `user_id` TEXT NOT NULL,
            `budget_id` TEXT NOT NULL,
            `starts_on` TEXT NOT NULL,
            `ends_on` TEXT NOT NULL,
            `limit_minor` INTEGER NOT NULL,
            `scope_category_id` TEXT,
            `created_at` INTEGER NOT NULL,
            PRIMARY KEY(`user_id`, `id`)
        )""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `budget_period_owner_interval_uq` ON `budget_periods` (`user_id`, `budget_id`, `starts_on`, `ends_on`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `budget_period_owner_budget_start_idx` ON `budget_periods` (`user_id`, `budget_id`, `starts_on`)")

        db.execSQL("""CREATE TABLE IF NOT EXISTS `budget_alert_ledger` (
            `user_id` TEXT NOT NULL,
            `budget_id` TEXT NOT NULL,
            `period_id` TEXT NOT NULL,
            `threshold_bps` INTEGER NOT NULL,
            `dedupe_key` TEXT NOT NULL,
            `emitted_at` INTEGER NOT NULL,
            PRIMARY KEY(`user_id`, `budget_id`, `period_id`, `threshold_bps`)
        )""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `budget_alert_owner_dedupe_uq` ON `budget_alert_ledger` (`user_id`, `dedupe_key`)")

        db.execSQL("""CREATE TABLE IF NOT EXISTS `budget_tombstones` (
            `user_id` TEXT NOT NULL,
            `budget_id` TEXT NOT NULL,
            `last_revision` INTEGER NOT NULL,
            `deleted_at` INTEGER NOT NULL,
            PRIMARY KEY(`user_id`, `budget_id`)
        )""")

        db.execSQL("""CREATE TABLE IF NOT EXISTS `budget_sync_outbox` (
            `sequence` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `operation_id` TEXT NOT NULL,
            `user_id` TEXT NOT NULL,
            `command_type` TEXT NOT NULL,
            `aggregate_id` TEXT NOT NULL,
            `expected_revision` INTEGER,
            `payload_json` TEXT NOT NULL,
            `state` TEXT NOT NULL,
            `attempt_count` INTEGER NOT NULL,
            `error_code` TEXT,
            `created_at` INTEGER NOT NULL,
            `updated_at` INTEGER NOT NULL
        )""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `budget_outbox_owner_operation_uq` ON `budget_sync_outbox` (`user_id`, `operation_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `budget_outbox_owner_state_sequence_idx` ON `budget_sync_outbox` (`user_id`, `state`, `sequence`)")
    }
}
