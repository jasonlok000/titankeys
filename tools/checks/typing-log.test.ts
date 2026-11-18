// The full typing log is only in TitanKeys Debug: these checks fail if a public release (com.titankeys, from GitHub or
// Google Play) could turn it on, record, show it or export it. They also check that Record a bug (#64), which is in every
// build, keeps what it promises. The gate's own rule is checked in tests/rawlog.
import { describe, expect, test } from "bun:test";

const root = new URL("../../", import.meta.url);
const text = (path: string) => Bun.file(new URL(path, root)).text();

describe("typing log: TitanKeys Debug only", () => {
  test("the public package is not a debug package, and the debug build is", async () => {
    const ink = await text("ink.toml");
    expect(ink).toMatch(/^package = "com\.titankeys"$/m);
    expect(await text("tools/debug.sh")).toContain('package = "com.titankeys.debug"');
  });

  test("the keyboard records the full log only through the gate", async () => {
    const rawLog = await text("android/kotlin/com/titankeys/keyboard/RawLog.kt");
    expect(rawLog).toContain("fun available(context: Context) = RawLogGate.allows(context.packageName)");
    expect(rawLog).toContain("fun on(context: Context) = available(context) && TypingSettings.rawLog(context)");
    expect(rawLog).toContain("val debug = if (on(context)) debugField(context, info, restarting, textAllowed) else null");
    expect(rawLog).toMatch(/fun enabled\(context: Context, enabled: Boolean\) \{\s*check\(!enabled \|\| available\(context\)\)/);
    expect(rawLog).toMatch(/fun export\(context: Context, out: OutputStream\) \{\s*check\(available\(context\)\)/);
    expect(rawLog).toContain("fun size(context: Context) = if (available(context))");
    // Nothing else may read the setting to decide whether to record.
    expect(rawLog.match(/TypingSettings\.rawLog\(context\)/g)?.length).toBe(2); // on() and removeLeftovers()
  });

  test("the app has no way in: not the settings row, the page, the switch or the export", async () => {
    const settings = await text("app/settings/index.tsx");
    expect(settings).toContain('{settings.rawLogAvailable && <Field label="Typing log" href="/settings/rawlog">');
    expect(settings.match(/href="\/settings\/rawlog"/g)?.length).toBe(1);
    expect(await text("app/settings/rawlog.tsx")).toContain("if (!result.data.rawLogAvailable) return");
    const adapter = await text("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
    expect(adapter).toMatch(/"exportRawLog" -> \{\s*check\(RawLog\.available\(activity\)\)/);
    expect(adapter).toContain('.put("rawLogAvailable", RawLog.available(activity))');
    expect(adapter).toContain('"rawLog" -> RawLog.enabled(activity, value as Boolean)');
    expect(await text("android/kotlin/com/vandam/ink/RawLogExportActivity.kt")).toMatch(/if \(!RawLog\.available\(this\)\) \{\s*complete\(false/);
    // An old 2.0 log on a public release goes away at keyboard start.
    expect(await text("android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt")).toContain("RawLog.removeLeftovers(this)");
  });

  test("in TitanKeys Debug it's on by default and stays on, with a cap that drops old data, never a time limit (owner, 2026-11-17)", async () => {
    const settings = await text("android/kotlin/com/titankeys/engine/TypingSettings.java");
    // On by default only for the Debug package; a release reads false, and the gate keeps it off there anyway.
    expect(settings).toContain('return preferences(context).getBoolean("raw_log", context.getPackageName().endsWith(".debug"));');
    expect(await text("android/kotlin/com/titankeys/keyboard/RawLogGate.kt")).toContain('const val DEBUG_SUFFIX = ".debug"');
    // Nothing turns it off by itself in Debug: only the switch, and removeLeftovers, which runs only where there's no log.
    const rawLog = await text("android/kotlin/com/titankeys/keyboard/RawLog.kt");
    expect(rawLog).toMatch(/fun removeLeftovers\(context: Context\) \{\s*if \(available\(context\)\) return/);
    for (const file of ["RawLog.kt", "RawLogStore.kt", "RawLogWriter.kt"]) {
      const source = await text(`android/kotlin/com/titankeys/keyboard/${file}`);
      expect(`${file}: ${/rawLog\(\w+, false\)/.test(source.replace("if (TypingSettings.rawLog(context)) TypingSettings.rawLog(context, false)", ""))}`).toBe(`${file}: false`);
      expect(`${file}: ${/DAYS|HOURS|expire|currentTimeMillis\(\) -/.test(source)}`).toBe(`${file}: false`);
    }
    // The cap makes room by deleting the oldest files, and keeps logging.
    const store = await text("android/kotlin/com/titankeys/keyboard/RawLogStore.kt");
    expect(store).toContain("const val CAP_BYTES = 200L * 1024 * 1024");
    expect(store).toMatch(/private fun makeRoom\(incoming: Long\) \{[\s\S]*?for \(file in files\(\)\.dropLast\(1\)\)/);
    expect(await text("app/settings/rawlog.tsx")).toContain("On from the start in TitanKeys Debug, and it stays on until you turn it off.");
  });

  test("LogPull and the owner's MARK are TitanKeys Debug's alone (owner, 2026-11-17)", async () => {
    // LogPull lives with the debug tools, which tools/debug.sh copies in; only adb, holding DUMP, can send to it.
    const manifest = await text("android/debug/recorder/manifest.xml");
    expect(manifest).toMatch(/android:name="com\.titankeys\.keyboard\.LogPull"\s+android:exported="true"\s+android:permission="android\.permission\.DUMP"/);
    expect(await text("android/src/main/AndroidManifest.xml")).not.toContain("LogPull");
    const pull = await text("android/debug/recorder/LogPull.kt");
    expect(pull).toContain("if (intent.action != ACTION || !RawLog.available(context)) return");
    expect(pull).toContain('context.getExternalFilesDir("logs")');
    // The mark: Debug only, only while the log records the field, and it puts Shift, Alt and the menu back.
    const ime = await text("android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt");
    expect(ime).toContain("modifiers.restore(markShift, markAlt, markAutoShifted)");
    expect(ime).toContain("RawLog.ownerMark(this,");
    expect(ime).toContain("if (!editing || !RawLog.on(this) || event.repeatCount > 0) return false");
  });

  test("no public text points to the typing log", async () => {
    for (const file of ["README.md", "app/settings/guide.tsx", "site/privacy/index.html", "lib/connected-text.ts", "app/settings/feedback.tsx"])
      expect(`${file}: ${(await text(file)).includes("Typing log") ? "Typing log" : ""}`).toBe(`${file}: `);
  });
});

describe("record a bug", () => {
  test("keeps its own store, apart from the typing log", async () => {
    const bug = await text("android/kotlin/com/titankeys/keyboard/BugRecording.kt");
    expect(bug).toContain('private const val DIRECTORY = "bug-recording"');
    expect(await text("android/kotlin/com/titankeys/keyboard/RawLog.kt")).toContain('private const val DIRECTORY = "rawlog"');
  });

  test("records only in fields that allow learning, never the text around the cursor, and stops by itself", async () => {
    const bug = await text("android/kotlin/com/titankeys/keyboard/BugRecording.kt");
    expect(bug).toContain("if (!Field.of(info.inputType, info.imeOptions).learning) {");
    expect(bug).toMatch(/RawLogLines\.fieldStart\([^)]*info\.initialSelStart, info\.initialSelEnd, null, now\)/);
    expect(bug).toContain("const val MAX_MILLIS = 3 * 60_000L");
    expect(bug).toContain("main.postDelayed(autoStop, MAX_MILLIS)");
    const rawLog = await text("android/kotlin/com/titankeys/keyboard/RawLog.kt");
    expect(rawLog).toContain("if (keepText) before?.toString() else null");
    expect(rawLog).toContain("if (bug != null && !bug.closed) bug.add(line(false))");
    // Marks come from any app, so only the debug log takes them.
    expect(rawLog).toMatch(/fun mark\(kind: String\?, label: String\?\) \{\s*if \(debug != null/);
  });

  test("never freezes the keyboard: the auto-stop runs off the main thread, and the summary is read once", async () => {
    // Bug hunt 2.1, #23: the auto-stop ran on the main looper and waited up to 2 s for the writer, and every Settings refresh
    // exported and parsed up to 24 MB, rewriting the file the share sheet had lent out.
    const bug = await text("android/kotlin/com/titankeys/keyboard/BugRecording.kt");
    expect(bug).toContain('private val autoStop = Runnable { app?.let { context -> Thread({ stop(context) }, "BugRecordingStop").start() } }');
    expect(bug).toMatch(/stopping\.join\(WRITE_WAIT_MILLIS\)[\s\S]{0,200}summary = read\(context\)/);
    expect(bug).toMatch(/fun summary\(context: Context\): Summary\? \{\s*if \(recording\) return null\s*summary\?\.let \{ return it \}/);
    // Only share() and the report form's attachment() (#97) make the shared file.
    expect(bug).toContain("private fun file(context: Context): File?");
    expect(bug.match(/\bfile\(context\)/g)?.length).toBe(2);
    expect(bug).toContain("fun attachment(context: Context): ByteArray? = file(context)?.readBytes()");
    expect(bug).toContain("val file = file(context) ?: throw IllegalStateException(\"Nothing recorded\")");
  });

  test("shares only through the share sheet, with a provider no other app can reach", async () => {
    const manifest = await text("android/src/main/AndroidManifest.xml");
    expect(manifest).toMatch(/android:name="com\.titankeys\.keyboard\.BugRecordingProvider"[\s\S]*?android:exported="false"[\s\S]*?android:grantUriPermissions="true"/);
    const bug = await text("android/kotlin/com/titankeys/keyboard/BugRecording.kt");
    expect(bug).toContain("Intent(Intent.ACTION_SEND)");
    expect(bug.includes("HttpsURLConnection") || bug.includes("Connected.open(")).toBe(false);
    const provider = await text("android/kotlin/com/titankeys/keyboard/BugRecordingProvider.kt");
    expect(provider).toContain('if (mode != "r") throw SecurityException("Read only")');
  });

  test("says plainly what the file shows before it is shared", async () => {
    const words = await text("lib/bug-text.ts");
    expect(words).toContain("someone can work out what you typed");
    expect(words).toContain("Password and private fields are never recorded");
    expect(words).toContain("TitanKeys only sends it if you press Send in the report form");
    expect(words.toLowerCase().includes("anonym")).toBe(false);
  });

  test("says what a recording can show before you start one, in the same words as the privacy policy", async () => {
    const said = "It doesn't save your text, but the keys and word choices it records can show most of what you typed while it ran. "
      + "Only share it with people you'd show that to.";
    expect(await text("lib/bug-text.ts")).toContain(`beforeRecord: "${said}"`);
    const screen = await text("app/settings/bug.tsx");
    expect(screen.indexOf("<Note>{text.beforeRecord}</Note>")).toBeGreaterThan(-1);
    expect(screen.indexOf("<Note>{text.beforeRecord}</Note>")).toBeLessThan(screen.indexOf("<Field label={text.record}"));
    const policy = (await text("site/privacy/index.html")).replace(/\s+/g, " ");
    expect(policy).toContain(said);
  });
});

test("the log's size reads in KB below 0.1 MB, so a small log never shows 0.0 MB", async () => {
  const { fileSize } = await import("../../lib/size");
  expect(fileSize(0)).toBe("0 KB");
  expect(fileSize(52_000)).toBe("51 KB");
  expect(fileSize(104_857)).toBe("103 KB");
  expect(fileSize(104_858)).toBe("0.1 MB");
  expect(fileSize(3_500_000)).toBe("3.3 MB");
  expect(await text("app/settings/rawlog.tsx")).toContain("{fileSize(rawLogBytes)}");
});
