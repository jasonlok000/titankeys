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
    const rawLog = await text("android/kotlin/com/atlas/keyboard/RawLog.kt");
    expect(rawLog).toContain("fun available(context: Context) = RawLogGate.allows(context.packageName)");
    expect(rawLog).toContain("fun on(context: Context) = available(context) && TypingSettings.rawLog(context)");
    expect(rawLog).toContain("val debug = if (on(context)) debugField(context, info, restarting) else null");
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
    expect(await text("android/kotlin/com/atlas/keyboard/AtlasIme.kt")).toContain("RawLog.removeLeftovers(this)");
  });

  test("no public text points to the typing log", async () => {
    for (const file of ["README.md", "app/settings/guide.tsx", "site/privacy/index.html", "lib/connected-text.ts", "app/settings/feedback.tsx"])
      expect(`${file}: ${(await text(file)).includes("Typing log") ? "Typing log" : ""}`).toBe(`${file}: `);
  });
});

describe("record a bug", () => {
  test("keeps its own store, apart from the typing log", async () => {
    const bug = await text("android/kotlin/com/atlas/keyboard/BugRecording.kt");
    expect(bug).toContain('private const val DIRECTORY = "bug-recording"');
    expect(await text("android/kotlin/com/atlas/keyboard/RawLog.kt")).toContain('private const val DIRECTORY = "rawlog"');
  });

  test("records only in fields that allow learning, never the text around the cursor, and stops by itself", async () => {
    const bug = await text("android/kotlin/com/atlas/keyboard/BugRecording.kt");
    expect(bug).toContain("if (!Field.of(info.inputType, info.imeOptions).learning) {");
    expect(bug).toMatch(/RawLogLines\.fieldStart\([^)]*info\.initialSelStart, info\.initialSelEnd, null, now\)/);
    expect(bug).toContain("const val MAX_MILLIS = 3 * 60_000L");
    expect(bug).toContain("main.postDelayed(autoStop, MAX_MILLIS)");
    const rawLog = await text("android/kotlin/com/atlas/keyboard/RawLog.kt");
    expect(rawLog).toContain("if (keepText) before?.toString() else null");
    expect(rawLog).toContain("if (bug != null && !bug.closed) bug.add(line(false))");
    // Marks come from any app, so only the debug log takes them.
    expect(rawLog).toMatch(/fun mark\(kind: String\?, label: String\?\) \{\s*if \(debug != null/);
  });

  test("shares only through the share sheet, with a provider no other app can reach", async () => {
    const manifest = await text("android/src/main/AndroidManifest.xml");
    expect(manifest).toMatch(/android:name="com\.atlas\.keyboard\.BugRecordingProvider"[\s\S]*?android:exported="false"[\s\S]*?android:grantUriPermissions="true"/);
    const bug = await text("android/kotlin/com/atlas/keyboard/BugRecording.kt");
    expect(bug).toContain("Intent(Intent.ACTION_SEND)");
    expect(bug.includes("HttpsURLConnection") || bug.includes("Connected.open(")).toBe(false);
    const provider = await text("android/kotlin/com/atlas/keyboard/BugRecordingProvider.kt");
    expect(provider).toContain('if (mode != "r") throw SecurityException("Read only")');
  });

  test("says plainly what the file shows before it is shared", async () => {
    const words = await text("lib/bug-text.ts");
    expect(words).toContain("someone can work out what you typed");
    expect(words).toContain("Password and private fields are never recorded");
    expect(words).toContain("TitanKeys doesn't upload it");
    expect(words.toLowerCase().includes("anonym")).toBe(false);
  });
});
