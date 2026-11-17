import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// Each feature that a phone setting can stop says so under its own row, with a button to the system screen, and the pages read
// the phone again when the app comes back (owner, 2026-11-17; docs/system-settings.md). No banner (owner: "unnecessary").
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");
const needs = read("lib/system-needs.tsx");
const settings = read("app/settings/index.tsx");

test("each need opens the exact system screen through the app's own actions", () => {
  for (const [id, open] of [["enabled", "enableKeyboard"], ["selected", "chooseKeyboard"], ["overlay", "allowOverlay"],
    ["scrollAssistant", "openScrollAssistant"], ["backService", "allowBack"], ["fnKey", "openShortcutKeys"], ["symKey", "openShortcutKeys"]])
    expect(needs).toMatch(new RegExp(`${id}: \\{ id: "${id}",[\\s\\S]*?open: ${open} \\}`));
  // And those actions are PhoneSetup's pages: Android's keyboard list, the overlay page, Accessibility.
  const setup = read("android/kotlin/com/titankeys/keyboard/PhoneSetup.kt");
  for (const screen of ["Settings.ACTION_INPUT_METHOD_SETTINGS", "Settings.ACTION_MANAGE_OVERLAY_PERMISSION", "Settings.ACTION_ACCESSIBILITY_SETTINGS"])
    expect(setup).toContain(screen);
});

test("each feature's own row says what's off, and there is no banner", () => {
  expect(settings).toContain('<Needs list={needs.touchpad(settings)} />');
  expect(settings).toContain('<Needs list={needs.scrollBlock(settings).filter(need => need.id === "backService")} />');
  expect(settings).toContain('<Needs list={needs.fnAsCtrl(settings)} />');
  expect(settings).toContain('<Needs list={needs.altSym(settings)} />');
  expect(settings).toContain('<Needs list={needs.keyboard(settings)} />');
  expect(read("app/settings/data/index.tsx")).toContain("<Needs list={needs.contactNames(result.data)} />");
  // Each sits right under its row.
  expect(settings.indexOf("needs.fnAsCtrl")).toBeGreaterThan(settings.indexOf('label="Use fn as Ctrl"'));
  expect(settings.indexOf("needs.scrollBlock")).toBeGreaterThan(settings.indexOf("label={scrollLabel}"));
  // No list of everything off at the top.
  const top = settings.slice(settings.indexOf("<Screen title=\"Settings\" rightAction={closeAction}>"), settings.indexOf('<Section title="Languages">'));
  expect(top).not.toContain("Needs");
});

test("the block warns only while it's on, and the Accessibility service is what it needs (TouchpadHold.wanted)", () => {
  expect(needs).toContain("scrollBlock: (s: State): Need[] => s.touchpadOnlyInFields");
  expect(read("android/kotlin/com/titankeys/keyboard/TouchpadHold.kt"))
    .toContain("settingOn && !editing && overlayUp && backServiceOn && !lettingGo");
});

test("Settings and Your words read the phone again when the app comes back to the front", () => {
  expect(needs).toContain("useEffect(() => onAppWindow(() => void resource.refresh()), []);");
  expect(settings).toContain("useRecheck(state);");
  expect(read("app/settings/data/index.tsx")).toContain("useRecheck(state);");
});

test("a Contacts permission taken away is told apart from the setting being off", () => {
  expect(read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt"))
    .toContain('.put("contactNamesBlocked", TypingSettings.contactNames(activity) && !PhoneNameSource.contactsAllowed(activity))');
  expect(needs).toContain("contactNames: (s: State): Need[] => s.contactNamesBlocked ? [NEEDS.contacts] : []");
});

test("TitanKeys never writes a secure or global setting", () => {
  for (const file of ["android/kotlin/com/titankeys/keyboard/PhoneSetup.kt", "android/kotlin/com/vandam/ink/AppNativeAdapters.kt"])
    expect(read(file)).not.toMatch(/Settings\.(Secure|Global)\.put/);
});

test("Names from my contacts shows on while its permission is blocked, so turning it off clears the line", () => {
  const page = read("app/settings/data/index.tsx");
  expect(page).toContain("value={contactNames || result.data.contactNamesBlocked}");
  // Off reaches the setting itself; only on asks Android for the permission.
  const bridge = read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
  expect(bridge).toContain('"contactNames" -> if (value as Boolean && !PhoneNameSource.contactsAllowed(activity)) ContactsPermissionActivity.start(activity)');
  expect(bridge).toContain(".put(\"contactNamesBlocked\", TypingSettings.contactNames(activity) && !PhoneNameSource.contactsAllowed(activity))");
});
