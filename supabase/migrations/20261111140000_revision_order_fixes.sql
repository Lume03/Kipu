-- L4: revision order and idempotency fixes. Local migration only; not applied.

-- SQL1-04/05/06 and decision 12. Based on 20260922000000_ep_cco_baseline,
-- retaining the later authenticated-only grants; search_path is now explicitly empty.
CREATE OR REPLACE FUNCTION public.update_category_presentation_v1(p_payload jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
    u uuid := auth.uid();
    op uuid := (p_payload->>'operation_id')::uuid;
    category uuid := (p_payload->>'category_id')::uuid;
    request_hash text;
    receipt internal.command_receipts%ROWTYPE;
    presentation public.category_presentations%ROWTYPE;
    response jsonb;
BEGIN
    IF u IS NULL THEN RAISE EXCEPTION 'Not authenticated' USING ERRCODE='28000'; END IF;
    IF op IS NULL OR category IS NULL THEN RAISE EXCEPTION 'Invalid presentation request' USING ERRCODE='22023'; END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(u::text || ':' || op::text,0));
    request_hash := pg_catalog.encode(extensions.digest(pg_catalog.convert_to((p_payload - 'payload_hash')::text,'UTF8'),'sha256'),'hex');
    SELECT * INTO receipt FROM internal.command_receipts WHERE user_id=u AND idempotency_key=op::text;
    IF FOUND THEN
        IF receipt.command_type <> 'UPDATE_CATEGORY_PRESENTATION' OR receipt.request_hash <> request_hash THEN
            RAISE EXCEPTION 'OPERATION_COLLISION: differing presentation request';
        END IF;
        RETURN receipt.response_payload;
    END IF;
    -- Lock the stable parent too: FOR UPDATE on an absent presentation cannot serialize first inserts.
    PERFORM 1 FROM public.categories WHERE id=category AND (user_id IS NULL OR user_id=u) FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Category not found' USING ERRCODE='P0002'; END IF;
    SELECT * INTO presentation FROM public.category_presentations
        WHERE user_id=u AND category_id=category FOR UPDATE;
    INSERT INTO public.category_presentations(category_id,user_id,name,icon,color,remote_revision)
    VALUES(category,u,pg_catalog.btrim(p_payload->>'name'),p_payload->>'icon',p_payload->>'color',1)
    ON CONFLICT (category_id,user_id) DO UPDATE SET
        name=EXCLUDED.name,icon=EXCLUDED.icon,color=EXCLUDED.color,
        remote_revision=public.category_presentations.remote_revision+1,updated_at=pg_catalog.now()
    RETURNING * INTO presentation;
    response := pg_catalog.jsonb_build_object('success',true,'status','APPLIED','revision',presentation.remote_revision);
    INSERT INTO internal.command_receipts(user_id,idempotency_key,command_type,request_hash,response_payload,status)
    VALUES(u,op::text,'UPDATE_CATEGORY_PRESENTATION',request_hash,response,'APPLIED');
    RETURN response;
END;
$$;

