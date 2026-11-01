export type RateBucketResult = {
  allowed: boolean;
  retryAfterSeconds: number;
};

export type AuthSessionData = {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
  userId: string;
};

type OtpStatus = { locked: boolean; busy: boolean; retry_after_seconds: number; failures: number; last_sent_at: string | null };
type OtpFailure = { locked: boolean; retry_after_seconds: number; failures: number };
type OtpSlot = { allowed: boolean; retry_after_seconds: number; reason?: string };
type ProviderResult = { error?: { message: string; status?: number; code?: string }; session?: AuthSessionData };

export type Dependencies = {
  beginOtpVerify?: (ip: string, email: string, attemptId: string) => Promise<{ rate: OtpSlot; status?: OtpStatus }>;
  beginOtpResend?: (ip: string, email: string) => Promise<{ rate: OtpSlot; slot?: OtpSlot }>;
  finishOtpVerify?: (email: string, attemptId: string, outcome: "failure" | "success" | "release") => Promise<OtpFailure | Record<string, never>>;
  getOtpStatus?: (email: string, attemptId: string) => Promise<OtpStatus>;
  registerOtpFailure?: (email: string, attemptId: string) => Promise<OtpFailure>;
  markOtpSent?: (email: string) => Promise<void>;
  resetOtpFailures?: (email: string, attemptId: string) => Promise<void>;
  releaseOtpAttempt?: (email: string, attemptId: string) => Promise<void>;
  consumeResendSlot?: (email: string) => Promise<OtpSlot>;
  verifyOtpWithAuth?: (email: string, token: string, captchaToken: string) => Promise<ProviderResult>;
  resendOtpWithAuth?: (email: string, captchaToken: string) => Promise<ProviderResult>;
  consumeRateBucket: (bucketKey: string) => Promise<RateBucketResult>;
  checkEmailExists: (email: string) => Promise<boolean>;
  validateCaptcha?: (token: string, clientIp: string) => Promise<boolean>;
  signUpWithAuth: (
    email: string,
    password: string,
  ) => Promise<{ error?: { message: string; status?: number } }>;
  signInWithAuth: (
    email: string,
    password: string,
  ) => Promise<{
    data?: {
      session?: {
        access_token: string;
        refresh_token: string;
        expires_in: number;
        token_type: string;
        user: { id: string };
      };
    };
    error?: { message: string; status?: number; code?: string };
  }>;
  resetPasswordForEmail: (
    email: string,
  ) => Promise<{ error?: { message: string } }>;
  sleep?: (ms: number) => Promise<void>;
};

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

const json = (
  body: unknown,
  status = 200,
  headers: Record<string, string> = {},
) =>
  new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "Cache-Control": "private, no-store",
      "Pragma": "no-cache",
      ...corsHeaders,
      ...headers,
    },
  });

function validateEmail(email: unknown): boolean {
  if (typeof email !== "string") return false;
  const trimmed = email.trim();
  return /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(trimmed);
}

function validatePassword(password: unknown): boolean {
  if (typeof password !== "string") return false;
  if (password.length < 8 || password.length > 72) return false;
  const hasLetter = /[A-Za-z]/.test(password);
  const hasDigit = /[0-9]/.test(password);
  return hasLetter && hasDigit;
}

function getClientIp(request: Request): string {
  const cfConnectingIp = request.headers.get("cf-connecting-ip");
  if (cfConnectingIp) return cfConnectingIp.trim();
  const xRealIp = request.headers.get("x-real-ip");
  if (xRealIp) return xRealIp.trim();
  const forwardedFor = request.headers.get("x-forwarded-for");
  if (forwardedFor) return forwardedFor.split(",")[0].trim();
  return "127.0.0.1";
}

