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
  const data = read("android/kotlin/com/titankeys/rime/RimeData.kt");
  expect(data).toContain('const val MANIFEST = "rime.sha256"');
  expect(data).toContain('const val ASSETS = "rime"');
  // Rime on the phone never deploys, so it never compiles the shipped tables again, whatever the copies' times: only the
  // deployer's tasks (sync needs them) and the installation's name. rime_tool opens Rime the same way for data.sh's checks.
  for (const source of ["native/rime/rime_jni.cc", "native/rime/rime_tool.cc"]) {
    expect(read(source)).toContain("rime->deployer_initialize(&traits);");
    expect(read(source)).toContain('rime->run_task("installation_update");');
  }
  expect(read("native/rime/rime_jni.cc")).not.toContain("start_maintenance");
});

test("the schemas the data ships are the repo's own, and the three the engine uses", () => {
  for (const schema of readdirSync(join(root, "native/rime/schemas"))) {
    // OpenCC's configs go in OpenCC's folder.
    const shipped = schema.endsWith(".json") ? `opencc/${schema}` : schema;
    expect(read(`android/assets/rime/${shipped}`)).toBe(read(`native/rime/schemas/${schema}`));
  }
  // The picker's eight places, over E to P.
  expect(read("android/assets/rime/default.yaml")).toMatch(/^  page_size: 8$/m);
  const engine = read("android/kotlin/com/titankeys/rime/RimeEngine.kt");
  for (const id of ["tk_yale", "tk_jyutping", "tk_pinyin"]) {
    expect(engine).toContain(`"${id}"`);
    expect(existsSync(join(root, `android/assets/rime/${id}.schema.yaml`))).toBe(true);
    expect(read(`native/rime/schemas/${id}.schema.yaml`)).toContain("__include: tk_variants:/");
  }
});

test("the variants are the schemas' options, one each", () => {
  const variants = read("native/rime/schemas/tk_variants.yaml");
  expect(variants).toContain("- options: [ zh_hk, zh_tw, zh_hans ]");
  const kotlin = read("android/kotlin/com/titankeys/rime/ChineseVariant.kt");
  for (const option of ["zh_hk", "zh_tw", "zh_hans"]) expect(kotlin).toContain(`"${option}", "${option}"`);
  for (const schema of ["tk_yale", "tk_jyutping", "tk_pinyin"]) {
    const text = read(`native/rime/schemas/${schema}.schema.yaml`);
    for (const option of ["zh_hk", "zh_tw", "zh_hans"]) expect(text).toMatch(new RegExp(`^${option}: +\\{ option_name: ${option}, +opencc_config: \\S+\\.json`, "m"));
  }
});

