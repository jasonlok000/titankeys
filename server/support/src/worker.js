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

/**
 * Control characters, bidi embeddings, overrides and isolates (U+202A-202E, U+2066-2069), marks that only steer direction, and
 * zero-width characters: none of them shows as text, and on a public board they can reorder or hide what's around them. No
 * content filter: the words themselves stay (owner's rule).
 */
const INVISIBLE = /[\u0000-\u001f\u007f-\u009f\u061c\u200b-\u200f\u202a-\u202e\u2060-\u2064\u2066-\u2069\ufeff]/gu;

function optionalText(value, max = NAME_LIMIT) {
    if (value == null) return null;
    if (typeof value !== "string" || Array.from(value).length > max) throw badRequest();
    return value.replace(INVISIBLE, "").trim() || null;
}

/** A name, up to 80 characters. */
const NAME_LIMIT = 80;
/** The longest message anyone can post: the top three's. */
const MESSAGE_LIMIT = 120;
/** How much of a message a place on the board shows (owner, 2026-11-19): beyond 100th, the name alone. */
export function messageLimit(rank) {
    if (!Number.isInteger(rank) || rank < 1) return 0;
    return rank <= 3 ? 120 : rank <= 10 ? 80 : rank <= 100 ? 40 : 0;
}

export function messageTier(rank) {
    const limit = messageLimit(rank);
    return limit === 120 ? "top3" : limit === 80 ? "top10" : limit === 40 ? "top100" : "name";
}

/** A message cut to [limit] code points, or null. */
function cut(message, limit) {
    if (!message || limit <= 0) return null;
    const points = Array.from(message);
    return points.length <= limit ? message : points.slice(0, limit).join("").trimEnd();
}

/** Everyone who opted in and has credit, with their total: only ever read inside SQL, never returned. */
const TOTALS = `
    SELECT s.account_id, s.public_id, s.name, s.message, SUM(o.cents) AS cents
    FROM supporters s JOIN support_orders o ON s.account_id = o.account_id
    GROUP BY s.account_id HAVING SUM(o.cents) > 0`;

/** An account's place on the board, or null when it isn't on it. Ties go by public id, as the board lists them. */
async function rankOf(db, accountId) {
    const row = await db.prepare(`
        WITH totals AS (${TOTALS})
        SELECT (SELECT COUNT(*) FROM totals t WHERE t.cents > me.cents OR (t.cents = me.cents AND t.public_id < me.public_id)) + 1 AS rank
        FROM totals me WHERE me.account_id = ?
    `).bind(accountId).all();
    return row.results[0]?.rank ?? null;
}

/** A per-hour counter under a salted hash. False once [limit] is used up this hour. */
async function allowed(env, kind, value, limit) {
    const salt = env.RATE_SALT;
    if (typeof salt !== "string" || salt.length < 16) throw new SupportError(503, "support_unavailable");
    const hour = Math.floor(Date.now() / 3_600_000);
    const key = kind + ":" + await hash(salt + ":" + value);
    const { results } = await env.DB.prepare(`
        INSERT INTO rate_limits (key, window, count) VALUES (?, ?, 1)
        ON CONFLICT (key, window) DO UPDATE SET count = count + 1 RETURNING count
    `).bind(key, hour).all();
    return results[0].count <= limit;
}

const PREVIEWS_PER_HOUR = 30;
const REPORTS_PER_HOUR = 5;
const PAGE_LIMIT = 50;

function isAccountKey(value) {
    return typeof value === "string" && ACCOUNT_KEY.test(value);
}

function validAdminToken(token) {
    return typeof token === "string" && token.length >= 32;
}

/**
 * Everyone who opted in, ranked by total, a page at a time: place, public id, name and the message as much as the place shows.
 * Never an amount.
 */
