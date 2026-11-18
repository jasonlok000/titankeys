import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { type FrontState, frontChoice, isTitan2Elite, otherPhone, setupToDo } from "../../lib/front";

// The front page's choice (#70, #71) and the other-phone warning (#75).
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");

/** A Titan 2 Elite with setup done, the guide seen and the warning never needed. */
const done: FrontState = {
  setupToDo: false, guideDue: false, touchpadFunction: "yes", device: "Titan_2", fakeOtherPhone: false, phoneWarningSeen: false,
};

test("setup done: the front page, which is the playground", () => {
  expect(frontChoice(done)).toBe("front");
});

test("a part to do: setup, and before the guide's first opening too", () => {
  expect(frontChoice({ ...done, setupToDo: true })).toBe("setup");
  // Setup comes before the guide's first opening, which then opens from it by itself.
  expect(frontChoice({ ...done, setupToDo: true, guideDue: true })).toBe("setup");
});

test("the front page and the playground decide setup by one definition, so they can't disagree (review, M2)", () => {
  // The phone answers setupToDo from PhoneSetup.toDo, the playground's own list: GuideSteps' setupToAsk without the guide-only
  // parts. A 2.1.0 user who updates with contacts unanswered and Sym and fn not set lands on the playground, which asks for
  // neither, so ✕ or Back from it lands on the front page again, which is the playground: no loop.
  const adapter = read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
  expect(adapter).toContain('.put("setupToDo", PhoneSetup.toDo(activity))');
  const phone = read("android/kotlin/com/titankeys/keyboard/PhoneSetup.kt");
  expect(phone).toContain("fun toDo(context: Context) = missing(context).isNotEmpty()");
  expect(phone).toContain("fun missing(context: Context, guide: Boolean = false) =");
  expect(phone).toContain("setupToAsk({ done(context, it) }, BackDisclosureActivity.declined(context), guide)");
  const steps = read("android/kotlin/com/titankeys/keyboard/GuideSteps.kt");
  expect(steps).toContain("it.guideOnly && !guide || done(it) || it == SetupPart.Back && backDeclined");
  // Sym and fn, and contacts, are the guide's own: only the guide's setup before the guide asks them.
  for (const part of ["Keys(", "Contacts("]) {
    const at = steps.indexOf(`    ${part}"`);
    expect({ part, guideOnly: steps.slice(at, steps.indexOf("),\n", at)).includes("guideOnly = true") }).toEqual({ part, guideOnly: true });
  }
  const guide = read("android/kotlin/com/titankeys/keyboard/GuideActivity.kt");
  expect(guide).toContain("if (repairing) repairMissing() else PhoneSetup.missing(this, guide = !playground)");
  expect(guide).not.toMatch(/PhoneSetup\.missing\(this\)/);
  // The front page has no list of its own any more.
  expect(read("lib/front.ts")).not.toContain("!enabled || !selected");
  // Whoever skipped them, or never saw them, finds them in Settings > Setup.
  const setup = read("app/settings/setup.tsx");
  expect(setup).toContain("symKey");
  expect(setup).toContain("contactsStep");
});

test("a 2.1.0 user who updates: setup done as the front page has it, so the playground, and leaving it lands there again", () => {
  // Contacts unanswered and Sym and fn not set don't count for the front page (the phone says setupToDo false).
  const updated = { ...done, setupToDo: false, guideDue: false };
  expect(frontChoice(updated)).toBe("front");
  // ✕ or Back from the playground brings the app's window to the front, which reads the same state: still the playground.
  for (let leave = 0; leave < 3; leave++) expect(frontChoice(updated)).toBe("front");
});

