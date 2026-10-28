import { createMerchantLogoSyncHandler, etagSlug } from "./index.ts";
import type { LogoStore, LogoUpdate, MerchantRow } from "./store.ts";

const SERVICE_KEY = "service-role-key-0123456789abcdef0123456789";
const SECRET = "logo-dev-secret";

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

function merchant(
  id: number,
  name: string,
  domain: string,
  overrides: Partial<MerchantRow> = {},
): MerchantRow {
  return {
    id: `00000000-0000-0000-0001-${String(id).padStart(12, "0")}`,
    name,
    domain,
    logo_etag: null,
    logo_path: null,
    logo_dark_path: null,
    logo_status: "PENDING",
    logo_checked_at: null,
    ...overrides,
  };
}

type Recorded = {
  updates: { ids: string[]; update: LogoUpdate; version: number | null }[];
  uploads: { path: string; upsert: boolean }[];
  logoDevCalls: string[];
  downloads: string[];
  sleeps: number[];
  logoDevAuth: (string | null)[];
  downloadAuth: (string | null)[];
};

type Scenario = {
  rows: MerchantRow[];
  logoDev?: (domain: string, call: number) => Response;
  download?: (url: URL) => Response;
};

function imageResponse(size = 1_024): Response {
  return new Response(new Uint8Array(size), {
    headers: { "content-type": "image/webp" },
  });
}

function logoJson(etag = "etag-1"): Response {
  return Response.json({
    logo: { url: "https://img.logo.dev/signed?token=abc", etag },
  });
}

function setup(scenario: Scenario) {
  const recorded: Recorded = {
    updates: [],
    uploads: [],
    logoDevCalls: [],
    downloads: [],
    sleeps: [],
    logoDevAuth: [],
    downloadAuth: [],
  };
  const callsPerDomain = new Map<string, number>();
  const fetchImpl = ((input: string | URL | Request, init?: RequestInit) => {
    const url = new URL(
      typeof input === "string"
        ? input
        : input instanceof URL
        ? input.href
        : input.url,
    );
    const auth = new Headers(init?.headers).get("authorization");
    if (url.hostname === "api.logo.dev") {
      const domain = url.searchParams.get("domain")!;
      recorded.logoDevCalls.push(domain);
      recorded.logoDevAuth.push(auth);
      const call = (callsPerDomain.get(domain) ?? 0) + 1;
      callsPerDomain.set(domain, call);
      return Promise.resolve(
        (scenario.logoDev ?? (() => logoJson()))(domain, call),
      );
    }
    recorded.downloads.push(url.searchParams.get("theme") ?? "");
    recorded.downloadAuth.push(auth);
    return Promise.resolve((scenario.download ?? (() => imageResponse()))(url));
  }) as typeof fetch;
  const store: LogoStore = {
    listMerchants: () =>
      Promise.resolve(scenario.rows.filter((row) => row.domain)),
    nextVersion: () => Promise.resolve(42),
    updateMerchants: (ids, update, version) => {
      recorded.updates.push({ ids, update, version });
      return Promise.resolve();
    },
    uploadImage: (path, _bytes, _type, upsert) => {
      recorded.uploads.push({ path, upsert });
      return Promise.resolve();
    },
  };
  const handler = createMerchantLogoSyncHandler(
    {
      fetchImpl,
      sleep: (ms) => {
        recorded.sleeps.push(ms);
        return Promise.resolve();
      },
      random: () => 0,
      now: () => new Date("2026-10-28T12:00:00Z"),
      store,
    },
    { serviceRoleKey: SERVICE_KEY, logoDevSecretKey: SECRET, concurrency: 3 },
  );
  return { handler, recorded };
}

async function call(
  handler: (request: Request) => Promise<Response>,
  body: Record<string, unknown> = {},
  token: string | null = SERVICE_KEY,
) {
  const headers: Record<string, string> = {
    "content-type": "application/json",
  };
  if (token) headers.authorization = `Bearer ${token}`;
  const response = await handler(
    new Request("https://x.supabase.co/functions/v1/merchant-logo-sync", {
      method: "POST",
      headers,
      body: JSON.stringify(body),
    }),
  );
  return {
    status: response.status,
    body: await response.json() as Record<string, any>,
  };
}

