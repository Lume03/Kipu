-- L9a: money and business-date corrections. Preserve existing signatures and ACLs.

BEGIN;

-- Source: 20261018190000_ep_mov_opening_ledger.sql

CREATE OR REPLACE FUNCTION internal.goal_confirmed_balance(p_user uuid,p_account uuid)
RETURNS bigint LANGUAGE sql STABLE SECURITY DEFINER SET search_path = '' AS $$
  SELECT COALESCE(sum(l.signed_amount_minor),0)::bigint
  FROM internal.ledger_entries l
  WHERE l.user_id=p_user AND l.account_id=p_account
    AND ((l.source_kind='TRANSACTION' AND EXISTS (
          SELECT 1 FROM public.transactions t
          WHERE t.id=l.transaction_id AND t.user_id=l.user_id
            AND t.status IN ('CONFIRMED','REVISED') AND t.deleted_at IS NULL))
      OR (l.source_kind='FINANCIAL_MOVEMENT' AND EXISTS (
          SELECT 1 FROM public.financial_movements m
          WHERE m.id=l.financial_movement_id AND m.user_id=l.user_id
            AND m.account_id=l.account_id AND m.status='POSTED')));
$$;

-- Source: 20261003004348_s4_movement_revision_rpc_v1.sql

CREATE OR REPLACE FUNCTION internal.movement_instrument_locked_v1(p_user_id uuid,p_account_id uuid)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path = '' AS $function$
    SELECT private.is_plan_quota_resource_locked(p_user_id,'INSTRUMENTS',p_account_id);
$function$;

-- Source: 20261031163043_credit_purchase_terms_and_cycles.sql

CREATE OR REPLACE FUNCTION public.allocate_credit_payment_v1_pre_liability(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_tx jsonb := p_command->'transaction';
    v_idempotency_key text := NULLIF(p_command->>'idempotency_key','');
    v_request_hash text;
    v_client_hash text := NULLIF(p_command->>'request_hash','');
    v_tx_id uuid;
    v_card_id uuid;
    v_account_id uuid;
    v_amount bigint;
    v_currency char(3);
    v_occurred_at timestamptz;
    v_card record;
    v_account record;
    v_existing record;
    v_receipt_id uuid;
    v_response jsonb;
    v_debt bigint;
    v_funds bigint;
    v_left bigint;
    v_alloc bigint;
    v_outstanding bigint;
    v_total_allocated bigint := 0;
    v_remaining_debt bigint;
    v_allocations jsonb := '[]'::jsonb;
    v_installment record;
    v_now timestamptz := clock_timestamp();
BEGIN
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','UNAUTHORIZED','field',NULL,'retryable',false));
    END IF;
    IF COALESCE((p_command->>'contract_version')::integer,1) <> 1 OR v_tx IS NULL OR v_idempotency_key IS NULL OR v_client_hash IS NULL THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_COMMAND','field',NULL,'retryable',false));
    END IF;
    IF upper(COALESCE(v_tx->>'type','')) <> 'TRANSFER' OR upper(COALESCE(v_tx->>'operation_kind','')) <> 'CARD_PAYMENT' THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_PAYMENT_KIND','field','transaction.type/operation_kind','retryable',false));
    END IF;
    BEGIN
        v_tx_id := (v_tx->>'id')::uuid;
        v_card_id := (v_tx->>'card_id')::uuid;
        v_account_id := (v_tx->>'source_account_id')::uuid;
        v_amount := (v_tx->>'amount_minor')::bigint;
        v_currency := (v_tx->>'currency_code')::char(3);
        v_occurred_at := COALESCE((v_tx->>'occurred_at')::timestamptz,v_now);
    EXCEPTION WHEN invalid_text_representation OR datetime_field_overflow THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_COMMAND_FIELD','field',NULL,'retryable',false));
    END;
    IF v_tx_id IS NULL OR v_card_id IS NULL OR v_account_id IS NULL OR v_amount IS NULL OR v_currency IS NULL OR v_amount < 1 OR v_amount > 10000000 THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_PAYMENT_FIELDS','field',NULL,'retryable',false));
    END IF;
    IF length(trim(v_currency::text))<>3 OR NOT EXISTS (SELECT 1 FROM public.currencies c WHERE c.code=v_currency) THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INVALID_CURRENCY','field','currency_code','retryable',false));
    END IF;

    -- Use the server's normalized JSONB representation as the authoritative
    -- request identity. A caller cannot force an idempotency hit with a reused hash.
    v_request_hash := encode(extensions.digest(convert_to(jsonb_build_object('contract_version',1,'transaction',v_tx)::text,'UTF8'),'sha256'),'hex');

    PERFORM pg_advisory_xact_lock(hashtextextended(v_user_id::text || ':' || v_idempotency_key,0));
    SELECT id,request_hash,response_payload INTO v_existing
    FROM internal.command_receipts WHERE user_id=v_user_id AND idempotency_key=v_idempotency_key;
    IF FOUND THEN
        IF v_existing.request_hash=v_request_hash THEN
            RETURN jsonb_set(v_existing.response_payload,'{status}','"DUPLICATE"'::jsonb);
        END IF;
        RETURN jsonb_build_object('status','CONFLICT','transaction_id',NULL,'receipt_id',v_existing.id,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','IDEMPOTENCY_CONFLICT','field','idempotency_key','retryable',false));
    END IF;
    IF EXISTS (SELECT 1 FROM public.transactions WHERE id=v_tx_id) THEN
        RETURN jsonb_build_object('status','CONFLICT','transaction_id',v_tx_id,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','TRANSACTION_ID_ALREADY_EXISTS','field','transaction.id','retryable',false));
    END IF;

    -- Lock in a consistent order: card, then source account, then installments by FIFO.
    SELECT c.id,c.user_id,c.is_credit,c.is_archived,c.account_id,c.revision INTO v_card
    FROM public.cards c WHERE c.id=v_card_id AND c.user_id=v_user_id FOR UPDATE;
    IF NOT FOUND OR NOT v_card.is_credit OR v_card.is_archived THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','CREDIT_CARD_UNAVAILABLE','field','card_id','retryable',false));
    END IF;
    SELECT a.id,a.user_id,a.currency_code,a.account_type,a.is_archived,a.initial_balance_minor_units INTO v_account
    FROM public.accounts a WHERE a.id=v_account_id AND a.user_id=v_user_id FOR UPDATE;
    IF NOT FOUND OR v_account.is_archived OR v_account.account_type='CREDIT_LIABILITY' OR v_account.currency_code<>v_currency THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','SOURCE_ACCOUNT_UNAVAILABLE_OR_CURRENCY_MISMATCH','field','source_account_id/currency_code','retryable',false));
    END IF;
    -- Canonical openings and net original/reversal/replacement entries, once.
    v_funds := internal.goal_confirmed_balance(v_user_id,v_account_id);
    IF v_funds < v_amount THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',NULL,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','INSUFFICIENT_FUNDS','field','amount_minor','retryable',false));
    END IF;

    SELECT coalesce(sum(b.outstanding_minor),0)::bigint INTO v_debt
    FROM private.credit_installment_balances(v_user_id,v_card_id,v_currency) b;
    IF v_debt=0 THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',0,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','NO_OUTSTANDING_DEBT','field','card_id','retryable',false));
    END IF;
    IF v_amount > v_debt THEN
        RETURN jsonb_build_object('status','REJECTED','transaction_id',NULL,'receipt_id',NULL,'allocated_total_minor',0,'remaining_debt_minor',v_debt,'allocations','[]'::jsonb,'server_updated_at',v_now,
            'error',jsonb_build_object('code','PAYMENT_EXCEEDS_OUTSTANDING_DEBT','field','amount_minor','retryable',false));
    END IF;

    INSERT INTO public.transactions (
        id,user_id,account_id,card_id,transaction_type,operation_kind,amount_minor,currency_code,
        occurred_at,installment_count,interest_mode,notes,status,revision,created_at,updated_at
    ) VALUES (
        v_tx_id,v_user_id,v_account_id,v_card_id,'TRANSFER','CARD_PAYMENT',v_amount,v_currency,
        v_occurred_at,1,'NONE',v_tx->>'note','CONFIRMED',1,v_now,v_now
    );
    INSERT INTO internal.ledger_entries (
        transaction_id,user_id,account_id,signed_amount_minor,currency_code,entry_role,occurred_at
    ) VALUES (v_tx_id,v_user_id,v_account_id,-v_amount,v_currency,'SOURCE',v_occurred_at);

    v_left := v_amount;
    FOR v_installment IN
        SELECT balances.* FROM private.credit_installment_balances(v_user_id,v_card_id,v_currency) balances
        JOIN public.credit_installments ci ON ci.id=balances.id AND ci.user_id=v_user_id
        WHERE balances.outstanding_minor>0
        ORDER BY balances.due_date,balances.purchase_occurred_at,balances.installment_number,balances.id
        FOR UPDATE OF ci
    LOOP
        EXIT WHEN v_left=0;
        v_outstanding := v_installment.outstanding_minor;
        IF v_outstanding=0 THEN CONTINUE; END IF;
        v_alloc := least(v_outstanding,v_left);
        INSERT INTO public.credit_payment_allocations(user_id,payment_transaction_id,installment_id,allocated_minor,created_at)
        VALUES(v_user_id,v_tx_id,v_installment.id,v_alloc,v_now);
        UPDATE public.credit_installments
        SET status=CASE WHEN v_alloc=v_outstanding THEN 'PAID' ELSE 'PARTIAL' END,
            revision=revision+1,updated_at=v_now
        WHERE id=v_installment.id AND user_id=v_user_id;
        v_allocations := v_allocations || jsonb_build_array(jsonb_build_object('installment_id',v_installment.id,'amount_minor',v_alloc));
        v_total_allocated := v_total_allocated+v_alloc;
        v_left := v_left-v_alloc;
    END LOOP;
    IF v_left<>0 OR v_total_allocated<>v_amount THEN
        RAISE EXCEPTION 'Credit payment allocation invariant failed for transaction %',v_tx_id USING ERRCODE='23514';
    END IF;

    UPDATE public.cards SET revision=revision+1,updated_at=v_now WHERE id=v_card_id AND user_id=v_user_id;
    v_remaining_debt := v_debt-v_total_allocated;
    v_receipt_id := extensions.gen_random_uuid();
    v_response := jsonb_build_object(
        'status','APPLIED','transaction_id',v_tx_id,'receipt_id',v_receipt_id,
        'allocated_total_minor',v_total_allocated,'remaining_debt_minor',v_remaining_debt,
        'allocations',v_allocations,'server_updated_at',v_now,'error',NULL
    );
    INSERT INTO internal.command_receipts(id,user_id,idempotency_key,command_type,request_hash,response_payload,status)
    VALUES(v_receipt_id,v_user_id,v_idempotency_key,'ALLOCATE_CREDIT_PAYMENT_V1',v_request_hash,v_response,'APPLIED');
    INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload)
    VALUES(v_user_id,'TRANSACTION',v_tx_id,1,'UPSERT',jsonb_build_object(
        'id',v_tx_id,'type','TRANSFER','operation_kind','CARD_PAYMENT','card_id',v_card_id,
        'amount_minor',v_amount,'currency_code',v_currency,'source_account_id',v_account_id,
        'occurred_at',v_occurred_at,'status','ACTIVE','allocations',v_allocations,
        'ledger_entries',jsonb_build_array(jsonb_build_object('account_id',v_account_id,'role','SOURCE','signed_amount_minor',-v_amount,'currency_code',v_currency))
    ));
    RETURN v_response;