-- Compatibility RPC, based on 20260923010000_fix_category_conflict_resolution.
CREATE OR REPLACE FUNCTION public.resolve_category_conflict_v1(p_payload jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
    u uuid := auth.uid();
    conflict public.category_conflicts%ROWTYPE;
    chosen jsonb;
    response jsonb;
BEGIN
    IF u IS NULL THEN RAISE EXCEPTION 'Not authenticated' USING ERRCODE='28000'; END IF;
    IF p_payload->>'chosen_version' IS NULL OR p_payload->>'chosen_version' NOT IN ('LOCAL','REMOTE')
        OR p_payload->>'operation_id' IS NULL THEN
        RAISE EXCEPTION 'Invalid conflict resolution request' USING ERRCODE='22023';
    END IF;
    SELECT * INTO conflict FROM public.category_conflicts
        WHERE id=(p_payload->>'conflict_id')::uuid AND user_id=u FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Conflict not found' USING ERRCODE='P0002'; END IF;
    chosen := CASE WHEN p_payload->>'chosen_version'='LOCAL' THEN conflict.local_version::jsonb ELSE conflict.remote_version::jsonb END;
    response := public.update_category_presentation_v1(pg_catalog.jsonb_build_object(
        'operation_id',p_payload->>'operation_id','category_id',conflict.category_id,
        'name',chosen->>'name','icon',chosen->>'icon','color',chosen->>'color'));
    UPDATE public.category_conflicts SET status='RESOLVED',
        resolution_operation_id=(p_payload->>'operation_id')::uuid,updated_at=pg_catalog.now()
        WHERE id=conflict.id AND user_id=u;
    RETURN response || pg_catalog.jsonb_build_object('status','RESOLVED');
END;
$$;

-- Resolve legacy OPEN presentation conflicts in chronological order. A newer current
-- presentation wins over an older proposal; otherwise the latest proposal is applied.
DO $$
DECLARE c record; chosen jsonb;
BEGIN
    FOR c IN SELECT * FROM public.category_conflicts WHERE status='OPEN' AND conflict_type='PRESENTATION'
        ORDER BY created_at,id FOR UPDATE LOOP
        chosen := c.local_version::jsonb;
        PERFORM 1 FROM public.categories WHERE id=c.category_id FOR UPDATE;
        PERFORM 1 FROM public.category_presentations WHERE category_id=c.category_id AND user_id=c.user_id FOR UPDATE;
        INSERT INTO public.category_presentations(category_id,user_id,name,icon,color,remote_revision,updated_at)
        VALUES(c.category_id,c.user_id,chosen->>'name',chosen->>'icon',chosen->>'color',1,c.created_at)
        ON CONFLICT(category_id,user_id) DO UPDATE SET
            name=EXCLUDED.name,icon=EXCLUDED.icon,color=EXCLUDED.color,
            remote_revision=public.category_presentations.remote_revision+1,updated_at=EXCLUDED.updated_at
        WHERE public.category_presentations.updated_at <= c.created_at;
        UPDATE public.category_conflicts SET status='RESOLVED',updated_at=pg_catalog.now() WHERE id=c.id;
    END LOOP;
END;
$$;

-- SQL2-01; final body from 20260928110000_credit_card_pull_projection.sql.
CREATE OR REPLACE FUNCTION public.delete_unused_card_v1(p_command jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_card_id uuid;
BEGIN
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED: Authentication required'; END IF;
    v_card_id := (p_command->>'card_id')::uuid;
    IF v_card_id IS NULL THEN RAISE EXCEPTION 'INVALID_REQUEST: card_id is required'; END IF;

    -- Same row lock as register_transaction_v1_pre_liability_s3, before inspecting history.
    PERFORM 1 FROM public.cards WHERE id=v_card_id AND user_id=v_user_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'CARD_NOT_FOUND'; END IF;

    IF EXISTS (
        SELECT 1 FROM public.financial_movements
        WHERE user_id = v_user_id AND card_id = v_card_id
    ) OR EXISTS (
        SELECT 1 FROM public.transactions
        WHERE user_id = v_user_id AND card_id = v_card_id
    ) THEN
        RAISE EXCEPTION 'CARD_HAS_HISTORY: Cannot physically delete card with transaction history. Archive instead.';
    END IF;

    DELETE FROM public.cards WHERE id = v_card_id AND user_id = v_user_id;
    INSERT INTO internal.sync_changes(user_id, entity_type, entity_id, revision, operation, payload)
    VALUES (v_user_id, 'CARD', v_card_id, 0, 'DELETE', jsonb_build_object('id', v_card_id));

    RETURN jsonb_build_object('status', 'APPLIED', 'card_id', v_card_id, 'deleted', true);
END;
$$;

-- SQL4-04; final body from 20261101140000_credit_card_principal_quota_archive.sql.
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

    -- Reactivating consumes quota
    IF NOT v_is_archived THEN
        SELECT (
            (SELECT COUNT(*) FROM public.accounts WHERE user_id = v_user_id AND is_archived = false AND account_type <> 'CASH') +
            (SELECT COUNT(*) FROM public.cards WHERE user_id = v_user_id AND is_archived = false)
        ) INTO v_computable_count;

        IF v_computable_count >= 4 THEN
            RAISE EXCEPTION 'FREE_LIMIT_REACHED: Reactivation would exceed Free tier limit of 4 active instruments';
        END IF;
    END IF;

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

REVOKE ALL ON FUNCTION public.update_category_presentation_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.update_category_presentation_v1(jsonb) TO authenticated;
REVOKE ALL ON FUNCTION public.resolve_category_conflict_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.resolve_category_conflict_v1(jsonb) TO authenticated;
REVOKE ALL ON FUNCTION public.delete_unused_card_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.delete_unused_card_v1(jsonb) TO authenticated;
REVOKE ALL ON FUNCTION public.set_instrument_archived_v1(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.set_instrument_archived_v1(jsonb) TO authenticated;