test("every pinned source is in SOURCES.md, and every licence it names is in licences/", () => {
  const sources = read("native/rime/SOURCES.md");
  const pins = [...read("native/rime/build.sh").matchAll(/@([0-9a-f]{40})$/gm), ...read("native/rime/data.sh").matchAll(/@([0-9a-f]{40})$/gm)].map(match => match[1]);
  // build.sh's three, and data.sh's prelude, essay, luna-pinyin, cantonese, cangjie, quick and terra-pinyin.
  expect(pins.length).toBe(10);
  for (const pin of pins) expect(sources).toContain(pin);
  for (const licence of sources.matchAll(/`licences\/([^`]+)`/g)) expect(existsSync(join(root, "licences", licence[1]))).toBe(true);
  // The library shares the app's libc++_shared.so only because both come from NDK r27c: build.sh checks the hash.
  expect(read("native/rime/build.sh")).toContain(`LIBCXX_SHA256=${sha("android/jniLibs/arm64-v8a/libc++_shared.so")}`);
});

test("the JNI class the library registers is the one in the app, kept from shrinking", () => {
  expect(read("native/rime/rime_jni.cc")).toContain('kClass = "com/titankeys/rime/RimeNative"');
  expect(existsSync(join(root, "android/kotlin/com/titankeys/rime/RimeNative.kt"))).toBe(true);
  expect(read("android/kotlin/com/titankeys/rime/RimeNative.kt")).toContain('System.loadLibrary("rime_tk")');
  expect(read("android/proguard-rules.pro")).toContain("-keep class com.titankeys.rime.RimeNative { *; }");
  // Every native the Kotlin side declares is one the library registers, by the same name, and no other.
  const kotlin = [...read("android/kotlin/com/titankeys/rime/RimeNative.kt").matchAll(/external fun (\w+)\(/g)].map(m => m[1]).sort();
  const registered = [...read("native/rime/rime_jni.cc").matchAll(/^ {4}\{"(\w+)", "\(/gm)].map(m => m[1]).sort();
  expect(kotlin.length).toBeGreaterThan(5);
  expect(registered).toEqual(kotlin);
});

test("librime-predict is built in, with each language's table, its licence and credits (#56)", () => {
  // Merged into Rime from Trime's pinned submodule, as Trime does; its tool compiles the tables.
  expect(read("native/rime/CMakeLists.txt")).toContain("foreach(plugin librime-predict librime-octagram)");
  expect(read("native/rime/CMakeLists.txt")).toContain('"${TK_RIME_SOURCES}/trime/app/src/main/jni/${plugin}"');
  expect(read("native/rime/build.sh")).toContain("--target rime_tool opencc_dict build_predict");
  expect(read("native/rime/SOURCES.md")).toContain("67c2881914a0242fc14ec7d6877782771b75800c");
  // Each schema has the predictor and its language's table; the switch is on.
  const tables: Record<string, string> = { tk_yale: "predict-yue.db", tk_jyutping: "predict-yue.db", tk_pinyin: "predict-cmn.db",
    tk_cangjie: "predict-yue.db", tk_quick: "predict-yue.db", tk_zhuyin: "predict-cmn.db" };
  for (const [schema, db] of Object.entries(tables)) {
    const text = read(`native/rime/schemas/${schema}.schema.yaml`);
    expect(text).toMatch(/processors: \[ ascii_composer, recognizer, predictor, key_binder,/);
    expect(text).toContain("translators: [ predict_translator,");
    expect(text).toContain(`predictor: { db: ${db},`);
    expect(existsSync(join(root, `android/assets/rime/${db}`))).toBe(true);
  }
  expect(read("native/rime/schemas/tk_variants.yaml")).toMatch(/- name: prediction\n {4}reset: 1/);
  // The tables: best first, at most 8 next words a word, and nothing for the start of a text, so nothing shows before
  // the first commit (owner: with nothing typed, the bar stays empty).
  for (const lang of ["yue", "cmn"]) {
    const rows = read(`native/rime/predict/${lang}.txt`).trimEnd().split("\n").map(line => line.split("\t"));
    expect(rows.every(row => row.length === 3 && row[0] !== "$")).toBe(true);
    const count = new Map<string, number>();
    for (const [word] of rows) count.set(word, (count.get(word) ?? 0) + 1);
    expect(Math.max(...count.values())).toBeLessThanOrEqual(8);
  }
  // Its licence, and the Licences page: the plugin, and Tatoeba's sentences behind the tables.
  expect(read("licences/Rime-predict-BSD-3-Clause.txt")).toContain("BSD 3-Clause License");
  const page = read("app/settings/licences/index.tsx");
  expect(page).toContain('title: "librime-predict"');
  expect(page).toMatch(/title: "Tatoeba", subtitle: "[^"]*Chinese/);
});

test("Cantonese's grammar model: librime-octagram built in, the model counted by data.sh, licensed as itself (#56)", () => {
  expect(read("native/rime/CMakeLists.txt")).toContain("foreach(plugin librime-predict librime-octagram)");
  expect(read("native/rime/build.sh")).toContain("--target rime_tool opencc_dict build_predict build_grammar");
  const data = read("native/rime/data.sh");
  expect(data).toContain('"$BUILD_GRAMMAR" tk_yue < grammar.txt');
  for (const pin of ["WIKI_SHA256", "HKCANCOR_SHA256", "VOICE_SHA256"]) expect(data).toMatch(new RegExp(`^${pin}=[0-9a-f]{64}$`, "m"));
  // Yale and Jyutping use it, counting pairs of single characters too; the others have none.
  for (const id of ["tk_yale", "tk_jyutping"])
    expect(read(`native/rime/schemas/${id}.schema.yaml`)).toContain("grammar: { language: tk_yue, collocation_min_length: 2 }");
  for (const id of ["tk_pinyin", "tk_cangjie", "tk_quick", "tk_zhuyin"])
    expect(read(`native/rime/schemas/${id}.schema.yaml`)).not.toMatch(/^grammar:/m);
  expect(read("android/assets/rime.sha256")).toMatch(/ tk_yue\.gram$/m);
  // The model carries Wikipedia's CC BY-SA 4.0; the plugin is GPL-3.0 at Trime's commit. Both credited, in the app too.
  expect(read("licences/Rime-cantonese-grammar-CC-BY-SA-4.0.txt")).toContain("Attribution-ShareAlike 4.0 International");
  expect(read("licences/Rime-octagram-GPL-3.0.txt")).toContain("GNU GENERAL PUBLIC LICENSE");
  const sources = read("native/rime/SOURCES.md");
  expect(sources).toContain("`tk_yue.gram` is licensed CC BY-SA 4.0 itself");
  const page = read("app/settings/licences/index.tsx");
  expect(page).toContain('title: "librime-octagram"');
  expect(page).toMatch(/title: "Cantonese grammar model", subtitle: "CC BY-SA 4.0\./);
  for (const ident of ["yue-yale", "yue-jyutping"])
    expect(read(`android/assets/im/${ident}/NOTICE`)).toContain("Cantonese Wikipedia (CC BY-SA 4.0, and so is the model)");
});

test("the keyboard shows the suggestions as choices, and clears them Rime's way (#56)", () => {
  const engine = read("android/kotlin/com/titankeys/rime/RimeEngine.kt");
  // Escape is what stops the predictor offering them again; a plain clear brings them back.
  expect(engine).toMatch(/private fun dismiss\(\) \{\s*if \(suggesting\(\)\) rime\.process\(RimeKeys\.ESCAPE, 0\)/);
  expect(engine).toMatch(/override fun reset\(\): EngineState \{[\s\S]*?rime\.reset\(\)\s*\/\/[^\n]*\n\s*dismiss\(\)/);
  const ime = read("android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt");
  // A key that's the keyboard's own closes the bar the suggestions were on.
  expect(ime).toContain("if (state.commit.isNotEmpty() || !state.composing && openPicker() is LanguagePicker.Choices) engineState(state)");
  // A panel symbol sends Space only for a code: with suggestions it would pick one.
  expect(ime).toContain("engineState(if (choices.state.composing) it.space() else it.reset())");
});
