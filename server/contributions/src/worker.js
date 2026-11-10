// The TitanKeys contributions server: a write-only Cloudflare Worker. It stores sealed batches of swipe examples in R2,
// and deletes all of a contributor's batches when the app asks. It can never read a batch: they are sealed on the phone
// with a public key whose private key is kept offline. It has no way to list or download batches; the owner reads the
// bucket with their own Cloudflare login. It logs nothing.
//
// Uploads need no account, so the Worker limits abuse: a per-client rate limit, a daily cap on uploads and a cap on the
// total stored. The client limit is keyed by a salted hash of the connecting address, used only for that moment's
// count: the address is never stored or logged. The training pipeline must still check every batch (see README.md).

const MAGIC = [0x54, 0x4b, 0x43, 0x31]; // "TKC1"
const HEADER_BYTES = 4 + 32 + 12;
const TAG_BYTES = 16;
/** The app sends at most 256 KB of examples before gzip, so a real batch is well under this. */
export const MAX_BATCH_BYTES = 320 * 1024;
/** Uploads a day from all phones together. A phone sends a few batches a day at most. */
export const DAILY_UPLOADS = 2000;
/** All batches stored, together. It keeps the bucket inside Cloudflare's free 10 GB. */
export const MAX_STORED_BYTES = 8 * 1024 * 1024 * 1024;
/** A Worker may make 1000 storage requests at most; each delete round makes two. */
const MAX_DELETE_ROUNDS = 400;
const FOLDER = /^[0-9a-f]{64}$/;
const CONTRIBUTOR = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

const answer = (status, body = null) => new Response(body === null ? null : JSON.stringify(body), {
  status,
  headers: body === null ? {} : { "Content-Type": "application/json" },
});

const hex = bytes => [...new Uint8Array(bytes)].map(byte => byte.toString(16).padStart(2, "0")).join("");
const sha256 = async text => hex(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text)));

/** The folder for a contributor: a hash of the ID, so that the stored names never show the ID itself. */
export const folder = contributor => sha256(contributor);

/**
 * Whether this client may make another request now. The key is a salted hash of the connecting address. It goes only
 * to Cloudflare's rate limiter, which counts in memory, and it is never stored or logged.
 */
async function clientAllowed(request, env, limiter) {
  if (!limiter) return true;
  const address = request.headers.get("CF-Connecting-IP") ?? "";
  const { success } = await limiter.limit({ key: await sha256(`${env.RATE_SALT ?? "titankeys"}:${address}`) });
  return success;
}

/** Asks the cap counter for room. It answers "ok", "day" (daily cap reached) or "full" (storage cap reached). */
async function reserve(env, path, bytes) {
  const counter = env.CAP.get(env.CAP.idFromName("global"));
  const response = await counter.fetch(`https://cap/${path}`, { method: "POST", body: JSON.stringify({ bytes }) });
  return (await response.json()).result;
}

async function store(request, env) {
  if (!await clientAllowed(request, env, env.UPLOAD_LIMIT)) return answer(429, { error: "too many requests" });
  const contributor = request.headers.get("X-Contributor") ?? "";
  if (!FOLDER.test(contributor)) return answer(400, { error: "bad contributor" });
  const length = Number(request.headers.get("Content-Length") ?? "0");
  if (length > MAX_BATCH_BYTES) return answer(413, { error: "too large" });
  const body = new Uint8Array(await request.arrayBuffer());
  if (body.length > MAX_BATCH_BYTES) return answer(413, { error: "too large" });
  if (body.length <= HEADER_BYTES + TAG_BYTES || MAGIC.some((byte, i) => body[i] !== byte)) return answer(400, { error: "not a sealed batch" });
  const room = await reserve(env, "take", body.length);
  if (room !== "ok") return answer(503, { error: room === "day" ? "daily limit reached" : "storage full" });
  const name = `batches/${contributor}/${new Date().toISOString().slice(0, 10)}-${crypto.randomUUID()}.tkc`;
  await env.BATCHES.put(name, body, { httpMetadata: { contentType: "application/octet-stream" } });
  return answer(204);
}

