// Checks the connected features' tools and texts: the announcement feed, the contributions server and the privacy
// claims. The app's own parts are checked in tests/connected.
import { Database } from "bun:sqlite";
import { describe, expect, test } from "bun:test";
import { appKey, type Envelope, type Feed, open, problems, sign } from "../announcements/feed";
import worker, {
  Cap, CLIENT_DELETES_PER_MINUTE, CLIENT_UPLOADS_PER_MINUTE, DAILY_UPLOADS, folder, KEEP_MILLIS, MAX_BATCH_BYTES, MAX_SHARD_BYTES,
  MAX_STORED_BYTES, Shard, SHARDS,
} from "../../server/contributions/src/worker.js";

const root = new URL("../../", import.meta.url);
const text = (path: string) => Bun.file(new URL(path, root)).text();

const entry = {
  id: "v2-1", title: "TitanKeys 2.1 is out", text: "Get it on GitHub.", action: { label: "Get it", url: "https://example.com/" },
  builds: ["github" as const],
};
const feed = (entries: unknown[]): Feed => ({ version: 1, issued: "2026-11-10T12:00:00Z", entries: entries as Feed["entries"] });

describe("announcement feed", () => {
  test("accepts a good feed", () => {
    expect(problems(feed([entry]))).toEqual([]);
  });

  test("refuses what the app refuses", () => {
    const bad = [
      { ...entry, id: "Bad Id" },
      { ...entry, title: "" },
      { ...entry, text: "x".repeat(601) },
      { ...entry, action: { label: "Go", url: "http://example.com/" } },
      { ...entry, action: { label: "Go", url: "https://example.com/", page: "/settings" } },
      { ...entry, action: { label: "Go", page: "/Settings Page" } },
      { ...entry, builds: [] },
      { ...entry, builds: ["store"] },
      { ...entry, minVersion: "two" },
      { ...entry, from: "2026-11-10" },
      { ...entry, colour: "red" },
    ];
    for (const item of bad) expect(problems(feed([item])).length).toBeGreaterThan(0);
    expect(problems(feed([entry, entry])).length).toBeGreaterThan(0);
  });

  test("signs and checks with the same key pair, and refuses changes", async () => {
    const pair = await crypto.subtle.generateKey({ name: "Ed25519" }, true, ["sign", "verify"]) as CryptoKeyPair;
    const pkcs8 = new Uint8Array(await crypto.subtle.exportKey("pkcs8", pair.privateKey));
    const spki = new Uint8Array(await crypto.subtle.exportKey("spki", pair.publicKey));
    const envelope = await sign(feed([entry]), pkcs8);
    expect((await open(envelope, spki)).entries[0].id).toBe("v2-1");
    expect(open({ ...envelope, feed: envelope.feed.replace("GitHub", "here") }, spki)).rejects.toThrow();
    expect(open(envelope, await appKey(root))).rejects.toThrow();
    expect(sign(feed([{ ...entry, id: "Bad Id" }]), pkcs8)).rejects.toThrow();
  });

  test("the published feed is signed with the app's key and matches its source", async () => {
    const envelope = JSON.parse(await text("site/feed.json")) as Envelope;
    const published = await open(envelope, await appKey(root));
    expect(problems(published)).toEqual([]);
    const source = JSON.parse(await text("announcements/entries.json")) as { entries: unknown[] };
    // Changing announcements/entries.json needs a new signature: bun tools/announcements/sign.ts <key>.
    expect(published.entries).toEqual(source.entries as Feed["entries"]);
  });
});

/** A Durable Object's storage: SQLite (Bun's own, the same engine) and the small key-value side, with its alarm. */
class Storage {
  db = new Database(":memory:");
  values = new Map<string, unknown>();
  alarmAt: number | null = null;
  sql = {
    exec: (query: string, ...bindings: unknown[]) => {
      const statement = this.db.query(query);
      const rows = /^\s*SELECT/i.test(query) ? statement.all(...(bindings as never[])) as Record<string, any>[] : (statement.run(...(bindings as never[])), []);
      return { toArray: () => rows, one: () => rows[0] };
    },
  };
  async get(key: string) { return this.values.get(key); }
  async put(key: string | Record<string, unknown>, value?: unknown) {
    if (typeof key === "string") this.values.set(key, value);
    else for (const [name, item] of Object.entries(key)) this.values.set(name, item);
  }
  async getAlarm() { return this.alarmAt; }
  async setAlarm(time: number) { this.alarmAt = time; }
}

