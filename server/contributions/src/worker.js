// The TitanKeys contributions server: a Cloudflare Worker on the Free plan, with no payment method, so it can never cost
// anything. Past a free limit, requests fail and the app keeps its queue. It stores sealed batches of swipe examples in
// SQLite-backed Durable Objects, and deletes all of a contributor's batches when the app asks. It can never read a
// batch: they are sealed on the phone with a public key whose private key is kept offline. It logs nothing.
//
// - POST /v1/batch and POST /v1/delete: for the app. Write-only: nothing can be read back through them.
// - POST /v1/report: a part of a bug report or idea from the app's form (#97), sealed the same way. Write-only too.
// - POST /v1/counts: one day of Sharing's daily totals (#96), sealed the same way, kept 1 year. Write-only too.
// - /v1/export...: for the owner only, with a long secret token. It returns the sealed batches and reports, still sealed.
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
/** A report is split into parts of at most MAX_BATCH_BYTES each, at most this many: a recording zips to a few MB. */
export const MAX_REPORT_PARTS = 24;
/**
 * Report parts a minute from one client, counted apart from batches: a whole report, and one more after a send that broke off,
 * since Try again sends every part again. Within the uploads limit alone, a report over 20 parts could never arrive.
 */
export const CLIENT_REPORT_PARTS_PER_MINUTE = 2 * MAX_REPORT_PARTS;
/** All reports, together, in their one storage object: under the Free plan's 1 GB per object. */
export const MAX_REPORT_BYTES = 800 * 1024 * 1024;
/** Report parts that one export page lists. */
const REPORT_PAGE = 200;
/** A day of Sharing's totals is a few hundred bytes sealed; this leaves room for many more counts. */
export const MAX_COUNTS_BYTES = 4 * 1024;
/** Days of totals a minute from one client: a phone sends at most 7 waiting days in a run. */
export const CLIENT_COUNTS_PER_MINUTE = 10;
/** Daily totals are deleted 1 year after they arrive (docs/analytics.md). */
export const COUNTS_KEEP_MILLIS = 365 * 24 * 60 * 60 * 1000;
/** All daily totals, together, in their one storage object. */
export const MAX_ALL_COUNTS_BYTES = 200 * 1024 * 1024;
/** Days of totals that one export page lists. */
const COUNTS_PAGE = 500;
const DAY = /^[0-9]{4}-[0-9]{2}-[0-9]{2}$/;
const COUNTS_ID = /^[0-9a-f]{32}$/;
/** The most counts IDs one deletion names: a year of 30-day IDs, twice over. */
const MAX_DELETED_COUNTS = 26;
const REPORT_ID = /^[0-9a-f]{32}$/;
const PART = /^([0-9]{1,2})\/([0-9]{1,2})$/;
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

const reports = env => env.REPORTS.get(env.REPORTS.idFromName("reports"));

/**
 * One part of a report from the app's form (#97). The headers say which: X-Report, a random ID the phone makes for the
 * report, and X-Part, "index/count". Parts take a place in the same caps and per-client limit as batches.
 */
async function report(request, env) {
  const id = request.headers.get("X-Report") ?? "";
  const part = PART.exec(request.headers.get("X-Part") ?? "");
  if (!REPORT_ID.test(id) || !part) return answer(400, { error: "bad report" });
  const index = Number(part[1]), count = Number(part[2]);
  if (count < 1 || count > MAX_REPORT_PARTS || index >= count) return answer(413, { error: "too large" });
  const length = Number(request.headers.get("Content-Length") ?? "0");
  if (length > MAX_BATCH_BYTES) return answer(413, { error: "too large" });
  const body = new Uint8Array(await request.arrayBuffer());
  if (body.length > MAX_BATCH_BYTES) return answer(413, { error: "too large" });
  if (body.length <= HEADER_BYTES + TAG_BYTES || MAGIC.some((byte, i) => body[i] !== byte)) return answer(400, { error: "not a sealed report" });
  const now = Date.now();
  const room = await call(counter(env), "take", { client: await clientKey(request, env), kind: "report", bytes: body.length, now });
  if (room.result === "busy") return answer(429, { error: "too many requests" });
  if (room.result !== "ok") return answer(503, { error: room.result === "day" ? "daily limit reached" : "storage full" });
  const stored = await call(reports(env), "put", { report: id, part: index, count, now }, body);
  if (Object.keys(stored.replaced ?? {}).length) await call(counter(env), "free", { months: stored.replaced });
  if (stored.result !== "ok") {
    await call(counter(env), "free", { months: { [monthOf(now)]: body.length } });
    return answer(503, { error: "storage full" });
  }
  return answer(204);
}