END;
$$;

-- Source: 20261017180300_ep_met_goal_lifecycle_commands.sql

CREATE OR REPLACE FUNCTION public.reassign_goal_reserve_v1(p_payload jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
  u uuid := auth.uid(); src uuid; dest uuid; backing uuid; eid_src uuid; eid_dest uuid;
  amount bigint; rev_src bigint; rev_dest bigint; c bpchar(3); g uuid;
  src_row public.goals%ROWTYPE; dest_row public.goals%ROWTYPE;
  seq bigint; prior jsonb; response jsonb;
BEGIN
  prior := internal.goal_check_command(p_payload,'REASSIGN_GOAL_RESERVE');
  IF prior IS NOT NULL THEN RETURN prior; END IF;
  BEGIN
    src := (p_payload->'payload'->>'source_goal_id')::uuid;
    dest := (p_payload->'payload'->>'destination_goal_id')::uuid;
    backing := (p_payload->'payload'->>'backing_account_id')::uuid;
    eid_src := (p_payload->'payload'->>'source_event_id')::uuid;
    eid_dest := (p_payload->'payload'->>'destination_event_id')::uuid;
    amount := (p_payload->'payload'->>'amount_minor')::bigint;
    c := (p_payload->'payload'->>'currency_code')::bpchar(3);
    rev_src := (p_payload->'payload'->>'source_expected_revision')::bigint;
    rev_dest := (p_payload->'payload'->>'destination_expected_revision')::bigint;
  EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','INVALID_COMMAND');
  END;
  IF src IS NULL OR dest IS NULL OR src=dest OR eid_src IS NULL OR eid_dest IS NULL OR eid_src=eid_dest
    OR backing IS NULL OR amount IS NULL OR amount<=0 OR rev_src IS NULL OR rev_dest IS NULL OR
    rev_src<1 OR rev_dest<1 OR c IS NULL OR p_payload->>'goal_id' <> src::text THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','INVALID_COMMAND'); END IF;
  PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('kipu:goal-quota:'||u::text,0));
  PERFORM 1 FROM public.accounts WHERE id=backing AND user_id=u AND currency_code=c AND deleted_at IS NULL FOR UPDATE;
  IF NOT FOUND THEN RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','OWNER_OR_ACCOUNT_INVALID'); END IF;
  -- Deterministic lock order between source and destination.
  FOR g IN SELECT x FROM pg_catalog.unnest(ARRAY[src,dest]) AS x ORDER BY x LOOP
    PERFORM 1 FROM public.goals WHERE id=g AND user_id=u AND deleted_at IS NULL FOR UPDATE;
    IF NOT FOUND THEN RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','OWNER_OR_ACCOUNT_INVALID'); END IF;
  END LOOP;
  SELECT * INTO src_row FROM public.goals WHERE id=src AND user_id=u;
  SELECT * INTO dest_row FROM public.goals WHERE id=dest AND user_id=u;
  IF src_row.currency_code<>c OR dest_row.currency_code<>c
    OR src_row.status='ARCHIVED' OR dest_row.status<>'ACTIVE' THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','GOAL_NOT_ELIGIBLE'); END IF;
  IF src_row.revision<>rev_src OR dest_row.revision<>rev_dest OR
      (p_payload->>'expected_revision')::bigint<>rev_src THEN
    RETURN pg_catalog.jsonb_build_object('status','CONFLICT','error','REVISION_MISMATCH',
      'current_revision',src_row.revision); END IF;
  IF EXISTS(SELECT 1 FROM public.goal_events WHERE id IN (eid_src,eid_dest)) THEN
    RETURN pg_catalog.jsonb_build_object('status','CONFLICT','error','EVENT_ID_REUSED'); END IF;
  IF internal.goal_reserved(u,src,backing)<amount THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','RESERVE_EXCEEDED'); END IF;
  -- Evaluate the final number of active goals: the destination may complete.
  IF src_row.status='COMPLETED' AND internal.goal_reserved(u,src)-amount<src_row.target_minor
    AND NOT private.is_user_pro(u)
    AND (SELECT count(*) FROM public.goals WHERE user_id=u AND deleted_at IS NULL AND status='ACTIVE')
      + 1 - (CASE WHEN internal.goal_reserved(u,dest)+amount>=dest_row.target_minor THEN 1 ELSE 0 END) > 2 THEN
    RETURN pg_catalog.jsonb_build_object('status','REJECTED','error','REPLACEMENT_REQUIRED');
  END IF;
  INSERT INTO public.goal_events(id,user_id,goal_id,event_type,amount_minor,delta_minor,currency_code,
      backing_account_id,operation_id)
  VALUES(eid_src,u,src,'REASSIGNMENT_OUT',-amount,-amount,c,backing,(p_payload->>'operation_id')::uuid),
        (eid_dest,u,dest,'REASSIGNMENT_IN',amount,amount,c,backing,(p_payload->>'operation_id')::uuid);
  UPDATE public.goals SET status=CASE WHEN status='LOCKED_BY_PLAN' THEN 'LOCKED_BY_PLAN'::public.goal_status
      WHEN internal.goal_reserved(u,src)>=target_minor THEN 'COMPLETED'::public.goal_status
      ELSE 'ACTIVE'::public.goal_status END,revision=revision+1,updated_at=pg_catalog.now()
    WHERE id=src AND user_id=u;
  UPDATE public.goals SET status=CASE WHEN internal.goal_reserved(u,dest)>=target_minor
      THEN 'COMPLETED'::public.goal_status ELSE 'ACTIVE'::public.goal_status END,
    revision=revision+1,updated_at=pg_catalog.now() WHERE id=dest AND user_id=u;
  PERFORM internal.goal_emit(src,eid_src);
  seq := internal.goal_emit(dest,eid_dest);
  response := pg_catalog.jsonb_build_object('status','APPLIED','goal_id',src,
    'event_id',eid_src,'revision',src_row.revision+1,'sequence',seq);
  PERFORM internal.goal_store_receipt(p_payload,response);
  RETURN response;
