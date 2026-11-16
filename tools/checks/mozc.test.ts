import { expect, test } from "bun:test";
import { createHash } from "node:crypto";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

// Mozc ships prebuilt, as Rime does: native/mozc/build.sh builds the library and the data from the pinned commit and records
// them in native/mozc/library.sha256 and android/assets/mozc.sha256 (docs/languages.md, "Japanese").
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8").replaceAll("\r\n", "\n");
const sha = (path: string) => createHash("sha256").update(readFileSync(join(root, path))).digest("hex");
const lines = (text: string) => text.split("\n").filter(line => line.trim()).map(line => {
  const [hash, path] = line.split(/  /);
  return { hash, path };
});

test("the library is the one build.sh recorded, with the sources it was built from", () => {
  const recorded = lines(read("native/mozc/library.sha256"));
  expect(recorded.map(line => line.path)).toEqual([
    "native/mozc/tk/BUILD.bazel",
    "native/mozc/tk/tk.map",
    "native/mozc/tk/tk_core.h",
    "native/mozc/tk/tk_core.cc",
    "native/mozc/tk/tk_jni.cc",
    "android/jniLibs/arm64-v8a/libmozc_tk.so",
  ]);
  for (const { hash, path } of recorded) expect(`${path} ${sha(path)}`).toBe(`${path} ${hash}`);
});

test("the shipped data is the one build.sh recorded", () => {
  const recorded = lines(read("android/assets/mozc.sha256"));
  expect(recorded.map(line => line.path)).toEqual(["mozc.data"]);
  for (const { hash, path } of recorded) expect(sha(`android/assets/mozc/${path}`)).toBe(hash);
});

test("the JNI layer registers what MozcNative declares, on MozcNative", () => {
  const jni = read("native/mozc/tk/tk_jni.cc");
  const kotlin = read("android/kotlin/com/titankeys/mozc/MozcCalls.kt");
  expect(jni).toContain(`const char* const kClass = "com/titankeys/mozc/MozcNative";`);
  for (const name of [...jni.matchAll(/\{"(\w+)", "/g)].map(m => m[1])) {
    expect(kotlin).toMatch(new RegExp(`@JvmStatic private external fun ${name}\\(`));
  }
  expect(read("native/mozc/tk/tk.map")).toContain("global: JNI_OnLoad;");
});

test("the pins and the licences travel together", () => {
  const build = read("native/mozc/build.sh");
  const sources = read("native/mozc/SOURCES.md");
  const commit = build.match(/mozc\.git@([0-9a-f]{40})/)![1];
  expect(sources).toContain(commit);
  expect(build).toMatch(/BAZELISK_SHA256=[0-9a-f]{64}/);
  for (const licence of ["licences/Mozc-BSD-3-Clause.txt", "licences/Mozc-data.txt"]) expect(existsSync(join(root, licence))).toBe(true);
});

test("Japanese is an input method on Mozc, and Mozc is an engine the app makes by name", () => {
  const im = JSON.parse(read("android/assets/im/ja/im.json"));
  expect(im.engine).toEqual({ mozc: "oss" });
  expect(im.script).toBe("Jpan");
  expect(read("android/kotlin/com/titankeys/keyboard/InputEngine.kt")).toContain(`"mozc" to "com.titankeys.mozc.MozcEngine"`);
  expect(read("android/kotlin/com/titankeys/keyboard/Chinese.kt")).toContain(`val BAR_ENGINES = setOf(ENGINE, "mozc")`);
});
