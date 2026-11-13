import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// Shift + Backspace and Alt + Sym are built in, so they work with Key Mapper off (owner, 2026-11-12). tests/modifiers checks
// the held, tapped and automatic modifiers; these check how the keyboard uses them.
const root = join(import.meta.dir, "../..");
const ime = readFileSync(join(root, "android/kotlin/com/atlas/keyboard/AtlasIme.kt"), "utf8");

test("Backspace asks the modifiers what it does, once a press, after exact typing has taken its keys", () => {
  expect(ime).toContain("backspaceDoes = modifiers.backspaceDoes(event.metaState, TypingSettings.altBackspaceForward(this))");
  expect(ime).toContain("if (event.repeatCount == 0 || backspaceDoes == null)");
  expect(ime.indexOf("if (field.exact) return exactKey(event)")).toBeLessThan(ime.indexOf("modifiers.backspaceDoes("));
});

test("Alt + Backspace deletes forward without Alt, or goes to the app with its Alt, and plays no haptic of its own", () => {
  expect(ime).toContain("Modifiers.Backspace.Forward -> command(withKey(event, KeyEvent.KEYCODE_FORWARD_DEL), dropMeta = KeyEvent.META_ALT_MASK)");
  expect(ime).toContain("Modifiers.Backspace.AltToApp -> command(withMeta(event, KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON))");
  const start = ime.indexOf("KeyEvent.KEYCODE_DEL -> {");
  expect(ime.slice(start, ime.indexOf("modifiers.afterBackspace()", start))).not.toContain("haptics.play");
  const typing = readFileSync(join(root, "android/kotlin/com/atlas/engine/TypingSettings.java"), "utf8");
  expect(typing).toContain('getBoolean("alt_backspace_forward", true)');
  const names = readFileSync(join(root, "lib/setting-names.ts"), "utf8");
  expect(names).toContain('altBackspaceLabel = "Alt + Backspace deletes forward"');
  expect(names).toContain('altBackspaceNote = "Like Delete on a PC. Off, Android deletes the whole line instead."');
  const settings = readFileSync(join(root, "app/settings/index.tsx"), "utf8");
  expect(settings.indexOf("<Toggle label={altBackspaceLabel}")).toBeGreaterThan(settings.indexOf('<Toggle label="Use fn as Ctrl"'));
  expect(readFileSync(join(root, "app/settings/guide-boring.tsx"), "utf8")).toContain("Alt and Backspace deletes forward");
  expect(readFileSync(join(root, "release-notes.txt"), "utf8")).toContain("- Alt + Backspace: Deletes forward");
});

test("Alt + Sym goes to the next keyboard, never the picker, and never from the guide's field", () => {
  const start = ime.indexOf("private fun switchKeyboardIfAsked()");
  const body = ime.slice(start, ime.indexOf("\n    }\n", start));
  expect(body).toContain("!modifiers.takeKeyboardSwitch() || inGuide");
  expect(body).toContain("switchToNextInputMethod(false)");
  expect(body).not.toContain("showInputMethodPicker");
});

test("both are listed in Settings and the boring guide", () => {
  const settings = readFileSync(join(root, "app/settings/index.tsx"), "utf8");
  expect(settings).toContain("Hold Shift + Backspace");
  expect(settings).toContain("Hold Alt + Sym");
  const guide = readFileSync(join(root, "app/settings/guide-boring.tsx"), "utf8");
  expect(guide).toContain("Backspace while you hold Shift");
  expect(guide).toContain("Hold Alt and press Sym");
});