test("Back declined with Not now counts as done, as the guide's own setup counts it", () => {
  expect(setupToDo({ setupToDo: false })).toBe(false);
  expect(read("android/kotlin/com/titankeys/keyboard/GuideSteps.kt")).toContain("it == SetupPart.Back && backDeclined");
  expect(read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt"))
    .toContain('.put("backDeclined", BackDisclosureActivity.declined(activity))');
});

test("with setup done, the guide opens once by itself, then never again", () => {
  expect(frontChoice({ ...done, guideDue: true })).toBe("guide");
  // The guide marks itself shown as it opens (GuideActivity.start), so any way out of it lands on the front page.
  expect(read("android/kotlin/com/titankeys/keyboard/GuideActivity.kt"))
    .toContain("activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean(SHOWN, true).apply()");
});

test("the front page reads the state again when its window comes to the front, and once as it first shows (#71)", () => {
  const page = read("app/index.tsx");
  const hook = page.slice(page.indexOf("export function useFreshState()"), page.indexOf("\n}\n", page.indexOf("export function useFreshState()")));
  expect(hook).toContain("void state.refresh();");
  expect(hook).toContain("return onAppWindow(() => void state.refresh());");
  for (const name of ["function SetupStart(", "function GuideStart("]) {
    const start = page.indexOf(name);
    expect({ name, fresh: page.slice(start, page.indexOf("\n}\n", start)).includes("useFreshState();") }).toEqual({ name, fresh: true });
  }
  // Home decides by lib/front.ts alone, and the playground's own window listener is still there.
  expect(page).toContain("const choice = frontChoice(result.data);");
  expect(page).not.toContain("!enabled || !selected");
  expect(page).toContain("const stopWindow = onAppWindow(() => { if (here && !leaving) open(); });");
});

test("a Titan 2 Elite is the touchpad's property and the device, the pair the touchpad code needs", () => {
  expect(isTitan2Elite({ touchpadFunction: "yes", device: "Titan_2" })).toBe(true);
  // Faked phones: a Titan 2 without the touchpad, another Unihertz, a plain phone, and unreadable properties.
  expect(isTitan2Elite({ touchpadFunction: "", device: "Titan_2" })).toBe(false);
  expect(isTitan2Elite({ touchpadFunction: "no", device: "Titan_2" })).toBe(false);
  expect(isTitan2Elite({ touchpadFunction: "yes", device: "Titan" })).toBe(false);
  expect(isTitan2Elite({ touchpadFunction: "yes", device: "titan_2" })).toBe(false);
  expect(isTitan2Elite({ touchpadFunction: "", device: "husky" })).toBe(false);
  expect(isTitan2Elite({ touchpadFunction: "", device: "" })).toBe(false);
  // The same pair as the touchpad: Touchpad.kt checks the device, TypingSettings the property.
  expect(read("android/kotlin/com/titankeys/keyboard/Touchpad.kt")).toContain('Build.DEVICE != "Titan_2"');
  expect(read("android/kotlin/com/titankeys/engine/TypingSettings.java")).toContain('"ro.agui.touchpad_function"');
  const adapter = read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
  expect(adapter).toContain('.put("touchpadFunction", systemProperty("ro.agui.touchpad_function"))');
  expect(adapter).toContain('.put("device", Build.DEVICE ?: "")');
});

test("the other-phone warning shows once, before setup, and never blocks", () => {
  const other = { ...done, device: "husky", touchpadFunction: "" };
  expect(otherPhone(other)).toBe(true);
  expect(frontChoice({ ...other, setupToDo: true, guideDue: true })).toBe("warning");
  // Once seen, everything goes on as on the Elite.
  expect(frontChoice({ ...other, phoneWarningSeen: true, setupToDo: true, guideDue: true })).toBe("setup");
  expect(frontChoice({ ...other, phoneWarningSeen: true })).toBe("front");
  // Never on the Elite, seen or not.
  expect(frontChoice({ ...done, setupToDo: true })).toBe("setup");
  // Its one button only marks it seen.
  const page = read("app/index.tsx");
  const warning = page.slice(page.indexOf("function PhoneWarning()"), page.indexOf("\n}\n", page.indexOf("function PhoneWarning()")));
  expect(warning.match(/<Action /g)?.length).toBe(1);
  expect(warning).toContain("onPress={() => void markPhoneWarningSeen()}");
});

test("the debug build's switch fakes another phone, and only there", () => {
  expect(otherPhone({ ...done, fakeOtherPhone: true })).toBe(true);
  expect(frontChoice({ ...done, fakeOtherPhone: true })).toBe("warning");
  const adapter = read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
  expect(adapter).toContain('.put("fakeOtherPhone", AppBuild.kind(activity) == "debug" && appPreferences().getBoolean(FAKE_OTHER_PHONE, false))');
  expect(adapter).toContain('require(AppBuild.kind(activity) == "debug") { "Only TitanKeys Debug can fake another phone" }');
  expect(read("app/settings/setup.tsx")).toContain('{settings.build === "debug" && <Section title="Debug">');
});

test("Settings > Setup: the other-phone line on top, and contacts as an Optional row that says On or Off (#75, #87)", () => {
  const setup = read("app/settings/setup.tsx");
  expect(setup).toContain("{otherPhone(settings) && <Note>{otherPhoneNote}</Note>}");
  expect(setup.indexOf("{otherPhone(settings) &&")).toBeLessThan(setup.indexOf('<Section title="Steps">'));
  const optional = setup.slice(setup.indexOf('<Section title="Optional">'), setup.indexOf("</Section>", setup.indexOf('<Section title="Optional">')));
  expect(optional).toContain("<Field label={contactsStep}");
  expect(optional).toContain("{onOff(settings.contactNames)}");
  expect(optional).not.toContain("status(");
  expect(setup).toContain('export const contactsStep = "Names from my contacts";');
  // The row never makes setup look unfinished.
  const label = setup.slice(setup.indexOf("export const setupLabel"), setup.indexOf("\n", setup.indexOf("export const setupLabel")));
  expect(label).not.toContain("contactNames");
});

test("the bar size page's sample line ends mid-word", () => {
  const page = read("app/settings/bar-text-size.tsx");
  const sample = page.match(/export const barTextSizeSample = "([^"]+)";/)?.[1];
  expect(sample).toBeDefined();
  expect(sample!).toMatch(/[a-z]$/);
});
