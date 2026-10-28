// Supabase REST/Storage access with the service role key (plain fetch, like the other functions).
export type MerchantRow = {
  id: string;
  name: string;
  domain: string;
  logo_etag: string | null;
  logo_path: string | null;
  logo_dark_path: string | null;
  logo_status: string | null;
  logo_checked_at: string | null;
};

export type LogoUpdate = {
  logo_path?: string;
  logo_dark_path?: string;
  logo_etag?: string;
  logo_source?: "LOGO_DEV";
  logo_status?: "READY" | "NOT_FOUND" | "NOT_INDEXED" | "FAILED";
  logo_checked_at: string;
};

export type LogoStore = {
  listMerchants(merchantIds: string[] | null): Promise<MerchantRow[]>;
  nextVersion(): Promise<number>;
  updateMerchants(
    ids: string[],
    update: LogoUpdate,
    version: number | null,
  ): Promise<void>;
  uploadImage(
    path: string,
    bytes: Uint8Array,
    contentType: string,
    upsert: boolean,
  ): Promise<void>;
};

const BUCKET = "merchant-logos";

// Only service keys may list Auth users, so a 200 here proves the caller holds one.
export async function verifyServiceKey(
  supabaseUrl: string,
  key: string,
  fetchImpl: typeof fetch = fetch,
): Promise<boolean> {
  const response = await fetchImpl(
    `${supabaseUrl.replace(/\/$/, "")}/auth/v1/admin/users?page=1&per_page=1`,
    {
      headers: { apikey: key, authorization: `Bearer ${key}` },
      signal: AbortSignal.timeout(10_000),
    },
  );
  await response.body?.cancel();
  return response.ok;
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function createLogoStore(
  supabaseUrl: string,
  serviceRoleKey: string,
  fetchImpl: typeof fetch = fetch,
): LogoStore {
  const base = supabaseUrl.replace(/\/$/, "");
  const headers = {
    apikey: serviceRoleKey,
    authorization: `Bearer ${serviceRoleKey}`,
  };

  async function rest(path: string, init: RequestInit = {}): Promise<Response> {
    const response = await fetchImpl(`${base}/rest/v1/${path}`, {
      ...init,
      signal: AbortSignal.timeout(15_000),
      headers: {
        ...headers,
        "content-type": "application/json",
        ...(init.headers ?? {}),
      },
    });
    if (!response.ok) {
      await response.body?.cancel();
      throw new Error(
        `REST ${init.method ?? "GET"} failed with ${response.status}`,
      );
    }
    return response;
  }

  return {
    async listMerchants(merchantIds) {
      let query =
        "merchant_services?select=id,name,domain,logo_etag,logo_path,logo_dark_path,logo_status,logo_checked_at" +
        "&is_system=eq.true&deleted_at=is.null&domain=not.is.null&logo_source=neq.MANUAL&order=domain.asc,id.asc";
      if (merchantIds) {
        if (!merchantIds.every((id) => UUID.test(id))) {
          throw new Error("Invalid merchant id");
        }
        query += `&id=in.(${merchantIds.join(",")})`;
      }
      return await (await rest(query)).json() as MerchantRow[];
    },
    async nextVersion() {
      const rows = await (await rest(
        "merchant_services?select=version&order=version.desc&limit=1",
      )).json() as { version: number }[];
      return Number(rows[0]?.version ?? 0) + 1;
    },
    async updateMerchants(ids, update, version) {
      if (!ids.every((id) => UUID.test(id))) {
        throw new Error("Invalid merchant id");
      }
      const body = version === null
        ? update
        : { ...update, version, updated_at: new Date().toISOString() };
      await rest(
        `merchant_services?id=in.(${ids.join(",")})&logo_source=neq.MANUAL`,
        {
          method: "PATCH",
          headers: { prefer: "return=minimal" },
          body: JSON.stringify(body),
        },
      );
    },
    async uploadImage(path, bytes, contentType, upsert) {
      const response = await fetchImpl(
        `${base}/storage/v1/object/${BUCKET}/${path}`,
        {
          method: "POST",
          signal: AbortSignal.timeout(20_000),
          headers: {
            ...headers,
            "content-type": contentType,
            "cache-control": "max-age=31536000",
            "x-upsert": upsert ? "true" : "false",
          },
          body: bytes as unknown as BodyInit,
        },
      );
      if (response.ok) {
        await response.body?.cancel();
        return;
      }
      const text = await response.text().catch(() => "");
      // Objects are keyed by etag, so an existing object already holds this exact version.
      if (
        !upsert &&
        (response.status === 409 || /duplicate|already exists/i.test(text))
      ) return;
      throw new Error(`Storage upload failed with ${response.status}`);
    },
  };
}
