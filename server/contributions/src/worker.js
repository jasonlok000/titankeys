// The TitanKeys contributions server: a Cloudflare Worker on the Free plan, with no payment method, so it can never cost
// anything. Past a free limit, requests fail and the app keeps its queue. It stores sealed batches of swipe examples in
// SQLite-backed Durable Objects, and deletes all of a contributor's batches when the app asks. It can never read a
// batch: they are sealed on the phone with a public key whose private key is kept offline. It logs nothing.
//
// - POST /v1/batch and POST /v1/delete: for the app. Write-only: nothing can be read back through them.
// - /v1/export...: for the owner only, with a long secret token. It returns the sealed batches, still sealed.
//
// Uploads need no account, so the Worker limits abuse: a per-client limit, counted in memory only and keyed by a salted
// hash of the connecting address, which is never stored or logged; a daily cap on uploads; and a cap on what is stored.

const MAGIC = [0x54, 0x4b, 0x43, 0x31]; // "TKC1"
const HEADER_BYTES = 4 + 32 + 12;
const TAG_BYTES = 16;
/** The app sends at most 256 KB of examples before gzip, so a real batch is well under this. */
export const MAX_BATCH_BYTES = 320 * 1024;
/** Uploads a day from all phones together. A phone sends a few batches a day at most. */
export const DAILY_UPLOADS = 2000;
/** All batches stored, together: inside the Free plan's 5 GB of Durable Object storage. */
export const MAX_STORED_BYTES = 4 * 1024 * 1024 * 1024;
/** Batches are spread over this many storage objects by the first two digits of the folder hash. */
export const SHARDS = 64;
/** One storage object may hold 1 GB on the Free plan; it stops taking batches a little before. */
export const MAX_SHARD_BYTES = 900 * 1024 * 1024;
/** Requests a minute from one client. The app sends at most 16 batches in one run. */
export const CLIENT_UPLOADS_PER_MINUTE = 20;
export const CLIENT_DELETES_PER_MINUTE = 5;
/** Batches are deleted 2 years after they arrive, as the privacy policy says. */
export const KEEP_MILLIS = 730 * 24 * 60 * 60 * 1000;
/** Batches that one export page lists. */
const EXPORT_PAGE = 500;
const DAY_MILLIS = 24 * 60 * 60 * 1000;
const FOLDER = /^[0-9a-f]{64}$/;
const CONTRIBUTOR = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

const answer = (status, body = null) => new Response(body === null ? null : JSON.stringify(body), {
  status,
  headers: body === null ? {} : { "Content-Type": "application/json" },
});

const hex = bytes => [...new Uint8Array(bytes)].map(byte => byte.toString(16).padStart(2, "0")).join("");
const sha256 = async text => hex(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text)));
const monthOf = time => new Date(time).toISOString().slice(0, 7);

/** The folder for a contributor: a hash of the ID, so that the stored names never show the ID itself. */
export const folder = contributor => sha256(contributor);

const shardOf = folderHash => parseInt(folderHash.slice(0, 2), 16) % SHARDS;
const shard = (env, number) => env.SHARDS.get(env.SHARDS.idFromName(`shard-${number}`));
const counter = env => env.CAP.get(env.CAP.idFromName("global"));

/** Sends JSON (or bytes with JSON beside them) to a Durable Object and reads its JSON answer. */
async function call(stub, path, body, bytes = null) {
  const response = await stub.fetch(`https://do/${path}`, {
    method: "POST",
    body: bytes ?? JSON.stringify(body),
    headers: bytes ? { "X-Meta": JSON.stringify(body) } : {},
  });
  return response.json();
}

/** The per-client key: a salted hash of the connecting address. Only the Cap object's memory ever holds it. */
const clientKey = (request, env) => sha256(`${env.RATE_SALT}:${request.headers.get("CF-Connecting-IP") ?? ""}`);

