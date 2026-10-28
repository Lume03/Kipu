// Server-side Logo.dev client. The secret key never leaves the Edge Function runtime.
export const LOGO_DEV_API = "https://api.logo.dev/v2/brands/logo";
export const MAX_LOGO_BYTES = 512 * 1024;

export type LogoLookup =
  | { kind: "ok"; url: string; etag: string }
  | { kind: "not_found" }
  | { kind: "not_indexed"; retryAfterSeconds: number | null }
  | { kind: "error"; code: string };

export type LogoDevDependencies = {
  fetchImpl: typeof fetch;
  sleep: (milliseconds: number) => Promise<void>;
  random: () => number;
};

const MAX_ATTEMPTS = 3;
const MAX_WAIT_MS = 15_000;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function headerSeconds(response: Response, ...names: string[]): number | null {
  for (const name of names) {
    const raw = response.headers.get(name);
    const parsed = raw === null ? NaN : Number(raw);
    if (Number.isFinite(parsed) && parsed >= 0) return parsed;
  }
  return null;
}

// v2 wraps the image in `{data: {...}}`; older shapes (`{logo: {...}}` or bare) are still accepted.
function unwrap(payload: unknown): Record<string, unknown> | null {
  if (!isRecord(payload)) return null;
  if (isRecord(payload.data)) return payload.data;
  if (isRecord(payload.logo)) return payload.logo;
  return payload;
}

export function parseLogoPayload(
  payload: unknown,
): { url: string; etag: string } | null {
  const candidate = unwrap(payload);
  if (!candidate) return null;
  const url = candidate.url;
  const etag = candidate.etag;
  if (typeof url !== "string" || !url.startsWith("https://")) return null;
  if (typeof etag !== "string" || etag.length === 0) return null;
  return { url, etag };
}

export async function lookupLogo(
  domain: string,
  secretKey: string,
  deps: LogoDevDependencies,
): Promise<LogoLookup> {
  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    let response: Response;
    try {
      response = await deps.fetchImpl(
        `${LOGO_DEV_API}?domain=${encodeURIComponent(domain)}`,
        {
          headers: {
            authorization: `Bearer ${secretKey}`,
            accept: "application/json",
          },
          signal: AbortSignal.timeout(20_000),
        },
      );
    } catch {
      return { kind: "error", code: "LOGO_DEV_UNREACHABLE" };
    }

    if (response.status === 429) {
      await response.body?.cancel();
      if (attempt === MAX_ATTEMPTS) {
        return { kind: "error", code: "LOGO_DEV_RATE_LIMITED" };
      }
      const seconds =
        headerSeconds(response, "retry-after", "ratelimit-reset") ??
          2 ** attempt;
      const wait = Math.min(seconds * 1_000, MAX_WAIT_MS) +
        Math.floor(deps.random() * 500);
      await deps.sleep(wait);
      continue;
    }
    if (response.status === 404) {
      await response.body?.cancel();
      return { kind: "not_found" };
    }
    if (response.status === 202) {
      await response.body?.cancel();
      return {
        kind: "not_indexed",
        retryAfterSeconds: headerSeconds(response, "retry-after"),
      };
    }
    if (!response.ok) {
      await response.body?.cancel();
      return { kind: "error", code: `LOGO_DEV_HTTP_${response.status}` };
    }
    const payload: unknown = await response.json().catch(() => null);
    const status = unwrap(payload)?.status;
    if (status === "not_indexed") {
      return {
        kind: "not_indexed",
        retryAfterSeconds: headerSeconds(response, "retry-after"),
      };
    }
    if (status === "not_found") {
      return { kind: "not_found" };
    }
    const logo = parseLogoPayload(payload);
    return logo
      ? { kind: "ok", ...logo }
      : { kind: "error", code: "LOGO_DEV_INVALID_RESPONSE" };
  }
  return { kind: "error", code: "LOGO_DEV_RATE_LIMITED" };
}

// The signed download URL carries its own authorization: never attach the API key to it.
export function variantUrl(signedUrl: string, theme: "light" | "dark"): string {
  const url = new URL(signedUrl);
  url.searchParams.set("size", "256");
  url.searchParams.set("format", "webp");
  url.searchParams.set("theme", theme);
  return url.toString();
}

export type ImageDownload =
  | { ok: true; bytes: Uint8Array; contentType: string }
  | { ok: false; code: string };

export async function downloadVariant(
  signedUrl: string,
  theme: "light" | "dark",
  fetchImpl: typeof fetch,
): Promise<ImageDownload> {
  let response: Response;
  try {
    response = await fetchImpl(variantUrl(signedUrl, theme), {
      signal: AbortSignal.timeout(20_000),
    });
  } catch {
    return { ok: false, code: "LOGO_DOWNLOAD_UNREACHABLE" };
  }
  if (!response.ok) {
    await response.body?.cancel();
    return { ok: false, code: `LOGO_DOWNLOAD_HTTP_${response.status}` };
  }
  const contentType =
    response.headers.get("content-type")?.split(";")[0].trim().toLowerCase() ??
      "";
  if (!contentType.startsWith("image/")) {
    await response.body?.cancel();
    return { ok: false, code: "LOGO_DOWNLOAD_NOT_IMAGE" };
  }
  const declared = Number(response.headers.get("content-length") ?? 0);
  if (declared > MAX_LOGO_BYTES) {
    await response.body?.cancel();
    return { ok: false, code: "LOGO_DOWNLOAD_TOO_LARGE" };
  }
  const bytes = new Uint8Array(await response.arrayBuffer());
  if (bytes.byteLength === 0) return { ok: false, code: "LOGO_DOWNLOAD_EMPTY" };
  if (bytes.byteLength > MAX_LOGO_BYTES) {
    return { ok: false, code: "LOGO_DOWNLOAD_TOO_LARGE" };
  }
  return { ok: true, bytes, contentType };
}
