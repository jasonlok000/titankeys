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
  expect(line).toContain("if ((lead.isEmpty() && next.isEmpty()) || !isShown) return");
  // At the bar's size and full brightness, on black with a soft orange glow; no joined tab (owner, 2026-11-17).
  expect(line).toContain("val above = y - height - dp(CODE_GAP_DP)");
  expect(line).toContain("codeText.textSize = wordPixels()");
  expect(line).toContain("codeText.color = look.ink");
  expect(line).toContain("menuPaint.setShadowLayer(");
  expect(strip).not.toContain("CODE_TAB");
});

test("the mark sits over Q and the more cue over P", () => {
  const picker = body(strip, "drawPicker");
  expect(picker).toContain("drawOnKey(canvas, picker.mark, ChineseRow.MARK, baseline)");
  expect(picker).toContain("drawOnKey(canvas, ChineseRow.MORE_CUE, ChineseRow.MORE, baseline)");
  // Orange while a code is typed: Shift opens more, though Shift isn't under it.
  expect(picker).toContain("pickerText.color = if (picker.lead.isNotEmpty()) look.accent else look.muted");
  // The language's mark where it has one input method on, else the input method's (owner, 2026-11-17).
  expect(body(ime, "engineState")).toContain("markLabel()");
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

test("Shift + a key and holding a key don't pick (owner, 2026-11-17: abandoned); a letter is the letter, Shift or not", () => {
  const key = body(ime, "engineKey");
  expect(key).toContain("else -> if (chinese && letter && !alt) engine.key(printedLetter(event.keyCode)) else {");
  expect(key).not.toContain("shifted");
  expect(ime).not.toContain("holdToPick");
  // Shift's own bar doesn't cover the choices in Chinese: Shift shows only where it changes what a key types.
  expect(body(ime, "showModifierBar")).toContain("languages.current.shiftMatters");
});

test("hold for symbols is back in Chinese: the letter leaves the code, the symbol goes through the engine", () => {
  expect(body(ime, "canHold")).not.toContain("usesBar");
  expect(body(ime, "replaceHeldLetter")).toContain("heldSymbolThroughEngine(engine, text)");
  const held = body(ime, "heldSymbolThroughEngine");
  expect(held).toContain("engineState(engine.backspace())");
  expect(held).toContain("engine.key(text[0])");
});

test("a swipe up opens the bigger menu, a page turn's length, each logged; no flick picks, and the row never pages", () => {
  const swipes = body(ime, "pageChoices");
  expect(swipes).toContain("val swipe = ChineseKeys.swipe(across, down, choicesMenuOpen, choicesMenu?.onFirstPage == true)");
  expect(swipes).toContain("ChineseKeys.Swipe.CloseMenu -> closeChoicesMenu()");
  // In every input method with choices on the bar (owner, 2026-11-17: "Also for other relevant language"); Korean has none.
  expect(swipes).toContain("ChineseKeys.Swipe.OpenMenu -> if (open.more) openChoicesMenu()");
  expect(swipes).not.toContain("usesBar");
  // The open menu takes keys in any of them, but Shift opens it only in Chinese and Japanese, where it types no capitals.
  const keys = body(ime, "chineseKey");
  expect(keys).toContain("if (!ChineseKeys.usesBar(languages.current) && !choicesMenuOpen) return false");
  expect(keys).toContain("val overflow = !choicesMenuOpen && (openPicker() as? LanguagePicker.Choices)?.more == true");
  expect(keys).toContain("overflow && ChineseKeys.usesBar(languages.current) -> openChoicesMenu()");
  for (const f of ["Telex.kt", "Rules.kt"]) {
    const engine = read(`android/kotlin/com/titankeys/keyboard/${f}`);
    expect(engine).toContain("override fun choices(start: Int, count: Int): List<String> {");
    expect(engine).toContain("override fun pickAt(index: Int): EngineState {");
  }
  expect(swipes).toContain("choicesLog(\"swipe:");
  expect(swipes).not.toContain("pick(");
  expect(swipes).not.toContain("engine.page(");
  // The owner dropped flick-to-pick (2026-11-17): no shorter distance for the bar's choices.
  const chinese = read("android/kotlin/com/titankeys/keyboard/Chinese.kt");
  expect(chinese).not.toContain("FLICK_KEYS");
  expect(chinese).not.toContain("flickColumn");
  expect(body(ime, "setPaging")).not.toContain("pageKeys");
  const contact = read("android/kotlin/com/titankeys/keyboard/Contact.kt");
  expect(contact).toContain("val swiped = touching.travel >= PAGE_KEYS && last.time - first.time <= PAGE_MILLIS");
  expect(contact).not.toContain("var pageKeys");
  expect(contact).toContain("pageLog?.invoke(");
});

test("the menu's P counts its pages from the first", () => {
  const menu = read("android/kotlin/com/titankeys/keyboard/ChoicesMenu.kt");
  expect(menu).toContain("val want = maxOf(starts.last() + 2 * PER_PAGE, COUNTED)");
  expect(menu).toContain("val COUNTED = 10 * PER_PAGE");
});

test("either Shift, or a tap on the more cue, opens the bigger menu with choices on the bar; Shift closes it; Sym is the panel", () => {
  const keys = body(ime, "chineseKey");
  // Either Shift, with a code typed or suggestions on the bar, opens and closes it (owner, 2026-11-17).
  expect(keys).toContain("if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {");
  expect(keys).toContain("choicesMenuOpen -> closeChoicesMenu()");
  // Its repeats and release are the menu's, so Shift itself never comes on.
  expect(keys).toContain("if (event.repeatCount > 0) return menuShift == keyCode");
  expect(body(ime, "chineseKeyUp")).toContain("if (keyCode != menuShift) return false");
  expect(body(ime, "onKeyUp")).toContain("if (editing && chineseKeyUp(keyCode)) return true");
  // Sym is always the Sym panel; with the menu open, it's the menu's ✕.
  expect(keys).not.toContain("KEYCODE_SYM");
  expect(ime).not.toContain("symOpensChoices");
  expect(ime).not.toContain("symCanWait");
  expect(body(ime, "choicesMenuKey")).toContain("KeyEvent.KEYCODE_SYM -> closeChoicesMenu()");
  // A symbol picked from the panel types the code's first choice first; suggestions with no code are cleared.
  expect(body(ime, "typePanelItem")).toContain("engineState(if (choices.state.composing) it.space() else it.reset())");
  expect(keys).not.toContain("KEYCODE_P");
  expect(ime).toContain("words.moreTap = { enqueue { if (openPicker() is LanguagePicker.Choices && !choicesMenuOpen) openChoicesMenu() } }");
  expect(body(strip, "onTouchEvent")).toContain("moreTap?.invoke()");
  expect(ime).not.toContain("pHeld");
});

test("the row shows no hover; the bigger menu does", () => {
  expect(body(ime, "markMenu")).toContain("if (open is LanguagePicker.Choices) {\n                strip.pickerMark = -1");
  expect(body(ime, "markMenu")).toContain("menu.hover = key");
});

test("the mark shows whenever an input method that isn't English types, the idle bar included", () => {
  expect(body(ime, "showInputMark")).toContain("strip?.inputMark = if (languages.current.isEnglish) \"\" else markLabel()");
  expect(body(ime, "inputMethodChanged")).toContain("showInputMark()");
  expect(body(strip, "dispatchDraw")).toContain("drawInputMark(canvas)");
});


test("engines get ready in the background, before the switch", () => {
  const languages = read("android/kotlin/com/titankeys/keyboard/Languages.kt");
  expect(body(languages, "refresh")).toContain("warmUp()");
  expect(body(languages, "warmUp")).toContain("it.warm(m, java.io.File(context.filesDir, \"engines/$name\"))");
  // Rime's warm selects its input method's schema, so a switch to it has nothing left to do (owner, 2026-11-17: "I'm still
  // seeing loading for Yale"), but only while no other engine has selected one: that one types, and keeps the session.
  const warm = body(read("android/kotlin/com/titankeys/rime/RimeEngine.kt"), "warm");
  expect(warm).toContain("if (open && id != null && schema == null && claimed(rime, this)) {");
  expect(warm).toContain("if (select(id))");
});

test("the menu: a press picks, Enter and Backspace turn pages, each key logged, Sym is the ✕, Back closes", () => {
  const keys = body(ime, "choicesMenuKey");
  expect(keys).toContain("choicesLog(\"menu key $keyCode");
  expect(keys).toContain("KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> if (menu.next())");
  expect(keys).toContain("KeyEvent.KEYCODE_DEL -> if (menu.previous())");
  expect(keys).toContain("KeyEvent.KEYCODE_SYM -> closeChoicesMenu()");
  expect(keys).toContain("pickFromMenu(index)");
  expect(ime).toContain("if (keyCode == KeyEvent.KEYCODE_BACK && choicesMenuOpen) {");
  // Swipes up and down turn its pages, the Sym panel's way (EmojiPages.forward: up goes forward).
  expect(body(ime, "pageChoices")).toContain("ChineseKeys.Swipe.NextPage -> if (choicesMenu?.next() == true)");
  expect(body(ime, "pageChoices")).toContain("ChineseKeys.Swipe.PageBefore -> if (choicesMenu?.previous() == true)");
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

test("Enter never sends while a code is pending or the bigger menu is open: the Back service keeps it for the keyboard", () => {
  // Owner, 2026-11-17: "Enter with the chinese overlay expanded still sends", "the keycode didn't get sent but the message is sent out".
  expect(ime).toContain("private val engineBuilding get() = choicesMenuOpen || (picker as? LanguagePicker.Choices)?.state?.composing == true");
  expect(body(ime, "updateKeyWatch")).toContain("val forPanel = panelOpen || panelKeys.holding || engineBuilding");
  expect(body(ime, "watchedKey")).toContain("panelKeys.takes(event.keyCode, down, event.repeatCount, modifiers.panel, engineBuilding)");
  expect(body(ime, "watchedKey")).toContain("else if (panelOpen || engineBuilding) onKeyDown(event.keyCode, event)");
  // The watch follows the picker and the menu as they open and close.
  for (const name of ["showPicker", "closePicker", "openChoicesMenu", "closeChoicesMenu"]) expect(body(ime, name)).toContain("updateKeyWatch()");
  // A picker shown again as it was still counts as the bar's: equal, not only the same object.
  expect(body(ime, "openPicker")).toContain("if (strip?.notice == open.notice) return open");
});

test("a Space touch never picks a code: only a press does, so a steer or a select keeps it on the bar (owner, 2026-11-17)", () => {
  expect(ime).toContain(
    'if (key == " " && engine != null && (openPicker() as? LanguagePicker.Choices)?.state?.composing == true) return',
  );
  // The steer and the select themselves never reach the engine.
  for (const name of ["startWordDrag", "registerGesture"]) expect(body(ime, name)).not.toContain("engine");
});

test("a flick with a code goes through the engine before deleting from the editor", () => {
  const flick = body(ime, "outcome");
  const hold = body(ime, "deleteHeldWord");
  expect(flick).toContain("engine?.flickComposition(choices.state)");
  expect(hold).toContain("languages.engine?.flickComposition(choices?.state)");
});

test("the mark over Q takes a tap over the choices row too, not only on the idle bar (owner, 2026-11-17: 耶 did nothing)", () => {
  const right = body(strip, "markTouchRight");
  expect(right).toContain("markRight > 0 -> markRight");
  expect(right).toContain("(notice as? Notice.Picker)?.mark?.isNotEmpty() == true -> menuColumn(ChineseRow.MARK).second");
  // The tap is tested before the row's own items, so the mark never falls through to a choice or to nothing.
  const touch = body(strip, "onTouchEvent");
  expect(touch.indexOf("markTouchRight()")).toBeGreaterThan(-1);
  expect(touch.indexOf("markTouchRight()")).toBeLessThan(touch.indexOf("notice is Notice.Picker && pickerTap != null"));
});
