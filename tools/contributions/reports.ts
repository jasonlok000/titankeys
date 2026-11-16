// Bug reports and ideas from the app's form (#97): download them from the server, still sealed, then open them with the
// owner's private key into one ZIP per report, holding report.json and any recording or crash report.
//
// Usage:
//   bun tools/contributions/reports.ts download <worker address> <file holding EXPORT_TOKEN> <folder> [--confirm]
//   bun tools/contributions/reports.ts open <contributions-x25519.pem> <folder>
//
// download writes each sealed part as <report>-<part>-of-<count>.tkr. With --confirm, it then asks the server to delete
// what it downloaded, but only up to the first report that hasn't arrived whole, so a report still on its way stays.
// open joins each whole report's parts, opens them, and writes <report>.zip beside them. The app makes them with
// android/kotlin/com/titankeys/connected/Reports.kt: one ZIP, split into parts, each sealed as a contribution batch is.
import { mkdir, readdir } from "node:fs/promises";
import { openBytes, pem } from "./open";

type Part = { id: number; report: string; part: number; count: number; month: string; size: number };

export async function download(address: string, tokenFile: string, folder: string, confirm: boolean) {
  const base = address.replace(/\/$/, "");
  const headers = { Authorization: `Bearer ${(await Bun.file(tokenFile).text()).trim()}` };
  await mkdir(folder, { recursive: true });
  const parts: Part[] = [];
  for (let after = 0; ;) {
    const response = await fetch(`${base}/v1/export/reports?after=${after}`, { headers });
    if (!response.ok) throw new Error(`Listing reports failed: ${response.status}. Check the address and the token.`);
    const page = await response.json() as { items: Part[]; more: boolean };
    for (const item of page.items) {
      const part = await fetch(`${base}/v1/export/report?id=${item.id}`, { headers });
      const bytes = new Uint8Array(await part.arrayBuffer());
      if (!part.ok || bytes.length !== item.size) throw new Error(`Report part ${item.id} did not download whole`);
      await Bun.write(`${folder}/${item.report}-${item.part}-of-${item.count}.tkr`, bytes);
      parts.push(item);
    }
    if (!page.more || page.items.length === 0) break;
    after = page.items[page.items.length - 1].id;
  }
  const reports = new Set(parts.map(part => part.report));
  const whole = new Set([...reports].filter(report => {
    const mine = parts.filter(part => part.report === report);
    return new Set(mine.map(part => part.part)).size === mine[0].count;
  }));
  let through = 0;
  for (const part of parts) {
    if (!whole.has(part.report)) break;
    through = part.id;
  }
  if (confirm && through > 0) {
    const confirmed = await fetch(`${base}/v1/export/reports/confirm`, {
      method: "POST", headers: { ...headers, "Content-Type": "application/json" }, body: JSON.stringify({ through }),
    });
    if (!confirmed.ok) throw new Error(`Deleting reports on the server failed: ${confirmed.status}`);
  }
  console.log(`Downloaded ${whole.size} whole reports (${reports.size - whole.size} still arriving) to ${folder}` +
    (confirm && through > 0 ? ", and deleted the whole ones on the server." : "."));
}

/** Joins and opens every whole report in [folder]. Returns the ZIPs written. */
export async function openAll(keyPath: string, folder: string): Promise<string[]> {
  const key = pem(await Bun.file(keyPath).text());
  const names = (await readdir(folder)).filter(name => /^[0-9a-f]{32}-\d+-of-\d+\.tkr$/.test(name));
  const byReport = new Map<string, string[]>();
  for (const name of names) byReport.set(name.slice(0, 32), [...(byReport.get(name.slice(0, 32)) ?? []), name]);
  const written: string[] = [];
  for (const [report, files] of byReport) {
    const count = Number(files[0].match(/-of-(\d+)\./)![1]);
    if (files.length !== count) {
      console.error(`${report}: ${files.length} of ${count} parts, still waiting for the rest`);
      continue;
    }
    const ordered = files.sort((a, b) => Number(a.split("-")[1]) - Number(b.split("-")[1]));
    const opened = await Promise.all(ordered.map(async file => openBytes(new Uint8Array(await Bun.file(`${folder}/${file}`).arrayBuffer()), key)));
    const zip = new Uint8Array(opened.reduce((sum, part) => sum + part.length, 0));
    let at = 0;
    for (const part of opened) { zip.set(part, at); at += part.length; }
    await Bun.write(`${folder}/${report}.zip`, zip);
    written.push(`${folder}/${report}.zip`);
  }
  return written;
}

if (import.meta.main) {
  const [command, ...rest] = process.argv.slice(2);
  if (command === "download" && rest.length >= 3) await download(rest[0], rest[1], rest[2], rest[3] === "--confirm");
  else if (command === "open" && rest.length === 2) for (const zip of await openAll(rest[0], rest[1])) console.log(zip);
  else {
    console.error("Usage: bun tools/contributions/reports.ts download <worker address> <token file> <folder> [--confirm]\n" +
      "       bun tools/contributions/reports.ts open <contributions-x25519.pem> <folder>");
    process.exit(2);
  }
}
