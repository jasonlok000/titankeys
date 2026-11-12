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

test("a held delete tells the bar where it started, how fast it goes and when it pauses", () => {
  expect(body("deleteHeldWord")).toContain("deleteHold.originX, deleteHold.pace)");
  expect(ime).toContain("strip?.gesture?.deletePaused(deleteHold.paused)");
  expect(ime).toContain("deleteHeld(deleteHold.repeatMillis, finger.time, pace = deleteHold.pace)");
});