END;
$$;

-- Source: 20261018190500_ep_rec_create_update.sql

CREATE OR REPLACE FUNCTION public._rec_template_references_valid(p_template jsonb, p_uid uuid)
RETURNS boolean
LANGUAGE plpgsql STABLE
SET search_path = ''
AS $$
DECLARE v_type text; v_currency text; v_source uuid; v_destination uuid; v_category uuid; v_merchant uuid;
BEGIN
  IF pg_catalog.jsonb_typeof(p_template) <> 'object'
     OR NOT (p_template ?& ARRAY['concept','type','amount_minor','currency_code','source_account_id','destination_account_id','category_id','merchant_id','merchant_provisional_text'])
     OR p_template - ARRAY['concept','type','amount_minor','currency_code','source_account_id','destination_account_id','category_id','merchant_id','merchant_provisional_text'] <> '{}'::jsonb
     OR pg_catalog.jsonb_typeof(p_template->'concept') <> 'string' OR pg_catalog.length(p_template->>'concept') = 0
     OR NOT public._rec_valid_integer(p_template->'amount_minor',1,9223372036854775807)
     OR (p_template->>'currency_code') !~ '^[A-Z]{3}$'
     OR pg_catalog.jsonb_typeof(p_template->'type') <> 'string'
     OR p_template->>'type' NOT IN ('EXPENSE','INCOME','TRANSFER')
     OR pg_catalog.jsonb_typeof(p_template->'source_account_id') NOT IN ('string','null')
     OR pg_catalog.jsonb_typeof(p_template->'destination_account_id') NOT IN ('string','null')
     OR pg_catalog.jsonb_typeof(p_template->'category_id') NOT IN ('string','null')
     OR pg_catalog.jsonb_typeof(p_template->'merchant_id') NOT IN ('string','null')
     OR pg_catalog.jsonb_typeof(p_template->'merchant_provisional_text') NOT IN ('string','null')
     OR (p_template->>'source_account_id' IS NOT NULL AND (p_template->>'source_account_id') !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')
     OR (p_template->>'destination_account_id' IS NOT NULL AND (p_template->>'destination_account_id') !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')
     OR (p_template->>'category_id' IS NOT NULL AND (p_template->>'category_id') !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')
     OR (p_template->>'merchant_id' IS NOT NULL AND (p_template->>'merchant_id') !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$') THEN RETURN false; END IF;
  v_type := p_template->>'type';
  v_currency := p_template->>'currency_code';
  v_source := NULLIF(p_template->>'source_account_id', '')::uuid;
  v_destination := NULLIF(p_template->>'destination_account_id', '')::uuid;
  v_category := NULLIF(p_template->>'category_id', '')::uuid;
  v_merchant := NULLIF(p_template->>'merchant_id', '')::uuid;
  IF NOT EXISTS (SELECT 1 FROM public.currencies c WHERE c.code::text = v_currency) THEN RETURN false; END IF;
  IF (v_type IN ('EXPENSE', 'INCOME', 'TRANSFER')) IS NOT TRUE THEN RETURN false; END IF;
  IF v_source IS NULL OR NOT EXISTS (SELECT 1 FROM public.accounts a WHERE a.id=v_source AND a.user_id=p_uid
       AND NOT a.is_archived AND a.deleted_at IS NULL AND a.currency_code::text=v_currency) THEN RETURN false; END IF;
  IF v_type = 'TRANSFER' THEN
    IF v_destination IS NULL OR v_destination = v_source OR NOT EXISTS (
      SELECT 1 FROM public.accounts a WHERE a.id=v_destination AND a.user_id=p_uid
        AND NOT a.is_archived AND a.deleted_at IS NULL AND a.currency_code::text=v_currency) THEN RETURN false; END IF;
  ELSIF v_destination IS NOT NULL THEN RETURN false;
  END IF;
  IF v_type = 'EXPENSE' THEN
    IF v_category IS NULL OR NOT EXISTS (SELECT 1 FROM public.categories c WHERE c.id=v_category
       AND (c.user_id=p_uid OR c.user_id IS NULL) AND c.is_active AND c.deleted_at IS NULL AND c.category_type IN ('GENERAL','EXPENSE')) THEN RETURN false; END IF;
  ELSIF v_category IS NOT NULL THEN RETURN false;
  END IF;
  IF v_merchant IS NOT NULL AND (p_template->>'merchant_provisional_text' IS NOT NULL
       OR NOT EXISTS (SELECT 1 FROM public.merchant_services m WHERE m.id=v_merchant AND m.is_active)) THEN RETURN false; END IF;
  IF v_merchant IS NULL AND p_template->>'merchant_provisional_text' IS NOT NULL
       AND pg_catalog.length(pg_catalog.btrim(p_template->>'merchant_provisional_text')) = 0 THEN RETURN false; END IF;
  RETURN true;
EXCEPTION WHEN OTHERS THEN RETURN false;
END;
$$;

-- Source: 20261018190500_ep_rec_create_update.sql

CREATE OR REPLACE FUNCTION public.create_recurrence_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_uid uuid := auth.uid(); v_payload jsonb; v_id uuid; v_op uuid; v_hash text;
  v_canonical text; v_receipt internal.command_receipts%ROWTYPE; v_response jsonb;
  v_template jsonb; v_now timestamptz := pg_catalog.clock_timestamp();
BEGIN
  IF v_uid IS NULL THEN RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','UNAUTHORIZED')); END IF;
  IF pg_catalog.jsonb_typeof(p_command) <> 'object'
     OR NOT (p_command ?& ARRAY['command_type','contract_version','operation_id','request_hash','recurrence_id','payload'])
     OR p_command - ARRAY['command_type','contract_version','operation_id','request_hash','recurrence_id','payload'] <> '{}'::jsonb
     OR p_command->>'command_type' <> 'CREATE_RECURRENCE'
     OR NOT public._rec_valid_integer(p_command->'contract_version',1,1)
     OR pg_catalog.jsonb_typeof(p_command->'payload') <> 'object'
     OR NOT (p_command->'payload' ?& ARRAY['amount_mode','custom_interval_unit','end_date','frequency','interval_count','next_due_date','reminder_lead_days','start_date','template_transaction'])
     OR (p_command->'payload') - ARRAY['amount_mode','custom_interval_unit','end_date','frequency','interval_count','next_due_date','reminder_lead_days','start_date','template_transaction'] <> '{}'::jsonb
     OR NOT public._rec_valid_integer(p_command->'payload'->'reminder_lead_days',0,30)
     OR NOT public._rec_valid_integer(p_command->'payload'->'interval_count',1,9223372036854775807)
     OR pg_catalog.jsonb_typeof(p_command->'payload'->'template_transaction') <> 'object' THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND'));
  END IF;
  v_op := (p_command->>'operation_id')::uuid; v_id := (p_command->>'recurrence_id')::uuid;
  IF p_command->>'operation_id' !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
     OR p_command->>'recurrence_id' !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
     OR p_command->>'request_hash' !~ '^[0-9a-f]{64}$' THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND'));
  END IF;
  v_payload := p_command->'payload'; v_template := v_payload->'template_transaction';
  IF pg_catalog.jsonb_typeof(v_template->'concept') <> 'string' OR pg_catalog.length(v_template->>'concept') = 0
     OR v_template->>'type' NOT IN ('EXPENSE','INCOME','TRANSFER')
     OR NOT public._rec_valid_integer(v_template->'amount_minor',1,9223372036854775807)
     OR v_template->>'currency_code' !~ '^[A-Z]{3}$'
     OR v_template - ARRAY['concept','type','amount_minor','currency_code','source_account_id','destination_account_id','category_id','merchant_id','merchant_provisional_text'] <> '{}'::jsonb
     OR (v_template - ARRAY['concept','type','amount_minor','currency_code','source_account_id','destination_account_id','category_id','merchant_id','merchant_provisional_text']) <> '{}'::jsonb
     OR NOT (v_template ?& ARRAY['concept','type','amount_minor','currency_code','source_account_id','destination_account_id','category_id','merchant_id','merchant_provisional_text']) THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_TEMPLATE'));
  END IF;
  IF (v_payload->>'amount_mode') IS NULL OR v_payload->>'amount_mode' NOT IN ('FIXED','APPROXIMATE')
     OR (v_payload->>'frequency') IS NULL OR v_payload->>'frequency' NOT IN ('WEEKLY','BIWEEKLY','MONTHLY','ANNUALLY','CUSTOM')
     OR pg_catalog.jsonb_typeof(v_payload->'custom_interval_unit') NOT IN ('string','null')
     OR NOT public._rec_valid_date(v_payload->'start_date')
     OR NOT public._rec_valid_date(v_payload->'next_due_date')
     OR (v_payload->'end_date' <> 'null'::jsonb AND NOT public._rec_valid_date(v_payload->'end_date'))
     OR (v_payload->>'frequency' <> 'CUSTOM' AND (v_payload->'custom_interval_unit' <> 'null'::jsonb OR (v_payload->'interval_count')::numeric <> 1))
     OR (v_payload->>'frequency' = 'CUSTOM' AND NOT ((v_payload->>'custom_interval_unit'='WEEK' AND (v_payload->'interval_count')::numeric BETWEEN 1 AND 5200) OR (v_payload->>'custom_interval_unit'='MONTH' AND (v_payload->'interval_count')::numeric BETWEEN 1 AND 1200)))
     OR (v_payload->'end_date' <> 'null'::jsonb AND (v_payload->>'end_date')::date < (v_payload->>'start_date')::date)
     OR NOT public._rec_schedule_valid((v_payload->>'start_date')::date,(v_payload->>'next_due_date')::date,
          v_payload->>'frequency',(v_payload->>'interval_count')::numeric::bigint,NULLIF(v_payload->>'custom_interval_unit','null'),
          NULLIF(v_payload->>'end_date','null')::date) THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_SCHEDULE'));
  END IF;
  v_canonical := public._rec_json_canonical(p_command - ARRAY['operation_id','request_hash']);
  IF pg_catalog.octet_length(v_canonical) > 16384 OR pg_catalog.encode(extensions.digest(v_canonical,'sha256'),'hex') <> p_command->>'request_hash' THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','HASH_MISMATCH'));
  END IF;
  PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_uid::text || ':' || v_op::text,0));
  SELECT * INTO v_receipt FROM internal.command_receipts WHERE user_id=v_uid AND idempotency_key=v_op::text;
  IF FOUND THEN
    IF v_receipt.command_type='CREATE_RECURRENCE' AND v_receipt.request_hash=p_command->>'request_hash' THEN RETURN v_receipt.response_payload; END IF;
    RETURN jsonb_build_object('status','CONFLICT','error',jsonb_build_object('code','OPERATION_ID_REUSED'));
  END IF;
  IF NOT public._rec_template_references_valid(v_template,v_uid) THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','REFERENCE_NOT_FOUND'));
  END IF;
  INSERT INTO public.recurrences(id,user_id,template_transaction,amount_mode,frequency,interval_count,custom_interval_unit,start_date,end_date,next_due_date,reminder_lead_days,status,revision)
  VALUES(v_id,v_uid,v_template,v_payload->>'amount_mode',v_payload->>'frequency',(v_payload->>'interval_count')::numeric::bigint,NULLIF(v_payload->>'custom_interval_unit','null'),(v_payload->>'start_date')::date,NULLIF(v_payload->>'end_date','null')::date,(v_payload->>'next_due_date')::date,(v_payload->>'reminder_lead_days')::numeric::smallint,'ACTIVE',1);
  v_response := jsonb_build_object('status','APPLIED','recurrence_id',v_id,'revision',1,'updated_at',v_now);
  INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload) VALUES(v_uid,'RECURRENCE',v_id,1,'UPSERT',v_response || jsonb_build_object('rule',v_payload));
  INSERT INTO internal.command_receipts(user_id,idempotency_key,command_type,request_hash,response_payload,status) VALUES(v_uid,v_op::text,'CREATE_RECURRENCE',p_command->>'request_hash',v_response,'APPLIED');
  RETURN v_response;
EXCEPTION WHEN OTHERS THEN
  RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND'));
END;
$$;

-- Source: 20261018190500_ep_rec_create_update.sql

CREATE OR REPLACE FUNCTION public.update_recurrence_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE v_uid uuid:=auth.uid(); v_id uuid; v_op uuid; v_rev bigint; v_rec public.recurrences%ROWTYPE;
  v_receipt internal.command_receipts%ROWTYPE; v_hash text; v_canonical text; v_patch jsonb; v_payload jsonb; v_response jsonb;
BEGIN
  IF v_uid IS NULL THEN RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','UNAUTHORIZED')); END IF;
  IF pg_catalog.jsonb_typeof(p_command)<>'object'
     OR NOT (p_command ?& ARRAY['command_type','contract_version','operation_id','request_hash','recurrence_id','expected_revision','payload'])
     OR (p_command-ARRAY['command_type','contract_version','operation_id','request_hash','recurrence_id','expected_revision','payload'])<>'{}'::jsonb
     OR p_command->>'command_type'<>'UPDATE_RECURRENCE' OR NOT public._rec_valid_integer(p_command->'contract_version',1,1)
     OR NOT public._rec_valid_integer(p_command->'expected_revision',1,9223372036854775807)
     OR p_command->>'operation_id' IS NULL OR p_command->>'operation_id' !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
     OR p_command->>'recurrence_id' IS NULL OR p_command->>'recurrence_id' !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
     OR p_command->>'request_hash' IS NULL OR p_command->>'request_hash' !~ '^[0-9a-f]{64}$'
     OR pg_catalog.jsonb_typeof(p_command->'payload')<>'object'
     OR (p_command->'payload') - ARRAY['template_transaction','amount_mode','frequency','interval_count','custom_interval_unit','end_date','next_due_date','reminder_lead_days'] <> '{}'::jsonb
     OR NOT public._rec_valid_integer(p_command->'payload'->'reminder_lead_days',0,30)
     OR (p_command->'payload') = '{}'::jsonb THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND'));
  END IF;
  v_id:=(p_command->>'recurrence_id')::uuid; v_op:=(p_command->>'operation_id')::uuid; v_rev:=(p_command->>'expected_revision')::numeric::bigint;
  v_patch:=p_command->'payload';
  v_canonical:=public._rec_json_canonical(p_command-ARRAY['operation_id','request_hash']);
  IF pg_catalog.octet_length(v_canonical) > 16384
     OR p_command->>'request_hash' !~ '^[0-9a-f]{64}$'
     OR pg_catalog.encode(extensions.digest(v_canonical,'sha256'),'hex')<>p_command->>'request_hash' THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','HASH_MISMATCH'));
  END IF;
  PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_uid::text||':'||v_op::text,0));
  SELECT * INTO v_receipt FROM internal.command_receipts WHERE user_id=v_uid AND idempotency_key=v_op::text;
  IF FOUND THEN
    IF v_receipt.command_type='UPDATE_RECURRENCE' AND v_receipt.request_hash=p_command->>'request_hash' THEN RETURN v_receipt.response_payload; END IF;
    RETURN jsonb_build_object('status','CONFLICT','error',jsonb_build_object('code','OPERATION_ID_REUSED'));
  END IF;
  SELECT * INTO v_rec FROM public.recurrences WHERE id=v_id AND user_id=v_uid AND deleted_at IS NULL FOR UPDATE;
  IF NOT FOUND THEN RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','NOT_FOUND')); END IF;
  IF v_rec.revision<>v_rev THEN RETURN jsonb_build_object('status','CONFLICT','error',jsonb_build_object('code','REVISION_MISMATCH','revision',v_rec.revision)); END IF;
  v_payload:=jsonb_build_object('template_transaction',v_rec.template_transaction,'amount_mode',v_rec.amount_mode,'frequency',v_rec.frequency,'interval_count',v_rec.interval_count,'custom_interval_unit',v_rec.custom_interval_unit,'end_date',v_rec.end_date,'next_due_date',v_rec.next_due_date,'reminder_lead_days',v_rec.reminder_lead_days) || v_patch;
  IF (v_payload->>'frequency' IS DISTINCT FROM v_rec.frequency OR (v_payload->>'interval_count')::numeric IS DISTINCT FROM v_rec.interval_count OR v_payload->>'custom_interval_unit' IS DISTINCT FROM v_rec.custom_interval_unit) AND NOT (v_patch ?& ARRAY['frequency','interval_count','custom_interval_unit','next_due_date']) THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','CADENCE_FIELDS_REQUIRED'));
  END IF;
  IF (v_payload->>'amount_mode') IS NULL OR v_payload->>'amount_mode' NOT IN ('FIXED','APPROXIMATE')
     OR (v_payload->>'frequency') IS NULL OR v_payload->>'frequency' NOT IN ('WEEKLY','BIWEEKLY','MONTHLY','ANNUALLY','CUSTOM')
     OR pg_catalog.jsonb_typeof(v_payload->'custom_interval_unit') NOT IN ('string','null')
     OR NOT public._rec_valid_integer(v_payload->'interval_count',1,9223372036854775807)
     OR NOT public._rec_valid_integer(v_payload->'reminder_lead_days',0,30)
     OR NOT public._rec_valid_date(v_payload->'next_due_date')
     OR (v_payload->'end_date' <> 'null'::jsonb AND NOT public._rec_valid_date(v_payload->'end_date'))
     OR (v_payload->>'frequency' IN ('WEEKLY','BIWEEKLY','MONTHLY','ANNUALLY') AND
         ((v_payload->'interval_count')::numeric <> 1 OR v_payload->'custom_interval_unit' <> 'null'::jsonb))
     OR (v_payload->>'frequency'='CUSTOM' AND NOT (
         (v_payload->>'custom_interval_unit'='WEEK' AND (v_payload->'interval_count')::numeric BETWEEN 1 AND 5200) OR
         (v_payload->>'custom_interval_unit'='MONTH' AND (v_payload->'interval_count')::numeric BETWEEN 1 AND 1200)))
     OR NOT public._rec_template_references_valid(v_payload->'template_transaction',v_uid)
     OR NOT public._rec_schedule_valid(v_rec.start_date,(v_payload->>'next_due_date')::date,
         v_payload->>'frequency',(v_payload->>'interval_count')::numeric::bigint,NULLIF(v_payload->>'custom_interval_unit','null'),
         NULLIF(v_payload->>'end_date','null')::date) THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_SCHEDULE'));
  END IF;
  IF (v_payload - ARRAY['template_transaction','amount_mode','frequency','interval_count','custom_interval_unit','end_date','next_due_date','reminder_lead_days']) <> '{}'::jsonb THEN RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND')); END IF;
  IF v_payload=jsonb_build_object('template_transaction',v_rec.template_transaction,'amount_mode',v_rec.amount_mode,'frequency',v_rec.frequency,'interval_count',v_rec.interval_count,'custom_interval_unit',v_rec.custom_interval_unit,'end_date',v_rec.end_date,'next_due_date',v_rec.next_due_date,'reminder_lead_days',v_rec.reminder_lead_days) THEN RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','NO_OP')); END IF;
  UPDATE public.recurrences SET template_transaction=v_payload->'template_transaction',amount_mode=v_payload->>'amount_mode',frequency=v_payload->>'frequency',interval_count=(v_payload->>'interval_count')::numeric::bigint,custom_interval_unit=NULLIF(v_payload->>'custom_interval_unit','null'),end_date=NULLIF(v_payload->>'end_date','null')::date,next_due_date=(v_payload->>'next_due_date')::date,reminder_lead_days=(v_payload->>'reminder_lead_days')::numeric::smallint,revision=revision+1,updated_at=pg_catalog.clock_timestamp() WHERE id=v_id RETURNING * INTO v_rec;
  v_response:=jsonb_build_object('status','APPLIED','recurrence_id',v_id,'revision',v_rec.revision,'updated_at',v_rec.updated_at);
  INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload) VALUES(v_uid,'RECURRENCE',v_id,v_rec.revision,'UPSERT',v_response||jsonb_build_object('rule',to_jsonb(v_rec)));
  INSERT INTO internal.command_receipts(user_id,idempotency_key,command_type,request_hash,response_payload,status) VALUES(v_uid,v_op::text,'UPDATE_RECURRENCE',p_command->>'request_hash',v_response,'APPLIED');
  RETURN v_response;