export function createAuthAccessHandler(deps: Dependencies) {
  return async (request: Request): Promise<Response> => {
    if (request.method === "OPTIONS") {
      return new Response("ok", { headers: corsHeaders });
    }

    const url = new URL(request.url);
    const path = url.pathname.split("/").filter(Boolean).pop();

    if (request.method !== "POST") {
      return json({ status: 405, title: "Method Not Allowed" }, 405);
    }

    let body: Record<string, unknown>;
    try {
      const parsed: unknown = await request.json();
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error("Invalid body");
      body = parsed as Record<string, unknown>;
    } catch {
      if (path === "verify-otp" || path === "resend-otp") {
        return json({ type: "invalid_request", status: 422, title: "Solicitud inválida", detail: "Envía un objeto JSON válido." }, 422, { "content-type": "application/problem+json" });
      }
      return json({ status: 400, title: "Invalid JSON body" }, 400);
    }

    const clientIp = getClientIp(request);
    const normalizedEmail = typeof body.email === "string" ? body.email.trim().toLowerCase() : "";

    async function checkRateLimits(operation: string): Promise<RateBucketResult> {
      // Partition by operation and client IP
      const ipRate = await deps.consumeRateBucket(`${operation}:ip:${clientIp}`);
      if (!ipRate.allowed) return ipRate;

      // Partition by operation and identity (email)
      if (normalizedEmail) {
        const idRate = await deps.consumeRateBucket(`${operation}:id:${normalizedEmail}`);
        if (!idRate.allowed) return idRate;
      }
      return { allowed: true, retryAfterSeconds: 0 };
    }

    async function checkCaptcha(): Promise<boolean> {
      if (!deps.validateCaptcha) return true;
      const token = body.captchaToken ?? body.captchaProof;
      if (typeof token !== "string" || !token.trim()) return false;
      return deps.validateCaptcha(token.trim(), clientIp);
    }

    if (path === "verify-otp" || path === "resend-otp") {
      const problem = (type: string, status: number, title: string, detail: string, extra: Record<string, unknown> = {}) =>
        json({ type, title, status, detail, ...extra }, status, {
          "content-type": "application/problem+json",
          ...(status === 429 ? { "Retry-After": String(extra.retry_after_seconds) } : {}),
        });
      const unavailable = () => problem("provider_unavailable", 503, "Servicio no disponible", "No se pudo procesar la solicitud. Inténtalo nuevamente.");
      const limited = (type: string, seconds: number) => problem(type, 429, "Demasiados intentos", "Espera antes de intentar nuevamente.", { retry_after_seconds: seconds });
      if (!validateEmail(body.email) || (path === "verify-otp" && (typeof body.token !== "string" || !/^[0-9]{6}$/.test(body.token)))) {
        return problem("invalid_request", 422, "Solicitud inválida", "Revisa el correo y el código ingresados.");
      }
      const attemptId = crypto.randomUUID();
      let attemptActive = false;
      try {
        if (!(await checkCaptcha())) return problem("captcha_failed", 403, "Verificación fallida", "La verificación de seguridad falló. Intenta nuevamente.");
        const begin = path === "verify-otp" ? await deps.beginOtpVerify!(clientIp, normalizedEmail, attemptId) : await deps.beginOtpResend!(clientIp, normalizedEmail);
        const status = "status" in begin ? begin.status : undefined;
        if (status?.locked) return limited("otp_locked", status.retry_after_seconds);
        if (status?.busy) return unavailable();
        attemptActive = status !== undefined && !status.locked && !status.busy && begin.rate.allowed;
        if (!begin.rate.allowed) return limited("rate_limited", begin.rate.retry_after_seconds);
        const captchaToken = String(body.captchaToken ?? body.captchaProof ?? "");
        if (path === "verify-otp" && status) {
          const result = await deps.verifyOtpWithAuth!(normalizedEmail, body.token as string, captchaToken);
          if (result.error) {
            if (result.error.code === "captcha_failed") return problem("captcha_failed", 403, "Verificación fallida", "La verificación de seguridad falló. Intenta nuevamente.");
            if (![400, 403, 404, 422].includes(result.error.status ?? 0)) return unavailable();
            if (status.last_sent_at && Date.now() - Date.parse(status.last_sent_at) > 600_000) {
              return problem("otp_expired", 410, "Código vencido", "Solicita un nuevo código de verificación.");
            }
            attemptActive = false; // On RPC failure the lease expires; never issue a second finish.
            const failure = await deps.finishOtpVerify!(normalizedEmail, attemptId, "failure") as OtpFailure;
            if (failure.locked) return limited("otp_locked", failure.retry_after_seconds);
            return problem("otp_invalid", 400, "Código incorrecto", "El código ingresado no es válido.", { attempts_left: Math.max(0, 5 - failure.failures) });
          }
          if (!result.session) return unavailable();
          attemptActive = false;
          await deps.finishOtpVerify!(normalizedEmail, attemptId, "success");
          return json(result.session, 200, { "Cache-Control": "private, no-store", "Pragma": "no-cache" });
        }
        const slot = "slot" in begin ? begin.slot : undefined;
        if (!slot) return unavailable();
        if (!slot.allowed) return limited(slot.reason === "limit" ? "resend_limit" : "resend_cooldown", slot.retry_after_seconds);
        if (!deps.resendOtpWithAuth) return unavailable();
        let result: ProviderResult;
        try {
          result = await deps.resendOtpWithAuth(normalizedEmail, captchaToken);
        } catch {
          // A slow SMTP call can time out only for a real address as well.
          result = {};
        }
        if (result.error?.code === "captcha_failed") return problem("captcha_failed", 403, "Verificación fallida", "La verificación de seguridad falló. Intenta nuevamente.");
        // SMTP errors and upstream per-address limits can occur only for real
        // users. Mask HTTP outcomes alike; 202 acknowledges this request, not
        // mail delivery. Storage and invalid API credentials fail
        // closed without exposing a provider error tied to an identity.
        if (result.error && (!result.error.status || result.error.status === 401)) return unavailable();
        await deps.markOtpSent!(normalizedEmail);
        return json({ status: "ACCEPTED" }, 202);
      } catch {
        return unavailable();
      } finally {
        if (attemptActive) {
          // A failed release retains the lease for 30s, closing access safely.
          try { await deps.finishOtpVerify!(normalizedEmail, attemptId, "release"); } catch { /* lease expires */ }
        }
      }
    }

    if (path === "register") {
      const { email, password } = body;
      if (!validateEmail(email) || !validatePassword(password)) {
        return json(
          {
            status: 422,
            title: "Invalid email or password policy",
            detail: "Password must be 8-72 chars with letter and number.",
          },
          422,
        );
      }

      if (!(await checkCaptcha())) {
        return json(
          {
            status: 403,
            title: "CAPTCHA Verification Failed",
            detail: "La verificación de seguridad falló. Intente nuevamente.",
          },
          403,
        );
      }

      const rate = await checkRateLimits("register");
      if (!rate.allowed) {
        return json(
          {
            status: 429,
            title: "Too Many Requests",
            retry_after_seconds: rate.retryAfterSeconds,
          },
          429,
          { "Retry-After": rate.retryAfterSeconds.toString() },
        );
      }

      const exists = await deps.checkEmailExists(normalizedEmail);
      if (exists) {
        // FR-051: Disclosure limited to registration
        return json(
          {
            status: 409,
            title: "Account already exists",
            detail: "El correo ingresado ya está asociado a una cuenta.",
          },
          409,
        );
      }

      const { error } = await deps.signUpWithAuth(normalizedEmail, password as string);
      if (error) {
        return json(
          {
            status: 503,
            title: "Registration unavailable",
            detail: "No se pudo procesar el registro con el proveedor de identidad.",
          },
          503,
        );
      }

      try { await deps.markOtpSent!(normalizedEmail); } catch {
        return json({ status: 503, title: "Registration unavailable", detail: "No se pudo guardar el estado de verificaci?n." }, 503);
      }
      return json({ status: "ACCEPTED", confirmationRequired: true }, 201);
    }

    if (path === "login") {
      const { email, password } = body;
      if (!validateEmail(email) || !validatePassword(password)) {
        // FR-004: Neutral response
        return json(
          {
            status: 401,
            title: "Authentication Rejected",
            detail: "Correo o contraseña incorrectos.",
          },
          401,
        );
      }

      if (!(await checkCaptcha())) {
        return json(
          {
            status: 403,
            title: "CAPTCHA Verification Failed",
            detail: "La verificación de seguridad falló. Intente nuevamente.",
          },
          403,
        );
      }

      const rate = await checkRateLimits("login");
      if (!rate.allowed) {
        return json(
          {
            status: 429,
            title: "Too Many Requests",
            retry_after_seconds: rate.retryAfterSeconds,
          },
          429,
          { "Retry-After": rate.retryAfterSeconds.toString() },
        );
      }

      let authResponse: Awaited<ReturnType<Dependencies["signInWithAuth"]>>;
      try {
        authResponse = await deps.signInWithAuth(normalizedEmail, password as string);
      } catch {
        authResponse = { error: { message: "Authentication rejected" } };
      }

      if (authResponse.error?.code === "email_not_confirmed") {
        return json(
          {
            type: "email_not_confirmed",
            title: "Correo sin confirmar",
            status: 403,
            detail: "Confirma tu correo con el código que te enviamos.",
          },
          403,
          { "content-type": "application/problem+json" },
        );
      }

      if (authResponse.error || !authResponse.data?.session) {
        return json(
          {
            status: 401,
            title: "Authentication Rejected",
            detail: "Correo o contraseña incorrectos.",
          },
          401,
        );
      }

      const session = authResponse.data.session;
      return json(
        {
          accessToken: session.access_token,
          refreshToken: session.refresh_token,
          expiresIn: session.expires_in,
          tokenType: session.token_type,
          userId: session.user.id,
        },
        200,
        {
          "Cache-Control": "private, no-store",
          "Pragma": "no-cache",
        },
      );
    }

    if (path === "recovery") {
      const { email } = body;

      if (!(await checkCaptcha())) {
        return json(
          {
            status: 403,
            title: "CAPTCHA Verification Failed",
            detail: "La verificación de seguridad falló. Intente nuevamente.",
          },
          403,
        );
      }

      const rate = await checkRateLimits("recovery");
      if (!rate.allowed) {
        return json(
          {
            status: 429,
            title: "Too Many Requests",
            retry_after_seconds: rate.retryAfterSeconds,
          },
          429,
          { "Retry-After": rate.retryAfterSeconds.toString() },
        );
      }

      if (validateEmail(email)) {
        await deps.resetPasswordForEmail(normalizedEmail);
      }

      // Timing normalization to protect against timing enumeration (SC-002)
      if (deps.sleep) {
        await deps.sleep(50);
      }

      return json({ status: "ACCEPTED" }, 202);
    }

    return json({ status: 404, title: "Endpoint Not Found" }, 404);
  };
}

