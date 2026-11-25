import { beforeAll, beforeEach, expect, test } from "bun:test";
import { Database } from "bun:sqlite";
import { hash, playVerifier } from "../../server/support/src/play.js";
import { createWorker, visitSource } from "../../server/support/src/worker.js";

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
const ranksSchema = await Bun.file(new URL("../../server/support/migrations/0002_ranks_reports.sql", import.meta.url)).text();
const visitsSchema = await Bun.file(new URL("../../server/support/migrations/0003_site_visits.sql", import.meta.url)).text();

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
    expect(options.redirect).toBe("manual");
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
    db.exec("PRAGMA foreign_keys=ON; " + schema + ranksSchema + visitsSchema);
    worker = createWorker(playVerifier(fakeGoogle));
    env = { DB: fixtureD1(db), PLAY_SERVICE_ACCOUNT: serviceAccount, APP_PACKAGE: "com.titankeys",
        TIP_SIP_CENTS: "49", MONTHLY_SIP_CENTS: "69", ADMIN_TOKEN: OWNER_SECRET, RATE_SALT: "fixture-salt-0123456789" };
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

/** The account's total in cups, from the database: the API never returns amounts. */
function cupsOf(accountKeyHash) {
    const row = db.query("SELECT COALESCE(SUM(cents), 0) AS cents FROM support_orders WHERE account_id = ?").get(accountKeyHash);
    return Math.round(row.cents / 50) / 10;
}

async function cupsAfter(posting, accountKey = ACCOUNT) {
    const response = await posting;
    expect(response.status).toBe(200);
    const body = await response.json();
    expect(Object.keys(body).sort()).toEqual(["messageLimit", "rank", "tier"]);
    return cupsOf(await hash(accountKey));
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
        expect(await response.json()).toEqual({ entries: [], total: 0, nextOffset: null });
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
    expect((await publish([tip], { message: "🧃".repeat(121) })).status).toBe(400);
    expect(await entries()).toEqual([]);

    const result = await publish([tip], { message: "🧃".repeat(80) });
    expect(await result.json()).toEqual({ rank: 1, tier: "top3", messageLimit: 120 });
    expect((await entries())[0]).toEqual({ rank: 1, id: expect.any(String), name: "Sunny", message: "🧃".repeat(80) });
    expect(cupsOf(await hash(ACCOUNT))).toBe(1.4);
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
    expect(cupsOf(await hash(ACCOUNT))).toBe(.4);
    const secondMonth = await paid({ kind: "subscription", productId: MONTHLY, label: "month-1", tokenLabel: "month", cents: 200 });
    const custom = await paid({ label: "custom", quantity: 7, cents: 700 });
    const fixed = await paid({ label: "fixed", productId: "support_tip_unit", quantity: 5, cents: 500 });
    expect(await cupsAfter(publish([secondMonth, custom, fixed]))).toBe(3.2);
    expect(db.query("SELECT COUNT(*) AS count FROM support_orders").get().count).toBe(4);
});

test("fixed tiers, the sip, and zero or pending receipts use the Play data", async () => {
    const pending = await paid({ label: "waiting", provider: { purchaseState: 2 } });
    const notPaid = await paid({ label: "test", provider: { purchaseType: 0 } });
    const notARealCharge = await paid({ label: "free", cents: 0 });
    const guessedQty = await paid({ label: "fixed-multiple", productId: "support_tip_full", quantity: 2, cents: 400 });
    expect((await publish([pending])).status).toBe(409);
    expect((await publish([notPaid])).status).toBe(422);
    expect((await publish([guessedQty])).status).toBe(422);
    expect(await cupsAfter(publish([notARealCharge]))).toBe(0);
    expect(db.query("SELECT COUNT(*) AS count FROM supporters").get().count).toBe(0);
    const choices = await Promise.all([[3, "support_tip_unit"], [5, "support_tip_unit"], [10, "support_tip_unit"], [20, "support_tip_unit"],
        [50, "support_tip_unit"]].map(([amount, productId]) => paid({
        label: "fixed-" + amount, productId, quantity: amount, cents: amount * 100,
    })));
    const sip = await paid({ label: "sip", productId: "support_tip_sip", cents: 49 });
    expect(await cupsAfter(publish([...choices, sip]))).toBe(17.7);
});

