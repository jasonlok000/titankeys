// Checks that Key calibration is only in TitanKeys Debug, as the Haptics lab is: the row, the operation and the screen.
import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");

test("Settings shows Key calibration only when the build has the Haptics lab", () => {
  const settings = read("app/settings/index.tsx");
  expect(settings).toContain('{settings.hapticsLab && <Field label="Key calibration" onPress={() => void showKeyCalibration()} />}');
});

test("the operation refuses outside a debug package", () => {
  const adapter = read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
  expect(adapter).toMatch(/"keyCalibration" -> \{\s+check\(HapticsLab\.available\(activity\)\)/);
});

test("the screen shows nothing outside a debug package", () => {
  const screen = read("android/kotlin/com/atlas/keyboard/KeyCalibrationActivity.kt");
  const onCreate = screen.slice(screen.indexOf("override fun onCreate("));
  expect(onCreate).toMatch(/^override fun onCreate\(savedInstanceState: Bundle\?\) \{\s+super\.onCreate\(savedInstanceState\)\s+\/\/[^\n]*\n\s+if \(!HapticsLab\.available\(this\)\) \{\s+finish\(\)\s+return\s+\}/);
  expect(onCreate.indexOf("HapticsLab.available(this)")).toBeLessThan(onCreate.indexOf("setContentView"));
  expect(read("android/src/main/AndroidManifest.xml"))
    .toMatch(/android:name="com\.atlas\.keyboard\.KeyCalibrationActivity"\s+android:exported="false"/);
});

test("only Key calibration writes the alignment", () => {
  const writers = [...new Bun.Glob("**/*.{kt,java}").scanSync(join(root, "android"))].filter(path =>
    /KeyCalibration\.(save|reset)\(/.test(read(join("android", path))));
  expect(writers.map(path => path.replaceAll("\\", "/"))).toEqual(["kotlin/com/atlas/keyboard/KeyCalibrationActivity.kt"]);
});
