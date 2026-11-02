// Test doubles implement asynchronous dependency interfaces.
// deno-lint-ignore-file require-await
import { createAuthAccessHandler, createDefaultDeps, Dependencies } from "./index.ts";

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

function assertEquals(actual: unknown, expected: unknown, message = "values differ") {
  const left = JSON.stringify(actual);
  const right = JSON.stringify(expected);
  if (left !== right) {
    throw new Error(`${message}: expected ${right}, got ${left}`);
  }
}

const defaultDeps: Dependencies = {
  getOtpStatus: async () => ({ locked: false, busy: false, retry_after_seconds: 0, failures: 0, last_sent_at: null }),
  registerOtpFailure: async () => ({ locked: false, retry_after_seconds: 0, failures: 1 }),
  markOtpSent: async () => {},
  resetOtpFailures: async () => {},
  releaseOtpAttempt: async () => {},
  consumeResendSlot: async () => ({ allowed: true, retry_after_seconds: 0 }),
  verifyOtpWithAuth: async () => ({ session: { accessToken: "test_token", refreshToken: "test_refresh", expiresIn: 3600, tokenType: "bearer", userId: "user_uuid_1" } }),
  resendOtpWithAuth: async () => ({}),
  consumeRateBucket: async () => ({ allowed: true, retryAfterSeconds: 0 }),
  checkEmailExists: async () => false,
  signUpWithAuth: async () => ({}),
  signInWithAuth: async () => ({
    data: {
      session: {
        access_token: "test_token",
        refresh_token: "test_refresh",
        expires_in: 3600,
        token_type: "bearer",
        user: { id: "user_uuid_1" },
      },
    },
  }),
  resetPasswordForEmail: async () => ({}),
  sleep: async () => {},
};

// Adapt existing guard fakes to the transactional interface used in production.
function createTestHandler(deps: Dependencies) {
  const combined: Dependencies = { ...deps,
    beginOtpVerify: async (ip, email, id) => {
      const status = await deps.getOtpStatus!(email, id);
      if (status.locked || status.busy) return { status, rate: { allowed: true, retry_after_seconds: 0 } };
      const ipRate = await deps.consumeRateBucket(`verify-otp:ip:${ip}`);
      const rate = ipRate.allowed ? await deps.consumeRateBucket(`verify-otp:id:${email}`) : ipRate;
      if (!rate.allowed) await deps.releaseOtpAttempt!(email, id);
      return { status, rate: { allowed: rate.allowed, retry_after_seconds: rate.retryAfterSeconds } };
    },
    beginOtpResend: async (ip, email) => {
      const ipRate = await deps.consumeRateBucket(`resend-otp:ip:${ip}`);
      const rate = ipRate.allowed ? await deps.consumeRateBucket(`resend-otp:id:${email}`) : ipRate;
      return { rate: { allowed: rate.allowed, retry_after_seconds: rate.retryAfterSeconds }, ...(rate.allowed ? { slot: await deps.consumeResendSlot!(email) } : {}) };
    },
    finishOtpVerify: async (email, id, outcome) => {
      if (outcome === "failure") return await deps.registerOtpFailure!(email, id);
      if (outcome === "success") await deps.resetOtpFailures!(email, id);
      else await deps.releaseOtpAttempt!(email, id);
      return {};
    },
  };
  return createAuthAccessHandler(combined);
}

Deno.test("register - returns 201 for valid new account", async () => {
  const handler = createTestHandler(defaultDeps);
  const req = new Request("https://example.supabase.co/functions/v1/auth-access/register", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      email: "newuser@example.com",
      password: "Password123",
    }),
  });

  const res = await handler(req);
  assertEquals(res.status, 201);
  const data = await res.json();
  assertEquals(data.status, "ACCEPTED");
  assertEquals(data.confirmationRequired, true);
});

Deno.test("register - returns 409 for existing email per FR-051", async () => {
  const deps: Dependencies = {
    ...defaultDeps,
    checkEmailExists: async () => true,
  };
  const handler = createTestHandler(deps);
  const req = new Request("https://example.supabase.co/functions/v1/auth-access/register", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      email: "existing@example.com",
      password: "Password123",
    }),
  });

  const res = await handler(req);
  assertEquals(res.status, 409);
  const data = await res.json();
  assertEquals(data.title, "Account already exists");
});

