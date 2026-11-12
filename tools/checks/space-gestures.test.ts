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

test("a triple press's pick shows its selection at once, as a drag does", () => {
  expect(body("selectWord")).toContain("wordSelected(SystemClock.uptimeMillis(), held = inFront && tripleDown)");
});

test("a held delete tells the bar its real words, how fast it goes and when it pauses, and starts only with something to delete", () => {
  const held = body("deleteHeldWord");
  expect(held).toContain("strip?.gesture?.deleteHeld(deleteHold.repeatMillis, SystemClock.uptimeMillis(), deleteHold.pace, before)");
  // With nothing to delete no hold starts: no words, no dots, only the wall the flick plays.
  const start = held.indexOf("if (before?.isEmpty() != true) {");
  expect(start).toBeGreaterThan(-1);
  expect(held.indexOf("deleteHeld(", start)).toBeGreaterThan(start);
  expect(held).toContain("if (deleteWall.reached()) haptics.wall(Haptic.DeleteRanOut)");
  // Each word's real text goes to the bar before it goes, and with nothing left the bar gets none.
  expect(held).toContain("before?.let { strip?.gesture?.deleteText(it) }");
  expect(held).toContain('strip?.gesture?.deleteText("")');
  expect(ime).toContain("strip?.gesture?.deletePaused(deleteHold.paused)");
  expect(ime).toContain("deleteHeld(deleteHold.repeatMillis, finger.time, deleteHold.pace)");
  // A clear flick's glow follows the finger, and fades at the lift or as the stroke stops being a flick.
  expect(ime).toContain("if (flickCrunch.crunching) samples.lastOrNull { it.valid }?.let { strip?.gesture?.flickGlow(it.x, it.time) }");
  expect(body("lift")).toContain("strip?.gesture?.flickGlowEnded(SystemClock.uptimeMillis())");
});

test("a held delete ticks once at each zone, while the lab's experiment is on", () => {
  expect(ime).toContain("if (deleteHold.crossed() != null && Haptics.zoneTicks(this) && !deleteWall.atWall) haptics.play(Haptic.DeleteZone)");
});

test("all three gestures hit their walls by the same rule, and only nothing at all plays no", () => {
  // Steering, through CursorSteps; selecting and a held delete through their own WallHits.
  expect(ime).toContain("if (selectionWall.reached()) haptics.play(Haptic.SelectionEnd)");
  expect(ime).toContain("if (deleteWall.reached()) haptics.wall(Haptic.DeleteRanOut)");
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

test("a delete's wall is the same for a flick and a hold, played clean, once per stroke", () => {
  expect(ime).toContain("if (nothingLeft && deleteWall.reached()) haptics.wall(Haptic.DeleteNothing)");
  expect(ime).not.toContain("haptics.play(Haptic.DeleteNothing)");
  expect(ime).not.toContain("haptics.play(Haptic.DeleteRanOut)");
  // A stroke with nothing to delete never crunches, so nothing comes before its wall.
  expect(ime).toContain("&& somethingToDelete(first))");
});

test("a double space with text selected steers, never says no", () => {
  expect(body("nothingHere")).toContain("if (!words && lastSelectionStart >= 0 && lastSelectionStart != lastSelectionEnd) return false");
  expect(body("registerGesture")).toContain("if (!words && collapseSelection()) enqueue { readCursorText() }");
  // Putting the cursor back never hides or shows the keyboard's window, nor does the bar's update that follows it.
  for (const name of ["collapseSelection", "showSelectedWord"])
    for (const call of ["requestHideSelf", "hideWindow", "requestShowSelf", "showWindow", "updateInputViewShown", "setCandidatesViewShown"])
      expect(body(name)).not.toContain(call);
});
