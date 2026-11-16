import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// Feedback without GitHub (#74): the mail app, one address everywhere, and a crash offer that never sends by itself.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");
const ADDRESS = "titankeys@everyones-niche.com";

test("one public address, in the app and every public text", () => {
  expect(read("android/kotlin/com/atlas/keyboard/Mail.kt")).toContain(`const val ADDRESS = "${ADDRESS}"`);
  expect(read("lib/atlas.ts")).toContain(`export const FEEDBACK_ADDRESS = "${ADDRESS}"`);
  for (const file of ["site/privacy/index.html", "docs/play/data-safety.md", "docs/play/listing.md", "docs/play/testers.md", "CONTRIBUTING.md"]) {
    const text = read(file);
    expect(`${file}: ${text.includes(ADDRESS)}`).toBe(`${file}: true`);
    expect(`${file}: ${text.includes("vignette@")}`).toBe(`${file}: false`);
  }
});

test("Send feedback opens the mail app, with no GitHub row", () => {
  const feedback = read("app/settings/feedback.tsx");
  expect(feedback).not.toContain("github.com");
  expect(feedback).toContain("TitanKeys bug, ${ready.version}");
  expect(feedback).toContain('"TitanKeys idea"');
  expect(feedback).toContain("sendMail(subject, body)");
  // Crash TitanKeys shows only in TitanKeys Debug, and the app refuses it in any other build.
  expect(feedback).toContain('{ready?.build === "debug" &&');
  expect(read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt"))
    .toContain('check(HapticsLab.available(activity)) { "Crash TitanKeys is only in TitanKeys Debug" }');
  expect(read("tools/checks/feedback.sh")).toContain("android.intent.action.SENDTO -d mailto:");
});

test("a recording over 10 MB is zipped before it's attached", () => {
  const bug = read("android/kotlin/com/atlas/keyboard/BugRecording.kt");
  expect(bug).toContain("const val ZIP_OVER_BYTES = 10L * 1024 * 1024");
  expect(bug).toContain("if (file.length() > ZIP_OVER_BYTES) file = zip(file)");
  expect(read("app/settings/bug.tsx")).toContain("const ZIP_OVER_BYTES = 10 * 1024 * 1024;");
});

test("the crash report holds no typed text, and is deleted once sent or dismissed", () => {
  const crash = read("android/kotlin/com/atlas/keyboard/CrashReport.kt");
  // Only class names and stack frames: never getMessage, toString or printStackTrace, which carry the message.
  expect(crash).not.toMatch(/\.message\b|localizedMessage|printStackTrace|stackTraceToString|error\.toString\(\)/);
  const offer = read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
  const start = offer.indexOf("private fun offerCrashReport()");
  const body = offer.slice(start, offer.indexOf("\n    }\n", start));
  expect(body).toContain("CrashReport.lend(activity, report)");
  expect(body).toContain('.setNegativeButton("No thanks") { _, _ -> CrashReport.clear(activity) }');
  expect(body).toContain(".setOnCancelListener { CrashReport.clear(activity) }");
  expect(crash).toMatch(/fun lend\([\s\S]*?clear\(context\)/);
  // Nothing new reaches the network.
  for (const file of ["android/kotlin/com/atlas/keyboard/CrashReport.kt", "android/kotlin/com/atlas/keyboard/Mail.kt"])
    expect(read(file)).not.toMatch(/HttpsURLConnection|URL\(|Socket/);
});
