import { beforeAll, beforeEach, expect, test } from "bun:test";
import { Database } from "bun:sqlite";
import { hash, playVerifier } from "../../server/support/src/play.js";
import { createWorker } from "../../server/support/src/worker.js";

const ACCOUNT = "0".repeat(63) + "1";
const OWNER_SECRET = "a".repeat(64);
const CURRENCY = "USD";
const TIP = "support_tip_unit";
const MONTHLY = "support_monthly_full";
const TOKENS = new Map();
const ORDERS = new Map();
const PROVIDER_ERRORS = new Map();
const outgoing = [];
let voided = [];
let failAt;
let oauthRequests;
let db;
let worker;
let env;
let serviceAccount;
const schema = await Bun.file(new URL("../../server/support/migrations/0001_support.sql", import.meta.url)).text();

beforeAll(async () => {
    const signer = await crypto.subtle.generateKey({
        name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256",
    }, true, ["sign", "verify"]);
    const exported = new Uint8Array(await crypto.subtle.exportKey("pkcs8", signer.privateKey));
    serviceAccount = JSON.stringify({
        client_email: "fixture@example.invalid",
        private_key: "-----BEGIN PRIVATE KEY-----\n" + btoa(String.fromCharCode(...exported)) + "\n-----END PRIVATE KEY-----\n",
    });
});

function fixtureD1(sqlite) {
    return {
        prepare(query) {
            function create(values) {
                const statement = sqlite.query(query);
                return {
                    bind: (...args) => create(args),
                    run: () => statement.run(...values),
                    all: () => ({ results: statement.all(...values) }),
                };
            }
            return create([]);
        },
        async batch(statements) {
            return sqlite.transaction(() => statements.map(statement => statement.all()))();
        },
    };
}

async function fakeGoogle(url, options) {
    if (url === "https://oauth2.googleapis.com/token") {
        oauthRequests++;
        const assertion = options.body.get("assertion");
        expect(assertion.split(".")).toHaveLength(3);
        return Response.json({ access_token: "fake-oauth", expires_in: 3600 });
    }
    expect(url.origin).toBe("https://androidpublisher.googleapis.com");
    expect(options.headers.Authorization).toBe("Bearer fake-oauth");
    expect(options.redirect).toBe("error");
    expect(url.pathname).toStartWith("/androidpublisher/v3/applications/com.titankeys/");
    const path = url.pathname.split("/com.titankeys/")[1];
    outgoing.push(path);
    if (path === failAt) throw new Error("private provider failure");
    const fields = url.searchParams.get("fields");
    expect(fields).not.toContain("buyerAddress");
    expect(fields).not.toContain("subscribeWithGoogleInfo");
    const rejected = PROVIDER_ERRORS.get(path);
    if (rejected) {
        return Response.json({ error: { errors: [{ reason: rejected.reason }], message: "private provider error" } },
            { status: rejected.status });
    }
    if (path === "purchases/voidedpurchases") {
        expect(url.searchParams.get("includeQuantityBasedPartialRefund")).toBe("true");
        return Response.json({ voidedPurchases: voided });
    }
    const data = path.startsWith("orders/") ? ORDERS.get(path.slice(7)) : TOKENS.get(path);
    return data === undefined ? Response.json({ message: "private provider response" }, { status: 404 }) : Response.json(data);
}

beforeEach(() => {
    if (db) db.close();
    db = new Database(":memory:");
    db.exec("PRAGMA foreign_keys=ON; " + schema);
    worker = createWorker(playVerifier(fakeGoogle));
    env = { DB: fixtureD1(db), PLAY_SERVICE_ACCOUNT: serviceAccount, APP_PACKAGE: "com.titankeys",
        TIP_SIP_CENTS: "49", MONTHLY_SIP_CENTS: "69", ADMIN_TOKEN: OWNER_SECRET };
    TOKENS.clear();
    ORDERS.clear();
    PROVIDER_ERRORS.clear();
    outgoing.length = 0;
    voided = [];
    failAt = undefined;
    oauthRequests = 0;
});

function money(cents, currencyCode = CURRENCY) {
    return { currencyCode, units: String(Math.floor(cents / 100)), nanos: cents % 100 * 10_000_000 };
}

