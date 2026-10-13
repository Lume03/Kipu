-- Apply account preset validation to every remote account write.
BEGIN;

DROP TRIGGER IF EXISTS accounts_validate_preset_compatibility ON public.accounts;

CREATE TRIGGER accounts_validate_preset_compatibility
BEFORE INSERT OR UPDATE OF account_type, preset_id ON public.accounts
FOR EACH ROW EXECUTE FUNCTION internal.validate_account_preset_compatibility();

COMMIT;