EXCEPTION WHEN OTHERS THEN RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND'));
END;
$$;

-- Source: 20261111140000_revision_order_fixes.sql

CREATE OR REPLACE FUNCTION public.set_instrument_archived_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid;
    v_operation_id uuid;
    v_instrument_id uuid;
    v_instrument_type text;
    v_is_archived boolean;
    v_current_revision bigint;
    v_computable_count integer;
    v_response jsonb;
    v_hash text;
    v_receipt internal.command_receipts%ROWTYPE;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED: Authentication required';
    END IF;

    v_operation_id := (p_command->>'operation_id')::uuid;
    v_instrument_id := (p_command->>'instrument_id')::uuid;
    v_instrument_type := p_command->>'instrument_type';
    v_is_archived := (p_command->>'is_archived')::boolean;

    IF v_operation_id IS NULL THEN RAISE EXCEPTION 'INVALID_REQUEST: operation_id is required'; END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_user_id::text || ':' || v_operation_id::text, 0));
    v_hash := pg_catalog.encode(extensions.digest(pg_catalog.convert_to((p_command - 'payload_hash')::text, 'UTF8'), 'sha256'), 'hex');
    SELECT * INTO v_receipt FROM internal.command_receipts
      WHERE user_id=v_user_id AND idempotency_key=v_operation_id::text;
    IF FOUND THEN
        IF v_receipt.command_type <> 'SET_ARCHIVED' OR v_receipt.request_hash <> v_hash THEN
            RAISE EXCEPTION 'OPERATION_COLLISION: differing archive request';
        END IF;
        RETURN v_receipt.response_payload;
    END IF;

    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('instrument-quota:' || v_user_id::text, 0));

    IF v_instrument_type = 'ACCOUNT' THEN
        UPDATE public.accounts
        SET is_archived = v_is_archived, revision = revision + 1, updated_at = now()
        WHERE id = v_instrument_id AND user_id = v_user_id
        RETURNING revision INTO v_current_revision;
    ELSIF v_instrument_type = 'CARD' THEN
        PERFORM 1 FROM public.cards WHERE id = v_instrument_id AND user_id = v_user_id FOR UPDATE;
        IF v_is_archived AND EXISTS (
            SELECT 1 FROM public.cards c WHERE c.id = v_instrument_id AND c.user_id = v_user_id AND c.is_credit
              AND EXISTS (SELECT 1 FROM private.credit_installment_balances(v_user_id, v_instrument_id, NULL) b WHERE b.outstanding_minor > 0)
        ) THEN
            RAISE EXCEPTION 'CARD_HAS_OUTSTANDING_DEBT: Debes liquidar la deuda de la tarjeta antes de archivarla.';
        END IF;
        UPDATE public.cards
        SET is_archived = v_is_archived, revision = revision + 1, updated_at = now()
        WHERE id = v_instrument_id AND user_id = v_user_id
        RETURNING revision INTO v_current_revision;
    ELSE
        RAISE EXCEPTION 'INVALID_REQUEST: Unknown instrument type %', v_instrument_type;
    END IF;

    -- Check the resulting groups; bank/debit/wallet affiliations share one slot.
    -- An exception rolls back the update and every trigger effect atomically.
    IF NOT v_is_archived AND NOT private.is_user_pro(v_user_id)
      AND (SELECT count(DISTINCT quota_id) FROM private.instrument_quota_groups(v_user_id)) > 4 THEN
        RAISE EXCEPTION 'FREE_LIMIT_REACHED: Reactivation would exceed Free tier limit of 4 active instruments';
    END IF;

    v_response := jsonb_build_object(
        'status', 'APPLIED',
        'instrument_id', v_instrument_id,
        'is_archived', v_is_archived,
        'revision', v_current_revision
    );

    INSERT INTO internal.command_receipts(user_id,idempotency_key,command_type,request_hash,response_payload,status)
    VALUES(v_user_id,v_operation_id::text,'SET_ARCHIVED',v_hash,v_response,'APPLIED');
    RETURN v_response;