/** A Durable Object namespace: one live object per name, as Cloudflare gives it. */
class Namespace<T extends { fetch(request: Request): Promise<Response> }> {
  objects = new Map<string, { object: T; storage: Storage }>();
  constructor(private make: (state: { storage: Storage }) => T) {}
  idFromName(name: string) { return name; }
  entry(name: string) {
    if (!this.objects.has(name)) {
      const storage = new Storage();
      this.objects.set(name, { object: this.make({ storage }), storage });
    }
    return this.objects.get(name)!;
  }
  get(name: string) { return { fetch: (url: string, init: RequestInit) => this.entry(name).object.fetch(new Request(url, init)) }; }
}

const TOKEN = "t".repeat(48);
const server = () => ({
  SHARDS: new Namespace(state => new Shard(state)),
  CAP: new Namespace(state => new Cap(state)),
  RATE_SALT: "test-salt",
  EXPORT_TOKEN: TOKEN,
});
type Server = ReturnType<typeof server>;
const cap = (env: Server) => env.CAP.entry("global");
const stored = (env: Server) => [...env.SHARDS.objects.values()].reduce((sum, { storage }) =>
  sum + (storage.sql.exec("SELECT COUNT(*) AS count FROM batches").one().count as number), 0);
const months = (env: Server) => cap(env).storage.values.get("months") as Record<string, number>;

const sealed = (size = 200) => { const body = new Uint8Array(size); body.set([0x54, 0x4b, 0x43, 0x31]); body[size - 1] = 7; return body; };
const ADDRESS = "203.0.113.7";
const URL_BASE = "https://titankeys-contributions.example.workers.dev";
const post = (path: string, body: BodyInit, headers: Record<string, string> = {}) =>
  new Request(`${URL_BASE}${path}`, { method: "POST", body, headers: { "CF-Connecting-IP": ADDRESS, ...headers } });
const get = (path: string, token = TOKEN) => new Request(`${URL_BASE}${path}`, { headers: { Authorization: `Bearer ${token}` } });

