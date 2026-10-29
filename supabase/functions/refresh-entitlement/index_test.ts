import {
  createRefreshEntitlementHandler,
  type PlanSnapshot,
} from "./index.ts";

const owner = "73000000-0000-4000-8000-000000000001";
const grant = { payload: "signed-payload", signature: "signature", keyId: "qa-key" };
const premium: PlanSnapshot = {
  effective_tier: "PREMIUM",
  source: "TEST_OVERRIDE",
  expires_at: "2026-10-23T12:00:00+00:00",
  test_plans_enabled: true,
};

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

function harness(options: {
  userId?: string | null;
  plan?: PlanSnapshot;
  authError?: boolean;
  planError?: boolean;
  issuedGrant?: typeof grant | null;
} = {}) {
  const calls: { readPlan: string[]; grants: unknown[] } = { readPlan: [], grants: [] };
  const handler = createRefreshEntitlementHandler({
    authenticate: async () => {
      if (options.authError) throw new Error("network");
      return options.userId === undefined ? owner : options.userId;
    },
    readPlan: async (userId) => {
      calls.readPlan.push(userId);
      if (options.planError) throw new Error("db");
      return options.plan ?? premium;
    },
    issueOfflineGrant: async (input) => {
      calls.grants.push(input);
      return options.issuedGrant === undefined ? grant : options.issuedGrant;
    },
  });
  return { handler, calls };
}

function request(body: unknown = { installationPublicKey: "DER-SPKI" }, options: { token?: string; method?: string } = {}) {
  return new Request("https://project.test/functions/v1/refresh-entitlement", {
    method: options.method ?? "POST",
    headers: {
      ...(options.token === "" ? {} : { authorization: `Bearer ${options.token ?? "valid"}` }),
      "content-type": "application/json",
    },
    body: options.method === "GET" ? undefined : JSON.stringify(body),
  });
}

Deno.test("refresh validates Auth, exact request keys and endpoint method", async () => {
  const missingAuth = await harness().handler(request({}, { token: "" }));
  assert(missingAuth.status === 401, "missing bearer must be rejected");
  assert((await harness({ userId: null }).handler(request())).status === 401, "invalid JWT user must be rejected");
  assert((await harness({ authError: true }).handler(request())).status === 503, "Auth outage is retryable");
  assert((await harness().handler(request(undefined, { method: "GET" }))).status === 405, "only POST is allowed");
  for (const body of [
    { installationPublicKey: "key", userId: owner },
    { installationPublicKey: "key", tier: "PREMIUM" },
    { installationPublicKey: "key", expiresAt: "2030-01-01T00:00:00Z" },
    {},
  ]) {
    assert((await harness().handler(request(body))).status === 400, "extra/missing fields must be rejected");
  }
});

Deno.test("premium refresh mints device-bound QA source grant and returns exact response contract", async () => {
  const h = harness();
  const response = await h.handler(request());
  const body = await response.json();
  assert(response.status === 200, "refresh should succeed");
  assert(JSON.stringify(Object.keys(body).sort()) === JSON.stringify(["effectiveTier", "expiresAt", "offlineEntitlementGrant", "source"]), "response keys must match contract");
  assert(body.effectiveTier === "PREMIUM" && body.source === "TEST_OVERRIDE", "effective plan source must be QA override");
  assert(body.expiresAt === "2026-10-23T12:00:00.000Z", "response expiry is canonical and matches the signed claim");
  assert(body.offlineEntitlementGrant.payload === grant.payload, "the shared signed grant is returned");
  assert(h.calls.readPlan[0] === owner, "user identity comes from validated Auth");
  const issued = h.calls.grants[0] as Record<string, unknown>;
  assert(issued.source === "TEST_OVERRIDE" && issued.entitlementEndsAt === body.expiresAt, "issuer receives the bounded QA expiry and signed source");
});

