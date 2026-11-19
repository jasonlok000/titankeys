import { expect, test } from "bun:test";

const native = await Bun.file("android/kotlin/com/titankeys/support/SupportBoard.kt").text();
const page = await Bun.file("app/support.tsx").text();

test("the board reads from the deployed HTTPS origin and checks readiness before opening a receipt request", () => {
  expect(native).toContain('ORIGIN = "https://titankeys-support.everyones-niche.workers.dev"');
  expect(native).toContain('getHeaderField("X-TitanKeys-Posting") == "ready"');
  const guard = native.indexOf("check(path != POST_PATH || postingReady)");
  expect(guard).toBeGreaterThan(-1);
  expect(guard).toBeLessThan(native.indexOf("Connected.open(ORIGIN + path"));
  expect(native).toContain("if (path.startsWith(BOARD_PATH)) postingReady = false");
  // Pages of the ranked board, and the rank preview and reports, go through the same origin (support v2).
  expect(native).toContain("request(\"$BOARD_PATH?offset=${offset.coerceAtLeast(0)}&limit=${limit.coerceIn(1, 50)}\", null, done)");
  expect(native).toContain("request(\"/v1/rank-preview\"");
  expect(native).toContain("request(\"/v1/report\"");
});

test("only an available, configured board offers posting, and an opted-in supporter can still remove their entry", () => {
  expect(page).toContain("const canJoinBoard = hasSupport && board.available && board.postingReady === true");
  expect(page).toContain("canJoinBoard && !offerBoard");
  expect(page).toContain("canJoinBoard && offerBoard");
  expect(page).toContain("{board.available && (board.joined ?? state?.boardJoined) &&");
});

test("the support ledger survives a bad file and never writes on the main thread (review, 2026-11-19)", async () => {
  const ledger = await Bun.file(new URL("../../android/kotlin/com/titankeys/support/SupportLedger.kt", import.meta.url)).text();
  const billing = await Bun.file(new URL("../../android/kotlin/com/titankeys/support/SupportBilling.kt", import.meta.url)).text();
  // A bad file goes aside, unread and unlogged; Support starts empty and buying waits.
  expect(ledger).toContain('file.baseFile.renameTo(File(file.baseFile.parentFile, "$FILE.bad-${System.currentTimeMillis()}"))');
  expect(ledger).not.toMatch(/Log\.\w\(/);
  expect(billing).toContain('.put("billing", if (offers.isEmpty() || ledger.broken) "unavailable" else "ready")');
  // Writes go to the ledger's own thread; Play is told only once the credit is on the disk.
  expect(ledger).toContain("writer.execute {");
  expect(billing).toMatch(/\{ saved ->\s+post \{ finish\(purchase, product, saved\) \}/);
  expect(billing.indexOf("client.acknowledgePurchase")).toBeGreaterThan(billing.indexOf("private fun finish("));
  // Each renewal's order is its key.
  expect(ledger).toContain("val key = supportKey(token, orderId, product.monthly)");
});
