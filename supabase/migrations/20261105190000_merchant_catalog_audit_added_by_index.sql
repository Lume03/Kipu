-- Index the merchant catalog audit FK so auth.users deletes (ON DELETE SET NULL)
-- do not scan the audit table. Found by local_advisor_security_hardening pgTAP.
BEGIN;

-- merchant_catalog_audit_added_by_fkey
CREATE INDEX IF NOT EXISTS idx_merchant_catalog_audit_added_by_fk
    ON internal.merchant_catalog_audit (added_by);

COMMIT;