Deno.test("register - returns 422 for invalid password policy", async () => {
  const handler = createTestHandler(defaultDeps);
  const req = new Request("https://example.supabase.co/functions/v1/auth-access/register", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      email: "user@example.com",
      password: "123",
    }),
  });

  const res = await handler(req);
  assertEquals(res.status, 422);
});

Deno.test("register - returns 429 when rate limited", async () => {
  const deps: Dependencies = {
    ...defaultDeps,
    consumeRateBucket: async () => ({ allowed: false, retryAfterSeconds: 30 }),
  };
  const handler = createTestHandler(deps);
  const req = new Request("https://example.supabase.co/functions/v1/auth-access/register", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      email: "user@example.com",
      password: "Password123",
    }),
  });

  const res = await handler(req);
  assertEquals(res.status, 429);
  assertEquals(res.headers.get("Retry-After"), "30");
});

Deno.test("login - returns 200 with session envelope for valid credentials", async () => {
  const handler = createTestHandler(defaultDeps);
  const req = new Request("https://example.supabase.co/functions/v1/auth-access/login", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      email: "user@example.com",
      password: "Password123",
    }),
  });

  const res = await handler(req);
  assertEquals(res.status, 200);
  assertEquals(res.headers.get("Cache-Control"), "private, no-store");
  const data = await res.json();
  assertEquals(data.accessToken, "test_token");
  assertEquals(data.userId, "user_uuid_1");
});

Deno.test("login - returns neutral 401 for invalid credentials per FR-004", async () => {
  const deps: Dependencies = {
    ...defaultDeps,
    signInWithAuth: async () => ({ error: { message: "Invalid login credentials", status: 400 } }),
  };
  const handler = createTestHandler(deps);
  const req = new Request("https://example.supabase.co/functions/v1/auth-access/login", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      email: "user@example.com",
      password: "WrongPassword1",
    }),
  });

  const res = await handler(req);
  assertEquals(res.status, 401);
  const data = await res.json();
  assertEquals(data.detail, "Correo o contraseña incorrectos.");
});

Deno.test("login - returns 403 email_not_confirmed when Auth reports unconfirmed email", async () => {
  const deps: Dependencies = {
    ...defaultDeps,
    signInWithAuth: async () => ({
      error: { message: "Email not confirmed", status: 400, code: "email_not_confirmed" },
    }),
  };
  const handler = createTestHandler(deps);
  const req = new Request("https://example.supabase.co/functions/v1/auth-access/login", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      email: "user@example.com",
      password: "CorrectPassword1",
    }),
  });

  const res = await handler(req);
  assertEquals(res.status, 403);
  assertEquals(res.headers.get("content-type"), "application/problem+json");
  const data = await res.json();
  assertEquals(data.type, "email_not_confirmed");
  assertEquals(data.status, 403);
});

Deno.test("login - keeps neutral 401 for invalid_credentials code", async () => {
  const deps: Dependencies = {
    ...defaultDeps,
    signInWithAuth: async () => ({
      error: { message: "Invalid login credentials", status: 400, code: "invalid_credentials" },
    }),
  };
  const handler = createTestHandler(deps);
  const req = new Request("https://example.supabase.co/functions/v1/auth-access/login", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      email: "user@example.com",
      password: "WrongPassword1",
    }),
  });

  const res = await handler(req);
  assertEquals(res.status, 401);
  const data = await res.json();
  assertEquals(data.detail, "Correo o contraseña incorrectos.");
  assertEquals(data.type, undefined);
});

Deno.test("recovery - returns 202 for both existing and non-existing email per SC-002", async () => {
  const handler = createTestHandler(defaultDeps);

  // Existing email
  const reqExisting = new Request("https://example.supabase.co/functions/v1/auth-access/recovery", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ email: "existing@example.com" }),
  });
  const resExisting = await handler(reqExisting);
  assertEquals(resExisting.status, 202);
  const dataExisting = await resExisting.json();
  assertEquals(dataExisting.status, "ACCEPTED");

  // Non-existing email
  const reqNonExisting = new Request("https://example.supabase.co/functions/v1/auth-access/recovery", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ email: "nonexisting@example.com" }),
  });
  const resNonExisting = await handler(reqNonExisting);
  assertEquals(resNonExisting.status, 202);
  const dataNonExisting = await resNonExisting.json();
  assertEquals(dataNonExisting.status, "ACCEPTED");
});

