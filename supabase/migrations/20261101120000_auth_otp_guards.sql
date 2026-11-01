CREATE TABLE private.auth_otp_guards (
  email_hash bytea PRIMARY KEY,
  failures integer NOT NULL DEFAULT 0 CHECK (failures BETWEEN 0 AND 5),
  blocked_until timestamptz,
  last_sent_at timestamptz,
  resend_times timestamptz[] NOT NULL DEFAULT '{}',
  attempt_id uuid,
  attempt_until timestamptz
);
ALTER TABLE private.auth_otp_guards ENABLE ROW LEVEL SECURITY;
ALTER TABLE private.auth_otp_guards FORCE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE private.auth_otp_guards FROM PUBLIC, anon, authenticated;

-- A short lease serializes provider calls across Edge isolates. No transaction
-- or row lock is held during HTTP; stale completions cannot change newer state.
CREATE FUNCTION public.auth_otp_status(p_email text, p_attempt_id uuid) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE r private.auth_otp_guards%ROWTYPE; n timestamptz; h bytea := extensions.digest(pg_catalog.convert_to(pg_catalog.lower(pg_catalog.btrim(p_email)), 'UTF8'), 'sha256');
BEGIN
  IF p_attempt_id IS NULL THEN RAISE EXCEPTION 'Attempt ID required'; END IF;
  INSERT INTO private.auth_otp_guards(email_hash) VALUES (h) ON CONFLICT DO NOTHING;
  SELECT * INTO r FROM private.auth_otp_guards WHERE email_hash = h FOR UPDATE;
  n := pg_catalog.clock_timestamp();
  IF r.blocked_until > n THEN
    RETURN pg_catalog.jsonb_build_object('locked', true, 'busy', false, 'retry_after_seconds', GREATEST(1, pg_catalog.ceil(EXTRACT(epoch FROM r.blocked_until - n))::integer), 'failures', r.failures, 'last_sent_at', r.last_sent_at);
  END IF;
  IF r.attempt_until > n THEN
    RETURN pg_catalog.jsonb_build_object('locked', false, 'busy', true, 'retry_after_seconds', 0, 'failures', r.failures, 'last_sent_at', r.last_sent_at);
  END IF;
  IF r.blocked_until IS NOT NULL THEN r.failures := 0; END IF;
  UPDATE private.auth_otp_guards SET failures = r.failures, blocked_until = NULL, attempt_id = p_attempt_id, attempt_until = n + interval '30 seconds' WHERE email_hash = h;
  RETURN pg_catalog.jsonb_build_object('locked', false, 'busy', false, 'retry_after_seconds', 0, 'failures', r.failures, 'last_sent_at', r.last_sent_at);
END $$;

CREATE FUNCTION public.auth_otp_register_failure(p_email text, p_attempt_id uuid) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE r private.auth_otp_guards%ROWTYPE; n timestamptz; h bytea := extensions.digest(pg_catalog.convert_to(pg_catalog.lower(pg_catalog.btrim(p_email)), 'UTF8'), 'sha256');
BEGIN
  SELECT * INTO r FROM private.auth_otp_guards WHERE email_hash = h FOR UPDATE;
  n := pg_catalog.clock_timestamp();
  IF NOT FOUND OR p_attempt_id IS NULL OR r.attempt_id IS DISTINCT FROM p_attempt_id OR r.attempt_until <= n THEN
    RAISE EXCEPTION 'OTP attempt no longer active';
  END IF;
  r.failures := r.failures + 1;
  UPDATE private.auth_otp_guards SET failures = r.failures, blocked_until = CASE WHEN r.failures >= 5 THEN n + interval '300 seconds' ELSE NULL END, attempt_id = NULL, attempt_until = NULL WHERE email_hash = h;
  RETURN pg_catalog.jsonb_build_object('failures', r.failures, 'locked', r.failures >= 5, 'retry_after_seconds', CASE WHEN r.failures >= 5 THEN 300 ELSE 0 END);
END $$;

