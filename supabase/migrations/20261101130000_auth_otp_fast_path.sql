-- Consolidate existing guards without changing their state transitions.
CREATE FUNCTION public.auth_otp_begin_verify(p_ip_key text, p_email text, p_attempt_id uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE s jsonb; r jsonb;
BEGIN
  s := public.auth_otp_status(p_email, p_attempt_id);
  IF (s->>'locked')::boolean OR (s->>'busy')::boolean THEN
    RETURN pg_catalog.jsonb_build_object('status', s, 'rate', pg_catalog.jsonb_build_object('allowed', true, 'retry_after_seconds', 0));
  END IF;
  r := public.consume_auth_rate_bucket('verify-otp:ip:' || p_ip_key, 900, 60, 1);
  IF (r->>'allowed')::boolean THEN
    r := public.consume_auth_rate_bucket('verify-otp:id:' || p_email, 900, 60, 1);
  END IF;
  IF NOT (r->>'allowed')::boolean THEN
    PERFORM public.auth_otp_release_attempt(p_email, p_attempt_id);
  END IF;
  RETURN pg_catalog.jsonb_build_object('status', s, 'rate', r);
END $$;

CREATE FUNCTION public.auth_otp_begin_resend(p_ip_key text, p_email text)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE r jsonb; s jsonb;
BEGIN
  r := public.consume_auth_rate_bucket('resend-otp:ip:' || p_ip_key, 900, 60, 1);
  IF (r->>'allowed')::boolean THEN
    r := public.consume_auth_rate_bucket('resend-otp:id:' || p_email, 900, 60, 1);
  END IF;
  IF (r->>'allowed')::boolean THEN
    s := public.auth_otp_consume_resend(p_email);
  END IF;
  RETURN pg_catalog.jsonb_build_object('rate', r, 'slot', s);
END $$;

CREATE FUNCTION public.auth_otp_finish_verify(p_email text, p_attempt_id uuid, p_outcome text)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
BEGIN
  CASE p_outcome
    WHEN 'failure' THEN RETURN public.auth_otp_register_failure(p_email, p_attempt_id);
    WHEN 'success' THEN RETURN public.auth_otp_reset_failures(p_email, p_attempt_id);
    WHEN 'release' THEN RETURN public.auth_otp_release_attempt(p_email, p_attempt_id);
    ELSE RAISE EXCEPTION 'Invalid OTP outcome';
  END CASE;
END $$;

REVOKE ALL ON FUNCTION public.auth_otp_begin_verify(text, text, uuid), public.auth_otp_begin_resend(text, text), public.auth_otp_finish_verify(text, uuid, text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.auth_otp_begin_verify(text, text, uuid), public.auth_otp_begin_resend(text, text), public.auth_otp_finish_verify(text, uuid, text) TO service_role;
