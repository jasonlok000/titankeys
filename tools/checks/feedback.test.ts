import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// Feedback without GitHub: the report form sends to TitanKeys' server, sealed (#97), with email as the backup (#74). One
// address everywhere, and a crash offer that never sends by itself.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");
const ADDRESS = "titankeys@everyones-niche.com";

test("one public address, in the app and every public text", () => {
  expect(read("android/kotlin/com/titankeys/keyboard/Mail.kt")).toContain(`const val ADDRESS = "${ADDRESS}"`);
  expect(read("lib/titankeys.ts")).toContain(`export const FEEDBACK_ADDRESS = "${ADDRESS}"`);
  for (const file of ["site/privacy/index.html", "docs/play/data-safety.md", "docs/play/listing.md", "docs/play/testers.md", "CONTRIBUTING.md"]) {
    const text = read(file);
    expect(`${file}: ${text.includes(ADDRESS)}`).toBe(`${file}: true`);
    expect(`${file}: ${text.includes("vignette@")}`).toBe(`${file}: false`);
  }
});

test("Send feedback and Record a bug open the form, with no GitHub row", () => {
  const feedback = read("app/settings/feedback.tsx");
  expect(feedback).not.toContain("github.com");
  expect(feedback).toContain('navigate("/settings/report")');
  expect(feedback).toContain('params: { kind: "idea" }');
  // After Stop, the form opens with the recording attached.
  expect(read("app/settings/bug.tsx")).toContain("stopBug().then(sendIt)");
  // Crash TitanKeys shows only in TitanKeys Debug, and the app refuses it in any other build.
  expect(feedback).toContain("result.data.build === \"debug\"");
  expect(read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt"))
    .toContain('check(HapticsLab.available(activity)) { "Crash TitanKeys is only in TitanKeys Debug" }');
  // The phone check follows: its rows open the form, not a mail app (tools/checks/feedback.py).
  expect(read("tools/checks/feedback.py")).not.toContain("mailto");
});

test("the form has the owner's kinds, sends only on Send, and offers email when it can't send", () => {
  const kinds = read("lib/report.ts");
  for (const label of ["Wrong word or fix", "Swiping", "Touchpad and Space taps", "Keyboard didn't show, or vanished",
    "Crash or freeze", "The guide", "Settings", "An idea", "Something else"]) expect(kinds).toContain(`label: "${label}"`);
  const ids = [...kinds.matchAll(/value: "([a-z]+)"/g)].map(match => match[1]);
  expect(read("android/kotlin/com/titankeys/connected/Reports.kt")).toContain(`val KINDS = listOf(${ids.map(id => `"${id}"`).join(", ")})`);
  const form = read("app/settings/report.tsx");
  expect(form).toContain('<Button disabled={!kind || what.trim() === ""');
  expect(form).toContain('label="Email it instead"');
  const adapter = read("android/kotlin/com/vandam/ink/ConnectedAdapter.kt");
  expect(adapter).toContain("Reports.keep(activity.noBackupFilesDir, name, zip)");
});

test("each part is sealed with the contributions key before it leaves, to the Worker's report route", () => {
  const upload = read("android/kotlin/com/titankeys/connected/ReportUpload.kt");
  expect(upload).toContain("val sealed = Seal.seal(part, recipient)");
  expect(upload).toContain("recipient: ByteArray = Connected.key(Connected.CONTRIBUTION_KEY)");
  expect(upload).toContain('"${Connected.CONTRIBUTIONS_URL}/v1/report"');
  expect(upload).toContain("connection.outputStream.use { it.write(sealed) }");
  // Reports.kt, which the JVM tests run, never touches the network.
  expect(read("android/kotlin/com/titankeys/connected/Reports.kt")).not.toMatch(/Connected\.open|HttpsURLConnection/);
  // The app's part fits the Worker's.
  const worker = read("server/contributions/src/worker.js");
  const reports = read("android/kotlin/com/titankeys/connected/Reports.kt");
  expect(worker).toContain(`export const MAX_REPORT_PARTS = ${reports.match(/const val MAX_PARTS = (\d+)/)![1]};`);
});

test("the crash report holds no typed text, and is deleted once sent or dismissed", () => {
  const crash = read("android/kotlin/com/titankeys/keyboard/CrashReport.kt");
  // Only class names and stack frames: never getMessage, toString or printStackTrace, which carry the message.
  expect(crash).not.toMatch(/\.message\b|localizedMessage|printStackTrace|stackTraceToString|error\.toString\(\)/);
  const offer = read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt");
  const start = offer.indexOf("private fun offerCrashReport()");
  const body = offer.slice(start, offer.indexOf("\n    }\n", start));
  expect(body).toContain("CrashReport.offered(activity)");
  expect(body).toContain('.setNegativeButton("No thanks") { _, _ -> CrashReport.clear(activity) }');
  expect(body).toContain(".setOnCancelListener { CrashReport.clear(activity) }");
  // Sent: gone. Offered and not sent: gone at the next open.
  expect(read("android/kotlin/com/vandam/ink/ConnectedAdapter.kt")).toContain("if (crash != null) CrashReport.clear(activity)");
  expect(crash).toMatch(/if \(File\(context\.noBackupFilesDir, OFFERED\)\.isFile\) \{\s*clear\(context\)\s*return null/);
  for (const file of ["android/kotlin/com/titankeys/keyboard/CrashReport.kt", "android/kotlin/com/titankeys/keyboard/Mail.kt"])
    expect(read(file)).not.toMatch(/HttpsURLConnection|URL\(|Socket/);
});

test("the recording goes only when asked: on after Record a bug's Stop, otherwise off; the crash report always goes (review)", () => {
  const form = read("app/settings/report.tsx");
  expect(form).toContain("useState(hasRecording && fromRecording)");
  expect(form).toContain("fromRecording={params.kind === \"recording\"}");
  expect(read("app/settings/bug.tsx")).toContain('navigate({ path: "/settings/report", params: { kind: "recording" } })');
  expect(form).toContain("{hasRecording && !recording && <Note>");
  expect(form).toContain("It always goes along.");
  expect(form).toMatch(/sendReport\(\{[^}]*recording, crash: hasCrash \}\)/);
});
