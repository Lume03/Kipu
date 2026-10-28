-- OPTIONAL / NOT APPLIED AUTOMATICALLY (lives outside supabase/migrations on purpose).
-- Monthly pg_cron job that calls the merchant-logo-sync Edge Function.
-- Prerequisites: migration 20261028150000 applied, function deployed, extensions pg_cron and pg_net enabled.
-- Run the Vault secret creation once, replacing the placeholder with the real service role key
-- (never commit the real value):
--   select vault.create_secret('<SERVICE_ROLE_KEY>', 'merchant_logo_sync_service_key');
-- Replace <PROJECT_REF> below.

select cron.schedule(
    'merchant-logo-sync-monthly',
    '0 7 1 * *', -- day 1 of each month, 07:00 UTC
    $$
    select net.http_post(
        url := 'https://<PROJECT_REF>.supabase.co/functions/v1/merchant-logo-sync',
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'Authorization', 'Bearer ' || (
                select decrypted_secret from vault.decrypted_secrets
                where name = 'merchant_logo_sync_service_key'
            )
        ),
        body := '{"staleDays": 30}'::jsonb,
        timeout_milliseconds := 120000
    );
    $$
);

-- Remove:  select cron.unschedule('merchant-logo-sync-monthly');