async function store(request, env) {
  const contributor = request.headers.get("X-Contributor") ?? "";
  if (!FOLDER.test(contributor)) return answer(400, { error: "bad contributor" });
  const length = Number(request.headers.get("Content-Length") ?? "0");
  if (length > MAX_BATCH_BYTES) return answer(413, { error: "too large" });
  const body = new Uint8Array(await request.arrayBuffer());
  if (body.length > MAX_BATCH_BYTES) return answer(413, { error: "too large" });
  if (body.length <= HEADER_BYTES + TAG_BYTES || MAGIC.some((byte, i) => body[i] !== byte)) return answer(400, { error: "not a sealed batch" });
  const now = Date.now();
  const room = await call(counter(env), "take", { client: await clientKey(request, env), bytes: body.length, now });
  if (room.result === "busy") return answer(429, { error: "too many requests" });
  if (room.result !== "ok") return answer(503, { error: room.result === "day" ? "daily limit reached" : "storage full" });
  const stored = await call(shard(env, shardOf(contributor)), "put", { folder: contributor, now }, body);
  if (stored.result !== "ok") {
    await call(counter(env), "free", { months: { [monthOf(now)]: body.length } });
    return answer(503, { error: "storage full" });
  }
  return answer(204);
}

async function remove(request, env) {
  const allowed = await call(counter(env), "allow", { client: await clientKey(request, env), kind: "delete", now: Date.now() });
  if (allowed.result === "busy") return answer(429, { error: "too many requests" });
  let contributor = "";
  try {
    const text = await request.text();
    if (text.length > 1024) return answer(413, { error: "too large" });
    contributor = JSON.parse(text).contributor ?? "";
  } catch {
    return answer(400, { error: "bad request" });
  }
  if (typeof contributor !== "string" || !CONTRIBUTOR.test(contributor)) return answer(400, { error: "bad contributor" });
  const hash = await folder(contributor);
  const { deleted, months } = await call(shard(env, shardOf(hash)), "delete-folder", { folder: hash });
  if (deleted) await call(counter(env), "free", { months });
  return answer(200, { deleted });
}

/** Compares the owner's token in constant time, through hashes, so its length and content cannot be guessed by timing. */
async function owner(request, env) {
  const token = env.EXPORT_TOKEN ?? "";
  if (token.length < 32) return false;
  const given = (request.headers.get("Authorization") ?? "").replace(/^Bearer /, "");
  const [a, b] = await Promise.all([given, token].map(text => crypto.subtle.digest("SHA-256", new TextEncoder().encode(text))));
  const x = new Uint8Array(a), y = new Uint8Array(b);
  let difference = 0;
  for (let i = 0; i < x.length; i++) difference |= x[i] ^ y[i];
  return difference === 0;
}

/**
 * The owner's export, still sealed:
 * - GET /v1/export?shard=N&after=ID lists batches as {id, month, size}, oldest first, and says whether more follow;
 * - GET /v1/export/batch?shard=N&id=ID returns one sealed batch;
 * - POST /v1/export/confirm {shard, through} deletes that shard's batches up to that ID, once they are safely exported.
 * Without the right token it answers 404, as if it were not there.
 */
async function exporting(request, env, pathname) {
  if (!await owner(request, env)) return answer(404, { error: "not found" });
  const url = new URL(request.url);
  if (request.method === "POST" && pathname === "/v1/export/confirm") {
    const { shard: number, through } = await request.json();
    if (!Number.isInteger(number) || number < 0 || number >= SHARDS || !Number.isInteger(through)) return answer(400, { error: "bad request" });
    const { deleted, months } = await call(shard(env, number), "delete-through", { through });
    if (deleted) await call(counter(env), "free", { months });
    return answer(200, { deleted });
  }
  const number = Number(url.searchParams.get("shard"));
  if (!Number.isInteger(number) || number < 0 || number >= SHARDS) return answer(400, { error: "bad shard" });
  if (request.method === "GET" && pathname === "/v1/export")
    return answer(200, await call(shard(env, number), "list", { after: Number(url.searchParams.get("after") ?? "0") }));
  if (request.method === "GET" && pathname === "/v1/export/batch") {
    const response = await shard(env, number).fetch("https://do/get", { method: "POST", body: JSON.stringify({ id: Number(url.searchParams.get("id")) }) });
    return new Response(response.body, { status: response.status, headers: { "Content-Type": "application/octet-stream" } });
  }
  return answer(404, { error: "not found" });
}

