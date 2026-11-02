-- Separate RPC prevents older servers from silently ignoring the identity update.
CREATE OR REPLACE FUNCTION public.update_debit_card_identity_v1(p_command jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
 owner_id uuid := auth.uid();
 target_id uuid := (p_command->>'card_id')::uuid;
 operation_id text := NULLIF(p_command->>'operation_id', '');
 digits text := p_command->>'last_four_digits';
 expected bigint := (p_command->>'expected_revision')::bigint;
 command_hash text := encode(extensions.digest(p_command::text,'sha256'),'hex');
 prior record;
 new_revision bigint;
 response jsonb;
BEGIN
 IF owner_id IS NULL THEN RAISE EXCEPTION 'UNAUTHORIZED'; END IF;
 IF operation_id IS NULL OR target_id IS NULL OR expected IS NULL OR expected < 1
    OR digits IS NULL OR digits !~ '^[0-9]{4}$' THEN
   RAISE EXCEPTION 'INVALID_DEBIT_CARD_IDENTITY';
 END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended(owner_id::text||':'||operation_id,0));
 SELECT request_hash,response_payload INTO prior FROM internal.command_receipts
 WHERE user_id=owner_id AND idempotency_key=operation_id;
 IF FOUND THEN
   IF prior.request_hash<>command_hash THEN RAISE EXCEPTION 'IDEMPOTENCY_CONFLICT'; END IF;
   RETURN jsonb_set(prior.response_payload,'{status}','"DUPLICATE"'::jsonb);
 END IF;
 UPDATE public.cards SET last4=digits,revision=revision+1,updated_at=now()
 WHERE id=target_id AND user_id=owner_id AND NOT is_credit AND NOT is_archived AND revision=expected
 RETURNING revision INTO new_revision;
 IF new_revision IS NULL THEN RAISE EXCEPTION 'NOT_FOUND_OR_REVISION_CONFLICT'; END IF;
 response:=jsonb_build_object('status','APPLIED','card_id',target_id,'revision',new_revision);
 INSERT INTO internal.command_receipts(id,user_id,idempotency_key,command_type,request_hash,response_payload,status)
 VALUES(extensions.gen_random_uuid(),owner_id,operation_id,'UPDATE_DEBIT_CARD_IDENTITY',command_hash,response,'APPLIED');
 RETURN response;
END;
$$;
REVOKE ALL ON FUNCTION public.update_debit_card_identity_v1(jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.update_debit_card_identity_v1(jsonb) TO authenticated;
