import {
  createMerchantSearchHandler,
  type Dependencies,
  normalizeQuery,
  parseCandidates,
} from "./index.ts";
const assert = (v: unknown) => {
  if (!v) throw new Error("assertion failed");
};
function fixture(overrides: Partial<Dependencies> = {}) {
  let fetches = 0;
  let syncs = 0;
  const calls: string[] = [];
  const d: Dependencies = {
    secret: "secret",
    authenticate: async () => "u",
    catalog: async () => [],
    rpc: async (n) => {
      calls.push(n);
      return null;
    },
    fetchImpl: (async () => {
      fetches++;
      return new Response(
        JSON.stringify({
          data: [{
            name: "Brand",
            domain: "brand.test",
            logo_url: "https://img.logo.dev/brand.test",
          }],
        }),
      );
    }) as typeof fetch,
    sync: async () => {
      syncs++;
    },
    ...overrides,
  };
  const handler = createMerchantSearchHandler(d);
  const request = (b: unknown, auth = true) =>
    handler(
      new Request("http://test", {
        method: "POST",
        headers: auth ? { authorization: "Bearer token" } : {},
        body: JSON.stringify(b),
      }),
    );
  return {
    request,
    calls,
    get fetches() {
      return fetches;
    },
    get syncs() {
      return syncs;
    },
  };
}
Deno.test("missing auth rejected", async () => {
  const f = fixture();
  assert(
    (await f.request({ query: "brand", internet: true }, false)).status === 401,
  );
  assert(f.fetches === 0);
});
Deno.test("invalid JWT rejected", async () => {
  const f = fixture({ authenticate: async () => null });
  assert((await f.request({ query: "brand", internet: true })).status === 401);
});
Deno.test("local search does not call provider or quota", async () => {
  const f = fixture();
  assert((await f.request({ query: "brand" })).status === 200);
  assert(f.fetches === 0 && f.calls.length === 0);
});
Deno.test("explicit search normalizes and caches", async () => {
  const f = fixture();
  const p = await (await f.request({ query: "  BRAND  ", internet: true }))
    .json();
  assert(p.candidates[0].query === "brand");
  assert(f.fetches === 1 && f.calls.length === 2);
});
Deno.test("cache hit and domain membership", async () => {
  const f = fixture({
    rpc: async () => [{ name: "Brand", domain: "brand.test", logo_url: null }],
    catalog: async () => [{ id: "id", domain: "brand.test" }],
  });
  const p = await (await f.request({ query: "brand", internet: true })).json();
  assert(f.fetches === 0 && p.candidates[0].merchantId === "id");
});
Deno.test("invalid query rejected before quota", async () => {
  const f = fixture();
  assert((await f.request({ query: "x", internet: true })).status === 400);
  assert(!f.calls.length);
});
for (
  const [label, error, status] of [
    ["quota", "MERCHANT_RATE_LIMITED detail", 429],
    ["uncached", "CANDIDATE_NOT_CACHED detail", 400],
    ["excluded", "DOMAIN_EXCLUDED detail", 400],
  ] as const
) {
  Deno.test(label + " sanitized", async () => {
    const f = fixture({
      rpc: async () => {
        throw new Error(error);
      },
    });
    const r = await f.request({
      action: "add",
      query: "brand",
      domain: "brand.test",
    });
    assert(r.status === status);
    assert(!(await r.text()).includes("detail"));
  });
}
Deno.test("provider 429 sanitized", async () => {
  const f = fixture({
    fetchImpl: (async () =>
      new Response("secret", { status: 429 })) as typeof fetch,
  });
  const r = await f.request({ query: "brand", internet: true });
  assert(r.status === 429);
  assert(!(await r.text()).includes("secret"));
});
Deno.test("network failure sanitized", async () => {
  const f = fixture({
    fetchImpl: (async () => {
      throw new Error("secret");
    }) as typeof fetch,
  });
  const r = await f.request({ query: "brand", internet: true });
  assert(r.status === 503);
  assert(!(await r.text()).includes("secret"));
});
Deno.test("only newly inserted merchant triggers download", async () => {
  let created = true;
  const f = fixture({
    rpc: async () => ({ id: "id", needs_logo_sync: created }),
  });
  await f.request({ action: "add", query: "brand", domain: "brand.test" });
  created = false;
  await f.request({ action: "add", query: "brand", domain: "brand.test" });
  assert(f.syncs === 1);
});
Deno.test("private never calls provider", async () => {
  const f = fixture({ rpc: async () => ({ id: "id" }) });
  assert(
    (await f.request({ action: "private", name: "My shop" })).status === 200,
  );
  assert(f.fetches === 0);
});
Deno.test("domain and preview validation", () => {
  const rows = parseCandidates({
    data: [{ name: "Bad", domain: "https://evil.test" }, {
      name: "Good",
      domain: "good.test",
      logo_url: "https://evil.test/a",
    }, { name: "Dup", domain: "good.test" }],
  });
  assert(rows.length === 1 && rows[0].logo_url === null);
  assert(normalizeQuery("\uFF21  B") === "a b");
});