describe("contributions server", () => {
  const contributor = "2b7e1516-28ae-4d2a-a6d2-abf7158809cf";
  const upload = async (env: Server, address = ADDRESS, id = contributor) =>
    worker.fetch(post("/v1/batch", sealed(), { "X-Contributor": await folder(id), "CF-Connecting-IP": address }), env);

  test("stores sealed batches with only a hash of the contributor ID", async () => {
    const env = server();
    expect((await upload(env)).status).toBe(204);
    expect(stored(env)).toBe(1);
    const [{ storage }] = env.SHARDS.objects.values();
    const row = storage.sql.exec("SELECT folder, month, size FROM batches").one();
    expect(row.folder).toBe(await folder(contributor));
    expect(row.size).toBe(200);
    expect(JSON.stringify(row).includes(contributor)).toBe(false);
    expect(storage.alarmAt).not.toBeNull();
  });

  test("refuses what is not a sealed batch", async () => {
    const env = server();
    const hash = await folder(contributor);
    expect((await worker.fetch(post("/v1/batch", new TextEncoder().encode("hello, plain text"), { "X-Contributor": hash }), env)).status).toBe(400);
    expect((await worker.fetch(post("/v1/batch", sealed(), { "X-Contributor": contributor }), env)).status).toBe(400);
    expect((await worker.fetch(post("/v1/batch", sealed(MAX_BATCH_BYTES + 1), { "X-Contributor": hash }), env)).status).toBe(413);
    expect(stored(env)).toBe(0);
  });

  test("refuses everything until its salt is set", async () => {
    const env = { ...server(), RATE_SALT: undefined };
    expect((await upload(env as never)).status).toBe(503);
    expect(stored(env as never)).toBe(0);
  });

  test("logs nothing and keeps no internet addresses", async () => {
    const source = await text("server/contributions/src/worker.js");
    for (const word of ["console.", "X-Forwarded-For", "request.cf"]) expect(source.includes(word)).toBe(false);
    const env = server();
    await upload(env);
    // The address is a salted hash, held only in the counter's memory for this minute: in nothing stored.
    const kept = JSON.stringify([...env.SHARDS.objects.values()].map(({ storage }) =>
      [storage.sql.exec("SELECT folder, month FROM batches").toArray(), [...storage.values]]).concat([[...cap(env).storage.values]]));
    expect(kept.includes(ADDRESS)).toBe(false);
    const clients = [...(cap(env).object as unknown as { clients: Map<string, number> }).clients.keys()];
    expect(clients.length).toBe(1);
    expect(clients[0]).toMatch(/^upload:[0-9a-f]{64}$/);
    expect(clients[0].includes(ADDRESS)).toBe(false);
    expect(await text("server/contributions/wrangler.toml")).toMatch(/\[observability\]\s*enabled = false/);
  });

  test("the app's routes are write-only", async () => {
    const env = server();
    await upload(env);
    for (const request of [
      new Request(`${URL_BASE}/v1/batch`),
      new Request(`${URL_BASE}/`),
      new Request(`${URL_BASE}/v1/list`, { method: "POST" }),
    ]) expect((await worker.fetch(request, env)).status).toBe(404);
  });

  test("deletes every batch of one contributor, and only those, and frees them by upload month", async () => {
    const env = server();
    const other = "0b7e1516-28ae-4d2a-a6d2-abf7158809cf";
    for (let i = 0; i < 3; i++) await upload(env, `198.51.100.${i}`);
    await upload(env, ADDRESS, other);
    expect(stored(env)).toBe(4);
    const response = await worker.fetch(post("/v1/delete", JSON.stringify({ contributor })), env);
    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ deleted: 3 });
    expect(stored(env)).toBe(1);
    expect(Object.values(months(env)).reduce((a, b) => a + b, 0)).toBe(200);
    // The stored hash cannot delete anything: only the phone knows the ID.
    expect((await worker.fetch(post("/v1/delete", JSON.stringify({ contributor: await folder(other) })), env)).status).toBe(400);
    expect((await worker.fetch(post("/v1/delete", "not json"), env)).status).toBe(400);
  });

  test("limits each client, for uploads and deletes", async () => {
    const env = server();
    for (let i = 0; i < CLIENT_UPLOADS_PER_MINUTE; i++) expect((await upload(env)).status).toBe(204);
    expect((await upload(env)).status).toBe(429);
    expect((await upload(env, "198.51.100.9")).status).toBe(204);
    const remove = () => worker.fetch(post("/v1/delete", JSON.stringify({ contributor })), env);
    for (let i = 0; i < CLIENT_DELETES_PER_MINUTE; i++) expect((await remove()).status).toBe(200);
    expect((await remove()).status).toBe(429);
    // The next minute starts the counts again, and they were never stored.
    const counter = cap(env).object as unknown as { minute: number };
    counter.minute -= 1;
    expect((await upload(env)).status).toBe(204);
  });

  test("caps uploads a day and bytes stored in all", async () => {
    const env = server();
    await upload(env, "198.51.100.1");
    cap(env).storage.values.set("uploads", DAILY_UPLOADS - 1);
    expect((await upload(env, "198.51.100.2")).status).toBe(204);
    expect((await upload(env, "198.51.100.3")).status).toBe(503);
    cap(env).storage.values.set("day", "2000-01-01");
    expect((await upload(env, "198.51.100.4")).status).toBe(204);
    const month = new Date().toISOString().slice(0, 7);
    cap(env).storage.values.set("months", { [month]: MAX_STORED_BYTES - 100 });
    expect((await upload(env, "198.51.100.5")).status).toBe(503);
    // Bytes from more than 24 months ago were deleted with their batches, so they no longer count.
    cap(env).storage.values.set("months", { [`${Number(month.slice(0, 4)) - 3}${month.slice(4)}`]: MAX_STORED_BYTES });
    expect((await upload(env, "198.51.100.6")).status).toBe(204);
  });

  test("a delete frees each batch's bytes in the month it was uploaded", async () => {
    const env = server();
    const month = new Date().toISOString().slice(0, 7);
    const previous = new Date(Date.UTC(Number(month.slice(0, 4)), Number(month.slice(5, 7)) - 2, 15)).toISOString().slice(0, 7);
    await upload(env, "198.51.100.1");
    await upload(env, "198.51.100.2");
    // Move the first batch to last month, in the shard and in the counter, as if it had been uploaded then.
    const [{ storage }] = env.SHARDS.objects.values();
    storage.sql.exec("UPDATE batches SET month = ? WHERE id = 1", previous);
    cap(env).storage.values.set("months", { [previous]: 200, [month]: 200 });
    expect((await worker.fetch(post("/v1/delete", JSON.stringify({ contributor })), env)).status).toBe(200);
    expect(months(env)).toEqual({ [previous]: 0, [month]: 0 });
  });

  test("a storage object stops taking batches before the Free plan's 1 GB", async () => {
    const env = server();
    await upload(env);
    const [{ storage }] = env.SHARDS.objects.values();
    storage.values.set("bytes", MAX_SHARD_BYTES - 50);
    expect((await upload(env, "198.51.100.1")).status).toBe(503);
    // The refused batch gives its place in the counter back.
    expect(Object.values(months(env)).reduce((a, b) => a + b, 0)).toBe(200);
  });

  test("deletes batches 2 years after they arrive", async () => {
    const env = server();
    await upload(env);
    const [{ object, storage }] = env.SHARDS.objects.values();
    await (object as unknown as { alarm(now: number): Promise<void> }).alarm(Date.now() + KEEP_MILLIS - 60_000);
    expect(stored(env)).toBe(1);
    await (object as unknown as { alarm(now: number): Promise<void> }).alarm(Date.now() + KEEP_MILLIS + 60_000);
    expect(stored(env)).toBe(0);
    expect(storage.values.get("bytes")).toBe(0);
  });

  test("the owner exports sealed batches with the token, and nobody else can", async () => {
    const env = server();
    for (let i = 0; i < 3; i++) await upload(env, `198.51.100.${i}`);
    const hash = await folder(contributor);
    const number = parseInt(hash.slice(0, 2), 16) % SHARDS;
    for (const token of ["", "wrong", "t".repeat(47)])
      expect((await worker.fetch(get(`/v1/export?shard=${number}`, token), env)).status).toBe(404);
    expect((await worker.fetch(get(`/v1/export?shard=${number}`), { ...env, EXPORT_TOKEN: "short" })).status).toBe(404);
    const list = await (await worker.fetch(get(`/v1/export?shard=${number}&after=0`), env)).json() as { items: { id: number; month: string; size: number }[]; more: boolean };
    expect(list.items.length).toBe(3);
    expect(list.more).toBe(false);
    expect(Object.keys(list.items[0]).sort()).toEqual(["id", "month", "size"]);
    const batch = new Uint8Array(await (await worker.fetch(get(`/v1/export/batch?shard=${number}&id=${list.items[0].id}`), env)).arrayBuffer());
    expect(batch).toEqual(sealed());
    const confirm = (token = TOKEN) => new Request(`${URL_BASE}/v1/export/confirm`, { method: "POST", headers: { Authorization: `Bearer ${token}` },
      body: JSON.stringify({ shard: number, through: list.items[1].id }) });
    expect((await worker.fetch(confirm("wrong"), env)).status).toBe(404);
    expect(stored(env)).toBe(3);
    expect(await (await worker.fetch(confirm(), env)).json()).toEqual({ deleted: 2 });
    expect(stored(env)).toBe(1);
    expect(Object.values(months(env)).reduce((a, b) => a + b, 0)).toBe(200);
  });

  test("accepts the largest batch that the app makes", async () => {
    // Contributions.kt sends at most 256 KB of examples before gzip; sealing adds 64 bytes.
    const source = await text("android/kotlin/com/atlas/connected/Contributions.kt");
    expect(source).toContain("BATCH_BYTES = 256 * 1024");
    expect(MAX_BATCH_BYTES).toBeGreaterThanOrEqual(256 * 1024 + 64);
  });

  test("uses only the Free plan's parts: no R2, no paid bindings", async () => {
    const config = await text("server/contributions/wrangler.toml");
    for (const word of ["r2_buckets", "ratelimits", "kv_namespaces", "queues", "new_classes = "]) expect(config.includes(word)).toBe(false);
    expect(config).toContain('new_sqlite_classes = ["Cap", "Shard"]');
  });
});