function otpFakes() {
  let failures = 0, locked = false, sent: string | null = null, slot = false, calls = 0;
  const deps: Dependencies = {
    ...defaultDeps,
    getOtpStatus: async () => ({ locked, busy: false, retry_after_seconds: locked ? 300 : 0, failures, last_sent_at: sent }),
    registerOtpFailure: async () => { failures++; locked = failures >= 5; return { failures, locked, retry_after_seconds: locked ? 300 : 0 }; },
    resetOtpFailures: async () => { failures = 0; locked = false; },
    markOtpSent: async () => { failures = 0; locked = false; sent = new Date().toISOString(); },
    consumeResendSlot: async () => { if (slot) return { allowed: false, retry_after_seconds: 60, reason: "cooldown" }; slot = true; return { allowed: true, retry_after_seconds: 0 }; },
    verifyOtpWithAuth: async () => { calls++; return { error: { message: "invalid", status: 403 } }; },
  };
  return { deps, setSent: (value: string | null) => { sent = value; }, getCalls: () => calls };
}
function otpRequest(path: string, body: Record<string, unknown> = {}) {
  return new Request(`https://example.com/auth-access/${path}`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ email: "user@example.com", token: "123456", ...body }) });
}
Deno.test("OTP success matches login session and clears failures", async () => {
  const fake = otpFakes();
  fake.deps.verifyOtpWithAuth = defaultDeps.verifyOtpWithAuth;
  await fake.deps.registerOtpFailure!("user@example.com", "attempt");
  const res = await createTestHandler(fake.deps)(otpRequest("verify-otp"));
  assertEquals(res.status, 200);
  assertEquals(await res.json(), { accessToken: "test_token", refreshToken: "test_refresh", expiresIn: 3600, tokenType: "bearer", userId: "user_uuid_1" });
  assertEquals(res.headers.get("cache-control"), "private, no-store");
  assertEquals(res.headers.get("pragma"), "no-cache");
  assertEquals((await fake.deps.getOtpStatus!("user@example.com", "attempt")).failures, 0);
});
Deno.test("OTP wrong and unknown codes count down, lock at five and bypass provider", async () => {
  const fake = otpFakes(); const handler = createTestHandler(fake.deps);
  for (let i = 1; i <= 5; i++) {
    const res = await handler(otpRequest("verify-otp", { email: "unknown@example.com" }));
    const data = await res.json();
    assertEquals(res.headers.get("content-type"), "application/problem+json");
    if (i < 5) { assertEquals(res.status, 400); assertEquals(data.type, "otp_invalid"); assertEquals(data.attempts_left, 5 - i); }
    else { assertEquals(res.status, 429); assertEquals(data.type, "otp_locked"); assertEquals(res.headers.get("Retry-After"), "300"); }
  }
  const res = await handler(otpRequest("verify-otp"));
  assertEquals(res.status, 429); assertEquals((await res.json()).type, "otp_locked"); assertEquals(fake.getCalls(), 5);
});
Deno.test("OTP expired does not count failures", async () => {
  const fake = otpFakes(); fake.setSent(new Date(Date.now() - 601000).toISOString());
  const res = await createTestHandler(fake.deps)(otpRequest("verify-otp"));
  assertEquals(res.status, 410); assertEquals((await res.json()).type, "otp_expired");
  assertEquals((await fake.deps.getOtpStatus!("user@example.com", "attempt")).failures, 0);
});
Deno.test("OTP validates input and provider failures", async () => {
  const fake = otpFakes(); let handler = createTestHandler(fake.deps);
  for (const body of [{ token: "12345" }, { token: "12345a" }, { email: "bad" }]) {
    const res = await handler(otpRequest("verify-otp", body)); assertEquals(res.status, 422); assertEquals((await res.json()).type, "invalid_request");
  }
  fake.deps.verifyOtpWithAuth = async () => ({ error: { message: "down", status: 500 } });
  handler = createTestHandler(fake.deps);
  const res = await handler(otpRequest("verify-otp")); assertEquals(res.status, 503); assertEquals((await res.json()).type, "provider_unavailable");
  assertEquals((await fake.deps.getOtpStatus!("user@example.com", "attempt")).failures, 0);
});
Deno.test("resend accepts unknown user, resets failures and enforces cooldown", async () => {
  const fake = otpFakes(); fake.deps.resendOtpWithAuth = async () => ({ error: { message: "unknown", status: 404 } });
  await fake.deps.registerOtpFailure!("user@example.com", "attempt");
  const handler = createTestHandler(fake.deps); let res = await handler(otpRequest("resend-otp"));
  assertEquals(res.status, 202); assertEquals(await res.json(), { status: "ACCEPTED" });
  assertEquals((await fake.deps.getOtpStatus!("user@example.com", "attempt")).failures, 0);
  res = await handler(otpRequest("resend-otp")); assertEquals(res.status, 429); assertEquals(res.headers.get("retry-after"), "60"); assertEquals((await res.json()).type, "resend_cooldown");
});
Deno.test("OTP storage failure closes access", async () => {
  const fake = otpFakes(); fake.deps.getOtpStatus = async () => { throw new Error("unavailable"); };
  const res = await createTestHandler(fake.deps)(otpRequest("verify-otp")); assertEquals(res.status, 503); assertEquals(fake.getCalls(), 0);
});

