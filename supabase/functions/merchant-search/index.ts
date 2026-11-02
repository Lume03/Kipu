import { createMerchantLogoSyncHandler } from "../merchant-logo-sync/index.ts";
import { createLogoStore } from "../merchant-logo-sync/store.ts";

export type Candidate = {
  name: string;
  domain: string;
  logo_url: string | null;
  alreadyInCatalog?: boolean;
  merchantId?: string;
  query?: string;
};
export const normalizeQuery = (v: string) =>
  v.normalize("NFKC").trim().replace(/\s+/g, " ").toLowerCase();
export function parseCandidates(payload: unknown): Candidate[] {
  const data = (payload as { data?: unknown })?.data;
  if (!Array.isArray(data)) throw new Error("PROVIDER_UNAVAILABLE");
  const domains = new Set<string>();
  return data.flatMap((v): Candidate[] => {
    if (typeof v?.name !== "string" || typeof v?.domain !== "string") return [];
    const domain = v.domain.toLowerCase();
    const name = v.name.trim();
    if (
      !name || /[\u0000-\u001f\u007f]/.test(name) || name.length > 60 ||
      domain.length > 253 || domains.has(domain) ||
      !/^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$/.test(
        domain,
      ) ||
      /^[0-9]+$/.test(domain.split(".").at(-1)!) ||
      domain.split(".").some((s: string) => s.length > 63) ||
      /\d{12,}|(?:cvv|cvc)\s*\d/i.test(name)
    ) return [];
    let logo_url: string | null = null;
    try {
      const url = new URL(v.logo_url);
      if (
        url.protocol === "https:" && url.hostname === "img.logo.dev" &&
        !url.username && !url.password && !url.port
      ) logo_url = url.href;
    } catch { /* preview optional */ }
    domains.add(domain);
    return [{ name, domain, logo_url }];
  }).slice(0, 5);
}
export type Dependencies = {
  authenticate(token: string): Promise<string | null>;
  rpc(
    name: string,
    body: Record<string, unknown>,
    token?: string,
  ): Promise<unknown>;
  catalog(query: string, token: string): Promise<Record<string, unknown>[]>;
  fetchImpl: typeof fetch;
  secret: string;
  sync(id: string): Promise<void>;
};
const headers = {
  "content-type": "application/json",
  "cache-control": "private, no-store",
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "authorization, apikey, content-type",
};
const json = (v: unknown, status = 200) =>
  new Response(JSON.stringify(v), { status, headers });
