import { createDeleteAccountHandler, type Dependencies, type Receipt } from "./index.ts";

const owner = "a1111111-1111-4111-8111-111111111111";
const id = "a2222222-2222-4222-8222-222222222222";
const secret = "ab".repeat(32);
const jwt = (amr: unknown = [{ method: "password", timestamp: 1000 }], sub = owner) => `header.${btoa(JSON.stringify({ sub, amr, iat: 1000 }))}.signature`;
function assert(value: unknown, message = "Assertion failed"): asserts value { if (!value) throw new Error(message); }
function fixture(overrides: Partial<Dependencies> = {}) {
  let receipt: Receipt | null = null;
  let exists = true;
  const calls: string[] = [];
  const deps: Dependencies = {
    now: () => 1100,
    user: () => Promise.resolve(exists ? { id: owner } : null),
    receipt: () => Promise.resolve(receipt),
    purge: () => { calls.push("purge"); receipt = { state: "data_deleted", user_id: owner }; return Promise.resolve(); },
    deleteUser: () => { calls.push("deleteAuth"); exists = false; return Promise.resolve(); },
    userExists: () => Promise.resolve(exists),
    complete: () => { calls.push("complete"); receipt = { state: "completed", user_id: null }; return Promise.resolve(); },
    ...overrides,
  };
  const handler = createDeleteAccountHandler(deps);
  const request = (token = jwt(), confirm = true) => new Request("http://localhost/delete-account", {
    method: "POST", headers: { Authorization: `Bearer ${token}` }, body: JSON.stringify({ request_id: id, secret, confirm }),
  });
  return { handler, request, calls };
}
Deno.test("completed then idempotent receipt reconciliation after Auth is gone", async () => {
  const f = fixture();
  assert((await (await f.handler(f.request())).json()).status === "completed");
  assert((await (await f.handler(f.request("expired.jwt.token"))).json()).status === "completed");
  assert(f.calls.join(",") === "purge,deleteAuth,complete");
});
Deno.test("no JWT is 401, invalid JWT is 401 and never purges", async () => {
  const f = fixture({ user: () => Promise.resolve(null) });
  assert((await f.handler(new Request("http://localhost", { method: "POST" }))).status === 401);
  assert((await f.handler(f.request())).status === 401);
  assert(f.calls.length === 0);
});
Deno.test("reauth requires password AMR timestamp, not refreshed iat or editable metadata", async () => {
  const f = fixture();
  for (const amr of [[], [{ method: "password", timestamp: 1 }], [{ method: "recovery", timestamp: 1000 }], [{ method: "password", timestamp: 1200 }]]) {
    assert((await f.handler(f.request(jwt(amr)))).status === 403);
  }
  assert(f.calls.length === 0);
});
Deno.test("subject mismatch and absent explicit confirmation cannot delete", async () => {
  const f = fixture();
  assert((await f.handler(f.request(jwt(undefined, "different-owner")))).status === 403);
  assert((await f.handler(f.request(jwt(), false))).status === 400);
  assert(f.calls.length === 0);
});
Deno.test("purge failure is error and does not delete Auth", async () => {
  const f = fixture({ purge: () => Promise.reject(new Error("db unavailable")) });
  const r = await f.handler(f.request());
  assert(r.status === 503 && (await r.json()).status === "error");
  assert(!f.calls.includes("deleteAuth"));
});
Deno.test("Auth failure is pending; retry skips purge and accepts prior authorization", async () => {
  let failures = 1;
  const f = fixture({ deleteUser: () => failures-- > 0 ? Promise.reject(new Error("Auth down")) : Promise.resolve() });
  const first = await f.handler(f.request());
  assert(first.status === 202 && (await first.json()).status === "pending");
  assert((await (await f.handler(f.request(jwt([])))).json()).status === "completed");
  assert(f.calls.filter((x) => x === "purge").length === 1);
});
Deno.test("lost deleteAuth response checks missing user before completion", async () => {
  const f = fixture({ deleteUser: () => Promise.reject(new Error("timeout")), userExists: () => Promise.resolve(false) });
  assert((await (await f.handler(f.request())).json()).status === "completed");
});
Deno.test("unknown Auth status and receipt persistence failure stay pending", async () => {
  for (const overrides of [
    { deleteUser: () => Promise.reject(new Error("timeout")), userExists: () => Promise.reject(new Error("network")) },
    { complete: () => Promise.reject(new Error("db down")) },
  ]) {
    const f = fixture(overrides);
    const r = await f.handler(f.request());
    assert(r.status === 202 && (await r.json()).status === "pending");
  }
});
Deno.test("pending receipt cannot be used to delete a different authenticated user", async () => {
  const f = fixture({ receipt: () => Promise.resolve({ state: "data_deleted", user_id: "someone-else" }) });
  assert((await f.handler(f.request())).status === 403);
  assert(f.calls.length === 0);
});
Deno.test("missing Auth after a lost completion response reconciles only a matching receipt", async () => {
  const f = fixture({ receipt: () => Promise.resolve({ state: "data_deleted", user_id: owner }), user: () => Promise.resolve(null), userExists: () => Promise.resolve(false) });
  assert((await (await f.handler(f.request())).json()).status === "completed");
  assert(f.calls.join(",") === "complete");
});