Deno.test("rejects callers that are not the service role", async () => {
  const { handler, recorded } = setup({
    rows: [merchant(1, "Tambo", "tambo.pe")],
  });
  assert((await call(handler, {}, null)).status === 401, "missing token");
  assert((await call(handler, {}, "anon-key")).status === 401, "wrong token");
  assert(recorded.logoDevCalls.length === 0, "no Logo.dev call");
});

Deno.test("rejects unknown request fields", async () => {
  const { handler } = setup({ rows: [] });
  assert(
    (await call(handler, { surprise: true })).status === 400,
    "invalid request",
  );
});

Deno.test("new etag downloads both themes, uploads without upsert and updates the merchant", async () => {
  const { handler, recorded } = setup({
    rows: [merchant(1, "Tambo", "tambo.pe")],
  });
  const { body } = await call(handler);
  assert(body.updated === 1 && body.checked === 1, "updated");
  assert(recorded.downloads.join() === "light,dark", "both variants");
  assert(recorded.uploads.every((upload) => !upload.upsert), "upsert false");
  assert(
    recorded.uploads[0].path === "logo-dev/tambo.pe/etag-1.webp",
    "light path",
  );
  assert(
    recorded.uploads[1].path === "logo-dev/tambo.pe/etag-1-dark.webp",
    "dark path",
  );
  const { update, version } = recorded.updates[0];
  assert(
    update.logo_source === "LOGO_DEV" && update.logo_status === "READY",
    "ready",
  );
  assert(
    update.logo_dark_path === "logo-dev/tambo.pe/etag-1-dark.webp" &&
      version === 42,
    "dark + version",
  );
  assert(recorded.logoDevAuth[0] === `Bearer ${SECRET}`, "API key to Logo.dev");
  assert(
    recorded.downloadAuth.every((auth) => auth === null),
    "API key never sent to the download URL",
  );
});

Deno.test("unchanged etag only refreshes logo_checked_at", async () => {
  const row = merchant(1, "Tambo", "tambo.pe", {
    logo_etag: "etag-1",
    logo_path: "logo-dev/tambo.pe/etag-1.webp",
    logo_status: "READY",
    logo_checked_at: "2026-08-01T00:00:00Z",
  });
  const { handler, recorded } = setup({ rows: [row] });
  const { body } = await call(handler);
  assert(body.unchanged === 1 && body.updated === 0, "unchanged");
  assert(
    recorded.downloads.length === 0 && recorded.uploads.length === 0,
    "no downloads",
  );
  assert(
    recorded.updates.length === 1 && recorded.updates[0].version === null,
    "no version bump",
  );
  assert(
    Object.keys(recorded.updates[0].update).join() === "logo_checked_at",
    "only checked_at",
  );
});

Deno.test("fresh READY merchants are skipped unless forced", async () => {
  const row = merchant(1, "Tambo", "tambo.pe", {
    logo_etag: "etag-1",
    logo_path: "p",
    logo_status: "READY",
    logo_checked_at: "2026-10-20T00:00:00Z",
  });
  const skipped = setup({ rows: [row] });
  assert((await call(skipped.handler)).body.checked === 0, "skipped");
  const forced = setup({ rows: [row] });
  assert(
    (await call(forced.handler, { force: true })).body.checked === 1,
    "forced",
  );
});

Deno.test("not_found marks NOT_FOUND without touching paths", async () => {
  const { handler, recorded } = setup({
    rows: [merchant(1, "Tambo", "tambo.pe")],
    logoDev: () =>
      Response.json({ object: "brand", status: "not_found" }, { status: 404 }),
  });
  const { body } = await call(handler);
  assert(body.notFound === 1, "notFound");
  assert(recorded.updates[0].update.logo_status === "NOT_FOUND", "status");
  assert(!("logo_path" in recorded.updates[0].update), "paths untouched");
});

