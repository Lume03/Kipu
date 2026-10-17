-- Keep one active Yape and one active Plin wallet per owner. Existing history
-- remains untouched; if legacy data violates the rule, this migration fails
-- instead of selecting or archiving an account without the owner's input.
CREATE UNIQUE INDEX IF NOT EXISTS accounts_one_active_wallet_per_channel_uidx
    ON public.accounts(user_id, preset_id)
    WHERE account_type = 'WALLET'
      AND preset_id IN ('YAPE', 'PLIN')
      AND NOT is_archived
      AND deleted_at IS NULL;

-- Serialize same-owner/channel writes and return a stable conflict before the
-- unique index is reached. The index remains the final concurrency safeguard.
CREATE OR REPLACE FUNCTION internal.enforce_one_active_wallet_per_channel()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    IF NEW.account_type = 'WALLET'
       AND NEW.preset_id IN ('YAPE', 'PLIN')
       AND NOT NEW.is_archived
       AND NEW.deleted_at IS NULL THEN
        PERFORM pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtextextended(NEW.user_id::text || ':wallet:' || NEW.preset_id, 0)
        );

        IF EXISTS (
            SELECT 1 FROM public.accounts a
            WHERE a.user_id = NEW.user_id
              AND a.account_type = 'WALLET'
              AND a.preset_id = NEW.preset_id
              AND NOT a.is_archived
              AND a.deleted_at IS NULL
              AND a.id <> NEW.id
        ) THEN
            RAISE EXCEPTION 'ACTIVE_WALLET_CHANNEL_EXISTS: % wallet is already active', NEW.preset_id
                USING ERRCODE = '23505';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION internal.enforce_one_active_wallet_per_channel() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS accounts_enforce_one_active_wallet_per_channel ON public.accounts;
CREATE TRIGGER accounts_enforce_one_active_wallet_per_channel
    BEFORE INSERT OR UPDATE ON public.accounts
    FOR EACH ROW
    EXECUTE FUNCTION internal.enforce_one_active_wallet_per_channel();
