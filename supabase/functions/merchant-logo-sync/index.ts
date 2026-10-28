import {
  downloadVariant,
  type LogoDevDependencies,
  lookupLogo,
} from "./logo-dev.ts";
import {
  createLogoStore,
  type LogoStore,
  type MerchantRow,
  verifyServiceKey,
} from "./store.ts";

export type MerchantLogoSyncConfig = {
  serviceRoleKey: string;
  logoDevSecretKey: string;
  concurrency: number;
};

export type MerchantLogoSyncDependencies = LogoDevDependencies & {
  store: LogoStore;
  now: () => Date;
  // Projects on the new API keys expose a different SUPABASE_SERVICE_ROLE_KEY than the legacy
  // service_role JWT, so a non-matching key may still be a valid service key: Supabase Auth decides.
  verifyServiceKey?: (key: string) => Promise<boolean>;
  storeForKey?: (key: string) => LogoStore;
};

type SyncRequest = {
  dryRun: boolean;
  merchantIds: string[] | null;
  force: boolean;
  staleDays: number;
  // Dry-run only: fetch logos for arbitrary domains (e.g. banks bundled in the app) into preview/.
  previewDomains: string[] | null;
};

const DOMAIN =
  /^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$/;

type ItemResult =
  | "updated"
  | "unchanged"
  | "notFound"
  | "notIndexed"
  | "failed";

type SyncItem = {
  domain: string;
  merchants: string[];
  result: ItemResult;
  paths?: string[];
  error?: string;
};

const RETRY_STATUSES = new Set(["PENDING", "NOT_INDEXED", "FAILED"]);

