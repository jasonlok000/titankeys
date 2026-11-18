import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";

const recorder = new URL("../../android/debug/recorder/", import.meta.url);
const read = (name: string) => readFileSync(new URL(name, recorder), "utf8");

test("the Debug method readback takes the saved values and exits before making any changes", () => {
  const receiver = read("InputMethodsSet.kt");
  const start = receiver.indexOf('if (intent.getBooleanExtra("read", false))');
  const write = receiver.indexOf("val index = InputMethodList.index(context)");
  const debugGuard = receiver.indexOf('if (!context.packageName.endsWith(".debug")) return');
  expect(debugGuard).toBeGreaterThan(-1);
  expect(start).toBeGreaterThan(debugGuard);
  expect(write).toBeGreaterThan(start);
  const branch = receiver.slice(start, write);
  expect(branch).toContain("TypingSettings.inputMethods(context)");
  expect(branch).toContain("TypingSettings.currentInputMethod(context)");
  expect(branch).toMatch(/resultData = "on \$on; typing \$current"\s+return\s+}/);
  expect(branch).not.toContain("context,");
  expect(branch).not.toContain("enable(");
  const name = read("manifest.xml").match(/<receiver\b[^>]*\.InputMethodsSet"[^>]*>/)?.[0];
  expect(name).toContain('android:permission="android.permission.DUMP"');
});
