// Checks the connected features' tools and texts: the announcement feed, the contributions server and the privacy
// claims. The app's own parts are checked in tests/connected.
import { describe, expect, test } from "bun:test";
import { appKey, type Envelope, type Feed, open, problems, sign } from "../announcements/feed";
import worker, { Cap, DAILY_UPLOADS, folder, MAX_BATCH_BYTES, MAX_STORED_BYTES } from "../../server/contributions/src/worker.js";

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

class Bucket {
  objects = new Map<string, Uint8Array>();
  options: unknown[] = [];
  async put(key: string, body: Uint8Array, options: unknown) { this.objects.set(key, body); this.options.push(options); }
  async list({ prefix, cursor, limit }: { prefix: string; cursor?: string; limit: number }) {
    const keys = [...this.objects.keys()].filter(key => key.startsWith(prefix)).sort();
    const start = cursor ? Number(cursor) : 0;
    const page = keys.slice(start, start + limit);
    return { objects: page.map(key => ({ key, size: this.objects.get(key)!.length })), truncated: start + limit < keys.length, cursor: String(start + limit) };
  }
  async delete(keys: string[]) { for (const key of keys) this.objects.delete(key); }
}

/** Cloudflare's rate limiter: a count per key, in memory. It records the keys it saw, to check what they are. */
class Limiter {
  counts = new Map<string, number>();
  constructor(private allowed: number) {}
  async limit({ key }: { key: string }) {
    const count = (this.counts.get(key) ?? 0) + 1;
    this.counts.set(key, count);
    return { success: count <= this.allowed };
  }
}

/** A Durable Object namespace with one live Cap object, as Cloudflare gives it. */
class Counters {
  data = new Map<string, unknown>();
  cap = new Cap({ storage: {
    get: async (key: string) => this.data.get(key),
    put: async (key: string | Record<string, unknown>, value?: unknown) => {
      if (typeof key === "string") this.data.set(key, value);
      else for (const [name, item] of Object.entries(key)) this.data.set(name, item);
    },
  } });
  idFromName(name: string) { return name; }
  get() { return { fetch: (url: string, init: RequestInit) => this.cap.fetch(new Request(url, init)) }; }
}

const server = (uploads = 1000, deletes = 1000) =>
  ({ BATCHES: new Bucket(), UPLOAD_LIMIT: new Limiter(uploads), DELETE_LIMIT: new Limiter(deletes), CAP: new Counters(), RATE_SALT: "test" });

const sealed = (size = 200) => { const body = new Uint8Array(size); body.set([0x54, 0x4b, 0x43, 0x31]); return body; };
const ADDRESS = "203.0.113.7";
const post = (path: string, body: BodyInit, headers: Record<string, string> = {}) =>
  new Request(`https://titankeys-contributions.example.workers.dev${path}`, { method: "POST", body, headers: { "CF-Connecting-IP": ADDRESS, ...headers } });

