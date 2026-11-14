import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// Settings > Setup checks the phone's own settings for the Sym key, the fn key and each app's Sliding Mode. TitanKeys only
// reads them: changing system settings is never its job.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");
const checks = read("android/kotlin/com/atlas/keyboard/PhoneChecks.kt");

test("the phone checks only read", () => {
  expect(checks).not.toMatch(/Settings\.(System|Global|Secure)\.put/);
  expect(checks).not.toMatch(/getMethod\("set"/);
  expect(checks).toContain('persist.sys.agui_smart_${key.id}_key');
  expect(checks).toContain('"persist.sys.touchpad_forcescroll"');
});

test("Setup shows each phone setting as a step that opens its page", () => {
  const setup = read("app/settings/setup.tsx");
  for (const step of ["Set Sym to Programmable key", "Set fn to Programmable key", "Sliding Mode 1 in your apps"]) expect(setup).toContain(step);
  expect(setup).toContain("onPress={openShortcutKeys}");
  expect(setup).toContain("onPress={openScrollAssistant}");
  const adapter = read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
  for (const field of ['"symKey"', '"fnKey"', '"outsideSlidingMode"', '"shortcutKeys"']) expect(adapter).toContain(field);
});

test("the keyboard notes each app's Sliding Mode, and the app can see the Shortcut keys page", () => {
  const ime = read("android/kotlin/com/atlas/keyboard/AtlasIme.kt");
  // Two readings per field, early and late, and a Sliding Mode 1 touch from the touchpad (tests/sliding-mode has the rules).
  expect(ime).toContain("main.postDelayed({ readSlidingMode(field, late = false) }, SLIDING_CHECK_MILLIS)");
  expect(ime).toContain("main.postDelayed({ readSlidingMode(field, late = true) }, SLIDING_CONFIRM_MILLIS)");
  expect(ime).toContain("private const val SLIDING_CONFIRM_MILLIS = 3000L");
  expect(ime).toContain("SlidingModeList.trustworthy(field.early, reading, sameField, field.mode1Touch)");
  expect(ime).toMatch(/private fun motion\(event: MotionEvent\) \{\s+noteSlidingTouch\(event\)/);
  expect(read("android/kotlin/com/atlas/keyboard/Touchpad.kt")).toContain("fun sentAsScreenTouch(event: MotionEvent)");
  expect(read("android/src/main/AndroidManifest.xml")).toContain('<package android:name="com.agui.shortcutsettings" />');
});

test("the first list, which misread Chrome and Settings, is never read again", () => {
  expect(checks).toContain('private const val OLD_OUTSIDE_SLIDING = "outside_sliding_mode_1"');
  expect(checks).toContain('private const val OUTSIDE_SLIDING = "outside_sliding_mode"');
  expect(checks.match(/OLD_OUTSIDE_SLIDING/g)?.length).toBe(3); // its name, the check that it's still there, and its removal
  expect(checks).toContain(".remove(OLD_OUTSIDE_SLIDING)");
});

test("Display over other apps and Accessibility try TitanKeys' own page first, then the list", () => {
  const setup = read("android/kotlin/com/atlas/keyboard/PhoneSetup.kt");
  expect(setup).toContain('private const val MANAGE_APP_OVERLAY_PERMISSION = "android.settings.MANAGE_APP_OVERLAY_PERMISSION"');
  expect(setup).toMatch(/Intent\(MANAGE_APP_OVERLAY_PERMISSION, [^\n]+\),\s+Intent\(Settings\.ACTION_MANAGE_OVERLAY_PERMISSION/);
  expect(setup).toContain('private const val ACCESSIBILITY_DETAILS_SETTINGS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"');
  expect(setup).toContain("listOf(Intent(ACCESSIBILITY_DETAILS_SETTINGS).putExtra(Intent.EXTRA_COMPONENT_NAME, service), list)");
  expect(setup).toContain("Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU");
  expect(setup).toContain("catch (error: ActivityNotFoundException)");
  expect(setup).toContain("catch (error: SecurityException)");
  // Every way in goes through open(), so each falls back alike.
  expect(read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt")).toContain("PhoneSetup.open(activity, part, activity::startActivity)");
  expect(read("android/kotlin/com/atlas/keyboard/BackDisclosureActivity.kt")).toContain("PhoneSetup.open(this, SetupPart.Back, ::startActivity)");
  expect(read("android/kotlin/com/atlas/keyboard/GuideActivity.kt")).toContain("PhoneSetup.open(this, part) { page -> startActivityForResult(page, SETUP_REQUEST) }");
  for (const path of ["android/kotlin/com/vandam/ink/AppNativeAdapters.kt", "android/kotlin/com/atlas/keyboard/BackDisclosureActivity.kt", "android/kotlin/com/atlas/keyboard/GuideActivity.kt"])
    expect({ path, page: read(path).includes("PhoneSetup.page(") }).toEqual({ path, page: false });
});

test("Setup names Sliding Mode 1, and says what to find when Android shows its whole list", () => {
  const page = read("app/settings/setup.tsx");
  expect(page).toContain('export const scrollAssistantStep = "Turn on Scroll assistant, Sliding Mode 1";');
  expect(page).toContain("<Field label={scrollAssistantStep} onPress={openScrollAssistant}>");
  expect(page).toContain("{!settings.overlay && settings.overlayOnList && <Note>{overlayListNote}</Note>}");
  expect(page).toContain("{!settings.backService && settings.backOnList && <Note>{backListNote}</Note>}");
  expect(page).toMatch(/overlayListNote = "[^"]*Find TitanKeys Keyboard in it/);
  expect(page).toMatch(/backListNote = "[^"]*Find TitanKeys Keyboard in it/);
  const adapter = read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
  expect(adapter).toContain('.put("overlayOnList", PhoneSetup.onList(activity, SetupPart.Overlay))');
  expect(adapter).toContain('.put("backOnList", PhoneSetup.onList(activity, SetupPart.Back))');
  expect(read("app/settings/guide-boring.tsx")).toContain("Turn on Scroll assistant, Sliding Mode 1 opens Scroll assistant");
});