END;
$$;

-- Source: 20261024190000_ep_pre_budget_scope_snapshot.sql

CREATE OR REPLACE FUNCTION public.get_budget_status(
    p_budget_id uuid, p_on date DEFAULT NULL, p_time_zone text DEFAULT 'America/Lima'
)
RETURNS TABLE (
    budget_id uuid, status text, currency_code bpchar, period_starts_on date, period_ends_on date,
    limit_minor bigint, consumed_minor bigint, remaining_minor bigint, usage_bps integer
)
LANGUAGE sql
STABLE
SET search_path = ''
AS $$
    WITH RECURSIVE b AS (
        SELECT * FROM public.budgets WHERE id = p_budget_id AND deleted_at IS NULL
    ), period AS (
        SELECT bp.* FROM public.budget_periods bp, b
        WHERE bp.budget_id=b.id AND bp.user_id=b.user_id
          AND bp.starts_on <= COALESCE(p_on,(pg_catalog.now() AT TIME ZONE p_time_zone)::date)
          AND bp.ends_on > COALESCE(p_on,(pg_catalog.now() AT TIME ZONE p_time_zone)::date)
        ORDER BY bp.starts_on DESC, bp.ends_on, bp.id
        LIMIT 1
    ), bounds AS (
        SELECT b.id, p.starts_on, p.ends_on FROM b CROSS JOIN period p
        UNION ALL
        SELECT b.id, bd.starts_on, bd.ends_on
        FROM b, LATERAL public.budget_period_bounds_v1(
            b.period_type::text,
            COALESCE(p_on,(pg_catalog.now() AT TIME ZONE p_time_zone)::date),
            b.range_start_on,b.range_end_on) bd
        WHERE NOT EXISTS (SELECT 1 FROM period)
    ), scope AS (
        SELECT CASE WHEN EXISTS (SELECT 1 FROM period)
                    THEN (SELECT p.scope_category_id FROM period p)
                    ELSE (SELECT b.category_id FROM b)
               END AS category_id
    ), tree AS (
        SELECT c.id FROM public.categories c, scope WHERE c.id = scope.category_id
        UNION
        SELECT c.id FROM public.categories c JOIN tree t ON c.parent_id = t.id
    ), eligible AS (
        SELECT t.id, t.amount_minor
        FROM public.transactions t, b, bounds, scope
        WHERE t.user_id = b.user_id
          AND t.transaction_type = 'EXPENSE'
          AND t.operation_kind IN ('STANDARD', 'CARD_PURCHASE')
          AND t.status IN ('CONFIRMED', 'REVISED')
          AND t.deleted_at IS NULL
          AND t.transfer_group_id IS NULL
          AND t.currency_code = b.currency_code
          AND (t.occurred_at AT TIME ZONE p_time_zone)::date >= bounds.starts_on
          AND (t.occurred_at AT TIME ZONE p_time_zone)::date < bounds.ends_on
          AND (scope.category_id IS NULL OR t.category_id IN (SELECT id FROM tree))
    ), refunded AS (
        SELECT e.id, pg_catalog.sum(r.amount_minor) AS amount_minor
        FROM eligible e
        JOIN public.transaction_links l
          ON l.link_type = 'REFUND' AND e.id IN (l.source_transaction_id, l.target_transaction_id)
        JOIN public.transactions r
          ON r.id = CASE WHEN l.source_transaction_id = e.id THEN l.target_transaction_id ELSE l.source_transaction_id END
         AND r.operation_kind = 'REFUND'
         AND r.status IN ('CONFIRMED', 'REVISED')
         AND r.deleted_at IS NULL
        JOIN b ON r.user_id = b.user_id AND r.currency_code = b.currency_code
        GROUP BY e.id
    ), net AS (
        SELECT COALESCE(pg_catalog.sum(e.amount_minor - LEAST(e.amount_minor, COALESCE(rf.amount_minor, 0))), 0)::bigint AS consumed
        FROM eligible e LEFT JOIN refunded rf ON rf.id = e.id
    )
    SELECT b.id, b.status, b.currency_code, bounds.starts_on, bounds.ends_on,
           COALESCE((SELECT p.limit_minor FROM period p), b.limit_minor),
           net.consumed,
           COALESCE((SELECT p.limit_minor FROM period p), b.limit_minor) - net.consumed,
           LEAST(2147483647, (net.consumed * 10000 / COALESCE((SELECT p.limit_minor FROM period p), b.limit_minor)))::integer
    FROM b
    JOIN bounds ON bounds.id = b.id
    CROSS JOIN net;
