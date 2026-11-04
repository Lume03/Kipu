BEGIN;
CREATE EXTENSION IF NOT EXISTS pgtap WITH SCHEMA extensions;
SET search_path=public,extensions;
SELECT plan(5);
INSERT INTO auth.users(id,email) VALUES ('f4000000-0000-0000-0000-000000000003','block4-repair@example.test');
INSERT INTO public.merchant_services(id,name,normalized_name,is_system,domain,logo_path,logo_source,logo_status)
VALUES ('f4000000-0000-0000-0001-000000000001','MINISO repair','miniso repair',true,'miniso-repair.test','logo-dev/miniso-repair.test/v1.webp','LOGO_DEV','READY'),
 ('f4000000-0000-0000-0001-000000000002','Legacy same domain','legacy same domain',true,'miniso-repair.test',NULL,'LOGO_DEV','PENDING'),
 ('f4000000-0000-0000-0001-000000000003','Manual stays','manual stays',true,'miniso-repair.test',NULL,'MANUAL',NULL),
 ('f4000000-0000-0000-0001-000000000004','MINISO name only','miniso name only',true,NULL,NULL,'BUNDLED',NULL);
INSERT INTO public.merchant_services(id,name,normalized_name,is_system,user_id,domain,logo_source)
VALUES ('f4000000-0000-0000-0001-000000000005','Private stays','private stays',false,'f4000000-0000-0000-0000-000000000003','miniso-repair.test','BUNDLED');
UPDATE public.merchant_services target
SET logo_path=source.logo_path, logo_dark_path=source.logo_dark_path,
 logo_source=source.logo_source, logo_status='READY', logo_etag=source.logo_etag,
 logo_checked_at=source.logo_checked_at, updated_at=now()
FROM (
 SELECT DISTINCT ON (domain) domain,logo_path,logo_dark_path,logo_source,logo_etag,logo_checked_at
 FROM public.merchant_services
 WHERE is_system AND is_active AND deleted_at IS NULL AND domain IS NOT NULL
 AND logo_status='READY' AND logo_path IS NOT NULL
 ORDER BY domain,version DESC,id
) source
WHERE target.domain=source.domain AND target.is_system AND target.is_active
 AND target.deleted_at IS NULL AND target.logo_source<>'MANUAL' AND target.logo_path IS NULL;

SELECT is((SELECT logo_path FROM public.merchant_services WHERE id='f4000000-0000-0000-0001-000000000002'),'logo-dev/miniso-repair.test/v1.webp','proven identical domain recovers existing asset');
SELECT is((SELECT logo_path FROM public.merchant_services WHERE id='f4000000-0000-0000-0001-000000000003'),NULL,'manual row untouched');
SELECT is((SELECT domain FROM public.merchant_services WHERE id='f4000000-0000-0000-0001-000000000004'),NULL,'name alone never invents domain');
SELECT is((SELECT logo_path FROM public.merchant_services WHERE id='f4000000-0000-0000-0001-000000000005'),NULL,'private row untouched');
SELECT is((SELECT user_id::text FROM public.merchant_services WHERE id='f4000000-0000-0000-0001-000000000005'),'f4000000-0000-0000-0000-000000000003','repair never promotes a private payer');
SELECT * FROM finish();
ROLLBACK;
