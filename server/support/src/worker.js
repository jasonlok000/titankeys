import { ExpiredSubscription, hash, knownProduct, playPostingConfigured, playVerifier, SupportError } from "./play.js";

const BODY_LIMIT = 32_768;
const ACCOUNT_KEY = /^[0-9a-f]{64}$/;
const PUBLIC_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const PURCHASE_TOKEN = /^[A-Za-z0-9._~+=/-]{1,2048}$/;
const ADMIN_SCHEME = "Bearer ";
const ADMIN_HEADER_LIMIT = 256;
const RESPONSE_HEADERS = {
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
};

function json(data, status = 200, headers = RESPONSE_HEADERS) {
    return Response.json(data, { status, headers });
}

function done() {
    return new Response(null, { status: 204, headers: RESPONSE_HEADERS });
}

function badRequest() {
    return new SupportError(400, "invalid_request");
}

function hasKeys(value, keys) {
    return value !== null && typeof value === "object" && !Array.isArray(value) &&
        Object.keys(value).every(key => keys.includes(key));
}

async function readJson(request) {
    if (!/^application\/json(?:;|$)/i.test(request.headers.get("Content-Type") ?? "") || request.body === null) {
        throw badRequest();
    }
    if (Number(request.headers.get("Content-Length")) > BODY_LIMIT) throw new SupportError(413, "request_too_large");
    const reader = request.body.getReader();
    const buffer = new Uint8Array(BODY_LIMIT);
    let size = 0;
    try {
        while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            if (size + value.byteLength > BODY_LIMIT) {
                await reader.cancel();
                throw new SupportError(413, "request_too_large");
            }
            buffer.set(value, size);
            size += value.byteLength;
        }
        return JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(buffer.subarray(0, size)));
    } catch (error) {
        if (error instanceof SupportError) throw error;
        throw badRequest();
    } finally {
        reader.releaseLock();
    }
}

function optionalText(value) {
    if (value == null) return null;
    if (typeof value !== "string" || Array.from(value).length > 80) throw badRequest();
    return value.trim() || null;
}

function isAccountKey(value) {
    return typeof value === "string" && ACCOUNT_KEY.test(value);
}

function cups(cents) {
    // One cup is 500 cents. Round to the nearest tenth once, after totaling the retained receipts.
    return Math.round(cents / 50) / 10;
}

function validAdminToken(token) {
    return typeof token === "string" && token.length >= 32;
}

async function board(env) {
    const { results } = await env.DB.prepare(`
        SELECT s.public_id AS id, s.name, s.message, SUM(o.cents) AS cents
        FROM supporters s JOIN support_orders o ON s.account_id = o.account_id
        GROUP BY s.account_id HAVING SUM(o.cents) > 0
        ORDER BY cents DESC, s.public_id ASC LIMIT 10
    `).all();
    const entries = results.map(({ id, name, message, cents }) => ({ id, name, message, cups: cups(cents) }));
    const adminToken = env.ADMIN_TOKEN;
    const ready = validAdminToken(adminToken) && adminToken.trimEnd() === adminToken &&
        adminToken.length <= ADMIN_HEADER_LIMIT - ADMIN_SCHEME.length && await playPostingConfigured(env);
    const headers = ready ? { ...RESPONSE_HEADERS, "X-TitanKeys-Posting": "ready" } : RESPONSE_HEADERS;
    return json({ entries }, 200, headers);
}

async function publish(request, env, verifier) {
    const body = await readJson(request);
    if (!hasKeys(body, ["accountKey", "optIn", "anonymous", "name", "message", "purchases"]) ||
        !isAccountKey(body.accountKey) || body.optIn !== true || typeof body.anonymous !== "boolean" ||
        !Array.isArray(body.purchases) || body.purchases.length < 1 || body.purchases.length > 10) throw badRequest();
    const enteredName = optionalText(body.name);
    const name = body.anonymous ? null : enteredName;
    const message = optionalText(body.message);
    const unique = new Map();
    for (const purchase of body.purchases) {
        if (!hasKeys(purchase, ["kind", "productId", "purchaseToken"]) ||
            !knownProduct(purchase.kind, purchase.productId) || typeof purchase.purchaseToken !== "string" ||
            !PURCHASE_TOKEN.test(purchase.purchaseToken)) throw badRequest();
        unique.set(`${purchase.productId}:${purchase.purchaseToken}`, purchase);
    }
    const accountId = await hash(body.accountKey);
    const verified = await Promise.allSettled(Array.from(unique.values(), purchase => verifier.verify(env, accountId, purchase)));
    const receipts = [];
    for (const result of verified) {
        if (result.status === "fulfilled") receipts.push(result.value);
        else if (!(result.reason instanceof ExpiredSubscription)) throw result.reason;
    }
    // Old, expired proofs cannot authorize a profile or grant new credit.
    if (receipts.length === 0) throw new ExpiredSubscription();
    const db = env.DB;
    const statements = [db.prepare(`
        INSERT INTO supporters (account_id, public_id, name, message)
        SELECT ?, ?, ?, ? WHERE ? OR EXISTS (SELECT 1 FROM supporters WHERE account_id = ?)
        ON CONFLICT (account_id) DO UPDATE SET name = excluded.name, message = excluded.message
    `).bind(accountId, crypto.randomUUID(), name, message, Number(receipts.some(receipt => receipt.cents > 0)), accountId)];
    for (const receipt of receipts) {
        // A processed order has a fixed original value. A stale repeat cannot restore refunded credit.
        statements.push(db.prepare(`
            INSERT INTO support_orders (order_hash, product_id, token_hash, account_id, nominal_cents, cents)
            SELECT ?, ?, ?, account_id, ?, ? FROM supporters WHERE account_id = ?
            ON CONFLICT (order_hash, product_id) DO UPDATE SET cents = MIN(support_orders.cents, excluded.cents)
            WHERE support_orders.account_id = excluded.account_id AND support_orders.token_hash = excluded.token_hash
        `).bind(receipt.orderHash, receipt.productId, receipt.tokenHash, receipt.nominalCents, receipt.cents, accountId));
    }
    statements.push(db.prepare("SELECT COALESCE(SUM(cents), 0) AS cents FROM support_orders WHERE account_id = ?").bind(accountId));
    const results = await db.batch(statements);
    return json({ cups: cups(results.at(-1).results[0].cents) });
}