$$;

-- Source: 20261103172646_profile_preferences_private_usage.sql

CREATE OR REPLACE FUNCTION public.update_profile_preferences(
    p_operation_id uuid,
    p_expected_revision bigint,
    p_payload jsonb
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid := private.profile_session_uid();
    v_now timestamptz := clock_timestamp();
    v_hash bytea;
    v_existing_receipt private.profile_preference_receipts%ROWTYPE;
    v_profile public.profiles%ROWTYPE;
    v_result jsonb;
    v_new_revision bigint;
    v_month_start smallint;
    v_currency_code text;
    v_hide_balances boolean;
    v_theme_mode text;
    v_display_name text;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    -- Canonical SHA-256 hash
    v_hash := sha256(convert_to('v1\n' || p_operation_id::text || '\n' || p_expected_revision::text || '\n' || p_payload::text, 'UTF8'));

    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('profile-preference:' || v_user_id::text || ':' || p_operation_id::text,0));

    -- Check duplicate receipt
    SELECT * INTO v_existing_receipt
    FROM private.profile_preference_receipts
    WHERE user_id = v_user_id AND operation_id = p_operation_id;

    IF FOUND THEN
        IF v_existing_receipt.payload_hash = v_hash THEN
            RETURN jsonb_build_object(
                'result', 'DUPLICATE',
                'accepted_revision', v_existing_receipt.resulting_revision,
                'data', v_existing_receipt.result
            );
        ELSE
            RETURN jsonb_build_object('result', 'OPERATION_COLLISION');
        END IF;
    END IF;

    -- Lock profile row
    SELECT * INTO v_profile
    FROM public.profiles
    WHERE user_id = v_user_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('result', 'NOT_FOUND');
    END IF;

    -- Optimistic revision check
    IF v_profile.revision != p_expected_revision THEN
        v_result := jsonb_build_object(
            'result', 'CONFLICT',
            'current_revision', v_profile.revision,
            'current_profile', row_to_json(v_profile)
        );
        INSERT INTO private.profile_preference_receipts (user_id, operation_id, payload_hash, result, resulting_revision, created_at)
        VALUES (v_user_id, p_operation_id, v_hash, v_result, v_profile.revision, v_now);
        RETURN v_result;
    END IF;

    -- Extract values from payload
    v_month_start := COALESCE((p_payload->>'month_start')::smallint, v_profile.month_start);
    v_currency_code := COALESCE(p_payload->>'currency_code', v_profile.currency_code);
    v_hide_balances := COALESCE((p_payload->>'hide_balances')::boolean, v_profile.hide_balances);
    v_theme_mode := COALESCE(p_payload->>'theme_mode', v_profile.theme_mode);
    v_display_name := COALESCE(p_payload->>'display_name', v_profile.display_name);

    IF v_month_start < 1 OR v_month_start > 28 THEN
        RAISE EXCEPTION 'Invalid month_start %: must be between 1 and 28', v_month_start;
    END IF;

    v_new_revision := v_profile.revision + 1;

    UPDATE public.profiles
    SET display_name = v_display_name,
        currency_code = v_currency_code,
        month_start = v_month_start,
        hide_balances = v_hide_balances,
        theme_mode = v_theme_mode,
        revision = v_new_revision,
        updated_at = v_now
    WHERE user_id = v_user_id;

    v_result := jsonb_build_object(
        'result', 'APPLIED',
        'accepted_revision', v_new_revision,
        'updated_at', v_now
    );

    INSERT INTO private.profile_preference_receipts (user_id, operation_id, payload_hash, result, resulting_revision, created_at)
    VALUES (v_user_id, p_operation_id, v_hash, v_result, v_new_revision, v_now);

    RETURN v_result;
END;
$$;

-- SQL4-09: support the existing MAX(version) serialized allocator.

CREATE INDEX IF NOT EXISTS merchant_services_version_idx ON public.merchant_services(version);

-- CREATE OR REPLACE preserves owners and grants, including the dedicated profile executor.

-- Source: 20261101150000_recurrence_resume_lima_date.sql
CREATE OR REPLACE FUNCTION public.set_recurrence_status_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_uid uuid := auth.uid(); v_id uuid; v_op uuid; v_expected bigint;
  v_target text; v_confirmed date; v_first_future date; v_anchor date; v_first_future_anchor date;
  v_rec public.recurrences%ROWTYPE; v_receipt internal.command_receipts%ROWTYPE;
  v_canonical text; v_response jsonb; v_n integer; v_step integer; v_month_delta integer;