async function board(request, env) {
    const query = new URL(request.url).searchParams;
    const offset = Number(query.get("offset") ?? "0");
    const limit = Number(query.get("limit") ?? "20");
    if (!Number.isInteger(offset) || offset < 0 || offset > 1_000_000 || !Number.isInteger(limit) || limit < 1 || limit > PAGE_LIMIT) {
        throw badRequest();
    }
    const [page, count] = await env.DB.batch([
        env.DB.prepare(`
            WITH totals AS (${TOTALS})
            SELECT rank, id, name, message FROM (
                SELECT ROW_NUMBER() OVER (ORDER BY cents DESC, public_id ASC) AS rank, public_id AS id, name, message FROM totals
            ) ORDER BY rank LIMIT ? OFFSET ?
        `).bind(limit, offset),
        env.DB.prepare(`WITH totals AS (${TOTALS}) SELECT COUNT(*) AS count FROM totals`),
    ]);
    const entries = page.results.map(({ rank, id, name, message }) => ({ rank, id, name, message: cut(message, messageLimit(rank)) }));
    const total = count.results[0].count;
    const adminToken = env.ADMIN_TOKEN;
    const ready = validAdminToken(adminToken) && adminToken.trimEnd() === adminToken &&
        adminToken.length <= ADMIN_HEADER_LIMIT - ADMIN_SCHEME.length && await playPostingConfigured(env);
    const headers = ready ? { ...RESPONSE_HEADERS, "X-TitanKeys-Posting": "ready" } : RESPONSE_HEADERS;
    return json({ entries, total, nextOffset: offset + entries.length < total ? offset + entries.length : null }, 200, headers);
}

async function publish(request, env, verifier) {
    const body = await readJson(request);
    if (!hasKeys(body, ["accountKey", "optIn", "anonymous", "name", "message", "purchases"]) ||
        !isAccountKey(body.accountKey) || body.optIn !== true || typeof body.anonymous !== "boolean" ||
        !Array.isArray(body.purchases) || body.purchases.length < 1 || body.purchases.length > 10) throw badRequest();
    const enteredName = optionalText(body.name);
    const name = body.anonymous ? null : enteredName;
    const message = optionalText(body.message, MESSAGE_LIMIT);
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
        // A subscription answers with each paid month's order; a tip with its one.
        if (result.status === "fulfilled") receipts.push(...[result.value].flat());
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
    await db.batch(statements);
    // The message keeps only what the new place allows (a place beyond 100th keeps a top-100 message, unshown until it rises).
    const rank = await rankOf(db, accountId);
    const stored = cut(message, Math.max(messageLimit(rank), 40));
    if (stored !== message) await db.prepare("UPDATE supporters SET message = ? WHERE account_id = ?").bind(stored, accountId).run();
    return json({ rank, tier: messageTier(rank), messageLimit: messageLimit(rank) });
}

/**
 * "This puts you at #N": the place this account would reach with [addCents] more, before paying. Only the place comes back,
 * never anyone's total, and each account and each address has an hourly limit.
 */
async function preview(request, env) {
    const body = await readJson(request);
    if (!hasKeys(body, ["accountKey", "addCents"]) || !isAccountKey(body.accountKey) ||
        !Number.isSafeInteger(body.addCents) || body.addCents < 0 || body.addCents > 1_000_000) throw badRequest();
    const accountId = await hash(body.accountKey);
    const address = request.headers.get("CF-Connecting-IP") ?? "";
    if (!await allowed(env, "preview-account", accountId, PREVIEWS_PER_HOUR) ||
        !await allowed(env, "preview-address", address, PREVIEWS_PER_HOUR * 2)) throw new SupportError(429, "too_many_requests");
    const [mine, ahead] = await env.DB.batch([
        env.DB.prepare("SELECT COALESCE(SUM(cents), 0) AS cents FROM support_orders WHERE account_id = ?").bind(accountId),
        env.DB.prepare(`WITH totals AS (${TOTALS}) SELECT account_id, cents FROM totals WHERE account_id != ?`).bind(accountId),
    ]);
    // Counted here, in the Worker: the totals never leave it.
    const reached = mine.results[0].cents + body.addCents;
    const rank = ahead.results.filter(other => other.cents > reached).length + 1;
    return json({ rank, tier: messageTier(rank), messageLimit: messageLimit(rank) });
}