export function createMerchantSearchHandler(d: Dependencies) {
  return async (req: Request): Promise<Response> => {
    if (req.method === "OPTIONS") return new Response(null, { headers });
    if (req.method !== "POST") return json({ code: "METHOD_NOT_ALLOWED" }, 405);
    const token = req.headers.get("authorization")?.match(/^Bearer (.+)$/i)
      ?.[1];
    if (!token) return json({ code: "UNAUTHENTICATED" }, 401);
    try {
      const user = await d.authenticate(token);
      if (!user) return json({ code: "UNAUTHENTICATED" }, 401);
      const b = await req.json().catch(() => null);
      if (!b || typeof b !== "object") {
        return json({ code: "INVALID_REQUEST" }, 400);
      }
      if (b.action === "private") {
        if (typeof b.name !== "string") {
          return json({ code: "INVALID_REQUEST" }, 400);
        }
        return json({
          merchant: await d.rpc("create_private_merchant_v1", {
            p_name: b.name,
          }, token),
        });
      }
      if (typeof b.query !== "string") {
        return json({ code: "INVALID_QUERY" }, 400);
      }
      const query = normalizeQuery(b.query);
      if (
        query.length < 2 || query.length > 60 || /[\u0000-\u001f]/.test(query)
      ) return json({ code: "INVALID_QUERY" }, 400);
      if (b.action === "add") {
        if (typeof b.domain !== "string") {
          return json({ code: "INVALID_REQUEST" }, 400);
        }
        const merchant = await d.rpc("add_merchant_from_logo_dev_v1", {
          p_query: query,
          p_domain: b.domain,
        }, token) as Record<string, unknown>;
        if (merchant.needs_logo_sync === true) {
          await d.sync(merchant.id as string).catch(() => undefined);
        }
        const refreshed = merchant.needs_logo_sync === true
          ? (await d.catalog("", token).catch(() => [])).find((m) =>
            m.id === merchant.id
          )
          : undefined;
        return json({ merchant: refreshed ?? merchant });
      }
      if (b.action !== undefined && b.action !== "search") {
        return json({ code: "INVALID_REQUEST" }, 400);
      }
      const catalog = await d.catalog(query, token);
      if (b.internet !== true) return json({ catalog, candidates: [] });
      let candidates = await d.rpc("merchant_search_cache_v1", {
        p_user: user,
        p_query: query,
      }) as Candidate[] | null;
      if (candidates === null) {
        const url = new URL("https://api.logo.dev/v2/search");
        url.search = new URLSearchParams({
          q: query,
          method: "match",
          limit: "5",
        }).toString();
        const response = await d.fetchImpl(url, {
          headers: { authorization: `Bearer ${d.secret}` },
          signal: AbortSignal.timeout(15000),
          redirect: "error",
        });
        if (!response.ok) {
          await response.body?.cancel();
          return json({
            code: response.status === 429
              ? "PROVIDER_RATE_LIMITED"
              : "PROVIDER_UNAVAILABLE",
          }, response.status === 429 ? 429 : 503);
        }
        candidates = parseCandidates(await response.json());
        await d.rpc("merchant_search_cache_v1", {
          p_user: user,
          p_query: query,
          p_candidates: candidates,
        });
      }
      // Check exact domain membership against the visible catalog, independently of the name query.
      const visible = await d.catalog("", token);
      return json({
        catalog,
        candidates: candidates.map((c) => {
          const m = visible.find((m) => m.domain === c.domain);
          return { ...c, query, alreadyInCatalog: !!m, merchantId: m?.id };
        }),
      });
    } catch (e) {
      const code = e instanceof Error ? e.message : "";
      if (code.includes("MERCHANT_RATE_LIMITED")) {
        return json({ code: "MERCHANT_RATE_LIMITED" }, 429);
      }
      if (
        ["CANDIDATE_NOT_CACHED", "DOMAIN_EXCLUDED", "INVALID_MERCHANT_NAME"]
          .some((v) => code.includes(v))
      ) {
        return json({
          code: code.includes("CANDIDATE_NOT_CACHED")
            ? "CANDIDATE_NOT_CACHED"
            : code.includes("DOMAIN_EXCLUDED")
            ? "DOMAIN_EXCLUDED"
            : "INVALID_MERCHANT_NAME",
        }, 400);
      }
      return json({ code: "SERVICE_UNAVAILABLE" }, 503);
    }
  };
}
if (import.meta.main) {
  const url = Deno.env.get("SUPABASE_URL")!;
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const secret = Deno.env.get("LOGO_DEV_SECRET_KEY")!;
  const rest = async (path: string, init: RequestInit = {}, token = key) => {
    const r = await fetch(`${url}/rest/v1/${path}`, {
      ...init,
      headers: {
        apikey: key,
        authorization: `Bearer ${token}`,
        "content-type": "application/json",
      },
      signal: AbortSignal.timeout(20000),
    });
    if (!r.ok) {
      const p = await r.json().catch(() => ({}));
      throw new Error(p.message ?? "SERVICE_UNAVAILABLE");
    }
    return r.json();
  };
  const sync = createMerchantLogoSyncHandler({
    store: createLogoStore(url, key),
    fetchImpl: fetch,
    now: () => new Date(),
    sleep: (ms) => new Promise((r) => setTimeout(r, ms)),
    random: Math.random,
  }, { serviceRoleKey: key, logoDevSecretKey: secret, concurrency: 1 });
  Deno.serve(
    createMerchantSearchHandler({
      secret,
      fetchImpl: fetch,
      authenticate: async (token) => {
        const r = await fetch(`${url}/auth/v1/user`, {
          headers: { apikey: key, authorization: `Bearer ${token}` },
          signal: AbortSignal.timeout(10000),
        });
        if (!r.ok) {
          await r.body?.cancel();
          return null;
        }
        return (await r.json()).id ?? null;
      },
      rpc: (name, body, token) =>
        rest(
          `rpc/${name}`,
          { method: "POST", body: JSON.stringify(body) },
          token,
        ),
      catalog: (query, token) => {
        const params = new URLSearchParams({
          select:
            "id,name,normalized_name,is_active,version,default_category_id,priority,logo_key,brand_color,logo_path,logo_dark_path,logo_source,domain,user_id",
          is_active: "eq.true",
          deleted_at: "is.null",
        });
        if (query) {
          params.set(
            "normalized_name",
            `ilike.*${query.replace(/[%*_]/g, "")}*`,
          );
        }
        return rest(`merchant_services?${params}`, {}, token);
      },
      sync: async (id) => {
        const r = await sync(
          new Request("http://localhost/merchant-logo-sync", {
            method: "POST",
            headers: {
              authorization: `Bearer ${key}`,
              "content-type": "application/json",
            },
            body: JSON.stringify({ merchantIds: [id] }),
          }),
        );
        await r.body?.cancel();
      },
    }),
  );
}
