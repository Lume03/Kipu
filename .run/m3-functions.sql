CREATE OR REPLACE FUNCTION internal.merchant_discovery_quota(p_user uuid, p_kind text) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE h integer; d integer;
BEGIN
 PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(p_user::text, 17));
 SELECT count(*) FILTER(WHERE created_at > now()-interval '1 hour'), count(*) INTO h,d
 FROM internal.merchant_discovery_events WHERE user_id=p_user AND kind=p_kind AND created_at > now()-interval '1 day';
 IF (p_kind='SEARCH' AND (h>=20 OR d>=100)) OR (p_kind='ADD' AND d>=10) THEN
 RAISE EXCEPTION 'MERCHANT_RATE_LIMITED' USING ERRCODE='P0001'; END IF;
 INSERT INTO internal.merchant_discovery_events(user_id,kind) VALUES(p_user,p_kind);
END $$;
CREATE OR REPLACE FUNCTION public.merchant_search_cache_v1(p_user uuid,p_query text,p_candidates jsonb DEFAULT NULL)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v jsonb;
BEGIN
 IF p_candidates IS NULL THEN
  PERFORM internal.merchant_discovery_quota(p_user,'SEARCH');
 ELSE
  IF jsonb_typeof(p_candidates) <> 'array' OR jsonb_array_length(p_candidates)>5 THEN RAISE EXCEPTION 'INVALID_CANDIDATES'; END IF;
  INSERT INTO internal.merchant_search_cache(query,candidates) VALUES(p_query,p_candidates)
  ON CONFLICT(query) DO UPDATE SET candidates=excluded.candidates, expires_at=now()+interval '30 days';
 END IF;
 SELECT candidates INTO v FROM internal.merchant_search_cache WHERE query=p_query AND expires_at>now();
 IF v IS NOT NULL THEN
 INSERT INTO internal.merchant_search_access(user_id,query) VALUES(p_user,p_query)
 ON CONFLICT(user_id,query) DO UPDATE SET expires_at=now()+interval '1 hour';
 END IF;
 RETURN v;
END $$;
CREATE OR REPLACE FUNCTION internal.merchant_catalog_version() RETURNS trigger LANGUAGE plpgsql SET search_path='' AS $$
BEGIN
 PERFORM pg_catalog.pg_advisory_xact_lock(8240181);
 SELECT coalesce(max(version),0)+1 INTO NEW.version FROM public.merchant_services;
 RETURN NEW;
END $$;
CREATE OR REPLACE FUNCTION internal.merchant_name_key(p_name text) RETURNS text
LANGUAGE sql IMMUTABLE SET search_path='' AS $$
 SELECT lower(trim(regexp_replace(regexp_replace(extensions.unaccent(p_name),'[^[:alnum:][:space:]]','','g'),'\s+',' ','g')))
$$;
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
 PERFORM internal.merchant_discovery_quota(u,'ADD');
 INSERT INTO public.merchant_services(name,normalized_name,user_id,is_system,domain,default_category_id,logo_source,logo_status,catalog_origin)
 VALUES(c->>'name',internal.merchant_name_key(c->>'name'),NULL,true,p_domain,NULL,'LOGO_DEV','PENDING','USER_ADDED') RETURNING * INTO m;
 INSERT INTO internal.merchant_catalog_audit(merchant_id,added_by) VALUES(m.id,u);
 created:=true;
 END IF;
 RETURN to_jsonb(m) || jsonb_build_object('needs_logo_sync',created);
END $$;
CREATE OR REPLACE FUNCTION public.create_private_merchant_v1(p_name text) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE u uuid:=auth.uid(); n text:=regexp_replace(trim(p_name),'\s+',' ','g'); m public.merchant_services;
BEGIN
 IF u IS NULL THEN RAISE EXCEPTION 'UNAUTHENTICATED'; END IF;
 IF n IS NULL OR length(n) NOT BETWEEN 1 AND 60 OR n ~ '[[:cntrl:]]' OR n ~* '(cvv|cvc|pan)[ :_-]*[0-9]' OR n ~ '[0-9][0-9 -]{11,}[0-9]' THEN
 RAISE EXCEPTION 'INVALID_MERCHANT_NAME'; END IF;
 -- One lock also protects a simultaneous global/private insertion with the same name.
 PERFORM pg_catalog.pg_advisory_xact_lock(8240181);
 SELECT * INTO m FROM public.merchant_services WHERE internal.merchant_name_key(normalized_name)=internal.merchant_name_key(n) AND is_active
 AND deleted_at IS NULL AND (is_system OR user_id=u) ORDER BY is_system DESC,id LIMIT 1;
 IF m.id IS NULL THEN
 INSERT INTO public.merchant_services(name,normalized_name,user_id,is_system,domain,logo_source,catalog_origin)
 VALUES(n,internal.merchant_name_key(n),u,false,NULL,'BUNDLED','USER_ADDED') RETURNING * INTO m;
 END IF;
 RETURN to_jsonb(m);
END $$;