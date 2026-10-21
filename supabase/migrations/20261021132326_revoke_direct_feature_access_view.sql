-- Keep the legacy projection available to its owner-scoped SECURITY DEFINER RPCs
-- and trusted server code, but remove the direct client/Data API surface.
REVOKE ALL ON public.v_feature_access FROM PUBLIC, anon, authenticated, service_role;
GRANT SELECT ON public.v_feature_access TO service_role;