async function paid({ label, accountKey = ACCOUNT, kind = "tip", productId = TIP, quantity = 1,
    cents = 100, currency = CURRENCY, state = "PROCESSED", provider = {}, tax = 0, tokenLabel = label }) {
    const purchaseToken = "fixture-token-" + tokenLabel;
    const orderId = "GPA.fixture-" + label;
    const accountId = await hash(accountKey);
    const productPath = "purchases/products/" + productId + "/tokens/" + purchaseToken;
    const subPath = "purchases/subscriptionsv2/tokens/" + purchaseToken;
    const product = { obfuscatedExternalAccountId: accountId, productId, orderId, purchaseState: 0, quantity,
        refundableQuantity: quantity, ...provider };
    const subscription = { externalAccountIdentifiers: { obfuscatedExternalAccountId: accountId },
        subscriptionState: "SUBSCRIPTION_STATE_ACTIVE", lineItems: [{ productId, latestSuccessfulOrderId: orderId }], ...provider };
    TOKENS.set(kind === "tip" ? productPath : subPath, kind === "tip" ? product : subscription);
    ORDERS.set(orderId, { orderId, purchaseToken, state, total: money(cents + tax, currency), tax: money(tax, currency),
        lineItems: [{ productId, oneTimePurchaseDetails: { quantity } }] });
    return { kind, productId, purchaseToken };
}

function api(path, body, headers = {}) {
    if (body !== undefined) headers["Content-Type"] = "application/json";
    return worker.fetch(new Request("https://fixture.invalid/v1/" + path, {
        method: body === undefined ? "GET" : "POST", headers, body: JSON.stringify(body),
    }), env);
}

async function publish(purchases, fields = {}) {
    return api("support", { accountKey: ACCOUNT, optIn: true, anonymous: false, name: "Sunny",
        message: "Cheers", purchases, ...fields });
}

async function entries() {
    const response = await api("leaderboard");
    expect(response.headers.get("Cache-Control")).toBe("no-store");
    return (await response.json()).entries;
}

test("the leaderboard advertises posting only with complete local configuration", async () => {
    const assertBoard = async readiness => {
        const response = await api("leaderboard");
        expect(response.status).toBe(200);
        expect(response.headers.get("Cache-Control")).toBe("no-store");
        expect(response.headers.get("X-TitanKeys-Posting")).toBe(readiness);
        expect(await response.json()).toEqual({ entries: [] });
    };
    await assertBoard("ready");
    for (const [setting, invalid] of [
        ["APP_PACKAGE", undefined],
        ["PLAY_SERVICE_ACCOUNT", undefined],
        ["PLAY_SERVICE_ACCOUNT", '{"client_email":"fixture@example.invalid"}'],
        ["PLAY_SERVICE_ACCOUNT", JSON.stringify({
            client_email: "fixture@example.invalid",
            private_key: "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----\n",
        })],
        ["ADMIN_TOKEN", undefined],
        ["ADMIN_TOKEN", "too-short"],
        ["ADMIN_TOKEN", "a".repeat(250)],
        ["TIP_SIP_CENTS", undefined],
        ["TIP_SIP_CENTS", "0"],
        ["MONTHLY_SIP_CENTS", undefined],
        ["MONTHLY_SIP_CENTS", "100"],
    ]) {
        const original = env[setting];
        env[setting] = invalid;
        await assertBoard(null);
        env[setting] = original;
    }
    env.PLAY_SERVICE_ACCOUNT = undefined;
    const tip = await paid({ label: "unconfigured" });
    const response = await publish([tip]);
    expect(response.status).toBe(503);
    expect(response.headers.get("X-TitanKeys-Posting")).toBeNull();
    expect(await response.json()).toEqual({ error: "play_unavailable" });
    await assertBoard(null);
    expect(oauthRequests).toBe(0);
    expect(outgoing).toEqual([]);
});