const countsStore = env => env.COUNTS.get(env.COUNTS.idFromName("counts"));

/**
 * One day of Sharing's totals (#96). X-Counts is a hash of the phone's counts ID, which changes every 30 days and is never the
 * contributor ID; X-Day is the phone's date for the totals, with no time. A day sent again replaces the first.
 */
async function counts(request, env) {
  const id = request.headers.get("X-Counts") ?? "";
  const day = request.headers.get("X-Day") ?? "";
  if (!FOLDER.test(id) || !DAY.test(day)) return answer(400, { error: "bad counts" });
  const length = Number(request.headers.get("Content-Length") ?? "0");
  if (length > MAX_COUNTS_BYTES) return answer(413, { error: "too large" });
  const body = new Uint8Array(await request.arrayBuffer());
  if (body.length > MAX_COUNTS_BYTES) return answer(413, { error: "too large" });
  if (body.length <= HEADER_BYTES + TAG_BYTES || MAGIC.some((byte, i) => body[i] !== byte)) return answer(400, { error: "not sealed counts" });
  const now = Date.now();
  const room = await call(counter(env), "take", { client: await clientKey(request, env), kind: "counts", bytes: body.length, now });
  if (room.result === "busy") return answer(429, { error: "too many requests" });
  if (room.result !== "ok") return answer(503, { error: room.result === "day" ? "daily limit reached" : "storage full" });
  const stored = await call(countsStore(env), "put", { folder: id, day, now }, body);
  if (Object.keys(stored.replaced ?? {}).length) await call(counter(env), "free", { months: stored.replaced });
  if (stored.result !== "ok") {
    await call(counter(env), "free", { months: { [monthOf(now)]: body.length } });
    return answer(503, { error: "storage full" });
  }
  return answer(204);
}

/**
 * "Delete what I've sent": {contributor} deletes that contributor's swipe batches, {counts: [ids]} the daily totals of each
 * counts ID, and both can come together. Only the phone knows the IDs; the server keeps their hashes.
 */
