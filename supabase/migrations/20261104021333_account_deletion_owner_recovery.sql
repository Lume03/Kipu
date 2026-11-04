BEGIN;
-- Aliases authorize reconciliation only; one canonical deletion receipt remains.
CREATE TABLE private.account_deletion_aliases (
  request_id uuid PRIMARY KEY,
  secret_hash text NOT NULL CHECK (secret_hash ~ '^[a-f0-9]{64}$'),
  receipt_id uuid NOT NULL REFERENCES private.account_deletion_requests(request_id)
);
CREATE INDEX account_deletion_alias_receipt ON private.account_deletion_aliases(receipt_id);
ALTER TABLE private.account_deletion_aliases ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON private.account_deletion_aliases FROM PUBLIC,anon,authenticated,service_role;

CREATE OR REPLACE FUNCTION public.account_deletion_status(p_request_id uuid,p_secret_hash text)
RETURNS jsonb LANGUAGE sql SECURITY DEFINER SET search_path='' AS $$
 SELECT jsonb_build_object('state',r.state,'user_id',r.user_id)
 FROM private.account_deletion_requests r
 WHERE (r.request_id=p_request_id AND r.secret_hash=p_secret_hash)
 OR EXISTS(SELECT 1 FROM private.account_deletion_aliases a
   WHERE a.request_id=p_request_id AND a.secret_hash=p_secret_hash AND a.receipt_id=r.request_id);
$$;

-- Edge calls only after GET /user and recent password AMR validation for new capabilities.
CREATE FUNCTION public.prepare_account_deletion(p_user_id uuid,p_request_id uuid,p_secret_hash text)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE r private.account_deletion_requests%ROWTYPE; a private.account_deletion_aliases%ROWTYPE;
BEGIN
 IF p_user_id IS NULL OR p_request_id IS NULL OR p_secret_hash IS NULL OR p_secret_hash !~ '^[a-f0-9]{64}$' THEN
   RAISE EXCEPTION 'Invalid deletion request';
 END IF;
 PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(p_user_id::text,6));
 -- Reject reused request identifiers before considering recovery by owner.
 SELECT * INTO a FROM private.account_deletion_aliases WHERE request_id=p_request_id;
 IF FOUND THEN
   IF a.secret_hash<>p_secret_hash THEN RAISE EXCEPTION 'Request mismatch'; END IF;
   SELECT * INTO r FROM private.account_deletion_requests WHERE request_id=a.receipt_id FOR UPDATE;
 ELSE
   SELECT * INTO r FROM private.account_deletion_requests WHERE request_id=p_request_id FOR UPDATE;
   IF FOUND AND r.secret_hash<>p_secret_hash THEN RAISE EXCEPTION 'Request mismatch'; END IF;
 END IF;
 IF r.request_id IS NOT NULL THEN
   IF r.user_id IS NOT NULL AND r.user_id<>p_user_id THEN RAISE EXCEPTION 'Request mismatch'; END IF;
 ELSE
   SELECT * INTO r FROM private.account_deletion_requests WHERE user_id=p_user_id FOR UPDATE;
   IF NOT FOUND THEN
     PERFORM public.purge_account_data(p_user_id,p_request_id,p_secret_hash);
     SELECT * INTO STRICT r FROM private.account_deletion_requests WHERE request_id=p_request_id FOR UPDATE;
   ELSE
     IF r.state<>'data_deleted' THEN RAISE EXCEPTION 'Deletion not ready'; END IF;
     INSERT INTO private.account_deletion_aliases VALUES(p_request_id,p_secret_hash,r.request_id);
   END IF;
 END IF;
 RETURN jsonb_build_object('state',r.state,'request_id',r.request_id,'secret_hash',r.secret_hash);
END $$;
REVOKE ALL ON FUNCTION public.prepare_account_deletion(uuid,uuid,text) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.prepare_account_deletion(uuid,uuid,text) TO service_role;

-- A verified alias may finalize the same receipt after an Auth timeout.
CREATE OR REPLACE FUNCTION public.complete_account_deletion(p_request_id uuid,p_secret_hash text)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE r private.account_deletion_requests%ROWTYPE;
BEGIN
 SELECT * INTO r FROM private.account_deletion_requests d
 WHERE (d.request_id=p_request_id AND d.secret_hash=p_secret_hash)
 OR EXISTS(SELECT 1 FROM private.account_deletion_aliases a WHERE a.request_id=p_request_id
   AND a.secret_hash=p_secret_hash AND a.receipt_id=d.request_id) FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Unknown request'; END IF;
 IF r.state='completed' THEN RETURN; END IF;
 IF r.state<>'data_deleted' OR EXISTS(SELECT 1 FROM auth.users WHERE id=r.user_id) THEN
   RAISE EXCEPTION 'Auth deletion not completed';
 END IF;
 UPDATE private.account_deletion_requests SET user_id=NULL,state='completed',completed_at=clock_timestamp() WHERE request_id=r.request_id;
END $$;
COMMIT;