test("only verified opt-in changes the board and the database stores hashes", async () => {
    expect(await entries()).toEqual([]);
    const tip = await paid({ label: "unit", quantity: 7, cents: 700 });
    const ignored = await publish([tip], { optIn: false });
    expect(ignored.status).toBe(400);
    expect(outgoing).toEqual([]);
    const wrongAccount = await publish([tip], { accountKey: "a".repeat(64) });
    expect(wrongAccount.status).toBe(422);
    expect((await publish([tip], { message: "🧃".repeat(81) })).status).toBe(400);
    expect(await entries()).toEqual([]);

    const result = await publish([tip], { message: "🧃".repeat(80) });
    expect(await result.json()).toEqual({ cups: 1.4 });
    expect((await entries())[0]).toMatchObject({ name: "Sunny", message: "🧃".repeat(80), cups: 1.4 });
    const row = db.query("SELECT * FROM support_orders").get();
    expect(row.token_hash).toBe(await hash(tip.purchaseToken));
    expect(row.order_hash).toBe(await hash("GPA.fixture-unit"));
    expect(row.account_id).toBe(await hash(ACCOUNT));
    expect(JSON.stringify(row)).not.toContain(tip.purchaseToken);
    expect(db.query("SELECT account_id FROM supporters").get().account_id).not.toBe(ACCOUNT);
});

test("retries and repeated tokens do not double count, but separate renewal orders accrue", async () => {
    const firstMonth = await paid({ kind: "subscription", productId: MONTHLY, label: "month-0", tokenLabel: "month", cents: 200 });
    await Promise.all([publish([firstMonth, firstMonth]), publish([firstMonth])]);
    expect((await entries())[0].cups).toBe(.4);
    const secondMonth = await paid({ kind: "subscription", productId: MONTHLY, label: "month-1", tokenLabel: "month", cents: 200 });
    const custom = await paid({ label: "custom", quantity: 7, cents: 700 });
    const fixed = await paid({ label: "fixed", productId: "support_tip_5", cents: 500 });
    expect(await (await publish([secondMonth, custom, fixed])).json()).toEqual({ cups: 3.2 });
    expect(db.query("SELECT COUNT(*) AS count FROM support_orders").get().count).toBe(4);
});

test("fixed tiers, the sip, and zero or pending receipts use the Play data", async () => {
    const pending = await paid({ label: "waiting", provider: { purchaseState: 2 } });
    const notPaid = await paid({ label: "test", provider: { purchaseType: 0 } });
    const notARealCharge = await paid({ label: "free", cents: 0 });
    const guessedQty = await paid({ label: "fixed-multiple", productId: "support_tip_20", quantity: 2, cents: 4000 });
    expect((await publish([pending])).status).toBe(409);
    expect((await publish([notPaid])).status).toBe(422);
    expect((await publish([guessedQty])).status).toBe(422);
    expect(await (await publish([notARealCharge])).json()).toEqual({ cups: 0 });
    expect(db.query("SELECT COUNT(*) AS count FROM supporters").get().count).toBe(0);
    const choices = await Promise.all([3, 5, 10, 20, 50].map(amount => paid({
        label: "fixed-" + amount, productId: "support_tip_" + amount, cents: amount * 100,
    })));
    const sip = await paid({ label: "sip", productId: "support_tip_sip", cents: 49 });
    expect(await (await publish([...choices, sip])).json()).toEqual({ cups: 17.7 });
});

test("a rejected receipt rolls back the entire submission and provider errors stay private", async () => {
    const tip = await paid({ label: "valid" });
    const unproved = { ...tip, purchaseToken: "fixture-unproved" };
    const response = await publish([tip, unproved]);
    expect(response.status).toBe(422);
    expect(await response.json()).toEqual({ error: "purchase_unverified" });
    expect(await entries()).toEqual([]);
    failAt = "orders/GPA.fixture-valid";
    const offline = await publish([tip]);
    expect(offline.status).toBe(503);
    expect(await offline.json()).toEqual({ error: "play_unavailable" });
    expect(await entries()).toEqual([]);
});

