// The announcement feed's rules, the same as the app's (android/kotlin/com/atlas/connected/Feed.kt). The signing tool
// refuses a feed that breaks them, so that the app never throws away a feed that was published.

export type Action = { label: string; url?: string; page?: string };
export type Entry = {
  id: string;
  title: string;
  text: string;
  action?: Action;
  from?: string;
  until?: string;
  minVersion?: string;
  maxVersion?: string;
  builds: ("github" | "play" | "debug")[];
  notify?: boolean;
};
export type Feed = { version: 1; issued: string; entries: Entry[] };
export type Envelope = { format: typeof FORMAT; feed: string; signature: string };

export const FORMAT = "titankeys-feed-1";
export const MAX_ENVELOPE_BYTES = 256 * 1024;
const MAX_ENTRIES = 50;
const ID = /^[a-z0-9][a-z0-9._-]{0,63}$/;
const PAGE = /^\/[a-z0-9/_-]{0,80}$/;
const VERSION = /^[0-9]{1,4}(\.[0-9]{1,4}){0,3}$/;
const BUILDS = new Set(["github", "play", "debug"]);
const KEYS = new Set(["id", "title", "text", "action", "from", "until", "minVersion", "maxVersion", "builds", "notify"]);

/** Returns the problems with a feed, or an empty list when the app will accept it. */
export function problems(feed: Feed): string[] {
  const found: string[] = [];
  const check = (ok: boolean, problem: string) => { if (!ok) found.push(problem); };
  check(feed.version === 1, "version must be 1");
  check(!Number.isNaN(Date.parse(feed.issued)) && /Z$/.test(feed.issued), "issued must be a UTC time such as 2026-11-10T12:00:00Z");
  check(Array.isArray(feed.entries) && feed.entries.length <= MAX_ENTRIES, `entries must be a list of at most ${MAX_ENTRIES}`);
  const ids = new Set<string>();
  for (const entry of feed.entries ?? []) {
    const name = typeof entry.id === "string" ? entry.id : "an entry";
    for (const key of Object.keys(entry)) check(KEYS.has(key), `${name}: unknown field ${key}`);
    check(typeof entry.id === "string" && ID.test(entry.id), `${name}: id must be lower case letters, digits, dots, dashes`);
    check(!ids.has(entry.id), `${name}: the id is used twice`);
    ids.add(entry.id);
    const text = (value: unknown, max: number, what: string) =>
      check(typeof value === "string" && value.trim().length > 0 && value.length <= max, `${name}: ${what} must have 1 to ${max} characters`);
    text(entry.title, 80, "title");
    text(entry.text, 600, "text");
    if (entry.action !== undefined) {
      text(entry.action.label, 24, "action label");
      check((entry.action.url === undefined) !== (entry.action.page === undefined), `${name}: the action needs a url or a page, not both`);
      if (entry.action.url !== undefined)
        check(/^https:\/\/\S+$/.test(entry.action.url) && entry.action.url.length <= 500, `${name}: the action must be an https link`);
      if (entry.action.page !== undefined) check(PAGE.test(entry.action.page), `${name}: bad page`);
    }
    for (const time of ["from", "until"] as const)
      if (entry[time] !== undefined) check(/Z$/.test(entry[time]!) && !Number.isNaN(Date.parse(entry[time]!)), `${name}: ${time} must be a UTC time`);
    for (const version of ["minVersion", "maxVersion"] as const)
      if (entry[version] !== undefined) check(VERSION.test(entry[version]!), `${name}: ${version} must be like 2.1.0`);
    check(Array.isArray(entry.builds) && entry.builds.length > 0 && entry.builds.every(build => BUILDS.has(build)),
      `${name}: builds must be some of github, play, debug`);
    if (entry.notify !== undefined) check(typeof entry.notify === "boolean", `${name}: notify must be true or false`);
  }
  return found;
}

const base64 = (bytes: ArrayBuffer | Uint8Array) => Buffer.from(bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes)).toString("base64");

/** Reads a PEM file's DER bytes. */
export function pem(text: string): Uint8Array {
  return new Uint8Array(Buffer.from(text.replace(/-----[^-]+-----/g, "").replace(/\s+/g, ""), "base64"));
}

/** Signs a feed with an Ed25519 private key in PKCS #8 form, and returns the envelope that the app reads. */
export async function sign(feed: Feed, pkcs8: Uint8Array): Promise<Envelope> {
  const found = problems(feed);
  if (found.length) throw new Error(`The feed is not valid:\n- ${found.join("\n- ")}`);
  const key = await crypto.subtle.importKey("pkcs8", pkcs8, { name: "Ed25519" }, false, ["sign"]);
  const text = JSON.stringify(feed, null, 1);
  const signature = await crypto.subtle.sign("Ed25519", key, new TextEncoder().encode(text));
  return { format: FORMAT, feed: text, signature: base64(signature) };
}

/** Checks an envelope with an Ed25519 public key in X.509 (SPKI) form, as the app does, and returns the feed inside. */
export async function open(envelope: Envelope, spki: Uint8Array): Promise<Feed> {
  if (envelope.format !== FORMAT) throw new Error("Unknown feed format");
  const key = await crypto.subtle.importKey("spki", spki, { name: "Ed25519" }, false, ["verify"]);
  const ok = await crypto.subtle.verify("Ed25519", key, Buffer.from(envelope.signature, "base64"), new TextEncoder().encode(envelope.feed));
  if (!ok) throw new Error("The feed's signature is not valid");
  return JSON.parse(envelope.feed) as Feed;
}

/** The public key that the app holds, read from the app's source, so the tools always check against the real one. */
export async function appKey(root: URL): Promise<Uint8Array> {
  const source = await Bun.file(new URL("android/kotlin/com/atlas/connected/Connected.kt", root)).text();
  const match = source.match(/FEED_KEY = "([A-Za-z0-9+/=]+)"/);
  if (!match) throw new Error("FEED_KEY is missing from Connected.kt");
  return new Uint8Array(Buffer.from(match[1], "base64"));
}
