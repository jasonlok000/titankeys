import { expect, test } from "bun:test";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

// Orange accents covers what the keyboard draws over other apps (Look.kt). TitanKeys' own screens (Settings, the guide, the
// front page, the debug screens) keep the app's orange (owner, 2026-11-12). Every keyboard colour comes from Look, so no
// keyboard file may hold its own orange.
const root = join(import.meta.dir, "../..");
const folder = "android/kotlin/com/atlas/keyboard";
const own = /^(Guide|HapticsLab|KeyCalibration|BackDisclosure|WpmEffectsView|Look\.kt)/;
const keyboard = readdirSync(join(root, folder)).filter(name => name.endsWith(".kt") && !own.test(name));

test("no keyboard drawing has an orange of its own", () => {
  for (const name of keyboard) {
    const source = readFileSync(join(root, folder, name), "utf8");
    expect({ name, orange: /0x[Ff]{2}EF804E|GuideColours\.ORANGE/.test(source) }).toEqual({ name, orange: false });
  }
});

test("Look's accent is the text's colour with Orange accents off", () => {
  const look = readFileSync(join(root, folder, "Look.kt"), "utf8");
  expect(look).toContain("val accent = if (orange) GuideColours.ORANGE else ink");
  expect(look).toContain("TypingSettings.orangeAccents(context)");
});

test("the switch says where it applies", () => {
  expect(readFileSync(join(root, "app/settings/index.tsx"), "utf8")).toContain('<Toggle label="Orange accents" subtitle={accentsNote}');
});
