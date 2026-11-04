-- Operational switch: disable new provider-backed global additions without deleting the catalog.
-- Arbitrary/private names never enter the shared catalog. Only authorized cached provider candidates do.
CREATE TABLE internal.merchant_discovery_settings (
 singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
 shared_additions_enabled boolean NOT NULL DEFAULT true
);
ALTER TABLE internal.merchant_discovery_settings ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON internal.merchant_discovery_settings FROM PUBLIC, anon, authenticated;
GRANT SELECT, UPDATE ON internal.merchant_discovery_settings TO service_role;
INSERT INTO internal.merchant_discovery_settings DEFAULT VALUES;

CREATE OR REPLACE FUNCTION public.add_merchant_from_logo_dev_v1(p_query text,p_domain text) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE u uuid:=auth.uid(); c jsonb; m public.merchant_services; created boolean:=false;
BEGIN
 IF u IS NULL THEN RAISE EXCEPTION 'UNAUTHENTICATED'; END IF;
 SELECT candidate INTO c FROM internal.merchant_search_cache cache
 JOIN internal.merchant_search_access access USING(query), jsonb_array_elements(cache.candidates) candidate
 WHERE access.user_id=u AND cache.query=p_query AND cache.expires_at>now() AND access.expires_at>now()
 AND candidate->>'domain'=p_domain LIMIT 1;
 IF c IS NULL THEN RAISE EXCEPTION 'CANDIDATE_NOT_CACHED'; END IF;
 IF length(p_domain)>253 OR p_domain !~ '^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$'
 OR EXISTS(SELECT 1 FROM internal.merchant_domain_exclusions WHERE domain=p_domain) THEN RAISE EXCEPTION 'DOMAIN_EXCLUDED'; END IF;
 PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(p_domain,18));
 SELECT * INTO m FROM public.merchant_services WHERE domain=p_domain AND is_active AND deleted_at IS NULL AND is_system ORDER BY id LIMIT 1;
 IF m.id IS NULL THEN
 -- Lock the operator switch until commit, so disabling it fences concurrent new publications.
 IF NOT (SELECT shared_additions_enabled FROM internal.merchant_discovery_settings WHERE singleton FOR SHARE) THEN
  RAISE EXCEPTION 'SHARED_MERCHANT_ADDS_DISABLED';
 END IF;
 PERFORM internal.merchant_discovery_quota(u,'ADD');
 INSERT INTO public.merchant_services(name,normalized_name,user_id,is_system,domain,default_category_id,logo_source,logo_status,catalog_origin)
 VALUES(c->>'name',internal.merchant_name_key(c->>'name'),NULL,true,p_domain,NULL,'LOGO_DEV','PENDING','USER_ADDED') RETURNING * INTO m;
 INSERT INTO internal.merchant_catalog_audit(merchant_id,added_by) VALUES(m.id,u);
 created:=true;
 END IF;
 RETURN to_jsonb(m) || jsonb_build_object('needs_logo_sync',created OR (m.logo_source <> 'MANUAL' AND m.domain IS NOT NULL AND
 (m.logo_path IS NULL OR m.logo_status IN ('PENDING','FAILED','NOT_INDEXED')) AND
 (m.logo_checked_at IS NULL OR m.logo_checked_at < now()-interval '5 minutes')));
END $$;
REVOKE ALL ON FUNCTION public.add_merchant_from_logo_dev_v1(text,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.add_merchant_from_logo_dev_v1(text,text) TO authenticated;

-- Conservative repair: reuse an already published logo only for the SAME proven domain.
-- No name matching, no private promotion, no movement/alias reassignment, no manual replacement.
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
