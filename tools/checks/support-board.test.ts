import { expect, test } from "bun:test";

const native = await Bun.file("android/kotlin/com/titankeys/support/SupportBoard.kt").text();
const page = await Bun.file("app/support.tsx").text();

test("the board reads from the deployed HTTPS origin and checks readiness before opening a receipt request", () => {
  expect(native).toContain('ORIGIN = "https://titankeys-support.everyones-niche.workers.dev"');
  expect(native).toContain('getHeaderField("X-TitanKeys-Posting") == "ready"');
  const guard = native.indexOf("check(path != POST_PATH || postingReady)");
  expect(guard).toBeGreaterThan(-1);
  expect(guard).toBeLessThan(native.indexOf("Connected.open(ORIGIN + path"));
  expect(native).toContain("if (path == BOARD_PATH) postingReady = false");
});

test("only an available, configured board offers posting, and an opted-in supporter can still remove their entry", () => {
  expect(page).toContain("const canJoinBoard = hasSupport && board.available && board.postingReady === true");
  expect(page).toContain("canJoinBoard && !offerBoard");
  expect(page).toContain("canJoinBoard && offerBoard");
  expect(page).toContain("{board.available && (board.joined ?? state?.boardJoined) &&");
});
