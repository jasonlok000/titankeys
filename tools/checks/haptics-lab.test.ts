// Checks that the Haptics lab is only in TitanKeys Debug: the row, the page and every native operation need a ".debug" package.
import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");

test("only a debug package has the lab", () => {
  expect(read("android/kotlin/com/atlas/keyboard/HapticsLab.kt"))
    .toContain('fun available(context: Context) = context.packageName.endsWith(".debug")');
  // Every other build sends null, which hides the row and empties the page.
  expect(read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt"))
    .toContain('.put("hapticsLab", if (HapticsLab.available(activity)) hapticsLab.value.state() else JSONObject.NULL)');
});

test("every lab operation refuses outside a debug package", () => {
  const adapter = read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
  const operations = [...adapter.matchAll(/^\s+"(lab\w+)" -> \{?\s*(.*)$/gm)];
  expect(operations.map(match => match[1]).sort()).toEqual(["labComments", "labLength", "labPlay", "labPlayRun", "labSaveComments"]);
  for (const [line, , body] of operations) {
    const first = body || adapter.slice(adapter.indexOf(line) + line.length).trimStart().split("\n")[0];
    expect(first.startsWith("lab().")).toBe(true);
  }
  expect(adapter).toContain('check(HapticsLab.available(activity)) { "The Haptics lab is only in TitanKeys Debug" }');
  // Nothing else reaches the lab without that check.
  expect(adapter.match(/hapticsLab\.value/g)?.length).toBe(3);
});

test("Settings shows the lab only when the build has it", () => {
  const settings = read("app/settings/index.tsx");
  expect(settings).toContain('{settings.hapticsLab && <Field label="Haptics lab" href="/settings/haptics-lab" />}');
  expect(settings.match(/haptics-lab/g)?.length).toBe(1);
  const page = read("app/settings/haptics-lab.tsx");
  expect(page).toContain('if (!lab) return <Screen title={title} subtitle="The Haptics lab is only in TitanKeys Debug." />;');
  expect(page.indexOf("if (!lab) return")).toBeLessThan(page.indexOf("<Lab "));
});
