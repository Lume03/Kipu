/** HU-06 B: never log credentials, request capabilities, financial data or provider bodies. */
export type Receipt = { state: "data_deleted" | "completed"; user_id: string | null };
export type Dependencies = {
  user: (jwt: string) => Promise<{ id: string } | null>;
  receipt: (id: string, hash: string) => Promise<Receipt | null>;
  purge: (user: string, id: string, hash: string) => Promise<void | { state: string; request_id?: string; secret_hash?: string }>;
  deleteUser: (user: string) => Promise<void>;
  userExists: (user: string) => Promise<boolean>;
  complete: (id: string, hash: string) => Promise<void>;
  now?: () => number;
};
const headers = { "content-type": "application/json", "Cache-Control": "private, no-store" };
const json = (status: string, code = 200, reason?: string) => new Response(JSON.stringify({ status, reason }), { status: code, headers });
export async function sha256(value: string): Promise<string> {
  return Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value))))
    .map((b) => b.toString(16).padStart(2, "0")).join("");
}
// Only call after Auth's GET /user has verified this exact JWT. `iat` is not
// authentication time: refreshes change it. AMR password timestamp does not.
export function recentPassword(jwt: string, subject: string, now: number): boolean {
  try {
    const part = jwt.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const claims = JSON.parse(atob(part));
    return claims.sub === subject && Array.isArray(claims.amr) && claims.amr.some(
      (a: { method: string; timestamp: number }) => a.method === "password" &&
        Number.isFinite(a.timestamp) && now - a.timestamp >= 0 && now - a.timestamp <= 300,
    );
  } catch { return false; }
}
export function createDeleteAccountHandler(deps: Dependencies) {
  return async (request: Request): Promise<Response> => {
    if (request.method !== "POST") return json("error", 405, "method_not_allowed");
    const jwt = request.headers.get("Authorization")?.match(/^Bearer (\S+)$/i)?.[1];
    if (!jwt) return json("error", 401, "authentication_required");
    let id: string, secret: string;
    try {
      const body = await request.json();
      id = body.request_id; secret = body.secret;
      if (body.confirm !== true || typeof id !== "string" ||
        !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id) ||
        typeof secret !== "string" || !/^[a-f0-9]{64}$/.test(secret)) return json("error", 400, "explicit_confirmation_required");
    } catch { return json("error", 400, "invalid_request"); }
    let dataDeleted = false;
    try {
      let hash = await sha256(secret);
      const receipt = await deps.receipt(id, hash);
      // Possession of the 256-bit capability only reconciles an already authorized
      // request after Auth is gone; it cannot initiate a deletion or change its owner.
      if (receipt?.state === "completed") return json("completed");
      const user = await deps.user(jwt);
      if (receipt && !user && receipt.user_id && !await deps.userExists(receipt.user_id)) {
        dataDeleted = true;
        await deps.complete(id, hash);
        return json("completed");
      }
      if (!user) return json("error", 401, "authentication_required");
      if (receipt && receipt.user_id !== user.id) return json("error", 403, "owner_mismatch");
      if (!receipt && !recentPassword(jwt, user.id, (deps.now ?? (() => Date.now() / 1000))())) {
        return json("error", 403, "recent_reauthentication_required");
      }
      if (!receipt) {
        const purged = await deps.purge(user.id, id, hash);
        if (purged?.state === "in_progress") return json("in_progress", 202, "another_request_active");
        // Canonical credentials stay inside Edge. The caller's capability is an
        // alias for reconciliation; never rotate or expose the original secret.
        if (purged?.request_id && purged?.secret_hash) {
          id = purged.request_id; hash = purged.secret_hash;
        }
        if (purged?.state === "completed") return json("completed");
      }
      dataDeleted = true;
      try { await deps.deleteUser(user.id); } catch {
        // A previous timeout may have removed Auth successfully. Distinguish a
        // missing user from provider/network errors; never infer success from 5xx.
        if (await deps.userExists(user.id)) return json("pending", 202, "auth_deletion_retry_required");
      }
      await deps.complete(id, hash);
      return json("completed");
    } catch {
      return dataDeleted ? json("pending", 202, "auth_deletion_retry_required") : json("error", 503, "retry_required");
    }
  };
}

export function productionDependencies(): Dependencies {
  const base = Deno.env.get("SUPABASE_URL")!;
  const service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const serviceHeaders = { apikey: service, Authorization: `Bearer ${service}`, "content-type": "application/json" };
  const rpc = async (name: string, body: unknown) => {
    const r = await fetch(`${base}/rest/v1/rpc/${name}`, {
      method: "POST", headers: serviceHeaders, body: JSON.stringify(body), signal: AbortSignal.timeout(15000),
    });
    if (!r.ok) throw new Error("Deletion persistence unavailable");
    return r.status === 204 ? null : await r.json();
  };
  const authRequest = (path: string, method: string, jwt = service) => fetch(`${base}/auth/v1/${path}`, {
    method, headers: { apikey: service, Authorization: `Bearer ${jwt}` }, signal: AbortSignal.timeout(10000),
  });
  return {
    user: async (jwt) => {
      const r = await authRequest("user", "GET", jwt);
      if (r.status === 401 || r.status === 403 || r.status === 404) return null;
      if (!r.ok) throw new Error("Auth unavailable");
      return await r.json();
    },
    receipt: (id, hash) => rpc("account_deletion_status", { p_request_id: id, p_secret_hash: hash }),
    purge: (user, id, hash) => rpc("prepare_account_deletion", { p_user_id: user, p_request_id: id, p_secret_hash: hash }),
    // The REST equivalent of auth.admin.deleteUser, service credential stays in Edge.
    deleteUser: async (user) => { const r = await authRequest(`admin/users/${user}`, "DELETE"); if (!r.ok) throw new Error("Auth deletion failed"); },
    userExists: async (user) => {
      const r = await authRequest(`admin/users/${user}`, "GET");
      if (r.status === 404) return false;
      if (!r.ok) throw new Error("Cannot determine Auth state");
      return true;
    },
    complete: async (id, hash) => { await rpc("complete_account_deletion", { p_request_id: id, p_secret_hash: hash }); },
  };
}
if (import.meta.main) Deno.serve(createDeleteAccountHandler(productionDependencies()));
