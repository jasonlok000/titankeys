var __defProp = Object.defineProperty;
var __name = (target, value) => __defProp(target, "name", { value, configurable: true });

// src/worker.js
var MAGIC = [84, 75, 67, 49];
var HEADER_BYTES = 4 + 32 + 12;
var TAG_BYTES = 16;
var MAX_BATCH_BYTES = 320 * 1024;
var DAILY_UPLOADS = 2e3;
var MAX_STORED_BYTES = 4 * 1024 * 1024 * 1024;
var SHARDS = 64;
var MAX_SHARD_BYTES = 900 * 1024 * 1024;
var CLIENT_UPLOADS_PER_MINUTE = 20;
var CLIENT_DELETES_PER_MINUTE = 5;
var KEEP_MILLIS = 730 * 24 * 60 * 60 * 1e3;
var EXPORT_PAGE = 500;
var DAY_MILLIS = 24 * 60 * 60 * 1e3;
var FOLDER = /^[0-9a-f]{64}$/;
var CONTRIBUTOR = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
var answer = /* @__PURE__ */ __name((status, body = null) => new Response(body === null ? null : JSON.stringify(body), {
  status,
  headers: body === null ? {} : { "Content-Type": "application/json" }
}), "answer");
var hex = /* @__PURE__ */ __name((bytes) => [...new Uint8Array(bytes)].map((byte) => byte.toString(16).padStart(2, "0")).join(""), "hex");
var sha256 = /* @__PURE__ */ __name(async (text) => hex(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text))), "sha256");
var monthOf = /* @__PURE__ */ __name((time) => new Date(time).toISOString().slice(0, 7), "monthOf");
var folder = /* @__PURE__ */ __name((contributor) => sha256(contributor), "folder");
var shardOf = /* @__PURE__ */ __name((folderHash) => parseInt(folderHash.slice(0, 2), 16) % SHARDS, "shardOf");
var shard = /* @__PURE__ */ __name((env, number) => env.SHARDS.get(env.SHARDS.idFromName(`shard-${number}`)), "shard");
var counter = /* @__PURE__ */ __name((env) => env.CAP.get(env.CAP.idFromName("global")), "counter");
async function call(stub, path, body, bytes = null) {
  const response = await stub.fetch(`https://do/${path}`, {
    method: "POST",
    body: bytes ?? JSON.stringify(body),
    headers: bytes ? { "X-Meta": JSON.stringify(body) } : {}
  });
  return response.json();
}
__name(call, "call");
var clientKey = /* @__PURE__ */ __name((request, env) => sha256(`${env.RATE_SALT}:${request.headers.get("CF-Connecting-IP") ?? ""}`), "clientKey");
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
__name(store, "store");
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
__name(remove, "remove");
async function owner(request, env) {
  const token = env.EXPORT_TOKEN ?? "";
  if (token.length < 32) return false;
  const given = (request.headers.get("Authorization") ?? "").replace(/^Bearer /, "");
  const [a, b] = await Promise.all([given, token].map((text) => crypto.subtle.digest("SHA-256", new TextEncoder().encode(text))));
  const x = new Uint8Array(a), y = new Uint8Array(b);
  let difference = 0;
  for (let i = 0; i < x.length; i++) difference |= x[i] ^ y[i];
  return difference === 0;
}
__name(owner, "owner");
async function exporting(request, env, pathname) {
  if (!await owner(request, env)) return answer(404, { error: "not found" });
  const url = new URL(request.url);
  if (request.method === "POST" && pathname === "/v1/export/confirm") {
    const { shard: number2, through } = await request.json();
    if (!Number.isInteger(number2) || number2 < 0 || number2 >= SHARDS || !Number.isInteger(through)) return answer(400, { error: "bad request" });
    const { deleted, months } = await call(shard(env, number2), "delete-through", { through });
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
__name(exporting, "exporting");
var worker_default = {
  async fetch(request, env) {
    if (!env.RATE_SALT) return answer(503, { error: "not configured" });
    const { pathname } = new URL(request.url);
    if (request.method === "POST" && pathname === "/v1/batch") return store(request, env);
    if (request.method === "POST" && pathname === "/v1/delete") return remove(request, env);
    if (pathname.startsWith("/v1/export")) return exporting(request, env, pathname);
    return answer(404, { error: "not found" });
  }
};
var Cap = class {
  static {
    __name(this, "Cap");
  }
  constructor(state) {
    this.storage = state.storage;
    this.clients = /* @__PURE__ */ new Map();
    this.minute = -1;
  }
  /** Counts one request from a client; true while it stays within the limit for this minute. */
  allow(client, kind, now) {
    const minute = Math.floor(now / 6e4);
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
    const months = await this.storage.get("months") ?? {};
    const recent = Object.fromEntries(Object.entries(months).filter(([name]) => monthsBetween(name, month) < STORED_MONTHS));
    if (pathname === "/free") {
      for (const [name, bytes] of Object.entries(body.months ?? {}))
        if (name in recent) recent[name] = Math.max(0, recent[name] - bytes);
      await this.storage.put("months", recent);
      return Response.json({ result: "ok" });
    }
    if (!this.allow(body.client, "upload", now)) return Response.json({ result: "busy" });
    const stored = Object.values(recent).reduce((sum, value) => sum + value, 0);
    const day = await this.storage.get("day");
    const uploads = day === today ? await this.storage.get("uploads") ?? 0 : 0;
    if (uploads >= DAILY_UPLOADS) return Response.json({ result: "day" });
    if (stored + body.bytes > MAX_STORED_BYTES) return Response.json({ result: "full" });
    recent[month] = (recent[month] ?? 0) + body.bytes;
    await this.storage.put({ day: today, uploads: uploads + 1, months: recent });
    return Response.json({ result: "ok" });
  }
};
var STORED_MONTHS = 24;
var monthsBetween = /* @__PURE__ */ __name((from, to) => (Number(to.slice(0, 4)) - Number(from.slice(0, 4))) * 12 + Number(to.slice(5, 7)) - Number(from.slice(5, 7)), "monthsBetween");
var Shard = class {
  static {
    __name(this, "Shard");
  }
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
    if (freed) await this.storage.put("bytes", Math.max(0, (await this.storage.get("bytes") ?? 0) - freed));
    return {
      deleted: rows.reduce((sum, row) => sum + row.count, 0),
      months: Object.fromEntries(rows.map((row) => [row.month, row.bytes]))
    };
  }
  async fetch(request) {
    const { pathname } = new URL(request.url);
    if (pathname === "/put") {
      const meta = JSON.parse(request.headers.get("X-Meta") ?? "{}");
      const body2 = new Uint8Array(await request.arrayBuffer());
      const used = await this.storage.get("bytes") ?? 0;
      if (used + body2.length > MAX_SHARD_BYTES) return Response.json({ result: "full" });
      try {
        this.sql.exec(
          "INSERT INTO batches (folder, created, month, size, body) VALUES (?, ?, ?, ?, ?)",
          meta.folder,
          meta.now,
          monthOf(meta.now),
          body2.length,
          body2
        );
      } catch {
        return Response.json({ result: "full" });
      }
      await this.storage.put("bytes", used + body2.length);
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
};
export {
  CLIENT_DELETES_PER_MINUTE,
  CLIENT_UPLOADS_PER_MINUTE,
  Cap,
  DAILY_UPLOADS,
  KEEP_MILLIS,
  MAX_BATCH_BYTES,
  MAX_SHARD_BYTES,
  MAX_STORED_BYTES,
  SHARDS,
  Shard,
  worker_default as default,
  folder
};
//# sourceMappingURL=worker.js.map