test("a rejected receipt counts for nothing, alone it posts nothing, and provider errors stay private", async () => {
    const tip = await paid({ label: "valid" });
    const unproved = { ...tip, purchaseToken: "fixture-unproved" };
    const response = await publish([unproved]);
    expect(response.status).toBe(422);
    expect(await response.json()).toEqual({ error: "purchase_unverified" });
    expect(await entries()).toEqual([]);
    // Beside a verified one, it's skipped: only the verified purchase counts.
    expect(await cupsAfter(publish([tip, unproved]))).toBe(await cupsAfter(publish([tip])));
    expect((await api("remove", { accountKey: ACCOUNT })).status).toBe(204);
    expect(await entries()).toEqual([]);
    failAt = "orders/GPA.fixture-valid";
    const offline = await publish([tip]);
    expect(offline.status).toBe(503);
    expect(await offline.json()).toEqual({ error: "play_unavailable" });
    expect(await entries()).toEqual([]);
});

test("a purchase Play no longer vouches for is skipped alongside a fresh verified receipt, and fails alone", async () => {
    const old = await paid({ kind: "subscription", productId: MONTHLY, label: "expired", cents: 200 });
    expect(await cupsAfter(publish([old]))).toBe(.4);
    const active = await paid({ label: "active" });
    const path = "purchases/subscriptionsv2/tokens/" + old.purchaseToken;
    PROVIDER_ERRORS.set(path, { status: 410, reason: "subscriptionNoLongerAvailable" });
    const onlyExpired = await publish([old], { name: "Unverified edit" });
    expect(onlyExpired.status).toBe(422);
    expect(await onlyExpired.json()).toEqual({ error: "purchase_expired" });
    expect((await entries())[0].name).toBe("Sunny");
    expect(await cupsAfter(publish([old, active], { name: "Verified edit" }))).toBe(.6);
    expect((await entries())[0].name).toBe("Verified edit");

    // Play keeps listing a refunded purchase on the phone for a while (owner's join failed with one, 2026-11-24): it adds
    // nothing, and the fresh purchase still posts. Alone, it's refused as before.
    for (const [index, error] of [
        { status: 410, reason: "purchaseTokenNoLongerValid" },
        { status: 404, reason: "subscriptionNoLongerAvailable" },
    ].entries()) {
        PROVIDER_ERRORS.set(path, error);
        const alone = await publish([old], { name: "Unverified edit" });
        expect(alone.status).toBe(422);
        expect(await alone.json()).toEqual({ error: "purchase_unverified" });
        expect((await entries())[0].name).toBe(index ? "Skipped 0" : "Verified edit");
        expect(await cupsAfter(publish([old, active], { name: `Skipped ${index}` }))).toBe(.6);
        expect((await entries())[0].name).toBe(`Skipped ${index}`);
    }
});

