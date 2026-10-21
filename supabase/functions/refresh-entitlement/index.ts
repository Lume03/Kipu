import {
  createOfflineEntitlementGrantIssuer,
  type OfflineEntitlementGrant,
} from "../verify-purchase/offline-entitlement-grant.ts";

export type PlanSnapshot = {
  effective_tier: "FREE" | "PREMIUM";
  source: "TEST_OVERRIDE" | "COMMERCIAL";
  expires_at: string | null;
  test_plans_enabled: boolean;
};

export type Dependencies = {
  authenticate: (request: Request) => Promise<string | null>;
  readPlan: (userId: string) => Promise<PlanSnapshot>;
  issueOfflineGrant: (input: {
    userId: string;
    installationPublicKey: string;
    effectivePremium: boolean;
    entitlementEndsAt: string | null;
    source?: "TEST_OVERRIDE";
  }) => Promise<OfflineEntitlementGrant | null>;
};

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
});

function error(code: string, status: number, retryable = false): Response {
  return json({ outcome: retryable ? "RETRYABLE" : "REJECTED", code, retryable }, status);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export function createRefreshEntitlementHandler(deps: Dependencies) {
  return async (request: Request): Promise<Response> => {
    if (new URL(request.url).pathname.split("/").pop() !== "refresh-entitlement") {
      return error("NOT_FOUND", 404);
    }
    if (request.method !== "POST") return error("METHOD_NOT_ALLOWED", 405);
    const authorization = request.headers.get("authorization") ?? "";
    if (!/^Bearer\s+\S+$/i.test(authorization)) return error("UNAUTHENTICATED", 401);

    let userId: string | null;
    try {
      userId = await deps.authenticate(request);
    } catch {
      return error("AUTH_UNAVAILABLE", 503, true);
    }
    if (!userId) return error("UNAUTHENTICATED", 401);

    if (request.headers.get("content-type")?.split(";")[0].trim().toLowerCase() !== "application/json") {
      return error("INVALID_REQUEST", 400);
    }
    let payload: unknown;
    try {
      const raw = await request.text();
      if (new TextEncoder().encode(raw).byteLength > 1024) return error("INVALID_REQUEST", 400);
      payload = JSON.parse(raw);
    } catch {
      return error("INVALID_REQUEST", 400);
    }
    if (!isRecord(payload) || Object.keys(payload).length !== 1 ||
      Object.keys(payload)[0] !== "installationPublicKey" ||
      typeof payload.installationPublicKey !== "string" ||
      payload.installationPublicKey.length < 1 || payload.installationPublicKey.length > 512) {
      return error("INVALID_REQUEST", 400);
    }

    let plan: PlanSnapshot;
    try {
      plan = await deps.readPlan(userId);
    } catch {
      return error("ENTITLEMENT_UNAVAILABLE", 503, true);
    }
    if (!plan.test_plans_enabled) return error("TEST_PLANS_DISABLED", 403);
    if ((plan.effective_tier !== "FREE" && plan.effective_tier !== "PREMIUM") ||
      (plan.source !== "TEST_OVERRIDE" && plan.source !== "COMMERCIAL") ||
      (plan.expires_at !== null && !Number.isFinite(Date.parse(plan.expires_at)))) {
      return error("ENTITLEMENT_UNAVAILABLE", 503, true);
    }
    const expiresAt = plan.effective_tier === "PREMIUM" && plan.expires_at !== null
      ? new Date(Date.parse(plan.expires_at)).toISOString()
      : null;

    let grant: OfflineEntitlementGrant | null = null;
    if (plan.effective_tier === "PREMIUM") {
      try {
        grant = await deps.issueOfflineGrant({
          userId,
          installationPublicKey: payload.installationPublicKey,
          effectivePremium: true,
          entitlementEndsAt: expiresAt,
          ...(plan.source === "TEST_OVERRIDE" ? { source: "TEST_OVERRIDE" as const } : {}),
        });
      } catch {
        grant = null;
      }
      if (!grant) return error("GRANT_UNAVAILABLE", 503, true);
    }

    return json({
      effectiveTier: plan.effective_tier,
      source: plan.source,
      expiresAt,
      offlineEntitlementGrant: grant,
    });
  };
}

function required(env: { get(name: string): string | undefined }, key: string): string {
  const value = env.get(key);
  if (!value) throw new Error(`Missing server configuration: ${key}`);
  return value;
}

function createDependencies(env: { get(name: string): string | undefined }): Dependencies {
  const supabaseUrl = required(env, "SUPABASE_URL");
  const publicKey = env.get("SUPABASE_ANON_KEY") ?? env.get("SUPABASE_PUBLISHABLE_KEY");
  const serviceRoleKey = required(env, "SUPABASE_SERVICE_ROLE_KEY");
  if (!publicKey) throw new Error("Missing Supabase public API key");
  return {
    authenticate: async (request) => {
      const response = await fetch(`${supabaseUrl}/auth/v1/user`, {
        signal: AbortSignal.timeout(8_000),
        headers: { apikey: publicKey, authorization: request.headers.get("authorization")!, accept: "application/json" },
      });
      if (response.status === 401 || response.status === 403) return null;
      if (!response.ok) throw new Error("Supabase Auth request failed");
      const user: unknown = await response.json();
      return isRecord(user) && typeof user.id === "string" ? user.id : null;
    },
    readPlan: async (userId) => {
      const response = await fetch(`${supabaseUrl}/rest/v1/rpc/get_effective_plan_for_refresh`, {
        method: "POST",
        signal: AbortSignal.timeout(8_000),
        headers: {
          apikey: serviceRoleKey,
          authorization: `Bearer ${serviceRoleKey}`,
          "content-type": "application/json",
          accept: "application/json",
        },
        body: JSON.stringify({ p_user_id: userId }),
      });
      if (!response.ok) throw new Error("Plan projection unavailable");
      const result: unknown = await response.json();
      const row = Array.isArray(result) ? result[0] : result;
      if (!isRecord(row)) throw new Error("Invalid plan projection");
      return row as PlanSnapshot;
    },
    issueOfflineGrant: createOfflineEntitlementGrantIssuer(env),
  };
}

if (import.meta.main) Deno.serve(createRefreshEntitlementHandler(createDependencies(Deno.env)));
