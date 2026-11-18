const PLAY = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/";
const TOKEN_URL = "https://oauth2.googleapis.com/token";
const SCOPE = "https://www.googleapis.com/auth/androidpublisher";
const encoder = new TextEncoder();
const BILLION = 1_000_000_000n;
const CENT = 10_000_000n;

const TIPS = new Map([
    ["support_tip_sip", "TIP_SIP_CENTS"],
    ["support_tip_small", 100], ["support_tip_full", 200], ["support_tip_unit", 100],
    ["support_tip_3", 300], ["support_tip_5", 500], ["support_tip_10", 1000],
    ["support_tip_20", 2000], ["support_tip_50", 5000],
]);
const MONTHLY = new Map([
    ["support_monthly_sip", "MONTHLY_SIP_CENTS"],
    ["support_monthly_small", 100], ["support_monthly_full", 200],
]);
const PRODUCT_FIELDS = "orderId,productId,purchaseState,purchaseType,quantity,refundableQuantity,obfuscatedExternalAccountId";
const SUBSCRIPTION_FIELDS = "subscriptionState,testPurchase,externalAccountIdentifiers(obfuscatedExternalAccountId),lineItems(productId,latestSuccessfulOrderId)";
const ORDER_FIELDS = "orderId,state,purchaseToken,total,tax,lineItems(productId,oneTimePurchaseDetails(quantity)),orderHistory(partialRefundEvents(state,refundDetails(total,tax)))";

export class SupportError extends Error {
    constructor(status, code) {
        super(code);
        this.status = status;
    }
}

export function knownProduct(kind, productId) {
    return (kind === "tip" && TIPS.has(productId)) || (kind === "subscription" && MONTHLY.has(productId));
}

function catalogCents(env, kind, productId) {
    const price = (kind === "tip" ? TIPS : MONTHLY).get(productId);
    if (typeof price === "number") return price;
    const value = env[price];
    if (!/^[1-9][0-9]?$/.test(value ?? "")) throw new SupportError(503, "play_unavailable");
    return Number(value);
}

export async function hash(value) {
    const bytes = new Uint8Array(await crypto.subtle.digest("SHA-256", encoder.encode(value)));
    return Array.from(bytes, byte => byte.toString(16).padStart(2, "0")).join("");
}

function base64url(bytes) {
    return btoa(String.fromCharCode(...bytes)).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}

async function authorize(env, fetcher) {
    try {
        const { client_email, private_key } = JSON.parse(env.PLAY_SERVICE_ACCOUNT);
        if (!client_email || !private_key) throw new Error();
        const pem = private_key.match(/^-----BEGIN PRIVATE KEY-----\s+([A-Za-z0-9+/=\s]+)-----END PRIVATE KEY-----\s*$/);
        if (pem === null) throw new Error();
        const key = await crypto.subtle.importKey(
            "pkcs8", Uint8Array.from(atob(pem[1].replace(/\s/g, "")), char => char.charCodeAt(0)),
            { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"],
        );
        const now = Math.floor(Date.now() / 1000);
        const header = base64url(encoder.encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
        const claims = base64url(encoder.encode(JSON.stringify({
            iss: client_email, scope: SCOPE, aud: TOKEN_URL, iat: now, exp: now + 600,
        })));
        const jwt = `${header}.${claims}`;
        const signature = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, encoder.encode(jwt)));
        const response = await fetcher(TOKEN_URL, {
            method: "POST",
            redirect: "error",
            signal: AbortSignal.timeout(8000),
            body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
                assertion: `${jwt}.${base64url(signature)}` }),
        });
        if (!response.ok) throw new Error();
        const { access_token, expires_in } = await response.json();
        if (typeof access_token !== "string" || !Number.isFinite(expires_in)) throw new Error();
        return { value: access_token, expires: Date.now() + expires_in * 1000 };
    } catch {
        throw new SupportError(503, "play_unavailable");
    }
}

// Money is returned in units and nanos. Use the exact integer value for refunds and ratios.
function nanoAmount(money, currency) {
    if (money?.currencyCode !== currency || !/^\d{1,15}$/.test(money.units ?? "0")) return null;
    const nanos = money.nanos ?? 0;
    if (!Number.isInteger(nanos) || nanos < 0 || nanos >= 1_000_000_000) return null;
    return BigInt(money.units ?? "0") * BILLION + BigInt(nanos);
}

function beforeTax(total, tax, currency) {
    const gross = nanoAmount(total, currency);
    const charges = tax === undefined ? 0n : nanoAmount(tax, currency);
    if (gross === null || charges === null || charges > gross) return null;
    return gross - charges;
}

function netCents(order, nominalCents) {
    if (order.state === "REFUNDED" || order.state === "CANCELED") return 0;
    if (order.state === "PENDING" || order.state === "PENDING_REFUND") throw new SupportError(409, "purchase_pending");
    if (order.state !== "PROCESSED" && order.state !== "PARTIALLY_REFUNDED") {
        throw new SupportError(422, "purchase_unverified");
    }
    const currency = order.total?.currencyCode;
    if (!/^[A-Z]{3}$/.test(currency ?? "")) throw new SupportError(422, "purchase_unverified");
    const paid = beforeTax(order.total, order.tax, currency);
    if (paid === null) throw new SupportError(422, "purchase_unverified");
    if (paid === 0n) return 0;

    let refunded = 0n;
    for (const item of order.orderHistory?.partialRefundEvents ?? []) {
        if (item.state !== "PROCESSED_SUCCESSFULLY") continue;
        const amount = beforeTax(item.refundDetails?.total, item.refundDetails?.tax, currency);
        if (amount === null) throw new SupportError(422, "purchase_unverified");
        refunded += amount;
    }
    if (order.state === "PARTIALLY_REFUNDED" && refunded === 0n) throw new SupportError(409, "purchase_pending");
    if (refunded >= paid) return 0;
    const remainder = paid - refunded;
    // For other currencies the Play US list price is the common base, scaled by the portion kept after refunds.
    const cents = currency === "USD" ? (remainder + CENT / 2n) / CENT
        : (BigInt(nominalCents) * remainder + paid / 2n) / paid;
    return Number(cents > BigInt(nominalCents) ? BigInt(nominalCents) : cents);
}