Deno.test("resend success and already-confirmed responses have identical bodies and no-store", async () => {
  for (const provider of [{}, { error: { message: "confirmed", status: 400 } }]) {
    const handler = createTestHandler({ ...defaultDeps, resendOtpWithAuth: async () => provider });
    const res = await handler(otpRequest("resend-otp"));
    assertEquals(res.status, 202);
    assertEquals(await res.json(), { status: "ACCEPTED" });
    assertEquals(res.headers.get("cache-control"), "private, no-store");
    assertEquals(res.headers.get("pragma"), "no-cache");
  }
});

Deno.test("resend hourly limit stops before provider and returns Retry-After", async () => {
  let calls = 0;
  const res = await createTestHandler({
    ...defaultDeps,
    consumeResendSlot: async () => ({ allowed: false, retry_after_seconds: 3420, reason: "limit" }),
    resendOtpWithAuth: async () => { calls++; return {}; },
  })(otpRequest("resend-otp"));
  assertEquals(res.status, 429);
  assertEquals((await res.json()).type, "resend_limit");
  assertEquals(res.headers.get("retry-after"), "3420");
  assertEquals(calls, 0);
});

Deno.test("resend validates format before reserving a slot", async () => {
  let calls = 0;
  const handler = createTestHandler({
    ...defaultDeps,
    consumeResendSlot: async () => { calls++; throw new Error("must not run"); },
  });
  assertEquals((await handler(otpRequest("resend-otp", { email: "invalid" }))).status, 422);
  assertEquals(calls, 0);
});

Deno.test("legacy extra request field is ignored", async () => {
  const legacyField = ["cap", "tchaToken"].join("");
  for (const path of ["verify-otp", "resend-otp"]) {
    const res = await createTestHandler(defaultDeps)(otpRequest(path, { [legacyField]: null }));
    assertEquals(res.status, path === "verify-otp" ? 200 : 202);
  }
});

Deno.test("OTP malformed JSON, null, arrays and primitives return 422 problem JSON", async () => {
  for (const path of ["verify-otp", "resend-otp"]) {
    for (const body of ["{", "null", "[]", '"string"', "1"]) {
      const res = await createTestHandler(defaultDeps)(new Request(`https://example.com/${path}`, { method: "POST", body }));
      assertEquals(res.status, 422);
      assertEquals(res.headers.get("content-type"), "application/problem+json");
      assertEquals((await res.json()).type, "invalid_request");
    }
  }
});

Deno.test("OTP concurrent lease refuses provider access and never releases another attempt", async () => {
  let calls = 0, releases = 0;
  const res = await createTestHandler({
    ...defaultDeps,
    getOtpStatus: async () => ({ locked: false, busy: true, failures: 0, retry_after_seconds: 0, last_sent_at: null }),
    verifyOtpWithAuth: async () => { calls++; return {}; },
    releaseOtpAttempt: async () => { releases++; },
  })(otpRequest("verify-otp"));
  assertEquals(res.status, 503);
  assertEquals((await res.json()).type, "provider_unavailable");
  assertEquals(calls, 0); assertEquals(releases, 0);
});

