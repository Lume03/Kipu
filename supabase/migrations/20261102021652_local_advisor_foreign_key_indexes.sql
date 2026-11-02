-- Cover complete referencing keys for joins, owner checks and parent deletes.
-- Standard indexes are appropriate here: the target is a clean rebuild, not
-- a live-data rollout. Keep these changes atomic with migration application.
BEGIN;

-- All FKs reference idx_transactions_owner_id_uq; its identical later copy
-- has no constraint dependencies. Do not use CASCADE.
DROP INDEX public.transactions_user_id_id_uq;

-- billing_event_receipts_user_id_fkey
CREATE INDEX idx_billing_event_receipts_user_id_fk
    ON internal.billing_event_receipts (user_id);

-- billing_reconciliation_jobs_latest_event_receipt_id_fkey
CREATE INDEX idx_billing_reconciliation_jobs_latest_event_receipt_id_fk
    ON internal.billing_reconciliation_jobs (latest_event_receipt_id);

-- ledger_entries_account_id_fkey
CREATE INDEX idx_ledger_entries_account_id_fk
    ON internal.ledger_entries (account_id);

-- ledger_entries_financial_movement_owner_account_fk
CREATE INDEX idx_ledger_entries_financial_movement_id_fk
    ON internal.ledger_entries (financial_movement_id, user_id, account_id);

-- movement_ledger_aliases_user_id_command_id_effect_ordinal_fkey
CREATE INDEX idx_movement_ledger_aliases_user_id_fk
    ON internal.movement_ledger_aliases (user_id, command_id, effect_ordinal);

-- movement_ledger_effects_user_id_transaction_id_revision_id_fkey
CREATE INDEX idx_movement_ledger_effects_user_id_fk
    ON internal.movement_ledger_effects (user_id, transaction_id, revision_id);

-- accounts_currency_code_fkey
CREATE INDEX idx_accounts_currency_code_fk
    ON public.accounts (currency_code);

-- billing_purchases_product_id_fkey
CREATE INDEX idx_billing_purchases_product_id_fk
    ON public.billing_purchases (product_id);

-- budget_periods_owner_budget_fk
CREATE INDEX idx_budget_periods_budget_id_fk
    ON public.budget_periods (budget_id, user_id);

-- cards_credit_product_id_fkey
CREATE INDEX idx_cards_product_id_fk
    ON public.cards (product_id);

-- credit_installments_transaction_id_fkey
CREATE INDEX idx_credit_installments_transaction_id_fk
    ON public.credit_installments (transaction_id);

-- credit_payment_allocations_installment_id_fkey
CREATE INDEX idx_credit_payment_allocations_installment_id_fk
    ON public.credit_payment_allocations (installment_id);

-- credit_payment_allocations_payment_transaction_id_fkey
CREATE INDEX idx_credit_payment_allocations_payment_transaction_id_fk
    ON public.credit_payment_allocations (payment_transaction_id);

-- obligation_events_obligation_id_fkey
CREATE INDEX idx_debt_events_debt_id_fk
    ON public.debt_events (debt_id);

-- goal_events_owner_account_fk
CREATE INDEX idx_goal_events_backing_account_id_fk
    ON public.goal_events (backing_account_id, user_id);

-- goal_events_owner_goal_fk
CREATE INDEX idx_goal_events_goal_id_fk
    ON public.goal_events (goal_id, user_id);

-- goal_events_owner_tx_fk
CREATE INDEX idx_goal_events_transaction_id_owner_fk
    ON public.goal_events (transaction_id, user_id);

-- goals_owner_linked_account_fk
CREATE INDEX idx_goals_linked_account_id_fk
    ON public.goals (linked_account_id, user_id);

-- savings_goals_user_id_fkey
CREATE INDEX idx_goals_user_id_fk
    ON public.goals (user_id);

-- merchant_alias_rules_merchant_id_fkey
CREATE INDEX idx_merchant_alias_rules_merchant_id_fk
    ON public.merchant_alias_rules (merchant_id);

-- merchant_category_preferences_category_id_fkey
CREATE INDEX idx_merchant_category_preferences_category_id_fk
    ON public.merchant_category_preferences (category_id);

-- merchant_category_preferences_merchant_id_fkey
CREATE INDEX idx_merchant_category_preferences_merchant_id_fk
    ON public.merchant_category_preferences (merchant_id);

-- merchant_rules_category_id_fkey
CREATE INDEX idx_merchant_rules_category_id_fk
    ON public.merchant_rules (category_id);

-- merchant_rules_merchant_id_fkey
CREATE INDEX idx_merchant_rules_merchant_service_id_fk
    ON public.merchant_rules (merchant_service_id);

-- merchant_rules_user_id_fkey
CREATE INDEX idx_merchant_rules_user_id_fk
    ON public.merchant_rules (user_id);

-- merchants_user_id_fkey
CREATE INDEX idx_merchant_services_user_id_fk
    ON public.merchant_services (user_id);

-- movement_official_revisions_user_id_transaction_id_revisio_fkey
CREATE INDEX idx_movement_official_revisions_user_id_fk
    ON public.movement_official_revisions (user_id, transaction_id, revision_id);

-- transaction_revisions_transaction_id_fkey
CREATE INDEX idx_transaction_revisions_transaction_id_fk
    ON public.transaction_revisions (transaction_id);

-- transactions_card_id_fkey
CREATE INDEX idx_transactions_card_id_fk
    ON public.transactions (card_id);

-- transactions_category_id_fkey
CREATE INDEX idx_transactions_category_id_fk
    ON public.transactions (category_id);

-- transactions_merchant_id_fkey
CREATE INDEX idx_transactions_merchant_service_id_fk
    ON public.transactions (merchant_service_id);

-- transactions_refund_of_transaction_id_fkey
CREATE INDEX idx_transactions_refund_of_transaction_id_fk
    ON public.transactions (refund_of_transaction_id);

COMMIT;