async function remove(request, db) {
    const body = await readJson(request);
    if (!hasKeys(body, ["accountKey"]) || !isAccountKey(body.accountKey)) throw badRequest();
    await db.prepare("DELETE FROM supporters WHERE account_id = ?").bind(await hash(body.accountKey)).run();
    return done();
}

async function adminDelete(request, env) {
    const candidate = request.headers.get("Authorization");
    const secret = env.ADMIN_TOKEN;
    if (!validAdminToken(secret) || candidate === null ||
        !candidate.startsWith(ADMIN_SCHEME) || candidate.length > ADMIN_HEADER_LIMIT) return json({ error: "not_found" }, 404);
    const [supplied, expected] = await Promise.all([hash(candidate.slice(ADMIN_SCHEME.length)), hash(secret)]);
    let difference = 0;
    for (let i = 0; i < expected.length; i++) difference |= supplied.charCodeAt(i) ^ expected.charCodeAt(i);
    if (difference !== 0) return json({ error: "not_found" }, 404);
    const body = await readJson(request);
    if (!hasKeys(body, ["id"]) || typeof body.id !== "string" || !PUBLIC_ID.test(body.id)) throw badRequest();
    await env.DB.prepare("DELETE FROM supporters WHERE public_id = ?").bind(body.id).run();
    return done();
}

async function reconcileRefunds(env, verifier) {
    let page;
    do {
        const results = await verifier.voided(env, page);
        const refunds = await Promise.all((results.voidedPurchases ?? []).filter(
            item => typeof item.orderId === "string" && typeof item.purchaseToken === "string",
        ).map(async item => ({ item, order: await hash(item.orderId), token: await hash(item.purchaseToken) })));
        if (refunds.length) {
            const ids = [...new Set(refunds.map(refund => refund.order))];
            const { results: saved } = await env.DB.prepare(`
                SELECT order_hash, token_hash, product_id, nominal_cents, cents
                FROM support_orders WHERE order_hash IN (${ids.map(() => "?").join(",")}) AND cents > 0
            `).bind(...ids).all();
            const updates = [];
            for (const receipt of saved) {
                const matches = refunds.filter(refund => refund.order === receipt.order_hash && refund.token === receipt.token_hash);
                if (matches.length === 0) continue;
                const knownRefund = matches.find(refund => !refund.item.voidedQuantity) ?? matches[0];
                const cents = await verifier.refundedBalance(env, knownRefund.item, receipt);
                updates.push(env.DB.prepare(`
                    UPDATE support_orders SET cents = MIN(cents, ?)
                    WHERE order_hash = ? AND product_id = ? AND token_hash = ?
                `).bind(cents, receipt.order_hash, receipt.product_id, receipt.token_hash));
            }
            if (updates.length) await env.DB.batch(updates);
        }
        page = results.tokenPagination?.nextPageToken;
    } while (page);
}

export function createWorker(verifier = playVerifier()) {
    return {
        async fetch(request, env) {
            try {
                const path = new URL(request.url).pathname;
                if (request.method === "GET" && path === "/v1/leaderboard") return await board(env);
                if (request.method === "POST" && path === "/v1/support") return await publish(request, env, verifier);
                if (request.method === "POST" && path === "/v1/remove") return await remove(request, env.DB);
                if (request.method === "POST" && path === "/v1/admin/delete") return await adminDelete(request, env);
                return json({ error: "not_found" }, 404);
            } catch (error) {
                if (error instanceof SupportError) return json({ error: error.message }, error.status);
                return json({ error: "support_unavailable" }, 503);
            }
        },
        async scheduled(_, env) {
            try {
                await reconcileRefunds(env, verifier);
            } catch {
                // Neither Google URLs nor response details belong in invocation errors.
                throw new Error("Support refund reconciliation failed");
            }
        },
    };
}

export default createWorker();