describe("privacy texts (#53)", () => {
  const files = ["README.md", "CONTRIBUTING.md", "release-notes.txt", "app/settings/data/index.tsx", "app/settings/rawlog.tsx",
    "lib/connected-text.ts", "site/index.html", "site/privacy/index.html"];

  test("nothing claims that TitanKeys has no network access", async () => {
    for (const file of files) {
      const content = (await text(file)).toLowerCase();
      for (const claim of ["no network access", "without network access", "no internet", "nothing you type is sent anywhere, and no data",
        "all data stays on the device", "do not add network calls"])
        expect(`${file}: ${content.includes(claim) ? claim : ""}`).toBe(`${file}: `);
    }
  });

  test("the app, the README and the policy point to the same policy", async () => {
    const url = "https://jasonlok000.github.io/titankeys/privacy/";
    expect(await text("lib/connected.ts")).toContain(url);
    expect(await text("README.md")).toContain(url);
    const kotlin = await text("android/kotlin/com/atlas/connected/Connected.kt");
    expect(kotlin).toContain('SITE = "https://jasonlok000.github.io/titankeys"');
    expect(kotlin).toContain('PRIVACY_URL = "$SITE/privacy/"');
  });

  test("the policy and the disclosure name the same limits as the code", async () => {
    const policy = await text("site/privacy/index.html");
    const disclosure = await text("lib/connected-text.ts");
    const code = await text("android/kotlin/com/atlas/connected/Contributions.kt");
    expect(code).toContain("QUEUE_CAP_BYTES = 4L * 1024 * 1024");
    for (const words of [policy, disclosure]) {
      expect(words).toContain("4 MB");
      expect(words).toContain("2 years");
      expect(words).toContain("18 numbers");
    }
  });
});

