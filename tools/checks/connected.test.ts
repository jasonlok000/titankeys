// Checks the connected features' tools and texts: the announcement feed, the contributions server and the privacy
// claims. The app's own parts are checked in tests/connected.
import { describe, expect, test } from "bun:test";
import { appKey, type Envelope, type Feed, open, problems, sign } from "../announcements/feed";
import worker, { folder, MAX_BATCH_BYTES } from "../../server/contributions/src/worker.js";

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
  async put(key: string, body: Uint8Array) { this.objects.set(key, body); }
  async list({ prefix, cursor, limit }: { prefix: string; cursor?: string; limit: number }) {
    const keys = [...this.objects.keys()].filter(key => key.startsWith(prefix)).sort();
    const start = cursor ? Number(cursor) : 0;
    const page = keys.slice(start, start + limit);
    return { objects: page.map(key => ({ key })), truncated: start + limit < keys.length, cursor: String(start + limit) };
  }
  async delete(keys: string[]) { for (const key of keys) this.objects.delete(key); }
}

const sealed = (size = 200) => { const body = new Uint8Array(size); body.set([0x54, 0x4b, 0x43, 0x31]); return body; };
const post = (path: string, body: BodyInit, headers: Record<string, string> = {}) =>
  new Request(`https://titankeys-contributions.example.workers.dev${path}`, { method: "POST", body, headers });

describe("contributions server", () => {
  const contributor = "2b7e1516-28ae-4d2a-a6d2-abf7158809cf";

  test("stores sealed batches under the hash of the contributor ID", async () => {
    const bucket = new Bucket();
    const env = { BATCHES: bucket };
    const hash = await folder(contributor);
    const response = await worker.fetch(post("/v1/batch", sealed(), { "X-Contributor": hash }), env);
    expect(response.status).toBe(204);
    const [key] = [...bucket.objects.keys()];
    expect(key.startsWith(`batches/${hash}/`)).toBe(true);
    expect(key.includes(contributor)).toBe(false);
  });

  test("refuses what is not a sealed batch", async () => {
    const env = { BATCHES: new Bucket() };
    const hash = await folder(contributor);
    expect((await worker.fetch(post("/v1/batch", new TextEncoder().encode("hello, plain text"), { "X-Contributor": hash }), env)).status).toBe(400);
    expect((await worker.fetch(post("/v1/batch", sealed(), { "X-Contributor": contributor }), env)).status).toBe(400);
    expect((await worker.fetch(post("/v1/batch", sealed(MAX_BATCH_BYTES + 1), { "X-Contributor": hash }), env)).status).toBe(413);
    expect(env.BATCHES.objects.size).toBe(0);
  });

  test("is write-only: nothing can be read or listed", async () => {
    const env = { BATCHES: new Bucket() };
    for (const request of [
      new Request("https://x.workers.dev/v1/batch"),
      new Request("https://x.workers.dev/batches/"),
      new Request("https://x.workers.dev/v1/list", { method: "POST" }),
    ]) expect((await worker.fetch(request, env)).status).toBe(404);
  });

  test("deletes every batch of one contributor, and only those", async () => {
    const bucket = new Bucket();
    const env = { BATCHES: bucket };
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