Deno.test("not_indexed marks NOT_INDEXED", async () => {
  const { handler, recorded } = setup({
    rows: [merchant(1, "Tambo", "tambo.pe")],
    logoDev: () =>
      Response.json({ object: "brand", status: "not_indexed" }, {
        status: 202,
        headers: { "retry-after": "30" },
      }),
  });
  const { body } = await call(handler);
  assert(body.notIndexed === 1, "notIndexed");
  assert(recorded.updates[0].update.logo_status === "NOT_INDEXED", "status");
});

Deno.test("429 honors Retry-After and retries", async () => {
  const { handler, recorded } = setup({
    rows: [merchant(1, "Tambo", "tambo.pe")],
    logoDev: (_domain, attempt) =>
      attempt === 1
        ? new Response("slow down", {
          status: 429,
          headers: { "retry-after": "3" },
        })
        : logoJson(),
  });
  const { body } = await call(handler);
  assert(body.updated === 1, "updated after retry");
  assert(recorded.logoDevCalls.length === 2, "two attempts");
  assert(recorded.sleeps[0] === 3_000, "waited Retry-After");
});

Deno.test("persistent 429 ends as failed", async () => {
  const { handler, recorded } = setup({
    rows: [merchant(1, "Tambo", "tambo.pe")],
    logoDev: () => new Response("", { status: 429 }),
  });
  const { body } = await call(handler);
  assert(
    body.failed === 1 && recorded.logoDevCalls.length === 3,
    "three attempts then failed",
  );
  assert(recorded.updates[0].update.logo_status === "FAILED", "FAILED");
});

Deno.test("oversize image fails and leaves paths untouched", async () => {
  const { handler, recorded } = setup({
    rows: [merchant(1, "Tambo", "tambo.pe")],
    download: () => imageResponse(600 * 1024),
  });
  const { body } = await call(handler);
  assert(
    body.failed === 1 && body.items[0].error === "LOGO_DOWNLOAD_TOO_LARGE",
    "too large",
  );
  assert(recorded.uploads.length === 0, "nothing uploaded");
  assert(!("logo_path" in recorded.updates[0].update), "paths untouched");
});

Deno.test("non-image download fails", async () => {
  const { handler } = setup({
    rows: [merchant(1, "Tambo", "tambo.pe")],
    download: () =>
      new Response("<html>", { headers: { "content-type": "text/html" } }),
  });
  assert(
    (await call(handler)).body.items[0].error === "LOGO_DOWNLOAD_NOT_IMAGE",
    "not image",
  );
});

Deno.test("dryRun uploads to preview/ and never updates the database", async () => {
  const { handler, recorded } = setup({
    rows: [merchant(1, "Tambo", "tambo.pe")],
  });
  const { body } = await call(handler, { dryRun: true });
  assert(
    body.dryRun === true && body.updated === 1,
    "reported as would-update",
  );
  assert(
    recorded.uploads.every((upload) =>
      upload.path.startsWith("preview/") && upload.upsert
    ),
    "preview uploads",
  );
  assert(recorded.updates.length === 0, "no database writes");
});

Deno.test("shared domain downloads once and updates both merchants", async () => {
  const { handler, recorded } = setup({
    rows: [
      merchant(1, "Google One", "google.com"),
      merchant(2, "Google AI Pro", "google.com"),
    ],
  });
  const { body } = await call(handler);
  assert(body.checked === 1 && body.updated === 1, "one domain");
  assert(
    recorded.logoDevCalls.length === 1 && recorded.downloads.length === 2,
    "one lookup, light + dark",
  );
  assert(
    recorded.updates.length === 1 && recorded.updates[0].ids.length === 2,
    "both merchants updated",
  );
  assert(body.items[0].merchants.length === 2, "both listed");
});