describe("contributions server", () => {
  const contributor = "2b7e1516-28ae-4d2a-a6d2-abf7158809cf";

  test("stores sealed batches under the hash of the contributor ID", async () => {
    const env = server();
    const bucket = env.BATCHES;
    const hash = await folder(contributor);
    const response = await worker.fetch(post("/v1/batch", sealed(), { "X-Contributor": hash }), env);
    expect(response.status).toBe(204);
    const [key] = [...bucket.objects.keys()];
    expect(key.startsWith(`batches/${hash}/`)).toBe(true);
    expect(key.includes(contributor)).toBe(false);
  });

  test("refuses what is not a sealed batch", async () => {
    const env = server();
    const hash = await folder(contributor);
    expect((await worker.fetch(post("/v1/batch", new TextEncoder().encode("hello, plain text"), { "X-Contributor": hash }), env)).status).toBe(400);
    expect((await worker.fetch(post("/v1/batch", sealed(), { "X-Contributor": contributor }), env)).status).toBe(400);
    expect((await worker.fetch(post("/v1/batch", sealed(MAX_BATCH_BYTES + 1), { "X-Contributor": hash }), env)).status).toBe(413);
    expect(env.BATCHES.objects.size).toBe(0);
  });

  test("logs nothing and keeps no internet addresses", async () => {
    const source = await text("server/contributions/src/worker.js");
    for (const word of ["console.", "X-Forwarded-For", "request.cf"]) expect(source.includes(word)).toBe(false);
    // The address is only a rate-limit key, salted and hashed. It is in nothing stored.
    const env = server();
    await worker.fetch(post("/v1/batch", sealed(), { "X-Contributor": await folder(contributor) }), env);
    const kept = JSON.stringify([...env.BATCHES.objects.keys(), env.BATCHES.options, [...env.CAP.data], [...env.UPLOAD_LIMIT.counts.keys()]]);
    expect(kept.includes(ADDRESS)).toBe(false);
    expect([...env.UPLOAD_LIMIT.counts.keys()][0]).toMatch(/^[0-9a-f]{64}$/);
    expect(await text("server/contributions/wrangler.toml")).toMatch(/\[observability\]\s*enabled = false/);
  });

  test("is write-only: nothing can be read or listed", async () => {
    const env = server();
    for (const request of [
      new Request("https://x.workers.dev/v1/batch"),
      new Request("https://x.workers.dev/batches/"),
      new Request("https://x.workers.dev/v1/list", { method: "POST" }),
    ]) expect((await worker.fetch(request, env)).status).toBe(404);
  });

  test("deletes every batch of one contributor, and only those", async () => {
    const env = server();
    const bucket = env.BATCHES;
    const mine = await folder(contributor);
    const other = await folder("0b7e1516-28ae-4d2a-a6d2-abf7158809cf");
    for (let i = 0; i < 2500; i++) bucket.objects.set(`batches/${mine}/${i}.tkc`, sealed());
    bucket.objects.set(`batches/${other}/0.tkc`, sealed());
    const response = await worker.fetch(post("/v1/delete", JSON.stringify({ contributor })), env);
    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ deleted: 2500 });
    expect([...bucket.objects.keys()]).toEqual([`batches/${other}/0.tkc`]);
    // The hash alone, as stored, cannot delete anything: only the phone knows the ID.
    expect((await worker.fetch(post("/v1/delete", JSON.stringify({ contributor: other })), env)).status).toBe(400);
    expect((await worker.fetch(post("/v1/delete", "not json"), env)).status).toBe(400);
  });

  test("limits each client, for uploads and deletes", async () => {
    const env = server(3, 2);
    const hash = await folder(contributor);
    const upload = (address = ADDRESS) => worker.fetch(post("/v1/batch", sealed(), { "X-Contributor": hash, "CF-Connecting-IP": address }), env);
    for (let i = 0; i < 3; i++) expect((await upload()).status).toBe(204);
    expect((await upload()).status).toBe(429);
    expect((await upload("198.51.100.9")).status).toBe(204);
    expect(env.BATCHES.objects.size).toBe(4);
    const remove = () => worker.fetch(post("/v1/delete", JSON.stringify({ contributor })), env);
    expect((await remove()).status).toBe(200);
    expect((await remove()).status).toBe(200);
    expect((await remove()).status).toBe(429);
  });

  test("caps uploads a day and storage in all, and frees storage on delete", async () => {
    const env = server();
    const hash = await folder(contributor);
    env.CAP.data.set("day", new Date().toISOString().slice(0, 10));
    env.CAP.data.set("uploads", DAILY_UPLOADS - 1);
    const upload = () => worker.fetch(post("/v1/batch", sealed(), { "X-Contributor": hash }), env);
    expect((await upload()).status).toBe(204);
    expect((await upload()).status).toBe(503);
    // A new day starts the count again.
    env.CAP.data.set("day", "2000-01-01");
    expect((await upload()).status).toBe(204);
    expect(env.CAP.data.get("uploads")).toBe(1);
    const month = new Date().toISOString().slice(0, 7);
    const year = Number(month.slice(0, 4));
    env.CAP.data.set("months", { [month]: MAX_STORED_BYTES - 100 });
    expect((await upload()).status).toBe(503);
    // Bytes from more than 24 months ago expired in R2, so they no longer count.
    env.CAP.data.set("months", { [`${year - 3}${month.slice(4)}`]: MAX_STORED_BYTES });
    expect((await upload()).status).toBe(204);
    const stored = () => Object.values(env.CAP.data.get("months") as Record<string, number>).reduce((a, b) => a + b, 0);
    expect(stored()).toBe(200);
    // Three batches of 200 bytes are stored now; deleting them frees all 600.
    env.CAP.data.set("months", { [month]: 600 });
    expect((await worker.fetch(post("/v1/delete", JSON.stringify({ contributor })), env)).status).toBe(200);
    expect(stored()).toBe(0);
    expect(env.BATCHES.objects.size).toBe(0);
  });

  test("accepts the largest batch that the app makes", async () => {
    // Contributions.kt sends at most 256 KB of examples before gzip; sealing adds 64 bytes.
    const source = await text("android/kotlin/com/atlas/connected/Contributions.kt");
    expect(source).toContain("BATCH_BYTES = 256 * 1024");
    expect(MAX_BATCH_BYTES).toBeGreaterThanOrEqual(256 * 1024 + 64);
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
