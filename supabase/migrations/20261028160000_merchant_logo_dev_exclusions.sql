-- EP-CCO: keep bundled logos where the Logo.dev result was reviewed and rejected (2026-10-28 dry run).
-- Inkafarma and Tambo resolved to other brands, DiDi to a grey mark, Cálidda and Primax were
-- low resolution, and Bitel only had an unlabeled "B". Without a domain the sync job skips them.
UPDATE public.merchant_services
SET domain = NULL,
    logo_status = NULL,
    updated_at = now()
WHERE is_system
  AND logo_source <> 'MANUAL'
  AND id IN (
      '00000000-0000-0001-0000-000000000001'::uuid, -- Tambo
      '00000000-0000-0000-0001-000000000017'::uuid, -- Inkafarma
      '00000000-0000-0000-0001-000000000021'::uuid, -- DiDi
      '00000000-0000-0000-0001-000000000023'::uuid, -- Primax
      '00000000-0000-0000-0001-000000000034'::uuid, -- Cálidda
      '00000000-0000-0000-0001-000000000038'::uuid  -- Bitel
  )
  AND domain IS NOT NULL;
