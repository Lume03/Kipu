-- Card snapshots already emit ARCHIVE and the mobile pull contract handles it.
-- Keep the operation vocabulary consistent with that established contract.
ALTER TABLE internal.sync_changes DROP CONSTRAINT sync_changes_operation_check;
ALTER TABLE internal.sync_changes ADD CONSTRAINT sync_changes_operation_check
    CHECK (operation IN ('UPSERT', 'DELETE', 'ARCHIVE'));
