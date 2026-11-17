// Sharing's daily totals (#96, docs/analytics.md): download them from the server, still sealed, then open them with the
// owner's private key and add them up, so only the owner ever sees them.
//
// Usage:
//   bun tools/contributions/counts.ts download <worker address> <file holding EXPORT_TOKEN> <folder> [--confirm]
//   bun tools/contributions/counts.ts open <contributions-x25519.pem> <folder>
//
// download writes each sealed day as <id>.tkd. With --confirm, it then asks the server to delete what it downloaded. open
// opens every day into days.jsonl, one payload a line, and writes totals.json: for each date, how many phones sent a day and the
// sum of each count, with the guide's and the settings' lists counted by phone.
import { mkdir, readdir } from "node:fs/promises";
import { openBytes, pem } from "./open";

type Day = { id: number; day: string; month: string; size: number };
type Payload = {
  v: number; envelope: number; id: string; day: string; app: string; build: string; android: string; phone: string; models: string;
  counts: Record<string, number>;
  guide?: { furthest: string; finished: boolean; skipped: string[] };
  settings?: { set: string[] };
};

export async function download(address: string, tokenFile: string, folder: string, confirm: boolean) {
  const base = address.replace(/\/$/, "");
  const headers = { Authorization: `Bearer ${(await Bun.file(tokenFile).text()).trim()}` };
  await mkdir(folder, { recursive: true });
  let through = 0;
  let count = 0;
  for (let after = 0; ;) {
    const response = await fetch(`${base}/v1/export/counts?after=${after}`, { headers });
    if (!response.ok) throw new Error(`Listing daily totals failed: ${response.status}. Check the address and the token.`);
    const page = await response.json() as { items: Day[]; more: boolean };
    for (const item of page.items) {
      const day = await fetch(`${base}/v1/export/counts/day?id=${item.id}`, { headers });
      const bytes = new Uint8Array(await day.arrayBuffer());
      if (!day.ok || bytes.length !== item.size) throw new Error(`Day ${item.id} did not download whole`);
      await Bun.write(`${folder}/${item.id}.tkd`, bytes);
      through = Math.max(through, item.id);
      count++;
    }
    if (!page.more || page.items.length === 0) break;
    after = page.items[page.items.length - 1].id;
  }
  if (confirm && through > 0) {
    const confirmed = await fetch(`${base}/v1/export/counts/confirm`, {
      method: "POST", headers: { ...headers, "Content-Type": "application/json" }, body: JSON.stringify({ through }),
    });
    if (!confirmed.ok) throw new Error(`Deleting daily totals on the server failed: ${confirmed.status}`);
  }
  console.log(`Downloaded ${count} days of totals to ${folder}` + (confirm && through > 0 ? ", and deleted them on the server." : "."));
}

/** The totals by date: phones that sent a day, each count summed, and the lists counted by phone. */
export function totals(payloads: Payload[]) {
  const byDay: Record<string, { phones: number; counts: Record<string, number>; furthest: Record<string, number>;
    finished: number; skipped: Record<string, number>; settings: Record<string, number> }> = {};
  for (const payload of payloads) {
    const day = byDay[payload.day] ??= { phones: 0, counts: {}, furthest: {}, finished: 0, skipped: {}, settings: {} };
    day.phones++;
    for (const [key, value] of Object.entries(payload.counts)) day.counts[key] = (day.counts[key] ?? 0) + value;
    if (payload.guide) {
      day.furthest[payload.guide.furthest] = (day.furthest[payload.guide.furthest] ?? 0) + 1;
      if (payload.guide.finished) day.finished++;
      for (const step of payload.guide.skipped) day.skipped[step] = (day.skipped[step] ?? 0) + 1;
    }
    for (const key of payload.settings?.set ?? []) day.settings[key] = (day.settings[key] ?? 0) + 1;
  }
  return byDay;
}

export async function openAll(keyPath: string, folder: string) {
  const key = pem(await Bun.file(keyPath).text());
  const names = (await readdir(folder)).filter(name => /^\d+\.tkd$/.test(name));
  const payloads: Payload[] = [];
  for (const name of names) {
    const opened = await openBytes(new Uint8Array(await Bun.file(`${folder}/${name}`).arrayBuffer()), key);
    payloads.push(JSON.parse(new TextDecoder().decode(opened)) as Payload);
  }
  await Bun.write(`${folder}/days.jsonl`, payloads.map(payload => JSON.stringify(payload)).join("\n") + "\n");
  await Bun.write(`${folder}/totals.json`, JSON.stringify(totals(payloads), null, 2) + "\n");
  console.log(`Opened ${payloads.length} days into ${folder}/days.jsonl and ${folder}/totals.json`);
}

if (import.meta.main) {
  const [command, ...rest] = process.argv.slice(2);
  if (command === "download" && rest.length >= 3) await download(rest[0], rest[1], rest[2], rest[3] === "--confirm");
  else if (command === "open" && rest.length === 2) await openAll(rest[0], rest[1]);
  else {
    console.error("Usage: bun tools/contributions/counts.ts download <worker address> <token file> <folder> [--confirm]\n" +
      "       bun tools/contributions/counts.ts open <contributions-x25519.pem> <folder>");
    process.exit(2);
  }
}