/** Anyone can report an entry, a few times an hour per address. It's kept for the owner; nothing is hidden by itself. */
async function report(request, env) {
    const body = await readJson(request);
    if (!hasKeys(body, ["id", "reason"]) || typeof body.id !== "string" || !PUBLIC_ID.test(body.id)) throw badRequest();
    const reason = optionalText(body.reason, MESSAGE_LIMIT);
    if (!await allowed(env, "report", request.headers.get("CF-Connecting-IP") ?? "", REPORTS_PER_HOUR)) {
        throw new SupportError(429, "too_many_requests");
    }
    const { results } = await env.DB.prepare("SELECT 1 FROM supporters WHERE public_id = ?").bind(body.id).all();
    if (results.length === 0) return json({ error: "not_found" }, 404);
    await env.DB.prepare("INSERT INTO support_reports (public_id, reason, created) VALUES (?, ?, ?)")
        .bind(body.id, reason, Date.now()).run();
    return done();
}

async function remove(request, db) {
    const body = await readJson(request);
    if (!hasKeys(body, ["accountKey"]) || !isAccountKey(body.accountKey)) throw badRequest();
    // The orders go with the profile, so rejoining starts fresh, as the privacy line says.
    const accountId = await hash(body.accountKey);
    await db.batch([
        db.prepare("DELETE FROM support_orders WHERE account_id = ?").bind(accountId),
        db.prepare("DELETE FROM supporters WHERE account_id = ?").bind(accountId),
    ]);
    return done();
}

/** The owner's bearer token, compared through hashes so its length and content can't be timed. */
async function isOwner(request, env) {
    const candidate = request.headers.get("Authorization");
    const secret = env.ADMIN_TOKEN;
    if (!validAdminToken(secret) || candidate === null ||
        !candidate.startsWith(ADMIN_SCHEME) || candidate.length > ADMIN_HEADER_LIMIT) return false;
    const [supplied, expected] = await Promise.all([hash(candidate.slice(ADMIN_SCHEME.length)), hash(secret)]);
    let difference = 0;
    for (let i = 0; i < expected.length; i++) difference |= supplied.charCodeAt(i) ^ expected.charCodeAt(i);
    return difference === 0;
}

/** The owner's list of reports, newest first, with the entry as it reads now. */
async function adminReports(request, env) {
    if (!await isOwner(request, env)) return json({ error: "not_found" }, 404);
    const { results } = await env.DB.prepare(`
        SELECT r.public_id AS id, s.name, s.message, r.reason, r.created
        FROM support_reports r LEFT JOIN supporters s ON s.public_id = r.public_id
        ORDER BY r.created DESC, r.id DESC LIMIT 500
    `).all();
    return json({ reports: results });
}

async function adminDelete(request, env) {
    if (!await isOwner(request, env)) return json({ error: "not_found" }, 404);
    const body = await readJson(request);
    if (!hasKeys(body, ["id"]) || typeof body.id !== "string" || !PUBLIC_ID.test(body.id)) throw badRequest();
    await env.DB.batch([
        env.DB.prepare("DELETE FROM support_orders WHERE account_id IN (SELECT account_id FROM supporters WHERE public_id = ?)").bind(body.id),
        env.DB.prepare("DELETE FROM supporters WHERE public_id = ?").bind(body.id),
    ]);
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
                if (request.method === "GET" && path === "/v1/leaderboard") return await board(request, env);
                if (request.method === "POST" && path === "/v1/rank-preview") return await preview(request, env);
                if (request.method === "POST" && path === "/v1/report") return await report(request, env);
                if (request.method === "GET" && path === "/v1/admin/reports") return await adminReports(request, env);
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
                // Rate counters older than the last hour are done with.
                await env.DB.prepare("DELETE FROM rate_limits WHERE window < ?").bind(Math.floor(Date.now() / 3_600_000) - 1).run();
                await reconcileRefunds(env, verifier);
            } catch {
                // Neither Google URLs nor response details belong in invocation errors.
                throw new Error("Support refund reconciliation failed");
            }
        },
    };
}

export default createWorker();
