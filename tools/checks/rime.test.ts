import { expect, test } from "bun:test";
import { createHash } from "node:crypto";
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";

// Rime ships prebuilt: the library and the compiled Chinese data are built by native/rime/build.sh and data.sh, from pinned
// sources, and recorded in native/rime/library.sha256 and android/assets/rime.sha256. These checks keep what ships the same as
// what the scripts build, and keep the pins, the licences and the credits together (#56).
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");
const sha = (path: string) => createHash("sha256").update(readFileSync(join(root, path))).digest("hex");
const lines = (text: string) => text.split("\n").filter(line => line.trim()).map(line => {
  const [hash, path] = line.split(/  /);
  return { hash, path };
});
const files = (dir: string): string[] => readdirSync(dir).flatMap(name => {
  const path = join(dir, name);
  return statSync(path).isDirectory() ? files(path) : [path];
});

test("the library is the one build.sh recorded, with the JNI source it was built from", () => {
  const recorded = lines(read("native/rime/library.sha256"));
  expect(recorded.map(line => line.path)).toEqual([
    "native/rime/CMakeLists.txt",
    "native/rime/rime_jni.cc",
    "android/jniLibs/arm64-v8a/librime_tk.so",
  ]);
  for (const { hash, path } of recorded) expect(`${path} ${sha(path)}`).toBe(`${path} ${hash}`);
});

test("the shipped Chinese data is exactly the list data.sh wrote, and the list is what the app copies by", () => {
  const recorded = lines(read("android/assets/rime.sha256"));
  const shipped = files(join(root, "android/assets/rime")).map(path => relative(join(root, "android/assets/rime"), path).replaceAll("\\", "/")).sort();
  expect(recorded.map(line => line.path)).toEqual(shipped);
  for (const { hash, path } of recorded) expect(`${path} ${sha(`android/assets/rime/${path}`)}`).toBe(`${path} ${hash}`);
  // Only compiled tables ship: the word lists' sources stay out, and so does the owner's comparison schema.
  expect(shipped.filter(path => path.endsWith(".dict.yaml") || path.startsWith("essay"))).toEqual([]);
  expect(shipped.filter(path => path.includes("owner"))).toEqual([]);
  for (const table of ["build/jyut6ping3.table.bin", "build/luna_pinyin.table.bin", "build/tk_yale.prism.bin", "build/tk_jyutping.prism.bin", "build/tk_pinyin.prism.bin"]) {
    expect(shipped).toContain(table);
  }
  // RimeData reads this asset and these names.
  const data = read("android/kotlin/com/atlas/rime/RimeData.kt");
  expect(data).toContain('const val MANIFEST = "rime.sha256"');
  expect(data).toContain('const val ASSETS = "rime"');
  // The copies get the time the data was compiled with, so Rime on the phone compiles nothing again.
  const time = read("native/rime/data.sh").match(/^SOURCE_TIME=(\d+)$/m)?.[1];
  expect(time).toBeDefined();
  expect(data).toContain(`const val SOURCE_TIME = ${time}L`);
});

test("the schemas the data ships are the repo's own, and the three the engine uses", () => {
  for (const schema of readdirSync(join(root, "native/rime/schemas"))) {
    // OpenCC's configs go in OpenCC's folder.
    const shipped = schema.endsWith(".json") ? `opencc/${schema}` : schema;
    expect(read(`android/assets/rime/${shipped}`)).toBe(read(`native/rime/schemas/${schema}`));
  }
  // The picker's ten places, over Q to P.
  expect(read("android/assets/rime/default.yaml")).toMatch(/^  page_size: 10$/m);
  const engine = read("android/kotlin/com/atlas/rime/RimeEngine.kt");
  for (const id of ["tk_yale", "tk_jyutping", "tk_pinyin"]) {
    expect(engine).toContain(`"${id}"`);
    expect(existsSync(join(root, `android/assets/rime/${id}.schema.yaml`))).toBe(true);
    expect(read(`native/rime/schemas/${id}.schema.yaml`)).toContain("__include: tk_variants:/");
  }
});

test("the variants are the schemas' options, one each", () => {
  const variants = read("native/rime/schemas/tk_variants.yaml");
  expect(variants).toContain("- options: [ zh_hk, zh_tw, zh_hans ]");
  const kotlin = read("android/kotlin/com/atlas/rime/ChineseVariant.kt");
  for (const option of ["zh_hk", "zh_tw", "zh_hans"]) expect(kotlin).toContain(`"${option}", "${option}"`);
  for (const schema of ["tk_yale", "tk_jyutping", "tk_pinyin"]) {
    const text = read(`native/rime/schemas/${schema}.schema.yaml`);
    for (const option of ["zh_hk", "zh_tw", "zh_hans"]) expect(text).toMatch(new RegExp(`^${option}: +\\{ option_name: ${option}, +opencc_config: \\S+\\.json`, "m"));
  }
});

test("every pinned source is in SOURCES.md, and every licence it names is in licences/", () => {
  const sources = read("native/rime/SOURCES.md");
  const pins = [...read("native/rime/build.sh").matchAll(/@([0-9a-f]{40})$/gm), ...read("native/rime/data.sh").matchAll(/@([0-9a-f]{40})$/gm)].map(match => match[1]);
  expect(pins.length).toBe(7);
  for (const pin of pins) expect(sources).toContain(pin);
  for (const licence of sources.matchAll(/`licences\/([^`]+)`/g)) expect(existsSync(join(root, "licences", licence[1]))).toBe(true);
  // The library shares the app's libc++_shared.so only because both come from NDK r27c: build.sh checks the hash.
  expect(read("native/rime/build.sh")).toContain(`LIBCXX_SHA256=${sha("android/jniLibs/arm64-v8a/libc++_shared.so")}`);
});

test("the JNI class the library registers is the one in the app, kept from shrinking", () => {
  expect(read("native/rime/rime_jni.cc")).toContain('kClass = "com/atlas/rime/RimeNative"');
  expect(existsSync(join(root, "android/kotlin/com/atlas/rime/RimeNative.kt"))).toBe(true);
  expect(read("android/kotlin/com/atlas/rime/RimeNative.kt")).toContain('System.loadLibrary("rime_tk")');
  expect(read("android/proguard-rules.pro")).toContain("-keep class com.atlas.rime.RimeNative { *; }");
});