test("only a documented expired subscription can be skipped alongside a fresh verified receipt", async () => {
    const old = await paid({ kind: "subscription", productId: MONTHLY, label: "expired", cents: 200 });
    expect(await (await publish([old])).json()).toEqual({ cups: .4 });
    const active = await paid({ label: "active" });
    const path = "purchases/subscriptionsv2/tokens/" + old.purchaseToken;
    PROVIDER_ERRORS.set(path, { status: 410, reason: "subscriptionNoLongerAvailable" });
    const onlyExpired = await publish([old], { name: "Unverified edit" });
    expect(onlyExpired.status).toBe(422);
    expect(await onlyExpired.json()).toEqual({ error: "purchase_expired" });
    expect((await entries())[0].name).toBe("Sunny");
    expect(await (await publish([old, active], { name: "Verified edit" })).json()).toEqual({ cups: .6 });
    expect((await entries())[0].name).toBe("Verified edit");

    for (const error of [
        { status: 410, reason: "purchaseTokenNoLongerValid" },
        { status: 404, reason: "subscriptionNoLongerAvailable" },
    ]) {
        PROVIDER_ERRORS.set(path, error);
        const rejected = await publish([old, active], { name: "Unverified edit" });
        expect(rejected.status).toBe(422);
        expect(await rejected.json()).toEqual({ error: "purchase_unverified" });
        expect((await entries())[0].name).toBe("Verified edit");
    }
});

test("anonymity and user or owner removal clear public data", async () => {
    const tip = await paid({ label: "person" });
    await publish([tip]);
    await publish([tip], { anonymous: true, name: "Do not expose this", message: "Public note" });
    const [entry] = await entries();
    expect(entry).toMatchObject({ name: null, message: "Public note", cups: .2 });
    expect((await api("admin/delete", { id: entry.id }, { Authorization: "Bearer invalid" })).status).toBe(404);
    expect(await entries()).toHaveLength(1);
    expect((await api("remove", { accountKey: ACCOUNT })).status).toBe(204);
    expect(await entries()).toEqual([]);
    expect(db.query("SELECT * FROM support_orders").all()).toEqual([]);

    await publish([tip]);
    const [republished] = await entries();
    expect(republished.id).not.toBe(entry.id);
    expect((await api("admin/delete", { id: republished.id }, { Authorization: "Bearer " + OWNER_SECRET })).status).toBe(204);
    expect(db.query("SELECT * FROM supporters").all()).toEqual([]);
});

test("the top ten sort by real cents and expose only cups and opt-in text", async () => {
    for (let quantity = 1; quantity <= 12; quantity++) {
        const accountKey = quantity.toString(16).padStart(64, "0");
        const tip = await paid({ label: "supporter-" + quantity, accountKey, quantity, cents: quantity * 100 });
        expect((await publish([tip], { accountKey, name: "Supporter " + quantity })).status).toBe(200);
    }
    const board = await entries();
    expect(board.map(entry => entry.name)).toEqual(Array.from({ length: 10 }, (_, idx) => "Supporter " + (12 - idx)));
    expect(Object.keys(board[0]).sort()).toEqual(["cups", "id", "message", "name"]);
});

test("partial and full Play refunds reduce the same order without restoring data on replay", async () => {
    const tip = await paid({ label: "part", quantity: 7, cents: 910, tax: 91, currency: "CAD" });
    expect(await (await publish([tip])).json()).toEqual({ cups: 1.4 });
    const order = ORDERS.get("GPA.fixture-part");
    order.state = "PARTIALLY_REFUNDED";
    order.orderHistory = { partialRefundEvents: [{ state: "PROCESSED_SUCCESSFULLY",
        refundDetails: { total: money(286, "CAD"), tax: money(26, "CAD") } }] };
    voided = [{ orderId: "GPA.fixture-part", purchaseToken: tip.purchaseToken, voidedQuantity: 2 }];
    await worker.scheduled({}, env);
    await worker.scheduled({}, env);
    expect((await entries())[0].cups).toBe(1);
    order.state = "PROCESSED";
    delete order.orderHistory;
    expect(await (await publish([tip])).json()).toEqual({ cups: 1 });
    voided = [{ orderId: "GPA.fixture-part", purchaseToken: "unrelated-token" }];
    await worker.scheduled({}, env);
    expect((await entries())[0].cups).toBe(1);
    voided = [{ orderId: "GPA.fixture-part", purchaseToken: tip.purchaseToken }];
    await worker.scheduled({}, env);
    expect(await entries()).toEqual([]);
    expect(await (await publish([tip])).json()).toEqual({ cups: 0 });
});