async function remove(request, env) {
  const allowed = await call(counter(env), "allow", { client: await clientKey(request, env), kind: "delete", now: Date.now() });
  if (allowed.result === "busy") return answer(429, { error: "too many requests" });
  let contributor, ids;
  try {
    const text = await request.text();
    if (text.length > 4096) return answer(413, { error: "too large" });
    ({ contributor, counts: ids } = JSON.parse(text));
  } catch {
    return answer(400, { error: "bad request" });
  }
  if (contributor === undefined && ids === undefined) return answer(400, { error: "bad contributor" });
  if (contributor !== undefined && (typeof contributor !== "string" || !CONTRIBUTOR.test(contributor))) return answer(400, { error: "bad contributor" });
  if (ids !== undefined && !(Array.isArray(ids) && ids.length <= MAX_DELETED_COUNTS && ids.every(id => typeof id === "string" && COUNTS_ID.test(id))))
    return answer(400, { error: "bad counts" });
  let deleted = 0;
  if (contributor !== undefined) {
    const hash = await folder(contributor);
    const result = await call(shard(env, shardOf(hash)), "delete-folder", { folder: hash });
    if (result.deleted) await call(counter(env), "free", { months: result.months });
    deleted += result.deleted;
  }
  if (ids?.length) {
    const folders = await Promise.all(ids.map(id => folder(id)));
    const result = await call(countsStore(env), "delete-folders", { folders });
    if (result.deleted) await call(counter(env), "free", { months: result.months });
    deleted += result.deleted;
  }
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
  // Reports (#97): GET /v1/export/reports?after=ID lists parts as {id, report, part, count, month, size}; GET
  // /v1/export/report?id=ID returns one sealed part; POST /v1/export/reports/confirm {through, reports} deletes the
  // parts of those reports up to that ID; without reports, every part up to it.
  if (request.method === "GET" && pathname === "/v1/export/reports")
    return answer(200, await call(reports(env), "list", { after: Number(url.searchParams.get("after") ?? "0") }));
  if (request.method === "GET" && pathname === "/v1/export/report") {
    const response = await reports(env).fetch("https://do/get", { method: "POST", body: JSON.stringify({ id: Number(url.searchParams.get("id")) }) });
    return new Response(response.body, { status: response.status, headers: { "Content-Type": "application/octet-stream" } });
  }
  if (request.method === "POST" && pathname === "/v1/export/reports/confirm") {
    const { through, reports: whole } = await request.json();
    if (!Number.isInteger(through)) return answer(400, { error: "bad request" });
    if (whole !== undefined && !(Array.isArray(whole) && whole.every(report => REPORT_ID.test(report)))) return answer(400, { error: "bad request" });
    const { deleted, months } = await call(reports(env), "delete-through", { through, reports: whole });
    if (deleted) await call(counter(env), "free", { months });
    return answer(200, { deleted });
  }
  // Sharing's daily totals (#96): GET /v1/export/counts?after=ID lists them as {id, day, month, size}; GET
  // /v1/export/counts/day?id=ID returns one, sealed; POST /v1/export/counts/confirm {through} deletes them up to that ID.
  if (request.method === "GET" && pathname === "/v1/export/counts")
    return answer(200, await call(countsStore(env), "list", { after: Number(url.searchParams.get("after") ?? "0") }));
  if (request.method === "GET" && pathname === "/v1/export/counts/day") {
    const response = await countsStore(env).fetch("https://do/get", { method: "POST", body: JSON.stringify({ id: Number(url.searchParams.get("id")) }) });
    return new Response(response.body, { status: response.status, headers: { "Content-Type": "application/octet-stream" } });
  }
  if (request.method === "POST" && pathname === "/v1/export/counts/confirm") {
    const { through } = await request.json();
    if (!Number.isInteger(through)) return answer(400, { error: "bad request" });
    const { deleted, months } = await call(countsStore(env), "delete-through", { through });
    if (deleted) await call(counter(env), "free", { months });
    return answer(200, { deleted });
  }
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
    if (request.method === "POST" && pathname === "/v1/report") return report(request, env);
    if (request.method === "POST" && pathname === "/v1/counts") return counts(request, env);
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
    return count <= (kind === "delete" ? CLIENT_DELETES_PER_MINUTE : kind === "report" ? CLIENT_REPORT_PARTS_PER_MINUTE
      : kind === "counts" ? CLIENT_COUNTS_PER_MINUTE : CLIENT_UPLOADS_PER_MINUTE);
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
    if (!this.allow(body.client, body.kind === "report" || body.kind === "counts" ? body.kind : "upload", now)) return Response.json({ result: "busy" });
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

/**
 * Bug reports and ideas from the app's form (#97), in one storage object: a SQLite table of sealed parts, each with its
 * report's random ID, its place and count, upload time, month and size. A part sent twice replaces the first. Like the
 * shards, it keeps its own byte total, and a daily alarm deletes parts older than 2 years.
 */
export class Reports {
  constructor(state) {
    this.storage = state.storage;
    this.sql = state.storage.sql;
    this.sql.exec(`CREATE TABLE IF NOT EXISTS parts (
      id INTEGER PRIMARY KEY AUTOINCREMENT, report TEXT NOT NULL, part INTEGER NOT NULL, count INTEGER NOT NULL,
      created INTEGER NOT NULL, month TEXT NOT NULL, size INTEGER NOT NULL, body BLOB NOT NULL, UNIQUE (report, part))`);
    this.sql.exec("CREATE INDEX IF NOT EXISTS parts_created ON parts (created)");
  }

  async removeWhere(where, ...values) {
    const rows = this.sql.exec(`SELECT month, SUM(size) AS bytes, COUNT(*) AS count FROM parts WHERE ${where} GROUP BY month`, ...values).toArray();
    this.sql.exec(`DELETE FROM parts WHERE ${where}`, ...values);
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
      // A part sent again, after a lost answer, takes the place of the first.
      // Its bytes go back to the Cap too (replaced), which counted them when the first came.
      const replaced = (await this.removeWhere("report = ? AND part = ?", meta.report, meta.part)).months;
      const used = (await this.storage.get("bytes")) ?? 0;
      if (used + body.length > MAX_REPORT_BYTES) return Response.json({ result: "full", replaced });
      try {
        this.sql.exec("INSERT INTO parts (report, part, count, created, month, size, body) VALUES (?, ?, ?, ?, ?, ?, ?)",
          meta.report, meta.part, meta.count, meta.now, monthOf(meta.now), body.length, body);
      } catch {
        return Response.json({ result: "full", replaced });
      }
      await this.storage.put("bytes", used + body.length);
      if (await this.storage.getAlarm() === null) await this.storage.setAlarm(meta.now + DAY_MILLIS);
      return Response.json({ result: "ok", replaced });
    }
    const body = await request.json();
    // A report that never arrives whole (its sender gave up) no longer holds back the ones after it: only the named reports go.
    if (pathname === "/delete-through") return Response.json(body.reports === undefined
      ? await this.removeWhere("id <= ?", body.through)
      : await this.removeWhere("id <= ? AND report IN (SELECT value FROM json_each(?))", body.through, JSON.stringify(body.reports)));
    if (pathname === "/list") {
      const items = this.sql.exec("SELECT id, report, part, count, month, size FROM parts WHERE id > ? ORDER BY id LIMIT ?",
        body.after, REPORT_PAGE + 1).toArray();
      return Response.json({ items: items.slice(0, REPORT_PAGE), more: items.length > REPORT_PAGE });
    }
    if (pathname === "/get") {
      const row = this.sql.exec("SELECT body FROM parts WHERE id = ?", body.id).toArray()[0];
      return row ? new Response(row.body) : new Response(null, { status: 404 });
    }
    return Response.json({ error: "not found" }, { status: 404 });
  }

  /** Once a day: deletes parts older than 2 years, and comes back while any are left. */
  async alarm(now = Date.now()) {
    await this.removeWhere("created < ?", now - KEEP_MILLIS);
    const left = this.sql.exec("SELECT COUNT(*) AS count FROM parts").one().count;
    if (left > 0) await this.storage.setAlarm(now + DAY_MILLIS);
  }
}

