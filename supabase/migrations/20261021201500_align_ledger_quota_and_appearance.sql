-- Use the same bank/debit/wallet groups when enforcing ledger writes.
CREATE OR REPLACE FUNCTION internal.enforce_ledger_entry_plan_quota_s4()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
BEGIN
 IF current_setting('kipu.movement_revision_apply',true) IS DISTINCT FROM 'on'
    AND private.is_plan_quota_resource_locked(NEW.user_id,'INSTRUMENTS',NEW.account_id) THEN
   RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='ledger instrument is locked by the Free plan selection';
 END IF;
 RETURN NEW;
END;
$$;

-- Match the mobile appearance contract (alias), preserving owner scope,
-- replay identity and revision checks. Existing row triggers emit full snapshots.
CREATE OR REPLACE FUNCTION public.update_instrument_appearance_v1(p_command jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
 owner_id uuid := auth.uid();
 target_id uuid := (p_command->>'instrument_id')::uuid;
 kind text := p_command->>'instrument_type';
 label text := btrim(COALESCE(p_command->>'alias',p_command->>'name'));
 operation_id text := p_command->>'operation_id';
 command_hash text := encode(extensions.digest(p_command::text,'sha256'),'hex');
 prior record;
 new_revision bigint;
 response jsonb;
BEGIN
 IF owner_id IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED'; END IF;
 IF operation_id IS NULL OR label IS NULL OR length(label) NOT BETWEEN 1 AND 120 THEN
   RAISE EXCEPTION 'INVALID_APPEARANCE';
 END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended(owner_id::text||':'||operation_id,0));
 SELECT request_hash,response_payload INTO prior FROM internal.command_receipts
 WHERE user_id=owner_id AND idempotency_key=operation_id;
 IF FOUND THEN
   IF prior.request_hash<>command_hash THEN RAISE EXCEPTION 'IDEMPOTENCY_CONFLICT'; END IF;
   RETURN jsonb_set(prior.response_payload,'{status}','"DUPLICATE"'::jsonb);
 END IF;
 IF kind='ACCOUNT' THEN
   UPDATE public.accounts SET name=label,color=p_command->>'color',icon=p_command->>'icon',
     preset_id=COALESCE(p_command->>'preset_id',preset_id),revision=revision+1,updated_at=now()
   WHERE id=target_id AND user_id=owner_id
     AND revision=COALESCE((p_command->>'expected_revision')::bigint,revision)
   RETURNING revision INTO new_revision;
 ELSIF kind='CARD' THEN
   UPDATE public.cards SET alias=label,color=p_command->>'color',icon=p_command->>'icon',
     preset_id=COALESCE(p_command->>'preset_id',preset_id),revision=revision+1,updated_at=now()
   WHERE id=target_id AND user_id=owner_id
     AND revision=COALESCE((p_command->>'expected_revision')::bigint,revision)
   RETURNING revision INTO new_revision;
 ELSE RAISE EXCEPTION 'INVALID_INSTRUMENT_TYPE';
 END IF;
 IF new_revision IS NULL THEN RAISE EXCEPTION 'NOT_FOUND_OR_REVISION_CONFLICT'; END IF;
 response:=jsonb_build_object('status','APPLIED','instrument_id',target_id,
   'account_id',CASE WHEN kind='ACCOUNT' THEN target_id END,
   'card_id',CASE WHEN kind='CARD' THEN target_id END,'revision',new_revision);
 INSERT INTO internal.command_receipts(id,user_id,idempotency_key,command_type,request_hash,response_payload,status)
 VALUES(extensions.gen_random_uuid(),owner_id,operation_id,'UPDATE_APPEARANCE',command_hash,response,'APPLIED');
 RETURN response;
END;
$$;
REVOKE ALL ON FUNCTION public.update_instrument_appearance_v1(jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.update_instrument_appearance_v1(jsonb) TO authenticated;
