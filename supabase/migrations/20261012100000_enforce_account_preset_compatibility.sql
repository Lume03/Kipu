-- Keep account presentation compatible with its financial type for all writers.
BEGIN;

CREATE OR REPLACE FUNCTION internal.validate_account_preset_compatibility()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = ''
AS $$
BEGIN
    IF NEW.account_type::text = 'CASH' AND NEW.preset_id IS DISTINCT FROM 'CASH' THEN
        RAISE EXCEPTION 'INVALID_REQUEST: CASH accounts must use the CASH preset';
    END IF;

    IF NEW.account_type::text IN ('SAVINGS', 'BANK')
        AND (NEW.preset_id IS NULL OR NEW.preset_id NOT IN ('BCP', 'BBVA', 'INTERBANK', 'SCOTIABANK', 'BANCO_NACION', 'GENERIC')) THEN
        RAISE EXCEPTION 'INVALID_REQUEST: Savings and bank accounts require a bank preset';
    END IF;

    IF NEW.account_type::text = 'WALLET' AND (NEW.preset_id IS NULL OR NEW.preset_id NOT IN ('YAPE', 'PLIN')) THEN
        RAISE EXCEPTION 'INVALID_REQUEST: Wallet accounts require a wallet preset';
    END IF;

    RETURN NEW;
END;
$$;

COMMIT;