function rejected() {
    return new SupportError(422, "purchase_unverified");
}

export class ExpiredSubscription extends SupportError {
    constructor() {
        super(422, "purchase_expired");
    }
}

export function playVerifier(fetcher = fetch) {
    let token;
    let signing;

    async function google(env, path, query) {
        try {
            if (!token || token.expires <= Date.now() + 30_000) {
                signing ??= authorize(env, fetcher).finally(() => { signing = undefined; });
                token = await signing;
            }
            const url = new URL(PLAY + encodeURIComponent(env.APP_PACKAGE) + path);
            url.search = new URLSearchParams(query);
            const response = await fetcher(url, {
                headers: { Authorization: `Bearer ${token.value}` }, redirect: "error", signal: AbortSignal.timeout(8000),
            });
            // Other 410 reasons include deleted accounts. Accept only Google's specific expiry signal for this lookup.
            if (response.status === 410 && path.startsWith("/purchases/subscriptionsv2/tokens/")) {
                const details = await response.json().catch(() => null);
                const errors = details?.error?.errors;
                if (Array.isArray(errors) && errors.length > 0 &&
                    errors.every(error => error?.reason === "subscriptionNoLongerAvailable")) {
                    throw new ExpiredSubscription();
                }
            }
            if (response.status === 404 || response.status === 410 || response.status === 400) throw rejected();
            if (response.status === 401) token = undefined;
            if (!response.ok) throw new Error();
            return await response.json();
        } catch (error) {
            if (error instanceof SupportError) throw error;
            throw new SupportError(503, "play_unavailable");
        }
    }

    async function orderFor(env, orderId, productId, purchaseToken) {
        if (!/^[A-Za-z0-9._-]{4,120}$/.test(orderId ?? "")) throw rejected();
        const order = await google(env, "/orders/" + encodeURIComponent(orderId), { fields: ORDER_FIELDS });
        if (order.orderId !== orderId || order.purchaseToken !== purchaseToken ||
            order.lineItems?.length !== 1 || order.lineItems[0].productId !== productId) throw rejected();
        return order;
    }

    return {
        async verify(env, accountId, { kind, productId, purchaseToken }) {
            const unit = catalogCents(env, kind, productId);
            const tokenPath = "/tokens/" + encodeURIComponent(purchaseToken);
            let orderId, quantity = 1, remaining = 1;
            if (kind === "tip") {
                const item = await google(env, "/purchases/products/" + encodeURIComponent(productId) + tokenPath,
                    { fields: PRODUCT_FIELDS });
                if (item.obfuscatedExternalAccountId !== accountId || item.productId && item.productId !== productId ||
                    item.purchaseType !== undefined) throw rejected();
                if (item.purchaseState === 2) throw new SupportError(409, "purchase_pending");
                if (item.purchaseState !== 0 && item.purchaseState !== 1) throw rejected();
                orderId = item.orderId;
                quantity = item.quantity ?? 1;
                remaining = item.purchaseState === 1 ? 0 : item.refundableQuantity ?? quantity;
                if (!Number.isSafeInteger(quantity) || quantity < 1 || !Number.isSafeInteger(quantity * unit) ||
                    !Number.isSafeInteger(remaining) || remaining < 0 || remaining > quantity ||
                    productId !== "support_tip_unit" && quantity !== 1) throw rejected();
            } else {
                const item = await google(env, "/purchases/subscriptionsv2" + tokenPath, { fields: SUBSCRIPTION_FIELDS });
                if (item.externalAccountIdentifiers?.obfuscatedExternalAccountId !== accountId || "testPurchase" in item) {
                    throw rejected();
                }
                if (item.subscriptionState === "SUBSCRIPTION_STATE_PENDING") throw new SupportError(409, "purchase_pending");
                const matches = item.lineItems?.filter(line => line.productId === productId) ?? [];
                if (matches.length !== 1 || !matches[0].latestSuccessfulOrderId) throw rejected();
                orderId = matches[0].latestSuccessfulOrderId;
            }
            const order = await orderFor(env, orderId, productId, purchaseToken);
            const orderQuantity = order.lineItems[0].oneTimePurchaseDetails?.quantity;
            if (kind === "tip" && orderQuantity !== undefined && orderQuantity !== quantity) throw rejected();
            const nominalCents = unit * quantity;
            const cents = Math.min(unit * remaining, netCents(order, nominalCents));
            return { productId, cents, nominalCents, orderHash: await hash(orderId), tokenHash: await hash(purchaseToken) };
        },
        async voided(env, page) {
            const query = { type: "1", includeQuantityBasedPartialRefund: "true", maxResults: "100",
                fields: "tokenPagination(nextPageToken),voidedPurchases(orderId,purchaseToken,voidedQuantity)" };
            if (page) query.token = page;
            return google(env, "/purchases/voidedpurchases", query);
        },
        async refundedBalance(env, refund, receipt) {
            if (!refund.voidedQuantity) return 0;
            const order = await orderFor(env, refund.orderId, receipt.product_id, refund.purchaseToken);
            return netCents(order, receipt.nominal_cents);
        },
    };
}
