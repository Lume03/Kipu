-- EP-CCO: Logo.dev merchant logo sync foundation.
-- Adds the metadata the merchant-logo-sync Edge Function needs to rehost brand logos in the
-- public merchant-logos bucket. Clients never call Logo.dev: they only read the bucket paths.
-- Idempotent and independent from 20261007144000_merchant_catalog_logo_keys_update.sql.
BEGIN;

ALTER TABLE public.merchant_services
    ADD COLUMN IF NOT EXISTS domain text
        CONSTRAINT merchant_services_domain_format_check CHECK (
            domain IS NULL OR (domain = lower(domain) AND domain ~ '^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$')
        ),
    ADD COLUMN IF NOT EXISTS logo_path text
        CONSTRAINT merchant_services_logo_path_format_check CHECK (
            logo_path IS NULL OR (logo_path ~ '^[a-z0-9][a-z0-9._/-]*$' AND logo_path !~ '\.\.')
        ),
    ADD COLUMN IF NOT EXISTS logo_dark_path text
        CONSTRAINT merchant_services_logo_dark_path_format_check CHECK (
            logo_dark_path IS NULL OR (logo_dark_path ~ '^[a-z0-9][a-z0-9._/-]*$' AND logo_dark_path !~ '\.\.')
        ),
    ADD COLUMN IF NOT EXISTS logo_etag text,
    ADD COLUMN IF NOT EXISTS logo_source text NOT NULL DEFAULT 'BUNDLED'
        CONSTRAINT merchant_services_logo_source_check CHECK (logo_source IN ('BUNDLED', 'LOGO_DEV', 'MANUAL')),
    ADD COLUMN IF NOT EXISTS logo_status text
        CONSTRAINT merchant_services_logo_status_check CHECK (
            logo_status IS NULL OR logo_status IN ('PENDING', 'READY', 'NOT_FOUND', 'NOT_INDEXED', 'FAILED')
        ),
    ADD COLUMN IF NOT EXISTS logo_checked_at timestamptz;

-- Several merchants may share a domain (Google One / Google AI Pro), so the index is not unique.
CREATE INDEX IF NOT EXISTS idx_merchant_services_system_domain
    ON public.merchant_services (domain)
    WHERE is_system AND domain IS NOT NULL;

-- Only service_role (the sync job, manual uploads) may write domain/logo_* columns.
-- RLS already limits authenticated users to their own rows; this keeps their custom merchants
-- from pointing the app at arbitrary storage paths.
CREATE OR REPLACE FUNCTION public.merchant_services_guard_logo_columns()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = ''
AS $$
BEGIN
    IF current_user NOT IN ('authenticated', 'anon') THEN
        RETURN NEW;
    END IF;

    IF TG_OP = 'INSERT' THEN
        NEW.domain := NULL;
        NEW.logo_path := NULL;
        NEW.logo_dark_path := NULL;
        NEW.logo_etag := NULL;
        NEW.logo_source := 'BUNDLED';
        NEW.logo_status := NULL;
        NEW.logo_checked_at := NULL;
    ELSE
        NEW.domain := OLD.domain;
        NEW.logo_path := OLD.logo_path;
        NEW.logo_dark_path := OLD.logo_dark_path;
        NEW.logo_etag := OLD.logo_etag;
        NEW.logo_source := OLD.logo_source;
        NEW.logo_status := OLD.logo_status;
        NEW.logo_checked_at := OLD.logo_checked_at;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_merchant_services_guard_logo_columns ON public.merchant_services;
CREATE TRIGGER trg_merchant_services_guard_logo_columns
    BEFORE INSERT OR UPDATE ON public.merchant_services
    FOR EACH ROW EXECUTE FUNCTION public.merchant_services_guard_logo_columns();

DO $$
DECLARE
    v_next_version bigint;