export default {
  async fetch(request, env) {
    // Without its salt, the server takes nothing: see the deploy steps in README.md.
    if (!env.RATE_SALT) return answer(503, { error: "not configured" });
    const { pathname } = new URL(request.url);
    if (request.method === "POST" && pathname === "/v1/batch") return store(request, env);
    if (request.method === "POST" && pathname === "/v1/delete") return remove(request, env);
    if (pathname.startsWith("/v1/export")) return exporting(request, env, pathname);
    return answer(404, { error: "not found" });
  },
};

/**
 * One counter for the whole server, so the caps hold exactly: uploads today, and bytes stored. A Durable Object handles
 * one request at a time, so two uploads cannot both take the last place. It stores a date and counts, nothing about any
 * phone. Bytes are kept per month and only the last 24 months count, because each batch is deleted after 2 years.
 * A delete takes each batch's bytes off the month it was uploaded in.
 *
 * It also counts requests per client for the current minute, in memory only: those keys are never written to storage,
 * and they go when the minute ends or the object leaves memory.
 */
export class Cap {
  constructor(state) {
    this.storage = state.storage;
    this.clients = new Map();
    this.minute = -1;
  }

  /** Counts one request from a client; true while it stays within the limit for this minute. */
  allow(client, kind, now) {
    const minute = Math.floor(now / 60_000);
    if (minute !== this.minute) {
      this.clients.clear();
      this.minute = minute;
    }
    const key = `${kind}:${client}`;
    const count = (this.clients.get(key) ?? 0) + 1;
    this.clients.set(key, count);
    return count <= (kind === "delete" ? CLIENT_DELETES_PER_MINUTE : CLIENT_UPLOADS_PER_MINUTE);
  }

  async fetch(request) {
    const { pathname } = new URL(request.url);
    const body = await request.json();
    const now = body.now ?? Date.now();
    if (pathname === "/allow") return Response.json({ result: this.allow(body.client, body.kind, now) ? "ok" : "busy" });
    const today = new Date(now).toISOString().slice(0, 10);
    const month = today.slice(0, 7);
    const months = (await this.storage.get("months")) ?? {};
    const recent = Object.fromEntries(Object.entries(months).filter(([name]) => monthsBetween(name, month) < STORED_MONTHS));
    if (pathname === "/free") {
      // A month that already aged out is gone from the count, so its batches need nothing taken off.
      for (const [name, bytes] of Object.entries(body.months ?? {}))
        if (name in recent) recent[name] = Math.max(0, recent[name] - bytes);
      await this.storage.put("months", recent);
      return Response.json({ result: "ok" });
    }
    // "take": room for one upload.
    if (!this.allow(body.client, "upload", now)) return Response.json({ result: "busy" });
    const stored = Object.values(recent).reduce((sum, value) => sum + value, 0);
    const day = await this.storage.get("day");
    const uploads = day === today ? (await this.storage.get("uploads")) ?? 0 : 0;
    if (uploads >= DAILY_UPLOADS) return Response.json({ result: "day" });
    if (stored + body.bytes > MAX_STORED_BYTES) return Response.json({ result: "full" });
    recent[month] = (recent[month] ?? 0) + body.bytes;
    await this.storage.put({ day: today, uploads: uploads + 1, months: recent });
    return Response.json({ result: "ok" });
  }
}

/** Batches live 24 months (the Shard alarm deletes them then), so bytes from older months are gone. */
const STORED_MONTHS = 24;
const monthsBetween = (from, to) => (Number(to.slice(0, 4)) - Number(from.slice(0, 4))) * 12 + Number(to.slice(5, 7)) - Number(from.slice(5, 7));

