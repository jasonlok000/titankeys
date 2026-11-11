// Downloads every sealed contribution batch from the server, for training. The batches stay sealed: open them with
// open.ts on the offline computer that holds the private key.
// Usage: bun tools/contributions/export.ts <worker address> <file holding EXPORT_TOKEN> <folder> [--confirm]
// With --confirm, it then asks the server to delete what it downloaded, shard by shard, only after every file of that
// shard is written. The token is read from a file, so it stays out of the shell history.
import { mkdir } from "node:fs/promises";

const SHARDS = 64;
const [address, tokenFile, folder, flag] = process.argv.slice(2);
if (!address || !tokenFile || !folder) {
  console.error("Usage: bun tools/contributions/export.ts <worker address> <file holding EXPORT_TOKEN> <folder> [--confirm]");
  process.exit(2);
}
const base = address.replace(/\/$/, "");
const headers = { Authorization: `Bearer ${(await Bun.file(tokenFile).text()).trim()}` };
await mkdir(folder, { recursive: true });

let total = 0;
for (let shard = 0; shard < SHARDS; shard++) {
  let after = 0;
  let last = 0;
  for (;;) {
    const response = await fetch(`${base}/v1/export?shard=${shard}&after=${after}`, { headers });
    if (!response.ok) throw new Error(`Listing shard ${shard} failed: ${response.status}. Check the address and the token.`);
    const page = await response.json() as { items: { id: number; month: string; size: number }[]; more: boolean };
    for (const item of page.items) {
      const batch = await fetch(`${base}/v1/export/batch?shard=${shard}&id=${item.id}`, { headers });
      const bytes = new Uint8Array(await batch.arrayBuffer());
      if (!batch.ok || bytes.length !== item.size) throw new Error(`Batch ${shard}/${item.id} did not download whole`);
      await Bun.write(`${folder}/${item.month}-${String(shard).padStart(2, "0")}-${item.id}.tkc`, bytes);
      last = item.id;
      total++;
    }
    if (!page.more || page.items.length === 0) break;
    after = page.items[page.items.length - 1].id;
  }
  if (flag === "--confirm" && last > 0) {
    const confirmed = await fetch(`${base}/v1/export/confirm`, {
      method: "POST", headers: { ...headers, "Content-Type": "application/json" }, body: JSON.stringify({ shard, through: last }),
    });
    if (!confirmed.ok) throw new Error(`Deleting shard ${shard} on the server failed: ${confirmed.status}`);
  }
}
console.log(`Downloaded ${total} sealed batches to ${folder}${flag === "--confirm" ? ", and deleted them on the server" : ""}.`);
