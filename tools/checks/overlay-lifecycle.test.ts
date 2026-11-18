import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// The firmware gives the touchpad to the topmost window titled "InputMethod". A keyboard that leaves its touchpad windows up
// after it's gone takes the touchpad from the next one: 2.1 left 96 on the owner's phone (2026-11-16), because Android's own
// onDestroy finishes the input after ours has taken them down, and finishing the input put them up again.
const root = join(import.meta.dir, "../..");
const ime = readFileSync(join(root, "android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt"), "utf8");
const back = readFileSync(join(root, "android/kotlin/com/titankeys/keyboard/BackService.kt"), "utf8");

test("the touchpad's windows are up only while this keyboard is alive and Android's current one", () => {
  const update = ime.slice(ime.indexOf("private fun updateOverlay("), ime.indexOf("private var destroyed"));
  expect(update).toContain("val wanted = !destroyed && PhoneSetup.selected(this) && (editing || TypingSettings.touchpadOnlyInFields(this))");
  expect(update).toContain("destroyed -> BackService.releaseTouchpad(overlayEvent)");
  // Nothing else shows the overlay or hands the Back service a handler.
  expect(ime.match(/overlay\.show\(\)/g)?.length).toBe(1);
  expect(ime.match(/BackService\.touchpad\(overlayEvent\b/g)?.length).toBe(1);
});

test("onDestroy marks the keyboard gone before anything else, and lets go of only its own handler", () => {
  const destroy = ime.slice(ime.indexOf("override fun onDestroy()"), ime.indexOf("super.onDestroy()", ime.indexOf("override fun onDestroy()")));
  expect(destroy).toMatch(/override fun onDestroy\(\) \{\s+destroyed = true/);
  expect(destroy).toContain("BackService.releaseTouchpad(overlayEvent)");
  expect(ime).not.toContain("BackService.touchpad(null)");
  expect(back).toMatch(/fun releaseTouchpad\(handler: \(MotionEvent\) -> Boolean\) \{\s+if \(touchpad === handler\) touchpad\(null\)/);
});

test("at each field start the keyboard's own overlay goes back on top of other apps' leftovers, new before old", () => {
  const overlay = readFileSync(join(root, "android/kotlin/com/titankeys/keyboard/TouchpadOverlay.kt"), "utf8");
  const raise = overlay.slice(overlay.indexOf("fun raise()"), overlay.indexOf("private fun added()"));
  // The new window is added before the old one goes: never none, never two.
  expect(raise.indexOf("added()")).toBeLessThan(raise.indexOf("removeViewImmediate(old)"));
  expect(raise).toContain("view = fresh");
  const start = ime.slice(ime.indexOf("override fun onStartInput("), ime.indexOf("private fun readSettings("));
  expect(start).toContain("updateOverlay(raise = editing)");
  expect(ime).toContain("if (wanted && raise) overlay.raise()");
  // The Back service's window isn't raised: Android stacks it by when the service connected, so adding it again does nothing.
  expect(back).not.toContain("raise()");
});

test("the Accessibility screens say the service keeps the touchpad from other keyboards' leftovers, and what goes without it", () => {
  const strings = readFileSync(join(root, "android/src/main/res/values/strings.xml"), "utf8");
  const line = (name: string) => strings.match(new RegExp(`<string name="${name}">([^<]*)</string>`))?.[1] ?? "";
  expect(line("back_disclosure_touchpad")).toContain("another keyboard left a layer behind");
  expect(line("back_service_description")).toContain("another keyboard left a layer behind");
  expect(line("back_disclosure_no")).toContain("leftover layer can take the touchpad");
  const disclosure = readFileSync(join(root, "android/kotlin/com/titankeys/keyboard/BackDisclosureActivity.kt"), "utf8");
  expect(disclosure).toContain("R.string.back_disclosure_more_touchpad");
});