/**
 * One of the storage objects: a SQLite table of sealed batches, each with its folder hash, upload time, month and size.
 * An alarm once a day deletes the batches older than 2 years. It keeps its own byte total, so an upload reads one value
 * instead of every row (the Free plan counts rows read).
 */
export class Shard {
  constructor(state) {
    this.storage = state.storage;
    this.sql = state.storage.sql;
    this.sql.exec(`CREATE TABLE IF NOT EXISTS batches (
      id INTEGER PRIMARY KEY AUTOINCREMENT, folder TEXT NOT NULL, created INTEGER NOT NULL,
      month TEXT NOT NULL, size INTEGER NOT NULL, body BLOB NOT NULL)`);
    this.sql.exec("CREATE INDEX IF NOT EXISTS batches_folder ON batches (folder)");
    this.sql.exec("CREATE INDEX IF NOT EXISTS batches_created ON batches (created)");
  }

  /** Deletes the batches that match and returns how many, with their bytes by upload month for the Cap counter. */
  async removeWhere(where, ...values) {
    const rows = this.sql.exec(`SELECT month, SUM(size) AS bytes, COUNT(*) AS count FROM batches WHERE ${where} GROUP BY month`, ...values).toArray();
    this.sql.exec(`DELETE FROM batches WHERE ${where}`, ...values);
    const freed = rows.reduce((sum, row) => sum + row.bytes, 0);
    if (freed) await this.storage.put("bytes", Math.max(0, ((await this.storage.get("bytes")) ?? 0) - freed));
    return {
      deleted: rows.reduce((sum, row) => sum + row.count, 0),
      months: Object.fromEntries(rows.map(row => [row.month, row.bytes])),
    };
  }

  async fetch(request) {
    const { pathname } = new URL(request.url);
    if (pathname === "/put") {
      const meta = JSON.parse(request.headers.get("X-Meta") ?? "{}");
      const body = new Uint8Array(await request.arrayBuffer());
      const used = (await this.storage.get("bytes")) ?? 0;
      if (used + body.length > MAX_SHARD_BYTES) return Response.json({ result: "full" });
      try {
        this.sql.exec("INSERT INTO batches (folder, created, month, size, body) VALUES (?, ?, ?, ?, ?)",
          meta.folder, meta.now, monthOf(meta.now), body.length, body);
      } catch {
        // The Free plan's storage is full (SQLITE_FULL). Nothing is billed; the app tries again later.
        return Response.json({ result: "full" });
      }
      await this.storage.put("bytes", used + body.length);
      if (await this.storage.getAlarm() === null) await this.storage.setAlarm(meta.now + DAY_MILLIS);
      return Response.json({ result: "ok" });
    }
    const body = await request.json();
    if (pathname === "/delete-folder") return Response.json(await this.removeWhere("folder = ?", body.folder));
    if (pathname === "/delete-through") return Response.json(await this.removeWhere("id <= ?", body.through));
    if (pathname === "/list") {
      const items = this.sql.exec("SELECT id, month, size FROM batches WHERE id > ? ORDER BY id LIMIT ?", body.after, EXPORT_PAGE + 1).toArray();
      return Response.json({ items: items.slice(0, EXPORT_PAGE), more: items.length > EXPORT_PAGE });
    }
    if (pathname === "/get") {
      const row = this.sql.exec("SELECT body FROM batches WHERE id = ?", body.id).toArray()[0];
      return row ? new Response(row.body) : new Response(null, { status: 404 });
    }
    return Response.json({ error: "not found" }, { status: 404 });
  }

  /** Once a day: deletes batches older than 2 years, and comes back while any are left. */
  async alarm(now = Date.now()) {
    await this.removeWhere("created < ?", now - KEEP_MILLIS);
    const left = this.sql.exec("SELECT COUNT(*) AS count FROM batches").one().count;
    if (left > 0) await this.storage.setAlarm(now + DAY_MILLIS);
  }
}