BEGIN
    SELECT COALESCE(MAX(version), 0) + 1 INTO v_next_version FROM public.merchant_services;

    -- Approved Logo.dev domains. Listo!, Metropolitano and Rutas de Lima have none (bundled logos).
    UPDATE public.merchant_services AS ms
    SET domain = approved.domain,
        logo_status = 'PENDING',
        version = v_next_version,
        updated_at = now()
    FROM (VALUES
        ('00000000-0000-0001-0000-000000000001'::uuid, 'tambo.pe'),
        ('00000000-0000-0001-0000-000000000002'::uuid, 'starbucks.com'),
        ('00000000-0000-0001-0000-000000000003'::uuid, 'plazavea.com.pe'),
        ('00000000-0000-0000-0001-000000000004'::uuid, 'tottus.com.pe'),
        ('00000000-0000-0000-0001-000000000005'::uuid, 'metro.pe'),
        ('00000000-0000-0000-0001-000000000006'::uuid, 'wong.pe'),
        ('00000000-0000-0000-0001-000000000007'::uuid, 'tiendasmass.com.pe'),
        ('00000000-0000-0000-0001-000000000008'::uuid, 'oxxo.com'),
        ('00000000-0000-0000-0001-000000000010'::uuid, 'kfc.com.pe'),
        ('00000000-0000-0000-0001-000000000011'::uuid, 'mcdonalds.com.pe'),
        ('00000000-0000-0000-0001-000000000012'::uuid, 'bembos.com.pe'),
        ('00000000-0000-0000-0001-000000000013'::uuid, 'burgerking.pe'),
        ('00000000-0000-0000-0001-000000000014'::uuid, 'pizzahut.com.pe'),
        ('00000000-0000-0000-0001-000000000015'::uuid, 'rappi.com.pe'),
        ('00000000-0000-0000-0001-000000000016'::uuid, 'pedidosya.com.pe'),
        ('00000000-0000-0000-0001-000000000017'::uuid, 'inkafarma.pe'),
        ('00000000-0000-0000-0001-000000000018'::uuid, 'mifarma.com.pe'),
        ('00000000-0000-0000-0001-000000000019'::uuid, 'uber.com'),
        ('00000000-0000-0000-0001-000000000020'::uuid, 'indrive.com'),
        ('00000000-0000-0000-0001-000000000021'::uuid, 'didiglobal.com'),
        ('00000000-0000-0000-0001-000000000022'::uuid, 'cabify.com'),
        ('00000000-0000-0000-0001-000000000023'::uuid, 'primax.com'),
        ('00000000-0000-0000-0001-000000000024'::uuid, 'repsol.pe'),
        ('00000000-0000-0000-0001-000000000025'::uuid, 'petroperu.com.pe'),
        ('00000000-0000-0000-0001-000000000027'::uuid, 'lineauno.pe'),
        ('00000000-0000-0000-0001-000000000029'::uuid, 'limaexpresa.pe'),
        ('00000000-0000-0000-0001-000000000030'::uuid, 'latamairlines.com'),
        ('00000000-0000-0000-0001-000000000031'::uuid, 'pluz.pe'),
        ('00000000-0000-0000-0001-000000000032'::uuid, 'luzdelsur.pe'),
        ('00000000-0000-0000-0001-000000000033'::uuid, 'sedapal.com.pe'),
        ('00000000-0000-0000-0001-000000000034'::uuid, 'calidda.com.pe'),
        ('00000000-0000-0000-0001-000000000035'::uuid, 'claro.com.pe'),
        ('00000000-0000-0000-0001-000000000036'::uuid, 'movistar.com.pe'),
        ('00000000-0000-0000-0001-000000000037'::uuid, 'entel.pe'),
        ('00000000-0000-0000-0001-000000000038'::uuid, 'bitel.com.pe'),
        ('00000000-0000-0000-0001-000000000039'::uuid, 'win.pe'),
        ('00000000-0000-0000-0001-000000000040'::uuid, 'netflix.com'),
        ('00000000-0000-0000-0001-000000000041'::uuid, 'disneyplus.com'),
        ('00000000-0000-0000-0001-000000000042'::uuid, 'primevideo.com'),
        ('00000000-0000-0000-0001-000000000043'::uuid, 'spotify.com'),
        ('00000000-0000-0000-0001-000000000044'::uuid, 'google.com'),
        ('00000000-0000-0000-0001-000000000045'::uuid, 'icloud.com'),
        ('00000000-0000-0000-0001-000000000046'::uuid, 'hbomax.com'),
        ('00000000-0000-0000-0001-000000000047'::uuid, 'youtube.com'),
        ('00000000-0000-0000-0001-000000000048'::uuid, 'crunchyroll.com'),
        ('00000000-0000-0000-0001-000000000049'::uuid, 'chatgpt.com'),
        ('00000000-0000-0000-0001-000000000050'::uuid, 'claude.ai'),
        ('00000000-0000-0000-0001-000000000051'::uuid, 'google.com'),
        ('00000000-0000-0000-0001-000000000052'::uuid, 'perplexity.ai'),
        ('00000000-0000-0000-0001-000000000053'::uuid, 'canva.com'),
        ('00000000-0000-0000-0001-000000000054'::uuid, 'microsoft.com'),
        ('00000000-0000-0000-0001-000000000055'::uuid, 'adobe.com'),
        ('00000000-0000-0000-0001-000000000056'::uuid, 'notion.com'),
        ('00000000-0000-0000-0001-000000000057'::uuid, 'steampowered.com'),
        ('00000000-0000-0000-0001-000000000058'::uuid, 'playstation.com'),
        ('00000000-0000-0000-0001-000000000059'::uuid, 'xbox.com'),
        ('00000000-0000-0000-0001-000000000060'::uuid, 'cineplanet.com.pe'),
        ('00000000-0000-0000-0001-000000000061'::uuid, 'cinemark-peru.com'),
        ('00000000-0000-0000-0001-000000000062'::uuid, 'teleticket.com.pe'),
        ('00000000-0000-0000-0001-000000000063'::uuid, 'joinnus.com')
    ) AS approved(id, domain)
    WHERE ms.id = approved.id
      AND ms.is_system
      AND ms.logo_source <> 'MANUAL'
      AND ms.domain IS DISTINCT FROM approved.domain;

    -- "Max" is now "HBO Max" (same UUID).
    UPDATE public.merchant_services
    SET name = 'HBO Max',
        normalized_name = 'hbo max',
        version = v_next_version,
        updated_at = now()
    WHERE id = '00000000-0000-0000-0001-000000000046'::uuid
      AND (name IS DISTINCT FROM 'HBO Max' OR normalized_name IS DISTINCT FROM 'hbo max');

    -- Bundled logo keys for merchants without a domain (remote may lack them).
    UPDATE public.merchant_services AS ms
    SET logo_key = bundled.logo_key,
        version = v_next_version,
        updated_at = now()
    FROM (VALUES
        ('00000000-0000-0000-0001-000000000009'::uuid, 'ic_merchant_listo'),
        ('00000000-0000-0000-0001-000000000026'::uuid, 'ic_merchant_metropolitano'),
        ('00000000-0000-0000-0001-000000000028'::uuid, 'ic_merchant_rutas_de_lima')
    ) AS bundled(id, logo_key)
    WHERE ms.id = bundled.id
      AND ms.logo_key IS DISTINCT FROM bundled.logo_key;
END $$;

NOTIFY pgrst, 'reload schema';
COMMIT;