export function createDefaultDeps(runtime: {
  env?: (name: string) => string | undefined;
  fetch?: typeof fetch;
  sleep?: (ms: number) => Promise<void>;
} = {}): Dependencies {
  const env = runtime.env ?? ((name: string) => Deno.env.get(name));
  const fetch = runtime.fetch ?? globalThis.fetch;
  const sleep = runtime.sleep ?? ((ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms)));
  const getBaseUrl = () => env("SUPABASE_URL") ?? "";
  const getAnonKey = () =>
    env("SUPABASE_ANON_KEY") ??
    env("SUPABASE_PUBLISHABLE_KEY") ??
    "";
  const getServiceRoleKey = () =>
    env("SUPABASE_SERVICE_ROLE_KEY") ??
    getAnonKey();

  const otpRpc = async (name: string, email: string, attemptId?: string, extra: Record<string, unknown> = {}): Promise<Record<string, unknown>> => {
    const baseUrl = getBaseUrl();
    const key = env("SUPABASE_SERVICE_ROLE_KEY") ?? "";
    if (!baseUrl || !key) throw new Error("OTP storage unavailable");
    const response = await fetch(`${baseUrl}/rest/v1/rpc/${name}`, {
      method: "POST", headers: { apikey: key, Authorization: `Bearer ${key}`, "content-type": "application/json" },
      body: JSON.stringify({ p_email: email, ...(attemptId ? { p_attempt_id: attemptId } : {}), ...extra }),
      signal: AbortSignal.timeout(2000),
    });
    if (!response.ok) throw new Error("OTP storage unavailable");
    const data = await response.json();
    if (!data || typeof data !== "object" || Array.isArray(data)) throw new Error("Invalid OTP storage response");
    const integer = (value: unknown) => typeof value === "number" && Number.isInteger(value) && value >= 0;
    if (name === "auth_otp_status" || name === "auth_otp_register_failure") {
      if (typeof data.locked !== "boolean" || !integer(data.failures) || data.failures > 5 || !integer(data.retry_after_seconds) || (data.locked && data.retry_after_seconds < 1)) throw new Error("Invalid OTP storage response");
    }
    if (name === "auth_otp_status" && (typeof data.busy !== "boolean" || !(data.last_sent_at === null || (typeof data.last_sent_at === "string" && Number.isFinite(Date.parse(data.last_sent_at)))))) throw new Error("Invalid OTP storage response");
    if (name === "auth_otp_consume_resend" && (typeof data.allowed !== "boolean" || !integer(data.retry_after_seconds) || (!data.allowed && (data.retry_after_seconds < 1 || !["limit", "cooldown"].includes(data.reason))))) throw new Error("Invalid OTP storage response");
    if (["auth_otp_mark_sent", "auth_otp_reset_failures", "auth_otp_release_attempt"].includes(name) && Object.keys(data).length !== 0) throw new Error("Invalid OTP storage response");
    const slotValid = (v: unknown, reasons: string[]) => {
      if (!v || typeof v !== "object" || Array.isArray(v)) return false;
      const x = v as Record<string, unknown>;
      return typeof x.allowed === "boolean" && integer(x.retry_after_seconds) && (x.allowed || ((x.retry_after_seconds as number) > 0 && (reasons.length === 0 || reasons.includes(String(x.reason)))));
    };
    if (name === "auth_otp_begin_verify" || name === "auth_otp_begin_resend") {
      if (!slotValid(data.rate, [])) throw new Error("Invalid OTP rate response");
      const rate = data.rate as OtpSlot;
      if (name === "auth_otp_begin_verify") {
        const x = data.status as OtpStatus | undefined;
        if (!x || typeof x.locked !== "boolean" || typeof x.busy !== "boolean" || !integer(x.failures) || x.failures > 5 || !integer(x.retry_after_seconds) || (x.locked && x.retry_after_seconds < 1) || !(x.last_sent_at === null || (typeof x.last_sent_at === "string" && Number.isFinite(Date.parse(x.last_sent_at))))) throw new Error("Invalid OTP status");
      } else if (rate.allowed && !slotValid(data.slot, ["limit", "cooldown"])) throw new Error("Invalid resend slot");
    }
    if (name === "auth_otp_finish_verify") {
      if (extra.p_outcome === "failure") {
        if (typeof data.locked !== "boolean" || !integer(data.failures) || data.failures > 5 || !integer(data.retry_after_seconds) || (data.locked && data.retry_after_seconds < 1)) throw new Error("Invalid OTP failure");
      } else if (Object.keys(data).length !== 0) throw new Error("Invalid OTP finish");
    }
    return data;
  };
  const authOtp = async (endpoint: string, payload: Record<string, unknown>): Promise<ProviderResult> => {
    const started = performance.now();
    let succeeded = false;
    try {
      const response = await fetch(`${getBaseUrl()}/auth/v1/${endpoint}`, {
        method: "POST", headers: { apikey: getAnonKey(), "content-type": "application/json" }, body: JSON.stringify(payload), signal: AbortSignal.timeout(4000),
      });
      if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        return { error: { message: "Auth request failed", status: response.status, code: error.error_code ?? error.code } };
      }
      if (endpoint === "resend") { await response.arrayBuffer(); return {}; }
      const data = await response.json();
      if (typeof data.access_token !== "string" || !data.access_token || typeof data.refresh_token !== "string" || !data.refresh_token || typeof data.expires_in !== "number" || !Number.isFinite(data.expires_in) || data.expires_in <= 0 || typeof data.token_type !== "string" || !data.token_type || typeof data.user?.id !== "string" || !data.user.id) throw new Error("Invalid session");
      succeeded = true;
      return { session: { accessToken: data.access_token, refreshToken: data.refresh_token, expiresIn: data.expires_in, tokenType: data.token_type, userId: data.user.id } };
    } finally {
      // Resend always pads to 4.1s: only known emails trigger an SMTP send.
      // Verify pads failures to 0.8s; a session already proves knowledge of the code.
      if (endpoint === "resend") await sleep(Math.max(0, 4100 - (performance.now() - started)));
      else if (!succeeded) await sleep(Math.max(0, 800 - (performance.now() - started)));
    }
  };
  return {
    beginOtpVerify: async (ip, email, attemptId) => await otpRpc("auth_otp_begin_verify", email, attemptId, { p_ip_key: ip }) as { rate: OtpSlot; status: OtpStatus },
    beginOtpResend: async (ip, email) => await otpRpc("auth_otp_begin_resend", email, undefined, { p_ip_key: ip }) as { rate: OtpSlot; slot?: OtpSlot },
    finishOtpVerify: async (email, attemptId, outcome) => await otpRpc("auth_otp_finish_verify", email, attemptId, { p_outcome: outcome }) as OtpFailure,
    getOtpStatus: async (email, attemptId) => await otpRpc("auth_otp_status", email, attemptId) as OtpStatus,
    registerOtpFailure: async (email, attemptId) => await otpRpc("auth_otp_register_failure", email, attemptId) as OtpFailure,
    markOtpSent: async (email) => { await otpRpc("auth_otp_mark_sent", email); },
    resetOtpFailures: async (email, attemptId) => { await otpRpc("auth_otp_reset_failures", email, attemptId); },
    releaseOtpAttempt: async (email, attemptId) => { await otpRpc("auth_otp_release_attempt", email, attemptId); },
    consumeResendSlot: async (email) => await otpRpc("auth_otp_consume_resend", email) as OtpSlot,
    verifyOtpWithAuth: (email, token, captchaToken) => authOtp("verify", { type: "signup", email, token, gotrue_meta_security: { captcha_token: captchaToken } }),
    resendOtpWithAuth: (email, captchaToken) => authOtp("resend", { type: "signup", email, gotrue_meta_security: { captcha_token: captchaToken } }),
    consumeRateBucket: async (bucketKey: string): Promise<RateBucketResult> => {
      const baseUrl = getBaseUrl();
      const serviceKey = getServiceRoleKey();
      if (!baseUrl || !serviceKey) {
        // FR-049/FR-050: Fail closed if rate limiting cannot be enforced
        return { allowed: false, retryAfterSeconds: 60 };
      }
      try {
        const res = await fetch(`${baseUrl}/rest/v1/rpc/consume_auth_rate_bucket`, {
          method: "POST",
          headers: {
            apikey: serviceKey,
            Authorization: `Bearer ${serviceKey}`,
            "content-type": "application/json",
          },
          // The remote schema has 1-arg and defaulted 4-arg overloads. Supply
          // all parameters to avoid PostgREST PGRST203 ambiguity. OTP's own
          // guard owns the five-failure counter and resets on accepted resend.
          body: JSON.stringify({ p_bucket_key: bucketKey, p_window_seconds: 900, p_max_attempts: /^(verify-otp|resend-otp):/.test(bucketKey) ? 60 : 5, p_cooldown_base_seconds: 1 }),
          signal: AbortSignal.timeout(2000),
        });
        if (!res.ok) {
          return { allowed: false, retryAfterSeconds: 60 };
        }
        const data = await res.json();
        if (typeof data?.allowed !== "boolean" || !Number.isInteger(data.retry_after_seconds) || data.retry_after_seconds < 0 || (!data.allowed && data.retry_after_seconds < 1)) return { allowed: false, retryAfterSeconds: 60 };
        return {
          allowed: data?.allowed ?? false,
          retryAfterSeconds: data?.retry_after_seconds ?? 60,
        };
      } catch {
        return { allowed: false, retryAfterSeconds: 60 };
      }
    },

    checkEmailExists: async (email: string): Promise<boolean> => {
      const baseUrl = getBaseUrl();
      const serviceKey = getServiceRoleKey();
      if (!baseUrl || !serviceKey) {
        return false;
      }
      try {
        const res = await fetch(`${baseUrl}/rest/v1/rpc/check_email_exists`, {
          method: "POST",
          headers: {
            apikey: serviceKey,
            Authorization: `Bearer ${serviceKey}`,
            "content-type": "application/json",
          },
          body: JSON.stringify({ p_email: email }),
        });
        if (!res.ok) {
          return false;
        }
        const exists = await res.json();
        return Boolean(exists);
      } catch {
        return false;
      }
    },

    validateCaptcha: async (token: string): Promise<boolean> => {
      const captchaSecret = env("CAPTCHA_SECRET_KEY");
      if (!captchaSecret) {
        // Allow in development/test if no secret configured
        return true;
      }
      try {
        const res = await fetch("https://challenges.cloudflare.com/turnstile/v0/siteverify", {
          method: "POST",
          headers: { "content-type": "application/json" },
          body: JSON.stringify({
            secret: captchaSecret,
            response: token,
          }),
          signal: AbortSignal.timeout(2000),
        });
        if (!res.ok) return false;
        const data = await res.json();
        return Boolean(data.success);
      } catch {
        return false;
      }
    },

    signUpWithAuth: async (email: string, password: string) => {
      const baseUrl = getBaseUrl();
      const anonKey = getAnonKey();
      const res = await fetch(`${baseUrl}/auth/v1/signup`, {
        method: "POST",
        headers: {
          apikey: anonKey,
          "content-type": "application/json",
        },
        body: JSON.stringify({ email, password }),
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({ message: "Sign up error" }));
        return {
          error: {
            message: err.msg || err.message || err.error_description || "Sign up error",
            status: res.status,
          },
        };
      }
      return {};
    },

    signInWithAuth: async (email: string, password: string) => {
      const baseUrl = getBaseUrl();
      const anonKey = getAnonKey();
      const res = await fetch(`${baseUrl}/auth/v1/token?grant_type=password`, {
        method: "POST",
        headers: {
          apikey: anonKey,
          "content-type": "application/json",
        },
        body: JSON.stringify({ email, password }),
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({ message: "Invalid credentials" }));
        return {
          error: {
            message: err.msg || err.error_description || "Invalid credentials",
            status: res.status,
            code: err.error_code ?? err.code,
          },
        };
      }
      const data = await res.json();
      return {
        data: {
          session: {
            access_token: data.access_token,
            refresh_token: data.refresh_token,
            expires_in: data.expires_in,
            token_type: data.token_type,
            user: { id: data.user.id },
          },
        },
      };
    },

    resetPasswordForEmail: async (email: string) => {
      const baseUrl = getBaseUrl();
      const anonKey = getAnonKey();
      const redirectTo = env("KIPU_AUTH_REDIRECT_URL") ?? "https://kipu.app/auth/recovery";
      const res = await fetch(`${baseUrl}/auth/v1/recover?redirect_to=${encodeURIComponent(redirectTo)}`, {
        method: "POST",
        headers: {
          apikey: anonKey,
          "content-type": "application/json",
        },
        body: JSON.stringify({ email }),
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({ message: "Password recovery failed" }));
        return { error: { message: err.msg || err.message || "Password recovery failed" } };
      }
      return {};
    },

    sleep,
  };
}

if (import.meta.main) {
  Deno.serve(createAuthAccessHandler(createDefaultDeps()));
}