function json(body: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

function constantTimeEquals(left: string, right: string): boolean {
  const leftBytes = new TextEncoder().encode(left);
  const rightBytes = new TextEncoder().encode(right);
  if (leftBytes.length !== rightBytes.length) return false;
  let mismatch = 0;
  for (let index = 0; index < leftBytes.length; index++) {
    mismatch |= leftBytes[index] ^ rightBytes[index];
  }
  return mismatch === 0;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function parseRequest(payload: unknown): SyncRequest | null {
  if (!isRecord(payload)) return null;
  const allowed = new Set([
    "dryRun",
    "merchantIds",
    "force",
    "staleDays",
    "previewDomains",
  ]);
  if (Object.keys(payload).some((key) => !allowed.has(key))) return null;
  const {
    dryRun = false,
    merchantIds = null,
    force = false,
    staleDays = 30,
    previewDomains = null,
  } = payload;
  if (typeof dryRun !== "boolean" || typeof force !== "boolean") return null;
  if (
    typeof staleDays !== "number" || !Number.isFinite(staleDays) ||
    staleDays < 0 || staleDays > 3650
  ) return null;
  if (
    merchantIds !== null &&
    (!Array.isArray(merchantIds) || merchantIds.length > 200 ||
      merchantIds.some((id) => typeof id !== "string"))
  ) return null;
  if (
    previewDomains !== null &&
    (dryRun !== true || merchantIds !== null ||
      !Array.isArray(previewDomains) || previewDomains.length === 0 ||
      previewDomains.length > 30 ||
      previewDomains.some((domain) =>
        typeof domain !== "string" || !DOMAIN.test(domain)
      ))
  ) return null;
  return {
    dryRun,
    merchantIds: merchantIds as string[] | null,
    force,
    staleDays,
    previewDomains: previewDomains as string[] | null,
  };
}

function needsCheck(
  row: MerchantRow,
  request: SyncRequest,
  now: Date,
): boolean {
  if (request.force) return true;
  if (row.logo_status === null || RETRY_STATUSES.has(row.logo_status)) {
    return true;
  }
  if (!row.logo_checked_at) return true;
  const ageMs = now.getTime() - new Date(row.logo_checked_at).getTime();
  return !(ageMs < request.staleDays * 86_400_000);
}

// Object names must satisfy the logo_path CHECK constraint, so reduce the etag to a safe slug.
export function etagSlug(etag: string): string {
  return etag.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "")
    .slice(0, 64);
}

export function createMerchantLogoSyncHandler(
  dependencies: MerchantLogoSyncDependencies,
  config: MerchantLogoSyncConfig,
) {
  async function syncDomain(
    store: LogoStore,
    domain: string,
    merchants: MerchantRow[],
    request: SyncRequest,
    versionProvider: () => Promise<number>,
  ): Promise<SyncItem> {
    const names = merchants.map((merchant) => merchant.name);
    const ids = merchants.map((merchant) => merchant.id);
    const checkedAt = dependencies.now().toISOString();
    const item = (
      result: ItemResult,
      extra: Partial<SyncItem> = {},
    ): SyncItem => ({
      domain,
      merchants: names,
      result,
      ...extra,
    });
    const markStatus = async (
      status: "NOT_FOUND" | "NOT_INDEXED" | "FAILED",
    ) => {
      if (request.dryRun) return;
      await store.updateMerchants(ids, {
        logo_status: status,
        logo_checked_at: checkedAt,
      }, null);
    };

    try {
      const lookup = await lookupLogo(
        domain,
        config.logoDevSecretKey,
        dependencies,
      );
      if (lookup.kind === "not_found") {
        await markStatus("NOT_FOUND");
        return item("notFound");
      }
      if (lookup.kind === "not_indexed") {
        await markStatus("NOT_INDEXED");
        return item("notIndexed");
      }
      if (lookup.kind === "error") {
        await markStatus("FAILED");
        return item("failed", { error: lookup.code });
      }

      const slug = etagSlug(lookup.etag);
      if (!slug) {
        await markStatus("FAILED");
        return item("failed", { error: "LOGO_DEV_INVALID_ETAG" });
      }

      const unchanged = merchants.length > 0 &&
        merchants.every((merchant) =>
          merchant.logo_etag === lookup.etag &&
          merchant.logo_status === "READY" && merchant.logo_path !== null
        );
      if (unchanged) {
        if (!request.dryRun) {
          await store.updateMerchants(
            ids,
            { logo_checked_at: checkedAt },
            null,
          );
        }
        return item("unchanged");
      }

      const light = await downloadVariant(
        lookup.url,
        "light",
        dependencies.fetchImpl,
      );
      if (!light.ok) {
        await markStatus("FAILED");
        return item("failed", { error: light.code });
      }
      const dark = await downloadVariant(
        lookup.url,
        "dark",
        dependencies.fetchImpl,
      );
      if (!dark.ok) {
        await markStatus("FAILED");
        return item("failed", { error: dark.code });
      }

      // dryRun writes to preview/ (overwritable) and leaves merchant_services untouched.
      const prefix = request.dryRun ? "preview" : "logo-dev";
      const lightPath = `${prefix}/${domain}/${slug}.webp`;
      const darkPath = `${prefix}/${domain}/${slug}-dark.webp`;
      await store.uploadImage(
        lightPath,
        light.bytes,
        "image/webp",
        request.dryRun,
      );
      await store.uploadImage(
        darkPath,
        dark.bytes,
        "image/webp",
        request.dryRun,
      );

      if (!request.dryRun) {
        await store.updateMerchants(ids, {
          logo_path: lightPath,
          logo_dark_path: darkPath,
          logo_etag: lookup.etag,
          logo_source: "LOGO_DEV",
          logo_status: "READY",
          logo_checked_at: checkedAt,
        }, await versionProvider());
      }
      return item("updated", { paths: [lightPath, darkPath] });
    } catch {
      // Existing logo paths stay untouched on failure.
      await markStatus("FAILED").catch(() => undefined);
      return item("failed", { error: "SYNC_STEP_FAILED" });
    }
  }

  return async (request: Request): Promise<Response> => {
    if (!new URL(request.url).pathname.endsWith("/merchant-logo-sync")) {
      return json({ outcome: "REJECTED", code: "NOT_FOUND" }, 404);
    }
    if (request.method !== "POST") {
      return json({ outcome: "REJECTED", code: "METHOD_NOT_ALLOWED" }, 405);
    }
    if (
      config.serviceRoleKey.length < 32 || config.logoDevSecretKey.length === 0
    ) {
      return json({
        outcome: "RETRYABLE",
        code: "SERVER_CONFIGURATION_INVALID",
      }, 503);
    }
    const presented = /^Bearer\s+(\S+)$/i.exec(
      request.headers.get("authorization") ?? "",
    )?.[1];
    if (!presented) {
      return json({ outcome: "REJECTED", code: "UNAUTHENTICATED" }, 401);
    }
    let store = dependencies.store;
    if (!constantTimeEquals(presented, config.serviceRoleKey)) {
      const verified = dependencies.verifyServiceKey &&
        await dependencies.verifyServiceKey(presented).catch(() => false);
      if (!verified || !dependencies.storeForKey) {
        return json({ outcome: "REJECTED", code: "UNAUTHENTICATED" }, 401);
      }
      store = dependencies.storeForKey(presented);
    }

    let payload: unknown;
    try {
      const raw = await request.text();
      if (new TextEncoder().encode(raw).byteLength > 16_384) {
        return json({ outcome: "REJECTED", code: "INVALID_REQUEST" }, 400);
      }
      payload = raw.trim().length === 0 ? {} : JSON.parse(raw);
    } catch {
      return json({ outcome: "REJECTED", code: "INVALID_REQUEST" }, 400);
    }
    const parsed = parseRequest(payload);
    if (!parsed) {
      return json({ outcome: "REJECTED", code: "INVALID_REQUEST" }, 400);
    }

    try {
      const now = dependencies.now();
      const groups = new Map<string, MerchantRow[]>();
      if (parsed.previewDomains) {
        for (const domain of parsed.previewDomains) groups.set(domain, []);
      } else {
        const rows = await store.listMerchants(parsed.merchantIds);
        for (const row of rows) {
          const group = groups.get(row.domain) ?? [];
          group.push(row);
          groups.set(row.domain, group);
        }
      }
      const pending = [...groups.entries()].filter(([, group]) =>
        parsed.previewDomains !== null ||
        group.some((row) => needsCheck(row, parsed, now))
      );

      // One shared version per run, fetched lazily so no-op runs never read it.
      let versionPromise: Promise<number> | null = null;
      const versionProvider = () => (versionPromise ??= store.nextVersion());

      const items: SyncItem[] = [];
      let cursor = 0;
      const worker = async () => {
        while (cursor < pending.length) {
          const [domain, group] = pending[cursor++];
          const result = await syncDomain(
            store,
            domain,
            group,
            parsed,
            versionProvider,
          );
          items.push(result);
          console.log(JSON.stringify({
            event: "merchant_logo_sync_item",
            domain,
            result: result.result,
            error: result.error,
            dryRun: parsed.dryRun,
          }));
        }
      };
      await Promise.all(
        Array.from({
          length: Math.max(1, Math.min(config.concurrency, pending.length)),
        }, worker),
      );
      items.sort((left, right) => left.domain.localeCompare(right.domain));

      const count = (result: ItemResult) =>
        items.filter((entry) => entry.result === result).length;
      const summary = {
        outcome: "COMPLETED",
        dryRun: parsed.dryRun,
        checked: items.length,
        updated: count("updated"),
        unchanged: count("unchanged"),
        notFound: count("notFound"),
        notIndexed: count("notIndexed"),
        failed: count("failed"),
        items,
      };
      console.log(
        JSON.stringify({
          event: "merchant_logo_sync_done",
          ...summary,
          items: undefined,
        }),
      );
      return json(summary);
    } catch {
      return json({ outcome: "RETRYABLE", code: "SYNC_UNAVAILABLE" }, 503);
    }
  };
}

if (import.meta.main) {
  const required = (name: string): string => {
    const value = Deno.env.get(name);
    if (!value) throw new Error(`Missing server configuration: ${name}`);
    return value;
  };
  const supabaseUrl = required("SUPABASE_URL");
  const serviceRoleKey = required("SUPABASE_SERVICE_ROLE_KEY");
  const handler = createMerchantLogoSyncHandler(
    {
      fetchImpl: fetch,
      sleep: (milliseconds) =>
        new Promise((resolve) => setTimeout(resolve, milliseconds)),
      random: Math.random,
      now: () => new Date(),
      store: createLogoStore(supabaseUrl, serviceRoleKey),
      verifyServiceKey: (key) => verifyServiceKey(supabaseUrl, key),
      storeForKey: (key) => createLogoStore(supabaseUrl, key),
    },
    {
      serviceRoleKey,
      logoDevSecretKey: required("LOGO_DEV_SECRET_KEY"),
      concurrency: 3,
    },
  );
  Deno.serve(handler);
}
