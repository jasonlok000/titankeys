import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// Shift + Backspace and Alt + Sym are built in, so they work with Key Mapper off (owner, 2026-11-12). tests/modifiers checks
// the held, tapped and automatic modifiers; these check how the keyboard uses them.
const root = join(import.meta.dir, "../..");
const ime = readFileSync(join(root, "android/kotlin/com/atlas/keyboard/AtlasIme.kt"), "utf8");

test("Backspace asks the modifiers what it does, once a press, after exact typing has taken its keys", () => {
  // The press's choice lives in the modifiers, and Backspace's release clears it (bug hunt #38).
  expect(ime).toContain("when (modifiers.backspacePress(event.repeatCount, event.metaState, TypingSettings.altBackspaceForward(this),");
  expect(ime).toContain("TypingSettings.shiftBackspaceWord(this))) {");
  expect(ime).toContain("if (keyCode == KeyEvent.KEYCODE_DEL) modifiers.backspaceReleased()");
  expect(ime.indexOf("if (field.exact) return exactKey(event)")).toBeLessThan(ime.indexOf("modifiers.backspacePress("));
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

test("each has its switch, on to start, read by the keyboard and reset with the rest", () => {
  // Owner, 2026-11-13: "why is delete a word shift backspace and next keyboard alt sym not toggleable?"
  const typing = readFileSync(join(root, "android/kotlin/com/atlas/engine/TypingSettings.java"), "utf8");
  expect(typing).toContain('getBoolean("shift_backspace_word", true)');
  expect(typing).toContain('getBoolean("alt_sym_next_keyboard", true)');
  expect(ime).toContain("modifiers.altSymSwitches = TypingSettings.altSymNextKeyboard(this)");
  const modifiers = readFileSync(join(root, "android/kotlin/com/atlas/keyboard/Modifiers.kt"), "utf8");
  expect(modifiers).toContain("if (panel == Panel.Closed && (holding(Key.Alt) || meta and KeyEvent.META_ALT_ON != 0) && altSymSwitches) {");
  expect(modifiers).toContain("shiftWord && (holding(Key.Shift) || meta and KeyEvent.META_SHIFT_ON != 0) -> Backspace.Word");
  const reset = readFileSync(join(root, "android/kotlin/com/atlas/keyboard/SettingsReset.kt"), "utf8");
  expect(reset).toContain('"shift_backspace_word"');
  expect(reset).toContain('"alt_sym_next_keyboard"');
  const adapters = readFileSync(join(root, "android/kotlin/com/vandam/ink/AppNativeAdapters.kt"), "utf8");
  for (const name of ["shiftBackspaceWord", "altSymNextKeyboard"]) {
    expect(adapters).toContain(`.put("${name}", TypingSettings.${name}(activity))`);
    expect(adapters).toContain(`"${name}" -> TypingSettings.${name}(activity, value as Boolean)`);
  }
});

test("both are switches in Settings > Shortcuts, and the boring guide says each can be turned off", () => {
  const settings = readFileSync(join(root, "app/settings/index.tsx"), "utf8");
  const shortcuts = settings.slice(settings.indexOf('<Section title="Shortcuts">'), settings.indexOf("</Section>", settings.indexOf('<Section title="Shortcuts">')));
  expect(shortcuts).toContain("<Toggle label={shiftBackspaceLabel} subtitle={shiftBackspaceNote} value={settings.shiftBackspaceWord}");
  expect(shortcuts).toContain("<Toggle label={altSymLabel} subtitle={altSymNote} value={settings.altSymNextKeyboard}");
  const names = readFileSync(join(root, "lib/setting-names.ts"), "utf8");
  expect(names).toContain('shiftBackspaceLabel = "Hold Shift + Backspace to delete a word"');
  expect(names).toContain('altSymLabel = "Hold Alt + Sym for the next keyboard"');
  const guide = readFileSync(join(root, "app/settings/guide-boring.tsx"), "utf8");
  expect(guide).toContain("Backspace while you hold Shift");
  expect(guide).toContain("Hold Alt and press Sym");
  expect(guide).toContain("Turn off Hold Shift + Backspace to delete a word in Settings");
  expect(guide).toContain("Turn off Hold Alt + Sym for the next keyboard in Settings");
  // Off can't leave anyone without another keyboard: Android's own switcher still works, and Settings says so.
  expect(names).toContain("Android's own keyboard switcher still works");
});
