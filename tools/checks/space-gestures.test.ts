// Checks how the keyboard wires Space's triple press and the held delete to the bar, where no Kotlin check can drive the IME.
import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

const root = join(import.meta.dir, "../..");
const ime = readFileSync(join(root, "android/kotlin/com/atlas/keyboard/AtlasIme.kt"), "utf8");

/** The body of the function [name] in the IME, up to the next function. */
const body = (name: string) => {
  const start = ime.indexOf(`private fun ${name}(`);
  expect(start).toBeGreaterThan(-1);
  const next = ime.indexOf("\n    private ", start + 1);
  return ime.slice(start, next < 0 ? undefined : next);
};

test("a held triple press glows every time, not only when it slides", () => {
  // The triple press registers as it lands, so the held glow has to come before the early return for a registered gesture:
  // otherwise only a slide's select ever turned it on, and a steady finger got none.
  const register = body("registerGesture");
  const glow = register.indexOf("pickHeldGlow(now)");
  const early = register.indexOf("if (taps == 0 || gestureRegistered || gestureEmpty) return");
  expect(glow).toBeGreaterThan(-1);
  expect(early).toBeGreaterThan(glow);
  expect(register).toContain("if (taps >= 3 && tripleDown && !gestureEmpty) strip?.gesture?.pickHeldGlow(now)");
  // The lift fades it.
  expect(body("lift")).toContain("strip?.gesture?.pickLifted(SystemClock.uptimeMillis())");
});

test("a triple press's pick shows only a caret, and a double tap's keeps its box", () => {
  expect(body("selectWord")).toContain("wordSelected(SystemClock.uptimeMillis(), held = inFront && tripleDown, caret = inFront)");
});

test("a held delete tells the bar its real words, how fast it goes, when it pauses, and where its glow goes", () => {
  const held = body("deleteHeldWord");
  expect(held).toContain("gesture.deleteGlowPlace = Haptics.deleteGlow(this)");
  expect(held).toContain("gesture.deleteHeld(deleteHold.repeatMillis, SystemClock.uptimeMillis(), deleteHold.pace,");
  // Each word's real text goes to the bar before it goes, and with nothing left the bar gets none.
  expect(held).toContain("before?.let { strip?.gesture?.deleteText(it) }");
  expect(held).toContain('strip?.gesture?.deleteText("")');
  expect(ime).toContain("strip?.gesture?.deletePaused(deleteHold.paused)");
  expect(ime).toContain("deleteHeld(deleteHold.repeatMillis, finger.time, deleteHold.pace)");
  // A glow under the finger follows it.
  expect(ime).toContain("strip?.gesture?.fingerAt(finger.x)");
});

test("a held delete ticks once at each zone, while the lab's experiment is on", () => {
  expect(ime).toContain("if (deleteHold.crossed() != null && Haptics.zoneTicks(this)) haptics.play(Haptic.DeleteZone)");
});

test("all three gestures hit their walls by the same rule, and only nothing at all plays no", () => {
  // Steering, through CursorSteps; selecting and a held delete through their own WallHits.
  expect(ime).toContain("if (selectionWall.reached()) haptics.play(Haptic.SelectionEnd)");
  expect(ime).toContain("if (deleteWall.reached()) haptics.play(Haptic.DeleteRanOut)");
  const extend = body("extendWords");
  expect(extend.indexOf("selectionWall.left()")).toBeGreaterThan(extend.indexOf("selectionWall.reached()"));
  expect(body("startWordDrag")).toContain("selectionWall.left()");
  const held = body("deleteHeldWord");
  expect(held.indexOf("deleteWall.left()", held.indexOf("deleteWall.reached()"))).toBeGreaterThan(-1);
  // The hold no longer ends with a no at the start of the text: it stays, quiet, until the lift.
  expect(held).not.toContain("Haptic.DeleteNothing");
  expect(held).not.toContain("contact.cancelActive()");
});

test("a double space with text selected puts the cursor back, then steers", () => {
  const collapse = body("collapseSelection");
  expect(collapse).toContain("WordSelection.collapseTo(start, end, cursorBeforeSelect)");
  expect(collapse).toContain("connection.setSelection(at, at)");
  expect(ime).toContain("if (!words && collapseSelection()) enqueue { readCursorText() }");
  expect(ime).toMatch(/if \(collapseSelection\(\)\) \{\s+haptics\.play\(Haptic\.Steer\)/);
  // Each Space select keeps where the cursor was.
  expect(body("pickOnLand")).toContain("rememberCursor()");
  expect(body("startWordDrag")).toContain("rememberCursor()");
});
