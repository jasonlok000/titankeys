// Signs announcements/entries.json into site/feed.json, the file that the app downloads.
// Usage: bun tools/announcements/sign.ts <path to announcements-ed25519.pem>
// The private key stays off GitHub. The tool checks the entries with the app's own rules, signs them with today's time,
// and checks the result with the public key in the app, so a feed that the app would refuse is never written.
import { appKey, type Entry, type Feed, open, pem, sign } from "./feed";

const keyPath = process.argv[2];
if (!keyPath) {
  console.error("Usage: bun tools/announcements/sign.ts <path to announcements-ed25519.pem>");
  process.exit(2);
}
const root = new URL("../../", import.meta.url);
const source = await Bun.file(new URL("announcements/entries.json", root)).json() as { entries: Entry[] };
const feed: Feed = { version: 1, issued: new Date().toISOString().replace(/\.\d+Z$/, "Z"), entries: source.entries };
const envelope = await sign(feed, pem(await Bun.file(keyPath).text()));
await open(envelope, await appKey(root));
await Bun.write(new URL("site/feed.json", root), JSON.stringify(envelope, null, 1) + "\n");
console.log(`Signed ${feed.entries.length} entries into site/feed.json, issued ${feed.issued}. Publish with: bash tools/site/publish.sh`);
