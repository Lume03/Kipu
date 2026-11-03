-- HU-06 B. Receipt secrets are hashed by Edge; completed receipts contain no user identity.
BEGIN;
CREATE TABLE private.account_deletion_requests (
  request_id uuid PRIMARY KEY,
  secret_hash text NOT NULL CHECK (secret_hash ~ '^[a-f0-9]{64}$'),
  user_id uuid UNIQUE,
  state text NOT NULL CHECK (state IN ('deleting','data_deleted','completed')),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  completed_at timestamptz,
  CHECK ((state = 'completed') = (user_id IS NULL))
);
ALTER TABLE private.account_deletion_requests ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON private.account_deletion_requests FROM PUBLIC, anon, authenticated, service_role;

CREATE FUNCTION public.account_deletion_status(p_request_id uuid, p_secret_hash text)
RETURNS jsonb LANGUAGE sql SECURITY DEFINER SET search_path = '' AS $$
  SELECT jsonb_build_object('state',r.state,'user_id',r.user_id)
  FROM private.account_deletion_requests r
  WHERE r.request_id=p_request_id AND r.secret_hash=p_secret_hash;
$$;

-- Block stale devices and billing/sync jobs from recreating data while Auth deletion retries.
-- The same transaction lock also serializes writes already in flight with the purge.
CREATE FUNCTION private.guard_account_deletion_write() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
BEGIN
  IF NEW.user_id IS NOT NULL THEN
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(NEW.user_id::text, 6));
    IF current_setting('kipu.deleting_account',true)=NEW.user_id::text
       AND EXISTS(SELECT 1 FROM private.account_deletion_requests WHERE user_id=NEW.user_id AND state='deleting')
    THEN RETURN NEW; END IF;
    IF EXISTS (SELECT 1 FROM private.account_deletion_requests WHERE user_id=NEW.user_id)
       OR NOT EXISTS (SELECT 1 FROM auth.users WHERE id=NEW.user_id) THEN
      RAISE EXCEPTION 'Account deletion in progress or account absent' USING ERRCODE='42501';
    END IF;
  END IF;
  RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION private.guard_account_deletion_write() FROM PUBLIC, anon, authenticated;
DO $$ DECLARE t record; BEGIN
  FOR t IN SELECT n.nspname,c.relname FROM pg_catalog.pg_class c
    JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
    JOIN pg_catalog.pg_attribute a ON a.attrelid=c.oid AND a.attname='user_id' AND NOT a.attisdropped
    WHERE n.nspname IN ('public','private','internal') AND c.relkind='r'
      AND a.atttypid='uuid'::regtype AND c.relname <> 'account_deletion_requests'
  LOOP
    EXECUTE format('CREATE TRIGGER account_deletion_write_guard BEFORE INSERT OR UPDATE ON %I.%I FOR EACH ROW EXECUTE FUNCTION private.guard_account_deletion_write()',t.nspname,t.relname);
  END LOOP;
END $$;

-- These DELETE exceptions are only possible inside the postgres-owned purge, for its
-- exact subject, with a receipt locked in the same transaction. Client-set GUCs alone
-- cannot bypass append-only evidence. UPDATE remains prohibited for every caller.
CREATE OR REPLACE FUNCTION internal.reject_movement_evidence_mutation() RETURNS trigger
LANGUAGE plpgsql SET search_path = '' AS $$
BEGIN
  IF TG_OP='DELETE' AND current_user='postgres'
    AND current_setting('kipu.deleting_account',true)=OLD.user_id::text
    AND EXISTS(SELECT 1 FROM private.account_deletion_requests WHERE user_id=OLD.user_id AND state='deleting')
  THEN RETURN OLD; END IF;
  RAISE EXCEPTION 'Movement financial evidence is append-only' USING ERRCODE='55000';
