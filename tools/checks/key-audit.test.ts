// Key audit (owner, 2026-11-20: "i suspect the back buttons or some keys might not have sent properly by the kyboard"). Each
// hole the audit found stays closed: Back's press and release decided together, a Back waiting for a hide that never comes,
// a hidden picker or menu taking Back, the word menu's Back reaching the app, a queued system key, a queued release whose
// press went to the app, and the touchpad hold's endless wait for a second Back.
import { expect, test } from "bun:test";

const root = new URL("../../", import.meta.url);
const read = (path: string) => Bun.file(new URL(path, root)).text();
const ime = () => read("android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt");
const between = (text: string, from: string, to: string) => {
  const start = text.indexOf(from);
  expect(start).toBeGreaterThanOrEqual(0);
  const end = text.indexOf(to, start + from.length);
  expect(end).toBeGreaterThan(start);
  return text.slice(start, end);
};

test("Back's release follows its press: kept with it, or the app's with it", async () => {
  const text = await ime();
  const up = between(text, "override fun onKeyUp(", "private fun switchKeyboardIfAsked");
  expect(up).toContain("keyCode == KeyEvent.KEYCODE_BACK && backKept");
  expect(up).not.toContain("keyCode == KeyEvent.KEYCODE_BACK && editing && isInputViewShown) {");
  expect(text.match(/backKept = true/g)?.length).toBe(2);
});

test("a kept Back whose keyboard the app hid during the push sends nothing more", async () => {
  // YouTube, ChatGPT, Smart Launcher and WhatsApp hide the keyboard on Back's push; sending the app a back at the release made one
  // press hide the keyboard and go back too (owner's recording, 2026-11-21: "the back straight up just hides the keyboard and
  // immediately back off in the app"). The owner's rule: with the keyboard up, it hides, nothing else happens.
  const up = between(await ime(), "override fun onKeyUp(", "private fun switchKeyboardIfAsked");
  const marker = 'keyAudit("Back released after the keyboard hid: nothing more, the app stays")';
  expect(up).toContain(marker);
  expect(up.slice(up.indexOf(marker)).slice(0, 160)).not.toContain("sendBack");
});

test("Back with the keyboard shown only hides it: no back is sent after the hide", async () => {
  // Owner, 2026-11-21: "after i released my back and the keyboard is away, a software back is issued". Hide only.
  const text = await ime();
  expect(text).not.toContain("passingBack");
  expect(text).not.toContain("backHideMissed");
  expect(between(text, "override fun onWindowHidden()", "updateHold()")).not.toContain("sendBack");
  const up = between(text, "override fun onKeyUp(", "private fun switchKeyboardIfAsked");
  const hide = up.slice(up.indexOf('keyAudit("Back: hiding the keyboard, the app stays")'));
  expect(hide.slice(0, 200)).toContain("requestHideSelf(0)");
  expect(hide.slice(0, 200)).not.toContain("sendBack");
});

test("a hidden switcher, accents or menu doesn't take Back, and the word menu's Back stays in the app", async () => {
  const text = await ime();
  expect(text).toContain("KEYCODE_BACK && choicesMenuOpen && isInputViewShown");
  expect(text).toContain("KEYCODE_BACK && openPicker().let { it != null && it !is LanguagePicker.Choices } && isInputViewShown");
  expect(text).toContain("KEYCODE_BACK && editing && isInputViewShown && menuOpen");
  const hidden = between(text, "override fun onWindowHidden()", "updateHold()");
  expect(hidden).toContain("it is LanguagePicker.Switch || it is LanguagePicker.Accents");
  expect(hidden).toContain("closePicker()");
});

test("keys waiting for the app: a system key goes on, and only a kept press's release waits", async () => {
  const text = await ime();
  expect(text).toContain("if (pendingInput.isNotEmpty() && carried.isSystem) {");
  expect(text).toContain("if (pendingInput.isNotEmpty() && consumedKeys.remove(keyCode)) {");
});

test("the touchpad hold's wait for a second Back ends", async () => {
  const text = await ime();
  expect(text).toContain("main.postDelayed(letGoExpired, LET_GO_MILLIS)");
  expect(await read("android/kotlin/com/titankeys/keyboard/TouchpadHold.kt")).toContain("fun letGoExpired()");
});

test("Back with an engine's code pending clears it as a Sym tap does, never falling back to the three suggestions", async () => {
  // Owner, 2026-11-21: "pressing back on chinese puts it to show three suggestions which is wrong" / "just clear the code, so
  // identical to what sym does now".
  const text = await ime();
  const down = between(text, "private fun keyDown(", "override fun onKeyUp(");
  const back = down.slice(down.indexOf("KEYCODE_BACK && codeOnBar() && isInputViewShown"));
  expect(back.slice(0, 200)).toContain("clearCode()");
  expect(down.indexOf("KEYCODE_BACK && codeOnBar()")).toBeLessThan(down.indexOf("KEYCODE_BACK && choicesMenuOpen"));
  expect(down).not.toContain("KEYCODE_BACK && openPicker() != null");
  expect(down).toContain("it !is LanguagePicker.Choices");
});

test("a Back right after one the keyboard handled is its replay, and is kept with its release", async () => {
  // Owner, 2026-11-21: Back cleared the code, then Key Mapper's replay of the same press hid the keyboard.
  const text = await ime();
  const down = between(text, "private fun keyDown(", "override fun onKeyUp(");
  expect(down.indexOf("BACK_ECHO_MILLIS")).toBeGreaterThan(0);
  expect(down.indexOf("BACK_ECHO_MILLIS")).toBeLessThan(down.indexOf("KEYCODE_BACK && codeOnBar()"));
  const up = between(text, "override fun onKeyUp(", "private fun switchKeyboardIfAsked");
  expect(up).toContain("if (keyCode == KeyEvent.KEYCODE_BACK) backHandledAt = SystemClock.uptimeMillis()");
  expect(up.indexOf("KEYCODE_BACK && backEcho")).toBeLessThan(up.indexOf("KEYCODE_BACK && backKept"));
});

test("in Smart Launcher's field, Back with the keyboard up is the keyboard's, and so is its replay", async () => {
  // Owner, 2026-11-21: "on smart launcher it didn't hide the keyboard it went back to the home page".
  const watched = between(await ime(), "private fun watchedKey(", "/**");
  const back = watched.slice(watched.indexOf("Smart Launcher takes Back before the keyboard too"));
  expect(back.slice(0, 700)).toContain("SystemClock.uptimeMillis() - backHandledAt < BACK_ECHO_MILLIS");
  expect(back.slice(0, 700)).toContain("if (down) onKeyDown(code, event) else onKeyUp(code, event)");
});

test("Chinese never falls back to the three-word bar: a closed picker gives its words back to the engine's row", async () => {
  // Owner, 2026-11-21: "the three word suggestion shd not show up in the first place no matter the situation".
  const close = between(await ime(), "private fun closePicker()", "private val choicesOpen");
  expect(close).toContain("ChineseKeys.usesBar(languages.current) && barWords.isNotEmpty()");
  expect(close).toContain("offerBarWords(barWords)");
  expect(close).toContain("reofferingBar = true");
});