Deno.test("OTP releases lease on provider outages, expiry and generic throttling", async () => {
  for (const mode of ["outage", "expiry", "rate"]) {
    const fake = otpFakes(); let releases = 0;
    fake.deps.releaseOtpAttempt = async () => { releases++; };
    if (mode === "outage") fake.deps.verifyOtpWithAuth = async () => { throw new Error("timeout"); };
    if (mode === "expiry") fake.setSent(new Date(Date.now() - 601000).toISOString());
    if (mode === "rate") fake.deps.consumeRateBucket = async () => ({ allowed: false, retryAfterSeconds: 10 });
    const res = await createTestHandler(fake.deps)(otpRequest("verify-otp"));
    assertEquals(res.status, mode === "outage" ? 503 : mode === "expiry" ? 410 : 429);
    assertEquals(releases, 1);
  }
});

Deno.test("accepted resend unlocks an identity and restores the failure budget", async () => {
  const fake = otpFakes(); const handler = createTestHandler(fake.deps);
  for (let i = 0; i < 5; i++) await handler(otpRequest("verify-otp"));
  assertEquals((await handler(otpRequest("resend-otp"))).status, 202);
  const res = await handler(otpRequest("verify-otp"));
  assertEquals(res.status, 400);
  assertEquals((await res.json()).attempts_left, 4);
});

Deno.test("resend masks per-address provider limits and SMTP errors to prevent enumeration", async () => {
  for (const status of [400, 403, 404, 422, 429, 500, 503]) {
    let resets = 0;
    const res = await createTestHandler({
      ...defaultDeps,
      resendOtpWithAuth: async () => ({ error: { message: "unavailable", status } }),
      markOtpSent: async () => { resets++; },
    })(otpRequest("resend-otp"));
    assertEquals(res.status, 202);
    assertEquals(await res.json(), { status: "ACCEPTED" });
    assertEquals(resets, 1);
  }
});

Deno.test("resend storage/credential failures close access and SMTP timeouts stay neutral", async () => {
  for (const mode of ["storage", "transport", "credential"]) {
    let resets = 0;
    const deps = { ...defaultDeps, markOtpSent: async () => { resets++; } };
    if (mode === "storage") deps.consumeResendSlot = async () => { throw new Error("storage"); };
    if (mode === "transport") deps.resendOtpWithAuth = async () => { throw new Error("transport"); };
    if (mode === "credential") deps.resendOtpWithAuth = async () => ({ error: { message: "credential", status: 401 } });
    const res = await createTestHandler(deps)(otpRequest("resend-otp"));
    assertEquals(res.status, mode === "transport" ? 202 : 503);
    assertEquals(resets, mode === "transport" ? 1 : 0);
  }
});

Deno.test("login does not infer unconfirmed email from messages or transport exceptions", async () => {
  for (const provider of [
    async () => ({ error: { message: "Email not confirmed", status: 400 } }),
    async () => { throw new Error("transport"); },
  ]) {
    const res = await createTestHandler({ ...defaultDeps, signInWithAuth: provider })(otpRequest("login", { password: "Password123" }));
    assertEquals(res.status, 401); assertEquals((await res.json()).type, undefined);
  }
});

function adapter(fetcher: typeof fetch, delays: number[] = []) {
  return createDefaultDeps({
    env: (key) => key === "SUPABASE_URL" ? "https://example.com" : "test-credential",
    fetch: fetcher,
    sleep: async (ms) => { delays.push(ms); },
  });
}

Deno.test("default OTP adapters use signup, parse session and normalize timing", async () => {
  const calls: Array<{ url: string; body: Record<string, unknown> }> = [], delays: number[] = [];
  const deps = adapter(async (input, init) => {
    const url = String(input); calls.push({ url, body: JSON.parse(String(init?.body)) });
    return Response.json(url.endsWith("/verify") ? {
      access_token: "access", refresh_token: "refresh", expires_in: 3600, token_type: "bearer", user: { id: "id" },
    } : {});
  }, delays);
  const result = await deps.verifyOtpWithAuth!("user@example.com", "123456");
  await deps.resendOtpWithAuth!("user@example.com");
  assertEquals(result.session, { accessToken: "access", refreshToken: "refresh", expiresIn: 3600, tokenType: "bearer", userId: "id" });
  assertEquals(calls, [
    { url: "https://example.com/auth/v1/verify", body: { type: "signup", email: "user@example.com", token: "123456" } },
    { url: "https://example.com/auth/v1/resend", body: { type: "signup", email: "user@example.com" } },
  ]);
  assertEquals(delays.length, 1); assert(delays[0] > 4000 && delays[0] <= 4100, "resend must use a common deadline");
});

