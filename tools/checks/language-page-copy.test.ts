import { expect, test } from "bun:test";
import methods from "../../android/assets/im/index.json";
import languages from "../../android/assets/im/languages.json";
import text from "../../tools/im/page-text.json";
import { availableSettings, globalSettings, languagePage, listMethodCount, practiceMethod, practicePrompt, sharedKeyRoles, unavailableNotes } from "../../lib/language-page";
import type { InputMethodEntry } from "../../lib/titankeys";

test("every key role on the language diagrams has an explanation", () => {
  const roles: Record<string, Record<string, string>> = text.roles;
  for (const method of methods) {
    for (const entry of method.guide.keys.roles) {
      expect(roles[entry.key]?.[entry.role]).toBeTruthy();
    }
  }
});

test("enabled language rows show how many of their methods are on", () => {
  expect(listMethodCount(6, 1)).toBe("1 of 6 on");
  expect(listMethodCount(6, 4)).toBe("4 of 6 on");
  expect(listMethodCount(6, 0)).toBeUndefined();
  expect(listMethodCount(1, 1)).toBeUndefined();
});

test("multi-method diagrams only show roles the methods agree on", () => {
  const chinese = methods.filter(method => method.language.id === "zh") as InputMethodEntry[];
  expect(sharedKeyRoles(chinese).map(role => role.key).sort()).toEqual(["backspace", "enter", "shift", "space", "sym"]);
  const english = { id: "en", guide: null } as InputMethodEntry;
  expect(sharedKeyRoles([...chinese, english])).toEqual([]);
  expect(sharedKeyRoles([])).toEqual([]);
});

test("language pages explain every unavailable language setting", () => {
  for (const page of Object.values(languages)) {
    for (const row of page.settings) {
      if (row.level === "language" && !row.available) {
        expect("reason" in row && Object.hasOwn(text.notAvailable, row.reason as string)).toBe(true);
      }
    }
  }
});

test("generated language pages keep global, language and input-method controls at their own levels", () => {
  const chinese = languagePage("zh")!;
  const thai = languagePage("th")!;
  expect(availableSettings(globalSettings, "global").map(row => row.id)).toEqual(["learn"]);
  expect(availableSettings(chinese.settings, "language").map(row => row.id)).toContain("chineseCharacters");
  expect(availableSettings(chinese.settings, "language").map(row => row.id)).not.toContain("lazySounds");
  for (const id of ["yue-yale", "yue-jyutping", "zh-pinyin", "zh-zhuyin", "zh-cangjie", "zh-quick"]) {
    const options = id.startsWith("yue-") ? ["lazySounds", "keyPreview"] : ["keyPreview"];
    expect(availableSettings(chinese.settings, "input-method", id).map(row => row.id)).toEqual(options);
  }
  expect(availableSettings(languagePage("pt")!.settings, "input-method", "pt").map(row => row.id)).toEqual(["variant", "keyPreview"]);
  expect(availableSettings(languagePage("en")!.settings, "input-method", "en").map(row => row.id)).toEqual(["keyPreview"]);
  expect(availableSettings(languagePage("en")!.settings, "language").map(row => row.id)).toContain("spelling");
  for (const method of methods) {
    const shared = availableSettings(languagePage(method.language.id)!.settings, "language").map(row => row.id);
    const specific = availableSettings(languagePage(method.language.id)!.settings, "input-method", method.id);
    expect(specific.some(row => row.id === "keyPreview")).toBe(true);
    expect(shared.includes("spelling")).toBe(false);
    expect(shared.includes("chineseCharacters")).toBe(method.language.id === "zh");
  }
  expect(availableSettings(thai.settings, "language").map(row => row.id)).not.toContain("doubleSpace");
  const notes = unavailableNotes(thai.settings, "Thai");
  expect(notes).toContain("Thai has no full stop, so double Space types two spaces.");
  expect(notes).toContain(`${text.settings.shorthand}: ${text.notAvailable.englishOnly}`);
  expect(notes.join(" ")).not.toContain("{");
  const noFullStop = thai.settings.find(row => row.id === "doubleSpace")!;
  expect(unavailableNotes([noFullStop, noFullStop], "Thai")).toEqual([notes.find(note => note.includes("no full stop"))!]);
});

test("the practice prompt follows the selected enabled method and falls back when it is removed", () => {
  const yale = methods.find(method => method.id === "yue-yale")! as InputMethodEntry;
  const indexed = methods.find(method => method.id === "zh-pinyin")! as InputMethodEntry;
  const pinyin = { ...indexed, guide: { ...indexed.guide, practice: "nihao" } };
  const chosen = practiceMethod([yale, pinyin], pinyin.id);
  expect(chosen?.id).toBe("zh-pinyin");
  expect(practicePrompt(chosen!)).toBe("Try nihao");
  expect(practiceMethod([yale], pinyin.id)?.id).toBe("yue-yale");
  expect(practiceMethod([], pinyin.id)).toBeUndefined();
});