BEGIN
  IF v_uid IS NULL THEN RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','UNAUTHORIZED')); END IF;
  IF pg_catalog.jsonb_typeof(p_command)<>'object'
     OR NOT (p_command ?& ARRAY['command_type','contract_version','operation_id','request_hash','recurrence_id','expected_revision','payload'])
     OR p_command->>'command_type'<>'SET_RECURRENCE_STATUS'
     OR NOT public._rec_valid_integer(p_command->'contract_version',1,1)
     OR NOT public._rec_valid_integer(p_command->'expected_revision',1,9223372036854775807)
     OR pg_catalog.jsonb_typeof(p_command->'payload')<>'object'
     OR (p_command-ARRAY['command_type','contract_version','operation_id','request_hash','recurrence_id','expected_revision','payload'])<>'{}'::jsonb
     OR pg_catalog.jsonb_typeof(p_command->'payload'->'status') IS DISTINCT FROM 'string'
     OR p_command->'payload'->>'status' NOT IN ('ACTIVE','PAUSED','CANCELLED') THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND'));
  END IF;
  v_target:=p_command->'payload'->>'status';
  IF (v_target='ACTIVE' AND ((p_command->'payload')-ARRAY['status','confirmed_next_due_date']<>'{}'::jsonb OR NOT (p_command->'payload'?'confirmed_next_due_date')))
     OR (v_target<>'ACTIVE' AND (p_command->'payload')-ARRAY['status']<>'{}'::jsonb) THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_STATUS_PAYLOAD'));
  END IF;
  v_id:=(p_command->>'recurrence_id')::uuid; v_op:=(p_command->>'operation_id')::uuid;
  v_expected:=(p_command->>'expected_revision')::numeric::bigint;
  IF p_command->>'operation_id' !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
     OR p_command->>'recurrence_id' !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
     OR p_command->>'request_hash' !~ '^[0-9a-f]{64}$' THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND'));
  END IF;
  IF v_target='ACTIVE' THEN
    IF NOT public._rec_valid_date(p_command->'payload'->'confirmed_next_due_date') THEN
      RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_RESUME_DATE'));
    END IF;
    v_confirmed:=(p_command->'payload'->>'confirmed_next_due_date')::date;
  END IF;
  v_canonical:=public._rec_json_canonical(p_command-ARRAY['operation_id','request_hash']);
  IF pg_catalog.octet_length(v_canonical)>16384
     OR pg_catalog.encode(extensions.digest(v_canonical,'sha256'),'hex')<>p_command->>'request_hash' THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','HASH_MISMATCH'));
  END IF;
  PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_uid::text||':'||v_op::text,0));
  SELECT * INTO v_receipt FROM internal.command_receipts WHERE user_id=v_uid AND idempotency_key=v_op::text;
  IF FOUND THEN
    IF v_receipt.command_type='SET_RECURRENCE_STATUS' AND v_receipt.request_hash=p_command->>'request_hash' THEN RETURN v_receipt.response_payload; END IF;
    RETURN jsonb_build_object('status','CONFLICT','error',jsonb_build_object('code','OPERATION_ID_REUSED'));
  END IF;
  SELECT * INTO v_rec FROM public.recurrences WHERE id=v_id AND user_id=v_uid AND deleted_at IS NULL FOR UPDATE;
  IF NOT FOUND THEN RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','NOT_FOUND')); END IF;
  IF v_rec.revision<>v_expected THEN RETURN jsonb_build_object('status','CONFLICT','error',jsonb_build_object('code','REVISION_MISMATCH','revision',v_rec.revision)); END IF;
  IF v_target='PAUSED' AND v_rec.status<>'ACTIVE' OR v_target='ACTIVE' AND v_rec.status<>'PAUSED' OR v_target='CANCELLED' AND v_rec.status NOT IN ('ACTIVE','PAUSED') THEN
    RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_TRANSITION'));
  END IF;
  IF v_target='ACTIVE' THEN
    v_anchor:=v_rec.start_date;
    v_first_future_anchor:=(pg_catalog.now() AT TIME ZONE 'America/Lima')::date+1;
    IF v_rec.frequency IN ('WEEKLY','BIWEEKLY') OR (v_rec.frequency='CUSTOM' AND v_rec.custom_interval_unit='WEEK') THEN
      v_step:=CASE WHEN v_rec.frequency='WEEKLY' THEN 7 WHEN v_rec.frequency='BIWEEKLY' THEN 14 ELSE v_rec.interval_count::integer*7 END;
      v_n:=GREATEST(0,CEIL((v_first_future_anchor-v_anchor)::numeric/v_step)::integer);
      v_first_future:=v_anchor+(v_n*v_step);
      IF v_first_future<v_first_future_anchor THEN v_first_future:=v_first_future+v_step; END IF;
    ELSE
      v_step:=CASE WHEN v_rec.frequency='ANNUALLY' THEN 12 WHEN v_rec.frequency='CUSTOM' THEN v_rec.interval_count::integer ELSE 1 END;
      v_month_delta:=(pg_catalog.date_part('year',v_first_future_anchor)::integer-pg_catalog.date_part('year',v_anchor)::integer)*12
        +(pg_catalog.date_part('month',v_first_future_anchor)::integer-pg_catalog.date_part('month',v_anchor)::integer);
      v_n:=GREATEST(0,CEIL(v_month_delta::numeric/v_step)::integer);
      v_first_future:=(v_anchor+pg_catalog.make_interval(months=>v_n*v_step))::date;
      IF v_first_future<v_first_future_anchor THEN v_first_future:=(v_anchor+pg_catalog.make_interval(months=>(v_n+1)*v_step))::date; END IF;
    END IF;
    IF v_first_future IS NULL OR v_first_future>COALESCE(v_rec.end_date,'9999-12-31'::date) OR v_confirmed<>v_first_future THEN
      RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','RESUME_DATE_MISMATCH'));
    END IF;
    UPDATE public.recurrences SET status='ACTIVE',next_due_date=v_confirmed,revision=revision+1,updated_at=pg_catalog.clock_timestamp() WHERE id=v_id RETURNING * INTO v_rec;
  ELSE
    UPDATE public.recurrences SET status=v_target,revision=revision+1,updated_at=pg_catalog.clock_timestamp() WHERE id=v_id RETURNING * INTO v_rec;
  END IF;
  v_response:=jsonb_build_object('status','APPLIED','recurrence_id',v_id,'state',v_rec.status,'revision',v_rec.revision,'updated_at',v_rec.updated_at);
  INSERT INTO internal.sync_changes(user_id,entity_type,entity_id,revision,operation,payload) VALUES(v_uid,'RECURRENCE',v_id,v_rec.revision,'UPSERT',v_response||jsonb_build_object('rule',to_jsonb(v_rec)));
  INSERT INTO internal.command_receipts(user_id,idempotency_key,command_type,request_hash,response_payload,status) VALUES(v_uid,v_op::text,'SET_RECURRENCE_STATUS',p_command->>'request_hash',v_response,'APPLIED');
  RETURN v_response;
EXCEPTION WHEN OTHERS THEN RETURN jsonb_build_object('status','REJECTED','error',jsonb_build_object('code','INVALID_COMMAND'));
END;
$$;

-- Source: 20261008161540_ep_cco_s5_alias_commands.sql (SQL3-06: receipt before mutable references).
CREATE OR REPLACE FUNCTION public.upsert_merchant_alias_rule_v1(p_payload jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
    v_user_id uuid := auth.uid();
    v_operation_id uuid := (p_payload->>'operation_id')::uuid;
    v_rule_id uuid := (p_payload->>'rule_id')::uuid;
    v_merchant_id uuid := (p_payload->>'merchant_id')::uuid;
    v_input_pattern text := p_payload->>'normalized_pattern';
    v_pattern text;
    v_expected_revision bigint := NULLIF(p_payload->>'expected_revision', '')::bigint;
    v_current public.merchant_alias_rules%ROWTYPE;
    v_receipt internal.command_receipts%ROWTYPE;
    v_request_hash text := pg_catalog.md5((p_payload - 'payload_hash')::text);
    v_revision bigint;
    v_response jsonb;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED' USING ERRCODE = '28000';
    END IF;
    IF v_operation_id IS NULL OR v_rule_id IS NULL OR v_merchant_id IS NULL
       OR v_input_pattern IS NULL OR pg_catalog.length(pg_catalog.btrim(v_input_pattern)) = 0
       OR pg_catalog.length(v_input_pattern) > 160 THEN
        RAISE EXCEPTION 'INVALID_ALIAS_RULE' USING ERRCODE = 'P0001';
    END IF;

    v_pattern := pg_catalog.lower(extensions.unaccent(
        pg_catalog.regexp_replace(pg_catalog.btrim(v_input_pattern), '[[:space:]]+', ' ', 'g')
    ));
    IF v_pattern <> v_input_pattern THEN
        RAISE EXCEPTION 'ALIAS_PATTERN_NOT_NORMALIZED' USING ERRCODE = 'P0001';
    END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(
        'merchant-alias:' || v_user_id::text || ':' || v_operation_id::text, 0
    ));
    SELECT * INTO v_receipt FROM internal.command_receipts AS r
    WHERE r.user_id = v_user_id AND r.idempotency_key = v_operation_id::text;
    IF FOUND THEN
        IF v_receipt.command_type <> 'UPSERT_MERCHANT_ALIAS_RULE'
           OR v_receipt.request_hash <> v_request_hash THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED' USING ERRCODE = '23505';
        END IF;
        RETURN pg_catalog.jsonb_set(v_receipt.response_payload, '{status}', '"DUPLICATE"'::jsonb, true);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM public.merchant_services AS m
        WHERE m.id = v_merchant_id AND m.is_system = true AND m.user_id IS NULL
          AND m.is_active = true AND m.deleted_at IS NULL
    ) THEN
        RAISE EXCEPTION 'MERCHANT_NOT_ACTIVE' USING ERRCODE = 'P0001';
    END IF;

    SELECT * INTO v_current FROM public.merchant_alias_rules AS r
    WHERE r.id = v_rule_id AND r.user_id = v_user_id FOR UPDATE;
    IF FOUND THEN
        IF v_expected_revision IS NULL OR v_current.revision <> v_expected_revision THEN
            RETURN pg_catalog.jsonb_build_object(
                'success', false, 'status', 'CONFLICT', 'rule_id', v_rule_id,
                'current_revision', v_current.revision
            );
        END IF;
        v_revision := v_current.revision + 1;
        UPDATE public.merchant_alias_rules
        SET normalized_pattern = v_pattern, merchant_id = v_merchant_id,
            revision = v_revision, deleted_at = NULL, updated_at = pg_catalog.now()
        WHERE id = v_rule_id AND user_id = v_user_id;
    ELSE
        IF v_expected_revision IS NOT NULL THEN
            RETURN pg_catalog.jsonb_build_object('success', false, 'status', 'REJECTED', 'code', 'RULE_NOT_FOUND');
        END IF;
        IF NOT EXISTS (
            SELECT 1
            FROM public.billing_purchases AS p
            JOIN public.billing_products AS product ON product.id = p.product_id
            WHERE p.user_id = v_user_id
              AND (
                (p.purchase_state = 'PURCHASED' AND product.plan_type = 'PRO_LIFETIME'
                    AND p.entitlement_state = 'ACTIVE' AND p.expires_at IS NULL)
                OR (product.plan_type IN ('PRO_MONTHLY', 'PRO_ANNUAL')
                    AND p.expires_at > pg_catalog.now()
                    AND ((p.purchase_state = 'PURCHASED' AND p.entitlement_state IN ('ACTIVE', 'IN_GRACE_PERIOD'))
                         OR (p.purchase_state = 'CANCELLED' AND p.entitlement_state = 'CANCELED_ACTIVE')))
              )
        ) THEN
            RAISE EXCEPTION 'PREMIUM_REQUIRED' USING ERRCODE = 'P0001';
        END IF;
        v_revision := 1;
        INSERT INTO public.merchant_alias_rules (
            id, user_id, normalized_pattern, merchant_id, revision
        ) VALUES (v_rule_id, v_user_id, v_pattern, v_merchant_id, v_revision);
    END IF;

    v_response := pg_catalog.jsonb_build_object(
        'success', true, 'status', 'APPLIED', 'rule_id', v_rule_id, 'revision', v_revision
    );
    INSERT INTO internal.command_receipts (
        user_id, idempotency_key, command_type, request_hash, response_payload, status
    ) VALUES (
        v_user_id, v_operation_id::text, 'UPSERT_MERCHANT_ALIAS_RULE', v_request_hash, v_response, 'APPLIED'
    );
    INSERT INTO internal.sync_changes (user_id, entity_type, entity_id, revision, operation, payload)
    VALUES (
        v_user_id, 'MERCHANT_ALIAS_RULE', v_rule_id, v_revision, 'UPSERT',
        pg_catalog.jsonb_build_object(
            'id', v_rule_id, 'normalized_pattern', v_pattern, 'merchant_id', v_merchant_id,
            'revision', v_revision, 'deleted_at', NULL
        )
    );
    RETURN v_response;