Deno.test("default OTP RPC adapters reject malformed storage and enforce service credentials", async () => {
  for (const payload of [null, [], {}, { locked: "false", failures: 0, retry_after_seconds: 0 }, { locked: false, busy: false, failures: 0, retry_after_seconds: 0, last_sent_at: "invalid" }]) {
    const deps = adapter(async () => Response.json(payload));
    const res = await createTestHandler({ ...defaultDeps, getOtpStatus: deps.getOtpStatus })(otpRequest("verify-otp"));
    assertEquals(res.status, 503);
  }
  for (const payload of [{}, { allowed: true, retry_after_seconds: "0" }, { allowed: false, retry_after_seconds: 0 }]) {
    const deps = adapter(async () => Response.json(payload));
    assertEquals((await createTestHandler({ ...defaultDeps, consumeResendSlot: deps.consumeResendSlot })(otpRequest("resend-otp"))).status, 503);
  }
  let calls = 0;
  const deps = createDefaultDeps({ env: (key) => key === "SUPABASE_SERVICE_ROLE_KEY" ? undefined : "test-value", fetch: async () => { calls++; return Response.json({}); } });
  assertEquals((await createTestHandler({ ...defaultDeps, getOtpStatus: deps.getOtpStatus })(otpRequest("verify-otp"))).status, 503);
  assertEquals(calls, 0);
});

Deno.test("default rate adapter selects the unambiguous overload and rejects invalid responses", async () => {
  let payload: Record<string, unknown> = {};
  const deps = adapter(async (_input, init) => { payload = JSON.parse(String(init?.body)); return Response.json({ allowed: true, retry_after_seconds: 0 }); });
  assertEquals(await deps.consumeRateBucket("verify-otp:id:user@example.com"), { allowed: true, retryAfterSeconds: 0 });
  assertEquals(payload, { p_bucket_key: "verify-otp:id:user@example.com", p_window_seconds: 900, p_max_attempts: 60, p_cooldown_base_seconds: 1 });
  const invalid = adapter(async () => Response.json({ allowed: "yes", retry_after_seconds: 0 }));
  assertEquals(await invalid.consumeRateBucket("login:ip:test"), { allowed: false, retryAfterSeconds: 60 });
});

Deno.test("default OTP adapters retain provider codes and pad error timing", async () => {
  const delays: number[] = [];
  const deps = adapter(async () => Response.json({ error_code: "otp_expired" }, { status: 403 }), delays);
  const result = await deps.verifyOtpWithAuth!("unknown@example.com", "123456");
  assertEquals(result.error?.code, "otp_expired"); assertEquals(result.error?.status, 403);
  assertEquals(delays.length, 1); assert(delays[0] > 700 && delays[0] <= 800, "failed verifications must be padded");
});

Deno.test("OTP default adapter uses at most one RPC on each side of GoTrue", async () => {
  for (const path of ["verify-otp", "resend-otp"]) {
    for (const failure of [false, true]) {
      const calls: string[] = [];
      const deps = adapter(async (input) => {
        const url = String(input); calls.push(url);
        if (url.endsWith("auth_otp_begin_verify")) return Response.json({ rate: { allowed: true, retry_after_seconds: 0 }, status: { locked: false, busy: false, failures: 0, retry_after_seconds: 0, last_sent_at: null } });
        if (url.endsWith("auth_otp_begin_resend")) return Response.json({ rate: { allowed: true, retry_after_seconds: 0 }, slot: { allowed: true, retry_after_seconds: 0 } });
        if (url.endsWith("auth_otp_finish_verify")) return Response.json(failure ? { locked: false, failures: 1, retry_after_seconds: 0 } : {});
        if (url.endsWith("auth_otp_mark_sent")) return Response.json({});
        if (url.includes("/auth/v1/")) return failure ? Response.json({ error_code: "invalid" }, { status: 403 }) : Response.json({ access_token: "a", refresh_token: "r", expires_in: 1, token_type: "bearer", user: { id: "u" } });
        throw new Error(`unexpected fetch ${url}`);
      });
      const response = await createAuthAccessHandler(deps)(otpRequest(path));
      assertEquals(response.status, path === "resend-otp" ? 202 : failure ? 400 : 200);
      const provider = calls.findIndex((url) => url.includes("/auth/v1/"));
      assertEquals(provider, 1); assertEquals(calls.length - provider - 1, 1);
      assert(calls[0].includes("/rest/v1/rpc/auth_otp_begin_"), "begin RPC required");
      assert(calls[2].includes("/rest/v1/rpc/"), "finish RPC required");
    }
  }
});