END $$;
CREATE OR REPLACE FUNCTION internal.reject_billing_event_mutation() RETURNS trigger
LANGUAGE plpgsql SET search_path = '' AS $$
BEGIN
  IF TG_OP='DELETE' AND current_user='postgres'
    AND EXISTS(SELECT 1 FROM private.account_deletion_requests r
      WHERE r.user_id::text=current_setting('kipu.deleting_account',true) AND r.state='deleting'
        AND (r.user_id=OLD.user_id OR (OLD.user_id IS NULL AND EXISTS(
          SELECT 1 FROM public.billing_purchases p WHERE p.user_id=r.user_id AND p.purchase_token_hash=OLD.purchase_token_hash))))
  THEN RETURN OLD; END IF;
  RAISE EXCEPTION 'billing events are append-only' USING ERRCODE='42501';
END $$;

CREATE FUNCTION public.purge_account_data(p_user_id uuid,p_request_id uuid,p_secret_hash text)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE r private.account_deletion_requests%ROWTYPE; t text; email text; remaining boolean; tokens text[];
BEGIN
  IF p_user_id IS NULL OR p_request_id IS NULL OR p_secret_hash !~ '^[a-f0-9]{64}$' OR p_secret_hash IS NULL THEN
    RAISE EXCEPTION 'Invalid deletion request';
  END IF;
  PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(p_user_id::text,6));
  SELECT * INTO r FROM private.account_deletion_requests WHERE request_id=p_request_id FOR UPDATE;
  IF FOUND THEN
    IF r.secret_hash<>p_secret_hash OR (r.user_id IS NOT NULL AND r.user_id<>p_user_id) THEN RAISE EXCEPTION 'Request mismatch'; END IF;
    RETURN jsonb_build_object('state',r.state);
  END IF;
  SELECT u.email INTO email FROM auth.users u WHERE u.id=p_user_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'User absent without deletion receipt'; END IF;
  INSERT INTO private.account_deletion_requests VALUES(p_request_id,p_secret_hash,p_user_id,'deleting',clock_timestamp(),NULL);
  PERFORM pg_catalog.set_config('kipu.deleting_account',p_user_id::text,true);
  SELECT array_agg(purchase_token_hash) INTO tokens FROM public.billing_purchases WHERE user_id=p_user_id;
  -- RTDN can arrive before it has been associated to Auth. Remove such receipts
  -- by verified purchase ownership as well, before deleting the purchase itself.
  DELETE FROM internal.billing_reconciliation_jobs WHERE user_id IS NULL AND purchase_token_hash=ANY(tokens);
  DELETE FROM internal.billing_event_receipts WHERE user_id IS NULL AND purchase_token_hash=ANY(tokens);
  DELETE FROM internal.billing_events WHERE user_id IS NULL AND purchase_token_hash=ANY(tokens);
  -- Leaf-first, including operational tables. Each statement deletes one partition;
  -- all self-references are removed in that same statement. Never disable FK triggers.
  FOREACH t IN ARRAY ARRAY[
    'internal.billing_reconciliation_jobs','internal.billing_event_receipts','internal.billing_events',
    'internal.movement_ledger_aliases','internal.movement_ledger_effects','public.movement_official_revisions',
    'public.transaction_revisions','public.credit_payment_allocations','public.transaction_links',
    'public.credit_installments','public.debt_events','public.debt_installments','public.debts',
    'public.goal_events','public.goals','internal.ledger_entries','public.import_candidates',
    'public.transactions','public.financial_movements','public.card_personal_teas','public.cards',
    'public.budget_periods','public.budget_tombstones','public.budgets',
    'public.merchant_category_preferences','public.merchant_alias_rules','public.merchant_rules',
    'public.category_conflicts','public.category_presentations','public.merchant_services','public.categories',
    'public.accounts','public.recurrences','public.app_notifications','public.user_devices',
    'public.billing_purchases','public.plan_preferences','public.profiles',
    'private.plan_quota_selection_items','private.plan_quota_selection_heads','private.plan_quota_selection_receipts',
    'private.plan_selection_heads','private.plan_selection_receipts','private.trial_eligibility_snapshots',
    'private.profile_preference_receipts','internal.command_receipts','internal.sync_changes',
    'internal.dev_plan_receipts','internal.developer_entitlements','internal.developer_entitlement_audit',
    'internal.test_plan_overrides','internal.test_plan_override_audit',
    'internal.merchant_search_access','internal.merchant_discovery_events'
  ] LOOP EXECUTE format('DELETE FROM %s WHERE user_id=$1',t) USING p_user_id; END LOOP;
  -- Shared catalog audit is retained without attribution; catalogs are not user data.
  UPDATE internal.merchant_catalog_audit SET added_by=NULL WHERE added_by=p_user_id;
  UPDATE internal.test_plan_overrides SET created_by='account-deleted' WHERE created_by=p_user_id::text;
  UPDATE internal.test_plan_overrides SET updated_by='account-deleted' WHERE updated_by=p_user_id::text;
  UPDATE internal.test_plan_override_audit SET actor='account-deleted' WHERE actor=p_user_id::text;
  UPDATE internal.developer_entitlement_audit SET actor='account-deleted' WHERE actor=p_user_id::text;
  UPDATE internal.developer_entitlements SET granted_by='account-deleted' WHERE granted_by=p_user_id::text;
  DELETE FROM private.auth_otp_guards WHERE email_hash=extensions.digest(convert_to(lower(btrim(email)),'UTF8'),'sha256');
  -- Identity buckets are SHA256(operation || ':id:' || normalized email); IP/global
  -- buckets cannot be attributed to one user and expire under existing rate policy.
  DELETE FROM private.registration_rate_buckets WHERE bucket_hash IN (
    SELECT extensions.digest(convert_to(op || ':id:' || lower(btrim(email)),'UTF8'),'sha256')
    FROM unnest(ARRAY['register','login','recovery','verify-otp','resend-otp']) op
  );
  -- Fail closed if a future user-owned table was added without updating this inventory.
  FOR t IN SELECT format('%I.%I',n.nspname,c.relname) FROM pg_class c
    JOIN pg_namespace n ON n.oid=c.relnamespace
    JOIN pg_attribute a ON a.attrelid=c.oid AND a.attname='user_id' AND NOT a.attisdropped
    WHERE n.nspname IN ('public','private','internal') AND c.relkind='r'
      AND a.atttypid='uuid'::regtype AND c.relname<>'account_deletion_requests'
  LOOP
    EXECUTE format('SELECT EXISTS(SELECT 1 FROM %s WHERE user_id=$1)',t) INTO remaining USING p_user_id;
    IF remaining THEN RAISE EXCEPTION 'Unpurged user data in %',t; END IF;
  END LOOP;
  UPDATE private.account_deletion_requests SET state='data_deleted' WHERE request_id=p_request_id;
  PERFORM pg_catalog.set_config('kipu.deleting_account','',true);
  RETURN jsonb_build_object('state','data_deleted');
END $$;

CREATE FUNCTION public.complete_account_deletion(p_request_id uuid,p_secret_hash text)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE r private.account_deletion_requests%ROWTYPE;
BEGIN
  SELECT * INTO r FROM private.account_deletion_requests WHERE request_id=p_request_id AND secret_hash=p_secret_hash FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Unknown request'; END IF;
  IF r.state='completed' THEN RETURN; END IF;
  IF r.state<>'data_deleted' OR EXISTS(SELECT 1 FROM auth.users WHERE id=r.user_id) THEN RAISE EXCEPTION 'Auth deletion not completed'; END IF;
  UPDATE private.account_deletion_requests SET user_id=NULL,state='completed',completed_at=clock_timestamp() WHERE request_id=p_request_id;
END $$;
REVOKE ALL ON FUNCTION public.account_deletion_status(uuid,text),public.purge_account_data(uuid,uuid,text),public.complete_account_deletion(uuid,text) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.account_deletion_status(uuid,text),public.purge_account_data(uuid,uuid,text),public.complete_account_deletion(uuid,text) TO service_role;
COMMIT;