END;
$function$;

-- Source: 20261008161631_ep_cco_s5_preference_commands.sql (SQL3-06: receipt before mutable references).
CREATE OR REPLACE FUNCTION public.upsert_merchant_category_preference_v1(p_payload jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
    v_user_id uuid := auth.uid();
    v_operation_id uuid := (p_payload->>'operation_id')::uuid;
    v_preference_id uuid := (p_payload->>'preference_id')::uuid;
    v_merchant_id uuid := (p_payload->>'merchant_id')::uuid;
    v_category_id uuid := (p_payload->>'category_id')::uuid;
    v_expected_revision bigint := NULLIF(p_payload->>'expected_revision', '')::bigint;
    v_category public.categories%ROWTYPE;
    v_root public.categories%ROWTYPE;
    v_current public.merchant_category_preferences%ROWTYPE;
    v_receipt internal.command_receipts%ROWTYPE;
    v_request_hash text := pg_catalog.md5((p_payload - 'payload_hash')::text);
    v_revision bigint;
    v_response jsonb;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED' USING ERRCODE = '28000';
    END IF;
    IF v_operation_id IS NULL OR v_preference_id IS NULL OR v_merchant_id IS NULL OR v_category_id IS NULL THEN
        RAISE EXCEPTION 'INVALID_CATEGORY_PREFERENCE' USING ERRCODE = 'P0001';
    END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(
        'merchant-preference:' || v_user_id::text || ':' || v_operation_id::text, 0
    ));
    SELECT * INTO v_receipt FROM internal.command_receipts AS r
    WHERE r.user_id = v_user_id AND r.idempotency_key = v_operation_id::text;
    IF FOUND THEN
        IF v_receipt.command_type <> 'UPSERT_MERCHANT_CATEGORY_PREFERENCE'
           OR v_receipt.request_hash <> v_request_hash THEN
            RAISE EXCEPTION 'IDEMPOTENCY_KEY_REUSED' USING ERRCODE = '23505';
        END IF;
        RETURN pg_catalog.jsonb_set(v_receipt.response_payload, '{status}', '"DUPLICATE"'::jsonb, true);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM public.merchant_services AS m
        WHERE m.id = v_merchant_id AND m.is_system = true AND m.user_id IS NULL
          AND m.is_active = true AND m.deleted_at IS NULL
    ) THEN
        RAISE EXCEPTION 'MERCHANT_NOT_ACTIVE' USING ERRCODE = 'P0001';
    END IF;

    SELECT * INTO v_category FROM public.categories AS c
    WHERE c.id = v_category_id
      AND (c.user_id = v_user_id OR (c.user_id IS NULL AND c.origin = 'SYSTEM'));
    IF NOT FOUND OR NOT v_category.is_active OR v_category.deleted_at IS NOT NULL THEN
        RAISE EXCEPTION 'CATEGORY_NOT_ACTIVE_OR_OWNED' USING ERRCODE = 'P0001';
    END IF;
    IF v_category.parent_id IS NULL THEN
        v_root := v_category;
    ELSE
        SELECT * INTO v_root FROM public.categories AS root
        WHERE root.id = v_category.parent_id
          AND (root.user_id = v_user_id OR (root.user_id IS NULL AND root.origin = 'SYSTEM'));
        IF NOT FOUND OR NOT v_root.is_active OR v_root.deleted_at IS NOT NULL OR v_root.parent_id IS NOT NULL THEN
            RAISE EXCEPTION 'CATEGORY_ROOT_NOT_ACTIVE_OR_OWNED' USING ERRCODE = 'P0001';
        END IF;
    END IF;
    IF private.is_plan_quota_resource_locked(v_user_id, 'CUSTOM_CATEGORIES', v_root.id) THEN
        RAISE EXCEPTION 'CATEGORY_PLAN_LOCKED' USING ERRCODE = 'P0001';
    END IF;

    SELECT * INTO v_current FROM public.merchant_category_preferences AS p
    WHERE p.user_id = v_user_id AND p.merchant_id = v_merchant_id FOR UPDATE;
    IF FOUND THEN
        IF v_current.id <> v_preference_id OR v_expected_revision IS NULL
           OR v_current.revision <> v_expected_revision THEN
            RETURN pg_catalog.jsonb_build_object(
                'success', false, 'status', 'CONFLICT', 'preference_id', v_current.id,
                'current_revision', v_current.revision
            );
        END IF;
        v_revision := v_current.revision + 1;
        UPDATE public.merchant_category_preferences
        SET category_id = v_category_id, revision = v_revision,
            deleted_at = NULL, updated_at = pg_catalog.now()
        WHERE id = v_current.id AND user_id = v_user_id;
    ELSE
        IF v_expected_revision IS NOT NULL THEN
            RETURN pg_catalog.jsonb_build_object('success', false, 'status', 'REJECTED', 'code', 'PREFERENCE_NOT_FOUND');
        END IF;
        v_revision := 1;
        INSERT INTO public.merchant_category_preferences (
            id, user_id, merchant_id, category_id, revision
        ) VALUES (v_preference_id, v_user_id, v_merchant_id, v_category_id, v_revision);
    END IF;

    v_response := pg_catalog.jsonb_build_object(
        'success', true, 'status', 'APPLIED', 'preference_id', v_preference_id,
        'merchant_id', v_merchant_id, 'category_id', v_category_id, 'revision', v_revision
    );
    INSERT INTO internal.command_receipts (
        user_id, idempotency_key, command_type, request_hash, response_payload, status
    ) VALUES (
        v_user_id, v_operation_id::text, 'UPSERT_MERCHANT_CATEGORY_PREFERENCE', v_request_hash, v_response, 'APPLIED'
    );
    INSERT INTO internal.sync_changes (user_id, entity_type, entity_id, revision, operation, payload)
    VALUES (
        v_user_id, 'MERCHANT_CATEGORY_PREFERENCE', v_preference_id, v_revision, 'UPSERT',
        pg_catalog.jsonb_build_object(
            'id', v_preference_id, 'merchant_id', v_merchant_id, 'category_id', v_category_id,
            'revision', v_revision, 'deleted_at', NULL
        )
    );
    RETURN v_response;
END;
$function$;

COMMIT;
