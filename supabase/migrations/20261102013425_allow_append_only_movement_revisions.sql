-- Revisions append new entries for the same transaction/account/role.
-- Opening movements keep their independent unique financial_movement_id identity
-- via ledger_entries_one_movement_source; command/effect identity and reverse-once
-- constraints continue to protect transaction revision retries.
BEGIN;
DROP INDEX IF EXISTS internal.ledger_entries_transaction_account_role;
CREATE INDEX ledger_entries_transaction_account_role
  ON internal.ledger_entries(transaction_id,account_id,entry_role)
  WHERE transaction_id IS NOT NULL;
COMMIT;