Deno.test("MANUAL merchants are never selected", async () => {
  // The store filters logo_source != MANUAL; verify the query asks for it.
  const requested: string[] = [];
  const { createLogoStore } = await import("./store.ts");
  const store = createLogoStore(
    "https://x.supabase.co",
    SERVICE_KEY,
    ((input: string | URL | Request) => {
      requested.push(String(input));
      return Promise.resolve(Response.json([]));
    }) as typeof fetch,
  );
  await store.listMerchants(null);
  await store.updateMerchants(["00000000-0000-0000-0001-000000000001"], {
    logo_checked_at: "x",
  }, null);
  assert(
    requested.every((url) => url.includes("logo_source=neq.MANUAL")),
    "MANUAL excluded in select and update",
  );
});

Deno.test("etagSlug produces path-safe names", () => {
  assert(etagSlug('W/"AbC_123"') === "w-abc-123", "slug");
  assert(etagSlug('""') === "", "empty");
});

Deno.test("a non-matching key is accepted only when Supabase verifies it as a service key", async () => {
  const usedKeys: string[] = [];
  const fakeStore = (key: string): LogoStore => ({
    listMerchants: () => {
      usedKeys.push(key);
      return Promise.resolve([]);
    },
    nextVersion: () => Promise.resolve(1),
    updateMerchants: () => Promise.resolve(),
    uploadImage: () => Promise.resolve(),
  });
  const handler = createMerchantLogoSyncHandler(
    {
      fetchImpl: (() => Promise.reject(new Error("unused"))) as typeof fetch,
      sleep: () => Promise.resolve(),
      random: () => 0,
      now: () => new Date("2026-10-28T12:00:00Z"),
      store: fakeStore("env"),
      verifyServiceKey: (key) => Promise.resolve(key === "legacy-service-jwt"),
      storeForKey: fakeStore,
    },
    { serviceRoleKey: SERVICE_KEY, logoDevSecretKey: SECRET, concurrency: 3 },
  );

  const accepted = await call(handler, {}, "legacy-service-jwt");
  assert(accepted.status === 200, "verified key accepted");
  assert(
    usedKeys.length === 1 && usedKeys[0] === "legacy-service-jwt",
    "store uses the verified key",
  );

  const rejected = await call(handler, {}, "anon-key");
  assert(rejected.status === 401, "unverified key rejected");
  assert(usedKeys.length === 1, "rejected call never touches the store");
});

Deno.test("parses the v2 data envelope", async () => {
  const { handler, recorded } = setup({
    rows: [merchant(1, "Netflix", "netflix.com")],
    logoDev: () =>
      Response.json({
        data: {
          object: "image",
          status: "ready",
          url: "https://img.logo.dev/signed?token=abc",
          etag: "etag-v2",
        },
        metadata: { request_id: "r1" },
      }),
  });
  const { body } = await call(handler);
  assert(body.updated === 1, "updated from data envelope");
  assert(recorded.updates[0].update.logo_etag === "etag-v2", "etag stored");
});

Deno.test("previewDomains downloads to preview/ without touching merchants", async () => {
  const { handler, recorded } = setup({
    rows: [merchant(1, "Netflix", "netflix.com")],
  });
  const { status, body } = await call(handler, {
    dryRun: true,
    previewDomains: ["viabcp.com", "yape.com.pe"],
  });
  assert(status === 200 && body.updated === 2, "both previewed");
  assert(
    recorded.logoDevCalls.join(",") === "viabcp.com,yape.com.pe",
    "only requested domains",
  );
  assert(recorded.updates.length === 0, "no database writes");
  assert(
    recorded.uploads.every((upload) => upload.path.startsWith("preview/")),
    "preview prefix",
  );
});

Deno.test("previewDomains requires dryRun", async () => {
  const { handler, recorded } = setup({ rows: [] });
  const { status } = await call(handler, { previewDomains: ["viabcp.com"] });
  assert(
    status === 400 && recorded.logoDevCalls.length === 0,
    "rejected without dryRun",
  );
});
