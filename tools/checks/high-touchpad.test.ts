import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// The window above the notification shade (#28) depends on a chain that a merge can break without any test failing on the JVM:
// a field start updates the overlays, that hands the touchpad to the Back service, and the service shows the window. These checks
// read the code and fail if a link of that chain goes missing.
const read = (file: string) => readFileSync(join(import.meta.dir, "../../android/kotlin/com/atlas/keyboard", file), "utf8");

/** The body of a function, from its first line to the closing brace at the same indent. */
function body(source: string, name: string) {
  const found = new RegExp(String.raw`\n([ \t]*)(?:override |private |internal )*fun ${name}\(`).exec(source);
  expect(found).not.toBeNull();
  const start = found!.index;
  const end = source.indexOf(`\n${found![1]}}`, start + 1);
  expect(end).toBeGreaterThan(start);
  return source.slice(start, end);
}

test("every field start and end updates the overlays", () => {
  const ime = read("AtlasIme.kt");
  expect(body(ime, "onStartInput")).toContain("updateOverlay()");
  expect(body(ime, "onFinishInput")).toContain("updateOverlay()");
});

test("updating the overlays hands the touchpad to the Back service while a field is being edited", () => {
  const update = body(read("AtlasIme.kt"), "updateOverlay");
  expect(update).toMatch(/val wanted = editing\b/);
  expect(update).toContain("BackService.touchpad(overlayEvent)");
});

test("the Back service checks the window when it connects and at every call, from the live screen state", () => {
  const service = read("BackService.kt");
  expect(body(service, "onServiceConnected")).toContain("updateTouchpad()");
  expect(body(service, "touchpad")).toContain("current?.updateTouchpad()");
  const update = body(service, "updateTouchpad");
  expect(update).toContain("HighTouchpad.up(");
  expect(update).toContain("isKeyguardLocked");
  expect(update).toContain("isInteractive");
  // A lock remembered from broadcasts stuck on after the first unlock, because the unlock broadcast never arrived.
  expect(service).not.toMatch(/var locked\b/);
});