describe("news on the phone", () => {
  test("turning News off takes down only news, never Record a bug's ongoing notification", async () => {
    // Bug hunt 2.1, #26: cancelAll() took Record a bug's notification and its Stop down too, while it went on recording.
    const news = await text("android/kotlin/com/atlas/connected/Announcements.kt");
    expect(news).not.toContain("cancelAll(");
    expect(news).toContain("if (!on) cancelNews(context)");
    expect(news).toContain("if (shown.notification.channelId == CHANNEL) manager.cancel(shown.tag, shown.id)");
    expect(news).toContain('private const val CHANNEL = "news"');
    expect(await text("android/kotlin/com/atlas/keyboard/BugRecording.kt")).toContain('private const val CHANNEL = "bug-recording"');
  });
});

describe("contributions on the phone", () => {
  test("a deletion waits for any batch on its way, and sent examples leave the queue by content", async () => {
    // Bug hunt 2.1, #28. tests/connected checks the queue's removal; this checks the lock between the two jobs.
    const code = await text("android/kotlin/com/atlas/connected/Contributions.kt");
    expect(code).toContain("repeat(BATCHES_PER_RUN) { synchronized(sending) {");
    expect(code).toContain("fun sendDeletion(context: Context): Boolean = synchronized(sending) {");
    expect(code).toContain("queue.removeSent(lines)");
    expect(code).not.toContain("removeOldest");
  });
});
