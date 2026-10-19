-- Synthetic owners for EP-REC database tests. Callers wrap suites in a
-- transaction and roll back; no hosted identities or application data are used.
INSERT INTO auth.users (id, email) VALUES
  ('a7000000-0000-4000-8000-000000000001', 'recurrence-owner-1@kipu.test'),
  ('a7000000-0000-4000-8000-000000000002', 'recurrence-owner-2@kipu.test')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.accounts (id, user_id, name, account_type, currency_code)
VALUES
  ('a7100000-0000-4000-8000-000000000001', 'a7000000-0000-4000-8000-000000000001', 'EP-REC owner 1 account', 'BANK', 'PEN'),
  ('a7100000-0000-4000-8000-000000000002', 'a7000000-0000-4000-8000-000000000002', 'EP-REC owner 2 account', 'BANK', 'PEN')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.categories (id, user_id, name, origin, is_system, is_active, remote_revision)
VALUES
  ('a7200000-0000-4000-8000-000000000001', 'a7000000-0000-4000-8000-000000000001', 'EP-REC owner 1 category', 'CUSTOM', false, true, 1),
  ('a7200000-0000-4000-8000-000000000002', 'a7000000-0000-4000-8000-000000000002', 'EP-REC owner 2 category', 'CUSTOM', false, true, 1)
ON CONFLICT (id) DO NOTHING;

SELECT pg_catalog.set_config(
  'request.jwt.claim.sub',
  'a7000000-0000-4000-8000-000000000001',
  true
);
