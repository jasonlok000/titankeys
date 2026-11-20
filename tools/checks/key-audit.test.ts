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

test("a Back that hides the keyboard can't fire later, in another field or app", async () => {
  const text = await ime();
  expect(text).toContain("main.postDelayed(backHideMissed, BACK_HIDE_MILLIS)");
  expect(between(text, "override fun onFinishInput()", "super.onFinishInput()")).toContain("dropWaitingBack(");
  expect(between(text, "override fun onUnbindInput()", "super.onUnbindInput()")).toContain("dropWaitingBack(");
  expect(between(text, "override fun onWindowHidden()", "updateHold()")).toContain("main.removeCallbacks(backHideMissed)");
});

test("a hidden switcher, accents or menu doesn't take Back, and the word menu's Back stays in the app", async () => {
  const text = await ime();
  expect(text).toContain("KEYCODE_BACK && choicesMenuOpen && isInputViewShown");
  expect(text).toContain("KEYCODE_BACK && openPicker() != null && isInputViewShown");
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
