import { createGooglePlayApi, ProviderRejectedError } from "./google-play-api.ts";

function assert(value: unknown, message: string): asserts value {
  if (!value) throw new Error(message);
}

async function adapter(details: Record<string, unknown>) {
  const pair = await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true, ["sign", "verify"],
  );
  const bytes = new Uint8Array(await crypto.subtle.exportKey("pkcs8", pair.privateKey));
  const privateKey = btoa(String.fromCharCode(...bytes));
  const urls: string[] = [];
  const api = createGooglePlayApi({ get: (name) => name === "GOOGLE_PLAY_SERVICE_ACCOUNT_JSON"
    ? JSON.stringify({ client_email: "test@example.test", private_key: privateKey }) : undefined },
    ((url: string | URL | Request) => {
      urls.push(String(url));
      return Promise.resolve(Response.json(String(url).includes("oauth2")
        ? { access_token: "test-access", expires_in: 3600 } : details));
    }) as typeof fetch);
  return { api, urls };
}

const lifetime = { storeProductId: "lifetime", basePlanId: null, planType: "PRO_LIFETIME" };
const accountId = "expected-account-hash";

Deno.test("productsv2 reads nested state, flat fallback, pending and cancellation without acknowledgement", async () => {
  const cases = [
    { fields: { purchaseStateContext: { purchaseState: "PURCHASED" }, purchaseState: "CANCELLED" }, state: "PURCHASED", lifecycle: "ACTIVE" },
    { fields: { purchaseState: "PURCHASED" }, state: "PURCHASED", lifecycle: "ACTIVE" },
    { fields: { purchaseStateContext: { purchaseState: "PENDING" } }, state: "PENDING", lifecycle: null },
    { fields: { purchaseStateContext: { purchaseState: "CANCELLED" } }, state: "CANCELLED", lifecycle: "REVOKED" },
  ];
  for (const sample of cases) {
    const { api, urls } = await adapter({
      productLineItem: [{ productId: "lifetime" }],
      obfuscatedExternalAccountId: accountId,
      acknowledgementState: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
      ...sample.fields,
    });
    const result = await api.verifyPurchase(lifetime, "token");
    assert(result.purchaseState === sample.state && result.entitlementState === sample.lifecycle, "v2 state is mapped consistently");
    assert(result.obfuscatedAccountId === accountId, "one-time identity is retained for verification");
    assert(urls[1].includes("/purchases/productsv2/"), "tests exercise the real v2 adapter");
    assert(urls.length === 2, "reading a purchase does not acknowledge it");
  }
});

Deno.test("productsv2 rejects absent or unknown state instead of inferring a purchased entitlement", async () => {
  for (const fields of [{}, { purchaseStateContext: { purchaseState: "UNKNOWN" }, purchaseState: "PURCHASED" }]) {
    const { api } = await adapter({ productLineItem: [{ productId: "lifetime" }], ...fields });
    let rejected = false;
    try { await api.verifyPurchase(lifetime, "token"); }
    catch (error) { rejected = error instanceof ProviderRejectedError; }
    assert(rejected, "unknown provider state is rejected");
  }
});

Deno.test("subscriptions retain the external account identity and canceled paid lifecycle", async () => {
  const { api } = await adapter({
    subscriptionState: "SUBSCRIPTION_STATE_CANCELED",
    externalAccountIdentifiers: { obfuscatedExternalAccountId: accountId },
    lineItems: [{ productId: "monthly", expiryTime: new Date(Date.now() + 86_400_000).toISOString() }],
  });
  const result = await api.verifyPurchase({ storeProductId: "monthly", basePlanId: null, planType: "PRO_MONTHLY" }, "token");
  assert(result.obfuscatedAccountId === accountId, "subscription identity is retained");
  assert(result.purchaseState === "CANCELLED" && result.entitlementState === "CANCELED_ACTIVE", "paid cancellation stays active");
});