async function remove(request, env) {
  if (!await clientAllowed(request, env, env.DELETE_LIMIT)) return answer(429, { error: "too many requests" });
  let contributor = "";
  try {
    const text = await request.text();
    if (text.length > 1024) return answer(413, { error: "too large" });
    contributor = JSON.parse(text).contributor ?? "";
  } catch {
    return answer(400, { error: "bad request" });
  }
  if (typeof contributor !== "string" || !CONTRIBUTOR.test(contributor)) return answer(400, { error: "bad contributor" });
  const prefix = `batches/${await folder(contributor)}/`;
  // Each round deletes what it listed, so the next round lists from the start again: a cursor could skip names.
  let deleted = 0;
  let freed = 0;
  for (let round = 0; round < MAX_DELETE_ROUNDS; round++) {
    const listed = await env.BATCHES.list({ prefix, limit: 1000 });
    const names = listed.objects.map(object => object.key);
    if (names.length === 0) {
      if (freed) await reserve(env, "free", freed);
      return answer(200, { deleted });
    }
    await env.BATCHES.delete(names);
    deleted += names.length;
    freed += listed.objects.reduce((sum, object) => sum + (object.size ?? 0), 0);
  }
  if (freed) await reserve(env, "free", freed);
  // Very many batches: the app asks again, and the next request deletes the rest.
  return answer(503, { deleted });
}

export default {
  async fetch(request, env) {
    const { pathname } = new URL(request.url);
    if (request.method === "POST" && pathname === "/v1/batch") return store(request, env);
    if (request.method === "POST" && pathname === "/v1/delete") return remove(request, env);
    return answer(404, { error: "not found" });
  },
};

/**
 * One counter for the whole server, so the caps hold exactly: uploads today, and bytes stored. A Durable Object handles
 * one request at a time, so two uploads cannot both take the last place. It stores a date and counts, nothing about any
 * phone. Bytes are kept per month and only the last 24 months count, because R2 deletes files after 2 years by itself.
 * A delete takes its bytes off the current month, which can go below zero; the total never does.
 */
export class Cap {
  constructor(state) {
    this.storage = state.storage;
  }

  async fetch(request) {
    const { pathname } = new URL(request.url);
    const { bytes } = await request.json();
    const now = new Date();
    const today = now.toISOString().slice(0, 10);
    const month = today.slice(0, 7);
    const months = (await this.storage.get("months")) ?? {};
    const recent = Object.fromEntries(Object.entries(months).filter(([name]) => monthsBetween(name, month) < STORED_MONTHS));
    const stored = Math.max(0, Object.values(recent).reduce((sum, value) => sum + value, 0));
    if (pathname === "/free") {
      recent[month] = (recent[month] ?? 0) - bytes;
      await this.storage.put("months", recent);
      return Response.json({ result: "ok" });
    }
    const day = await this.storage.get("day");
    const uploads = day === today ? (await this.storage.get("uploads")) ?? 0 : 0;
    if (uploads >= DAILY_UPLOADS) return Response.json({ result: "day" });
    if (stored + bytes > MAX_STORED_BYTES) return Response.json({ result: "full" });
    recent[month] = (recent[month] ?? 0) + bytes;
    await this.storage.put({ day: today, uploads: uploads + 1, months: recent });
    return Response.json({ result: "ok" });
  }
}

/** Files live 24 months in R2 (the lifecycle rule in README.md), so bytes from older months are gone. */
const STORED_MONTHS = 24;
const monthsBetween = (from, to) => (Number(to.slice(0, 4)) - Number(from.slice(0, 4))) * 12 + Number(to.slice(5, 7)) - Number(from.slice(5, 7));
