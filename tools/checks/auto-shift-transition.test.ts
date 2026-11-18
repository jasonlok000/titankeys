import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

const root = join(import.meta.dir, "../..");
const read = (name: string) => readFileSync(join(root, name), "utf8");
const ime = read("android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt");

function body(name: string) {
  return ime.match(new RegExp("    (?:override|private) fun " + name + "\\([\\s\\S]*?(?=\\n    (?:override|private) fun |$)"))?.[0] ?? "";
}

function ordered(source: string, ...steps: string[]) {
  let previous = -1;
  for (const step of steps) {
    const current = source.indexOf(step, previous + 1);
    expect(current, step).toBeGreaterThan(previous);
    previous = current;
  }
}

test("a field gets its own automatic Shift before replaying saved physical keys", () => {
  const start = body("onStartInput");
  ordered(start, "samePosition", "refreshesShift == true", "loadGate.takeKeys(pendingInput)");
  ordered(start,
    "readSettings()",
    "composer.start(field, info.initialSelStart, info.initialSelEnd)",
    "refreshAutoShiftBeforeKeys()",
    "pendingInput.addAll(held)",
    "drainInput()");
});

test("a method load samples the editor at the input queue stop before replay", () => {
  ordered(body("inputMethodChanged"), "readSettings()", "composer.wordsChanged()", "loadEnded()");
  ordered(body("loadEnded"), "loadGate.open(", "stop.action = ::refreshAutoShiftBeforeKeys", "stop.ready = true", "drainInput()");
});

test("capital state is sampled ahead of the waiting keys and after older edits", () => {
  const refresh = body("refreshAutoShiftBeforeKeys");
  expect(refresh).toContain("showModifiers(beforeQueuedInput = true)");
  ordered(refresh, "pendingInput.addFirst(stop)", "composer.whenInputIdle", "pendingInput.firstOrNull() !== stop", "stop.ready = true", "drainInput()");
  expect(body("updateAutoShift")).toContain("!beforeQueuedInput && pendingInput.isNotEmpty()");
  expect(body("updateAutoShift")).toContain("languages.current.rules.capitalise && TypingSettings.autoCapitaliseIn(this, languages.languageId)");
  expect(body("sentenceStartHere")).toContain("if (!editing || field.exact || !field.sentenceCapitalisation) return false");
});

test("Playground requests sentence capitals and the debug gesture field adds them only on request", () => {
  const guide = read("android/kotlin/com/titankeys/keyboard/GuideActivity.kt");
  expect(guide).toMatch(/inputType = InputType\.TYPE_CLASS_TEXT or InputType\.TYPE_TEXT_FLAG_MULTI_LINE or InputType\.TYPE_TEXT_FLAG_CAP_SENTENCES/);
  const debug = read("android/debug/recorder/SpaceGestureActivity.kt");
  expect(debug).toMatch(/inputType = InputType\.TYPE_CLASS_TEXT or InputType\.TYPE_TEXT_FLAG_MULTI_LINE\s/);
  expect(debug).toMatch(/if \(intent\.getBooleanExtra\("sentence_capitals", false\)\) \{\s+inputType = inputType or InputType\.TYPE_TEXT_FLAG_CAP_SENTENCES\s+}/);
  expect(debug.match(/TYPE_TEXT_FLAG_CAP_SENTENCES/g)).toHaveLength(1);
});
