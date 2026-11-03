CREATE TABLE internal.merchant_search_inflight(query text PRIMARY KEY,expires_at timestamptz NOT NULL);
CREATE OR REPLACE FUNCTION public.merchant_search_cache_v1(p_user uuid,p_query text,p_candidates jsonb DEFAULT NULL)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v jsonb; claimed text;
BEGIN
 IF p_candidates IS NULL THEN
  PERFORM internal.merchant_discovery_quota(p_user,'SEARCH');
 ELSE
  IF jsonb_typeof(p_candidates) <> 'array' OR jsonb_array_length(p_candidates)>5 THEN RAISE EXCEPTION 'INVALID_CANDIDATES'; END IF;
  INSERT INTO internal.merchant_search_cache(query,candidates) VALUES(p_query,p_candidates)
  ON CONFLICT(query) DO UPDATE SET candidates=excluded.candidates, expires_at=now()+interval '30 days';
  DELETE FROM internal.merchant_search_inflight WHERE query=p_query;
 END IF;
 SELECT candidates INTO v FROM internal.merchant_search_cache WHERE query=p_query AND expires_at>now();
 IF v IS NULL AND p_candidates IS NULL THEN
  INSERT INTO internal.merchant_search_inflight(query,expires_at) VALUES(p_query,now()+interval '30 seconds')
  ON CONFLICT(query) DO UPDATE SET expires_at=excluded.expires_at
  WHERE internal.merchant_search_inflight.expires_at<=now() RETURNING query INTO claimed;
  IF claimed IS NULL THEN RAISE EXCEPTION 'SEARCH_BUSY'; END IF;
 END IF;
 IF v IS NOT NULL THEN
 INSERT INTO internal.merchant_search_access(user_id,query) VALUES(p_user,p_query)
 ON CONFLICT(user_id,query) DO UPDATE SET expires_at=now()+interval '1 hour';
 END IF;
 RETURN v;
END $$;