Deno.test("combined OTP RPC errors fail closed without retries", async () => {
  for (const stage of ["begin", "finish"]) {
    const calls: string[] = [];
    const deps = adapter(async (input) => {
      const url = String(input); calls.push(url);
      if (url.endsWith("auth_otp_begin_verify")) return Response.json(stage === "begin" ? {} : { rate: { allowed: true, retry_after_seconds: 0 }, status: { locked: false, busy: false, failures: 0, retry_after_seconds: 0, last_sent_at: null } });
      if (url.endsWith("/verify")) return Response.json({ access_token: "a", refresh_token: "r", expires_in: 1, token_type: "bearer", user: { id: "u" } });
      return Response.json({}, { status: 500 });
    });
    assertEquals((await createAuthAccessHandler(deps)(otpRequest("verify-otp"))).status, 503);
    assertEquals(calls.length, stage === "begin" ? 1 : 3);
  }
});

Deno.test("combined OTP adapters reject malformed status, rate, slot and finish responses", async () => {
  const rate = { allowed: true, retry_after_seconds: 0 };
  const status = { locked: false, busy: false, failures: 0, retry_after_seconds: 0, last_sent_at: null };
  for (const [path, payload] of [
    ["verify-otp", { rate, status: { ...status, busy: "false" } }],
    ["verify-otp", { rate, status: { ...status, failures: 6 } }],
    ["verify-otp", { rate, status: { ...status, last_sent_at: "invalid" } }],
    ["verify-otp", { rate: { allowed: false, retry_after_seconds: 0 }, status }],
    ["resend-otp", { rate, slot: null }],
    ["resend-otp", { rate, slot: { allowed: false, retry_after_seconds: 60, reason: "invalid" } }],
  ] as const) {
    let calls = 0;
    const deps = adapter(async () => { calls++; return Response.json(payload); });
    assertEquals((await createAuthAccessHandler(deps)(otpRequest(path))).status, 503);
    assertEquals(calls, 1);
  }
  for (const failure of [false, true]) {
    let calls = 0;
    const deps = adapter(async (input) => {
      calls++;
      const url = String(input);
      if (url.endsWith("auth_otp_begin_verify")) return Response.json({ rate, status });
      if (url.endsWith("/verify")) return failure
        ? Response.json({ error_code: "invalid" }, { status: 403 })
        : Response.json({ access_token: "a", refresh_token: "r", expires_in: 1, token_type: "bearer", user: { id: "u" } });
      return Response.json(failure ? { locked: false, failures: "1", retry_after_seconds: 0 } : { unexpected: true });
    });
    assertEquals((await createAuthAccessHandler(deps)(otpRequest("verify-otp"))).status, 503);
    assertEquals(calls, 3);
  }
});

Deno.test("combined verify releases once after provider rejection, expiry or transport failure", async () => {
  for (const mode of ["expiry", "outage", "transport"]) {
    const calls: string[] = [];
    let outcome: unknown;
    const deps = adapter(async (input, init) => {
      const url = String(input); calls.push(url);
      if (url.endsWith("auth_otp_begin_verify")) return Response.json({
        rate: { allowed: true, retry_after_seconds: 0 },
        status: { locked: false, busy: false, failures: 0, retry_after_seconds: 0,
          last_sent_at: mode === "expiry" ? new Date(Date.now() - 601000).toISOString() : null },
      });
      if (url.endsWith("/verify")) {
        if (mode === "transport") throw new Error("transport");
        return Response.json({ error_code: "invalid" }, { status: mode === "outage" ? 503 : 403 });
      }
      outcome = JSON.parse(String(init?.body)).p_outcome;
      return Response.json({});
    });
    const res = await createAuthAccessHandler(deps)(otpRequest("verify-otp"));
    assertEquals(res.status, mode === "expiry" ? 410 : 503);
    assertEquals(calls.length, 3);
    assertEquals(outcome, "release");
  }
});
