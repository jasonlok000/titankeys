import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// Chinese on the bar (docs/languages.md, "Chinese on the bar"): the owner's design, 2026-11-16. The places and the English
// word are checked in tests/chinese; these keep the keyboard's wiring to the design, where only the phone could run it.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8").replaceAll("\r\n", "\n");
const ime = read("android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt");
const strip = read("android/kotlin/com/titankeys/keyboard/Strip.kt");

/** The body of Kotlin function [name] in [source], up to the next function at the same depth. */
const body = (source: string, name: string) => {
  const start = source.indexOf(`fun ${name}(`);
  expect(start).toBeGreaterThan(-1);
  const open = source.indexOf("{", start);
  let depth = 0;
  for (let i = open; i < source.length; i++) {
    if (source[i] === "{") depth++;
    if (source[i] === "}" && --depth === 0) return source.slice(start, i + 1);
  }
  throw new Error(`no end to ${name}`);
};

test("the code floats over the app, above the bar's left edge, only while a code is typed", () => {
  expect(body(strip, "drawEffects")).toContain("drawCodeLine(canvas, layer)");
  const line = body(strip, "drawCodeLine");
  expect(line).toContain("picker.lead.isEmpty()");
  expect(line).toContain("val top = y - height");
  // The bar no longer draws the code over Q and W.
  expect(body(strip, "drawPicker")).not.toContain("picker.lead");
});

test("the mark sits over Q and the more cue over P", () => {
  const picker = body(strip, "drawPicker");
  expect(picker).toContain("drawOnKey(canvas, picker.mark, ChineseRow.MARK, baseline)");
  expect(picker).toContain("drawOnKey(canvas, ChineseRow.MORE_CUE, ChineseRow.MORE, baseline)");
  expect(body(ime, "engineState")).toContain("languages.current.label");
});

test("Space picks the first choice: a resting finger doesn't change that", () => {
  const key = body(ime, "engineKey");
  expect(key).toContain("KeyEvent.KEYCODE_SPACE -> if (alt) return false else engine.space()");
  expect(key).not.toContain("pickerMark");
  expect(ime).not.toMatch(/marked >= 0\) engine\.pick/);
});

test("a tap on the screen picks the choice under it", () => {
  expect(ime).toContain("val choice = if (open is LanguagePicker.Choices) open.itemAt(index) else index");
  expect(body(strip, "onTouchEvent")).toContain("pickerTap?.invoke(item)");
});

test("Shift + the key under a choice picks it, and Shift alone does nothing special", () => {
  const key = body(ime, "engineKey");
  expect(key).toContain("if (shifted && choices != null && item >= 0)");
  expect(key).toContain("engine.key(printedLetter(event.keyCode))");
  // Shift's own bar doesn't cover the choices in Chinese.
  expect(body(ime, "showModifierBar")).toContain("ModifierBar.SHIFT || it == ModifierBar.CAPS");
});

test("holding the key under a choice picks it, and hold for symbols is off in Chinese", () => {
  expect(body(ime, "canHold")).toContain("if (ChineseKeys.usesBar(languages.current)) return false");
  expect(body(ime, "holdToPick")).toContain("TypingSettings.holdSymbolMillis(this)");
  // The letter the press typed goes again before the pick.
  expect(ime).toMatch(/engine\.backspace\(\)\n\s+pick\(held\.choices, held\.item\)/);
  expect(body(ime, "chineseKeyUp")).toContain("cancelHoldPick()");
});

test("a flick up picks, and the row never pages", () => {
  const swipes = body(ime, "pageChoices");
  expect(swipes).toContain("ChineseKeys.flickColumn(");
  expect(swipes).not.toContain("engine.page(");
});

test("the menu's P counts its pages from the first", () => {
  const menu = read("android/kotlin/com/titankeys/keyboard/ChoicesMenu.kt");
  expect(menu).toContain("val want = maxOf(starts.last() + 2 * PER_PAGE, COUNTED)");
  expect(menu).toContain("val COUNTED = 10 * PER_PAGE");
});

test("only holding P opens the bigger menu", () => {
  const callers = [...ime.matchAll(/openChoicesMenu\(\)/g)].length;
  // Its definition and the one call, in P's hold.
  expect(callers).toBe(2);
  expect(ime).toMatch(/private val pHeld = Runnable \{[\s\S]*?openChoicesMenu\(\)/);
  expect(body(ime, "chineseKey")).toContain("KeyEvent.KEYCODE_P");
  // Before the hold, P is the letter p, ahead of any key that comes first.
  expect(body(ime, "typeWaitingP")).toContain("it.key('p')");
});

test("the menu: a press picks, Enter and Backspace turn pages, Sym is the ✕, Back closes", () => {
  const keys = body(ime, "choicesMenuKey");
  expect(keys).toContain("KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> if (menu.next())");
  expect(keys).toContain("KeyEvent.KEYCODE_DEL -> if (menu.previous())");
  expect(keys).toContain("KeyEvent.KEYCODE_SYM -> closeChoicesMenu()");
  expect(keys).toContain("pickFromMenu(index)");
  expect(ime).toContain("if (keyCode == KeyEvent.KEYCODE_BACK && choicesMenuOpen) {");
  // Swipes up and down turn its pages, the Sym panel's way.
  expect(body(ime, "pageChoices")).toContain("if (EmojiPages.forward(down)) choicesMenu?.next() else choicesMenu?.previous()");
  // It sits in the Sym panel's place, and the bar hides under it as under the panel.
  expect(ime).toContain("addView(keys)\n            addView(choices)");
  expect(ime).toContain("strip?.visibility = if (panelOpen || choicesMenuOpen) View.GONE else View.VISIBLE");
});

test("Enter types the code as letters, Backspace takes the last letter", () => {
  // Enter with a code pending is the engine's, before the field's own Enter (phone check, 2026-11-16: a new line went in).
  const keys = body(ime, "key");
  expect(keys.indexOf("if (enteringCode(event))")).toBeGreaterThan(-1);
  expect(keys.indexOf("if (enteringCode(event))")).toBeLessThan(keys.indexOf("enter(event)) return true"));
  expect(body(ime, "enteringCode")).toContain("openPicker() is LanguagePicker.Choices");
  expect(read("android/kotlin/com/titankeys/rime/RimeEngine.kt")).not.toContain("send(RimeKeys.RETURN)");
  const key = body(ime, "engineKey");
  expect(key).toContain("KeyEvent.KEYCODE_DEL -> if (alt) return false else engine.backspace()");
  expect(key).toContain("KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> if (alt) return false else engine.enter()");
});

test("full-width punctuation by default, plain with the setting, and Alt's marks go to Rime", () => {
  const settings = read("android/kotlin/com/titankeys/engine/TypingSettings.java");
  expect(settings).toContain(`getBoolean("chinese_plain_punctuation", false)`);
  expect(read("android/kotlin/com/titankeys/keyboard/SettingsReset.kt")).toContain(`"chinese_plain_punctuation"`);
  expect(read("android/kotlin/com/titankeys/keyboard/Languages.kt"))
    .toContain("engine.option(ChineseKeys.PLAIN_PUNCTUATION, TypingSettings.chinesePlainPunctuation(context))");
  expect(body(ime, "engineKey")).toContain("if (alt && !chinese) return false");
});

test("an English word is offered only in Chinese, never first", () => {
  const english = body(ime, "englishFor");
  expect(english).toContain("ChineseKeys.isChinese(languages.current)");
  expect(english).toContain("EnglishOffer.offers(");
  expect(read("android/kotlin/com/titankeys/keyboard/Chinese.kt")).toContain("const val PLACE = 1");
});