/**
 * Sharing's daily totals (#96), in one storage object: a SQLite table of sealed days, each with the hash of its counts ID, the
 * phone's date for it, upload time, month and size. A day sent again for the same ID replaces the first. Like the others, it
 * keeps its own byte total, and a daily alarm deletes days older than 1 year.
 */
export class Counts {
  constructor(state) {
    this.storage = state.storage;
    this.sql = state.storage.sql;
    this.sql.exec(`CREATE TABLE IF NOT EXISTS days (
      id INTEGER PRIMARY KEY AUTOINCREMENT, folder TEXT NOT NULL, day TEXT NOT NULL, created INTEGER NOT NULL,
      month TEXT NOT NULL, size INTEGER NOT NULL, body BLOB NOT NULL, UNIQUE (folder, day))`);
    this.sql.exec("CREATE INDEX IF NOT EXISTS days_created ON days (created)");
  }

  async removeWhere(where, ...values) {
    const rows = this.sql.exec(`SELECT month, SUM(size) AS bytes, COUNT(*) AS count FROM days WHERE ${where} GROUP BY month`, ...values).toArray();
    this.sql.exec(`DELETE FROM days WHERE ${where}`, ...values);
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
      const replaced = (await this.removeWhere("folder = ? AND day = ?", meta.folder, meta.day)).months;
      const used = (await this.storage.get("bytes")) ?? 0;
      if (used + body.length > MAX_ALL_COUNTS_BYTES) return Response.json({ result: "full", replaced });
      try {
        this.sql.exec("INSERT INTO days (folder, day, created, month, size, body) VALUES (?, ?, ?, ?, ?, ?)",
          meta.folder, meta.day, meta.now, monthOf(meta.now), body.length, body);
      } catch {
        return Response.json({ result: "full", replaced });
      }
      await this.storage.put("bytes", used + body.length);
      if (await this.storage.getAlarm() === null) await this.storage.setAlarm(meta.now + DAY_MILLIS);
      return Response.json({ result: "ok", replaced });
    }
    const body = await request.json();
    if (pathname === "/delete-folders")
      return Response.json(await this.removeWhere("folder IN (SELECT value FROM json_each(?))", JSON.stringify(body.folders)));
    if (pathname === "/delete-through") return Response.json(await this.removeWhere("id <= ?", body.through));
    if (pathname === "/list") {
      const items = this.sql.exec("SELECT id, day, month, size FROM days WHERE id > ? ORDER BY id LIMIT ?", body.after, COUNTS_PAGE + 1).toArray();
      return Response.json({ items: items.slice(0, COUNTS_PAGE), more: items.length > COUNTS_PAGE });
    }
    if (pathname === "/get") {
      const row = this.sql.exec("SELECT body FROM days WHERE id = ?", body.id).toArray()[0];
      return row ? new Response(row.body) : new Response(null, { status: 404 });
    }
    return Response.json({ error: "not found" }, { status: 404 });
  }

  /** Once a day: deletes days older than 1 year, and comes back while any are left. */
  async alarm(now = Date.now()) {
    await this.removeWhere("created < ?", now - COUNTS_KEEP_MILLIS);
    const left = this.sql.exec("SELECT COUNT(*) AS count FROM days").one().count;
    if (left > 0) await this.storage.setAlarm(now + DAY_MILLIS);
  }
}