Deno.test("Free, missing override, expired override and disabled gate never receive a grant", async () => {
  const states: PlanSnapshot[] = [
    { effective_tier: "FREE", source: "TEST_OVERRIDE", expires_at: premium.expires_at, test_plans_enabled: true },
    { effective_tier: "FREE", source: "COMMERCIAL", expires_at: null, test_plans_enabled: true },
    { effective_tier: "FREE", source: "COMMERCIAL", expires_at: null, test_plans_enabled: true },
  ];
  for (const plan of states) {
    const response = await harness({ plan }).handler(request());
    const body = await response.json();
    assert(response.status === 200 && body.effectiveTier === plan.effective_tier, "resolved tier is returned");
    assert(body.offlineEntitlementGrant === null, "Free has an explicit null grant to clear cache");
    assert(body.expiresAt === null, "Free entitlement has no expiration even if an administrative override has a TTL");
  }
  const disabled = await harness({ plan: { ...premium, test_plans_enabled: false } }).handler(request());
  assert(disabled.status === 403 && (await disabled.json()).code === "TEST_PLANS_DISABLED", "disabled global gate rejects the endpoint");
});

Deno.test("commercial Premium can refresh without Google Play and omits the QA source claim", async () => {
  const commercialPlan: PlanSnapshot = {
    effective_tier: "PREMIUM",
    source: "COMMERCIAL",
    expires_at: "2026-10-22T00:00:00.000Z",
    test_plans_enabled: true,
  };
  const h = harness({ plan: commercialPlan });
  const response = await h.handler(request());
  const body = await response.json();
  assert(response.status === 200 && body.source === "COMMERCIAL", "commercial entitlement is resolved by the server projection");
  assert(body.offlineEntitlementGrant !== null, "commercial Premium also reuses the shared issuer");
  const input = h.calls.grants[0] as Record<string, unknown>;
  assert(!("source" in input), "commercial grant must not be marked as QA");
});

Deno.test("signing or plan lookup failures fail closed without leaking credentials", async () => {
  const noGrant = await harness({ issuedGrant: null }).handler(request());
  assert(noGrant.status === 503 && (await noGrant.json()).code === "GRANT_UNAVAILABLE", "Premium without signed grant is not successful");
  const unavailable = await harness({ planError: true }).handler(request());
  assert(unavailable.status === 503 && (await unavailable.json()).code === "ENTITLEMENT_UNAVAILABLE", "RPC outage fails closed");
});

Deno.test("developer refresh ignores disabled QA gate and signs permanent server source", async () => {
  const h = harness({ plan: { effective_tier: "PREMIUM", source: "DEVELOPER", expires_at: null, test_plans_enabled: false } });
  const response = await h.handler(request());
  const body = await response.json();
  assert(response.status === 200 && body.source === "DEVELOPER" && body.expiresAt === null, "permanent developer plan refreshes without QA");
  const input = h.calls.grants[0] as Record<string, unknown>;
  assert(input.source === "DEVELOPER" && input.entitlementEndsAt === null, "developer source is signed with no entitlement expiry");
  assert((await h.handler(request({ installationPublicKey: "key", source: "DEVELOPER" }))).status === 400, "client cannot select developer source");
});

Deno.test("developer revocation can refresh commercial Free with disabled QA gate", async () => {
  const h = harness({ plan: { effective_tier: "FREE", source: "COMMERCIAL", expires_at: null, test_plans_enabled: false } });
  const response = await h.handler(request());
  const body = await response.json();
  assert(response.status === 200 && body.effectiveTier === "FREE" && body.offlineEntitlementGrant === null, "server revocation clears cached developer grants");
  assert(h.calls.grants.length === 0, "Free must never sign");
});

Deno.test("malformed developer projections fail closed", async () => {
  for (const plan of [
    { effective_tier: "FREE", source: "DEVELOPER", expires_at: null, test_plans_enabled: false },
    { effective_tier: "PREMIUM", source: "DEVELOPER", expires_at: premium.expires_at, test_plans_enabled: false },
  ] as PlanSnapshot[]) {
    const h = harness({ plan });
    assert((await h.handler(request())).status === 503 && h.calls.grants.length === 0, "invalid developer state cannot sign");
  }
});