CREATE FUNCTION public.auth_otp_mark_sent(p_email text) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
BEGIN
  INSERT INTO private.auth_otp_guards(email_hash, last_sent_at) VALUES (extensions.digest(pg_catalog.convert_to(pg_catalog.lower(pg_catalog.btrim(p_email)), 'UTF8'), 'sha256'), pg_catalog.clock_timestamp())
  ON CONFLICT (email_hash) DO UPDATE SET last_sent_at = EXCLUDED.last_sent_at, failures = 0, blocked_until = NULL, attempt_id = NULL, attempt_until = NULL;
  RETURN '{}'::jsonb;
END $$;

CREATE FUNCTION public.auth_otp_reset_failures(p_email text, p_attempt_id uuid) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
BEGIN
  UPDATE private.auth_otp_guards SET failures = 0, blocked_until = NULL, attempt_id = NULL, attempt_until = NULL
  WHERE email_hash = extensions.digest(pg_catalog.convert_to(pg_catalog.lower(pg_catalog.btrim(p_email)), 'UTF8'), 'sha256')
    AND attempt_id = p_attempt_id AND attempt_until > pg_catalog.clock_timestamp();
  IF NOT FOUND THEN RAISE EXCEPTION 'OTP attempt no longer active'; END IF;
  RETURN '{}'::jsonb;
END $$;

CREATE FUNCTION public.auth_otp_release_attempt(p_email text, p_attempt_id uuid) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
BEGIN
  UPDATE private.auth_otp_guards SET attempt_id = NULL, attempt_until = NULL
  WHERE email_hash = extensions.digest(pg_catalog.convert_to(pg_catalog.lower(pg_catalog.btrim(p_email)), 'UTF8'), 'sha256') AND attempt_id = p_attempt_id;
  RETURN '{}'::jsonb;
END $$;

CREATE FUNCTION public.auth_otp_consume_resend(p_email text) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE r private.auth_otp_guards%ROWTYPE; n timestamptz; h bytea := extensions.digest(pg_catalog.convert_to(pg_catalog.lower(pg_catalog.btrim(p_email)), 'UTF8'), 'sha256'); times timestamptz[]; retry integer; last_request timestamptz;
BEGIN
  INSERT INTO private.auth_otp_guards(email_hash) VALUES (h) ON CONFLICT DO NOTHING;
  SELECT * INTO r FROM private.auth_otp_guards WHERE email_hash = h FOR UPDATE;
  n := pg_catalog.clock_timestamp();
  SELECT COALESCE(pg_catalog.array_agg(t ORDER BY t), '{}'::timestamptz[]) INTO times FROM pg_catalog.unnest(r.resend_times) AS t WHERE t > n - interval '1 hour';
  IF pg_catalog.cardinality(times) >= 5 THEN
    retry := pg_catalog.ceil(EXTRACT(epoch FROM times[1] + interval '1 hour' - n))::integer;
    RETURN pg_catalog.jsonb_build_object('allowed', false, 'retry_after_seconds', GREATEST(retry, 1), 'reason', 'limit');
  END IF;
  last_request := GREATEST(r.last_sent_at, times[pg_catalog.cardinality(times)]);
  IF last_request > n - interval '60 seconds' THEN
    retry := pg_catalog.ceil(EXTRACT(epoch FROM last_request + interval '60 seconds' - n))::integer;
    RETURN pg_catalog.jsonb_build_object('allowed', false, 'retry_after_seconds', GREATEST(retry, 1), 'reason', 'cooldown');
  END IF;
  UPDATE private.auth_otp_guards SET resend_times = pg_catalog.array_append(times, n) WHERE email_hash = h;
  RETURN pg_catalog.jsonb_build_object('allowed', true, 'retry_after_seconds', 0);
END $$;

REVOKE ALL ON FUNCTION public.auth_otp_status(text, uuid), public.auth_otp_register_failure(text, uuid), public.auth_otp_mark_sent(text), public.auth_otp_reset_failures(text, uuid), public.auth_otp_release_attempt(text, uuid), public.auth_otp_consume_resend(text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.auth_otp_status(text, uuid), public.auth_otp_register_failure(text, uuid), public.auth_otp_mark_sent(text), public.auth_otp_reset_failures(text, uuid), public.auth_otp_release_attempt(text, uuid), public.auth_otp_consume_resend(text) TO service_role;