test("anonymity and user or owner removal clear public data", async () => {
    const tip = await paid({ label: "person" });
    await publish([tip]);
    await publish([tip], { anonymous: true, name: "Do not expose this", message: "Public note" });
    // Anonymous keeps its credit but isn't on the board (owner, 2026-11-20: "if they are anonymous they don't get on the board"),
    // and the name it was sent with is kept nowhere.
    expect(await entries()).toEqual([]);
    const tables = db.query("SELECT name FROM sqlite_master WHERE type = 'table'").all();
    expect(tables.map(({ name }) => JSON.stringify(db.query(`SELECT * FROM ${name}`).all())).join("")).not.toContain("Do not expose this");
    await publish([tip], { name: "Named again", message: "Public note" });
    const [entry] = await entries();
    expect(entry).toMatchObject({ rank: 1, name: "Named again", message: "Public note" });
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

test("the board ranks everyone by real cents, a page at a time, and shows only place, id, name and message", async () => {
    for (let quantity = 1; quantity <= 12; quantity++) {
        const accountKey = quantity.toString(16).padStart(64, "0");
        const tip = await paid({ label: "supporter-" + quantity, accountKey, quantity, cents: quantity * 100 });
        expect((await publish([tip], { accountKey, name: "Supporter " + quantity })).status).toBe(200);
    }
    const page = await (await api("leaderboard?limit=10")).json();
    expect(page.entries.map(entry => entry.name)).toEqual(Array.from({ length: 10 }, (_, idx) => "Supporter " + (12 - idx)));
    expect(page.entries.map(entry => entry.rank)).toEqual(Array.from({ length: 10 }, (_, idx) => idx + 1));
    expect(Object.keys(page.entries[0]).sort()).toEqual(["id", "message", "name", "rank"]);
    expect(page).toMatchObject({ total: 12, nextOffset: 10 });
    const rest = await (await api("leaderboard?offset=10&limit=10")).json();
    expect(rest.entries.map(entry => [entry.rank, entry.name])).toEqual([[11, "Supporter 2"], [12, "Supporter 1"]]);
    expect(rest.nextOffset).toBe(null);
    expect(JSON.stringify([page, rest])).not.toMatch(/cents|cups/);
    expect((await api("leaderboard?limit=51")).status).toBe(400);
});

test("partial and full Play refunds reduce the same order without restoring data on replay", async () => {
    const tip = await paid({ label: "part", quantity: 7, cents: 910, tax: 91, currency: "CAD" });
    expect(await cupsAfter(publish([tip]))).toBe(1.4);
    const order = ORDERS.get("GPA.fixture-part");
    order.state = "PARTIALLY_REFUNDED";
    order.orderHistory = { partialRefundEvents: [{ state: "PROCESSED_SUCCESSFULLY",
        refundDetails: { total: money(286, "CAD"), tax: money(26, "CAD") } }] };
    voided = [{ orderId: "GPA.fixture-part", purchaseToken: tip.purchaseToken, voidedQuantity: 2 }];
    await worker.scheduled({}, env);
    await worker.scheduled({}, env);
    expect(cupsOf(await hash(ACCOUNT))).toBe(1);
    order.state = "PROCESSED";
    delete order.orderHistory;
    expect(await cupsAfter(publish([tip]))).toBe(1);
    voided = [{ orderId: "GPA.fixture-part", purchaseToken: "unrelated-token" }];
    await worker.scheduled({}, env);
    expect(cupsOf(await hash(ACCOUNT))).toBe(1);
    voided = [{ orderId: "GPA.fixture-part", purchaseToken: tip.purchaseToken }];
    await worker.scheduled({}, env);
    expect(await entries()).toEqual([]);
    expect(await cupsAfter(publish([tip]))).toBe(0);
});

test("a subscription credits every paid month's order, each once, even posted only in the latest month", async () => {
    const month = await paid({ kind: "subscription", productId: MONTHLY, label: "sub", tokenLabel: "sub", cents: 200 });
    // Play's renewal orders: the first, then ..0 and ..1. The latest is ..1.
    const first = ORDERS.get("GPA.fixture-sub");
    for (const id of ["GPA.fixture-sub..0", "GPA.fixture-sub..1"]) ORDERS.set(id, { ...first, orderId: id });
    TOKENS.get("purchases/subscriptionsv2/tokens/" + month.purchaseToken).lineItems[0].latestSuccessfulOrderId = "GPA.fixture-sub..1";
    expect(await cupsAfter(publish([month]))).toBe(1.2);
    // Posting again adds nothing; next month's renewal adds one month.
    expect(await cupsAfter(publish([month]))).toBe(1.2);
    ORDERS.set("GPA.fixture-sub..2", { ...first, orderId: "GPA.fixture-sub..2" });
    TOKENS.get("purchases/subscriptionsv2/tokens/" + month.purchaseToken).lineItems[0].latestSuccessfulOrderId = "GPA.fixture-sub..2";
    expect(await cupsAfter(publish([month]))).toBe(1.6);
    expect(db.query("SELECT COUNT(*) AS count FROM support_orders").get().count).toBe(4);
    // An earlier order Play no longer has is skipped; a refunded month counts nothing.
    ORDERS.delete("GPA.fixture-sub..0");
    ORDERS.set("GPA.fixture-sub..1", { ...first, orderId: "GPA.fixture-sub..1", state: "REFUNDED" });
    expect(await cupsAfter(publish([month]))).toBe(1.2);
});

test("names and messages lose control, bidi and zero-width characters, and keep their words", async () => {
    const tip = await paid({ label: "chars" });
    await publish([tip], { name: "‮Sun​ny\u0007", message: "⁦Che‪ers⁩ ﻿!" });
    const [entry] = await entries();
    expect(entry.name).toBe("Sunny");
    expect(entry.message).toBe("Cheers !");
    // A name of nothing but invisible characters is no name: anonymous, so off the board (owner, 2026-11-20).
    await publish([tip], { name: "​‮", message: "damn fine keyboard" });
    expect(await entries()).toEqual([]);
});

test("Remove me deletes the account's orders, so rejoining starts fresh", async () => {
    const tip = await paid({ label: "leaver" });
    await publish([tip]);
    db.exec("PRAGMA foreign_keys=OFF");
    expect((await api("remove", { accountKey: ACCOUNT })).status).toBe(204);
    expect(db.query("SELECT COUNT(*) AS count FROM support_orders").get().count).toBe(0);
    expect(db.query("SELECT COUNT(*) AS count FROM supporters").get().count).toBe(0);
    db.exec("PRAGMA foreign_keys=ON");
});

async function supporter(number, cents, fields = {}) {
    const accountKey = number.toString(16).padStart(64, "0");
    const tip = await paid({ label: "rank-" + number, accountKey, quantity: cents / 100, cents });
    const response = await publish([tip], { accountKey, name: "S" + number, ...fields });
    expect(response.status).toBe(200);
    return { accountKey, ...(await response.json()) };
}

test("messages are as long as the place allows: 120 for the top 3, 80 for the top 10, 40 for the top 100, then the name only", async () => {
    const long = "m".repeat(120);
    const first = await supporter(1, 10_000, { message: long });
    expect(first).toMatchObject({ rank: 1, tier: "top3", messageLimit: 120 });
    // Ten more above it: it drops to 11th, and the board shows 40 characters of what it stored.
    for (let n = 2; n <= 11; n++) await supporter(n, 20_000 + n * 100);
    const page = await (await api("leaderboard?offset=10&limit=1")).json();
    expect(page.entries[0]).toMatchObject({ rank: 11, name: "S1", message: "m".repeat(40) });
    // Posting at 11th keeps only what that place allows.
    const again = await supporter(12, 500, { message: long });
    expect(again).toMatchObject({ rank: 12, tier: "top100", messageLimit: 40 });
    expect(db.query("SELECT message FROM supporters WHERE name = 'S12'").get().message).toBe("m".repeat(40));
    expect(messageLimitFor(4)).toBe(80);
});

function messageLimitFor(rank) {
    return rank <= 3 ? 120 : rank <= 10 ? 80 : rank <= 100 ? 40 : 0;
}

test("beyond 100th the board shows the name only, and the message is still kept", async () => {
    db.exec("INSERT INTO supporters (account_id, public_id, name, message) VALUES ('low', '00000000-0000-4000-8000-000000000000', 'Low', 'kept')");
    for (let n = 1; n <= 101; n++) {
        const account = "acct-" + n;
        const id = "00000000-0000-4000-8000-" + n.toString().padStart(12, "0");
        if (n <= 100) db.query("INSERT INTO supporters (account_id, public_id, name, message) VALUES (?, ?, ?, ?)").run(account, id, "A" + n, "hi");
        db.query("INSERT INTO support_orders (order_hash, product_id, token_hash, account_id, nominal_cents, cents) VALUES (?, 'support_tip_unit', ?, ?, ?, ?)")
            .run("o" + n, "t" + n, n <= 100 ? account : "low", 1_000_000, n <= 100 ? 10_000 : 1);
    }
    const last = await (await api("leaderboard?offset=100&limit=5")).json();
    expect(last.entries).toEqual([{ rank: 101, id: "00000000-0000-4000-8000-000000000000", name: "Low", message: null }]);
    expect(db.query("SELECT message FROM supporters WHERE account_id = 'low'").get().message).toBe("kept");
});

test("the rank preview says where more would put you, never anyone's total, within an hourly limit", async () => {
    await supporter(1, 500);
    await supporter(2, 300);
    const preview = body => api("rank-preview", body, { "CF-Connecting-IP": "203.0.113.9" });
    const NEWCOMER = "e".repeat(64);
    // A newcomer with $2 would be third; with $4 more, second; with $6, first.
    expect(await (await preview({ accountKey: NEWCOMER, addCents: 200 })).json())
        .toEqual({ rank: 3, tier: "top3", messageLimit: 120, myRank: null, myId: null });
    const own = await (await preview({ accountKey: (2).toString(16).padStart(64, "0"), addCents: 0 })).json();
    expect(own).toMatchObject({ rank: 2, myRank: 2, myId: (await entries())[1].id });
    expect((await (await preview({ accountKey: NEWCOMER, addCents: 400 })).json()).rank).toBe(2);
    expect((await (await preview({ accountKey: NEWCOMER, addCents: 600 })).json()).rank).toBe(1);
    // An account already on the board counts its own total, not itself as someone ahead.
    expect((await (await preview({ accountKey: (2).toString(16).padStart(64, "0"), addCents: 300 })).json()).rank).toBe(1);
    expect((await (await preview({ accountKey: ACCOUNT, addCents: 0 })).json()).rank).toBe(1);
    expect((await preview({ accountKey: ACCOUNT, addCents: -1 })).status).toBe(400);
    expect((await preview({ accountKey: ACCOUNT, addCents: 1.5 })).status).toBe(400);
    let status = 200;
    for (let i = 0; i < 40 && status === 200; i++) status = (await preview({ accountKey: NEWCOMER, addCents: 100 })).status;
    expect(status).toBe(429);
    expect(JSON.stringify(db.query("SELECT key FROM rate_limits").all())).not.toContain("203.0.113.9");
    // The account limit holds from another address too.
    expect((await api("rank-preview", { accountKey: NEWCOMER, addCents: 1 }, { "CF-Connecting-IP": "x" })).status).toBe(429);
});

test("anyone can report an entry a few times an hour; the owner lists reports, and nothing is hidden by itself", async () => {
    const { rank } = await supporter(1, 500, { message: "hello" });
    expect(rank).toBe(1);
    const [entry] = await entries();
    const report = (id, ip = "198.51.100.1") => api("report", { id, reason: "‮spam" }, { "CF-Connecting-IP": ip });
    expect((await report(entry.id)).status).toBe(204);
    expect((await report("00000000-0000-4000-8000-0000000000ff")).status).toBe(404);
    for (let i = 0; i < 3; i++) await report(entry.id);
    expect((await report(entry.id)).status).toBe(429);
    expect((await report(entry.id, "198.51.100.2")).status).toBe(204);
    expect(await entries()).toHaveLength(1);
    expect((await api("admin/reports", undefined, { Authorization: "Bearer wrong-token-".repeat(3) })).status).toBe(404);
    const list = await (await api("admin/reports", undefined, { Authorization: "Bearer " + OWNER_SECRET })).json();
    expect(list.reports).toHaveLength(5);
    expect(list.reports[0]).toMatchObject({ id: entry.id, name: "S1", message: "hello", reason: "spam" });
    expect(JSON.stringify(db.query("SELECT * FROM support_reports").all())).not.toContain("198.51.100");
});

test("without a rate salt, the preview and reports stay off rather than count by raw address", async () => {
    delete env.RATE_SALT;
    expect((await api("rank-preview", { accountKey: ACCOUNT, addCents: 100 })).status).toBe(503);
    expect((await api("report", { id: "00000000-0000-4000-8000-000000000001", reason: null })).status).toBe(503);
});

test("half a coffee is $2.50, one-time and monthly, and counts no more than that", async () => {
    const tip = await paid({ label: "half", productId: "support_tip_full", cents: 250 });
    expect(await cupsAfter(publish([tip]))).toBe(.5);
    const over = await paid({ label: "half-over", productId: "support_tip_full", cents: 300 });
    expect(await cupsAfter(publish([over]))).toBe(1);
    const month = await paid({ kind: "subscription", productId: MONTHLY, label: "half-month", cents: 250 });
    expect(await cupsAfter(publish([month]))).toBe(1.5);
});

test("website visits count by day, page, source and event, and keep nothing about the person", async () => {
    const hit = query => worker.fetch(new Request("https://fixture.invalid/v1/visit?" + query, { method: "POST" }), env);
    expect((await hit("e=view&p=/titankeys/&s=l.facebook.com")).status).toBe(204);
    expect((await hit("e=view&p=/titankeys/&s=com.facebook.katana")).status).toBe(204);
    expect((await hit("e=group&p=/titankeys/&s=fb")).status).toBe(204);
    expect((await hit("e=view&p=/titankeys/")).status).toBe(204);
    expect((await hit("e=steal&p=/titankeys/")).status).toBe(400);
    expect((await hit("e=view&p=https://evil")).status).toBe(400);
    const rows = db.query("SELECT page, source, event, count FROM site_visits ORDER BY source, event").all();
    expect(rows).toEqual([
        { page: "/titankeys/", source: "direct", event: "view", count: 1 },
        { page: "/titankeys/", source: "facebook", event: "group", count: 1 },
        { page: "/titankeys/", source: "facebook", event: "view", count: 2 },
    ]);
    expect(Object.keys(db.query("SELECT * FROM site_visits").get())).toEqual(["day", "page", "source", "event", "count"]);
    expect(["www.reddit.com", "com.reddit.frontpage", "www.google.com.hk", "youtu.be", "github.com", "forum.example.org", "<script>"]
        .map(visitSource)).toEqual(["reddit", "reddit", "google", "youtube", "github", "forum.example.org", "other"]);
});
