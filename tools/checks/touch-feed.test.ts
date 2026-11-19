import { expect, test } from "bun:test";
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

// The touch simulator's touchpad (tools/dev/touch): a feed of built touchpad events into the keyboard. It's TitanKeys Debug's
// alone, and only adb can use it: in TitanKeys it doesn't exist, and the hook it hands events to is never set.
const root = join(import.meta.dir, "../..");
const read = (path: string) => readFileSync(join(root, path), "utf8");

test("the feed is only in TitanKeys Debug, and only adb can send to it", () => {
  expect(existsSync(join(root, "android/kotlin/com/titankeys/keyboard/TouchFeed.kt"))).toBe(false);
  expect(read("android/src/main/AndroidManifest.xml")).not.toContain("TouchFeed");
  const entry = read("android/debug/recorder/manifest.xml").match(/<receiver\s+android:name="com\.titankeys\.keyboard\.TouchFeed"[^>]*\/>/)?.[0];
  expect(entry).toBeDefined();
  expect(entry).toContain('android:permission="android.permission.DUMP"');
  expect(read("android/debug/recorder/TouchFeed.kt")).toContain('packageName.endsWith(".debug")');
});

test("TitanKeys never sets the hook: only TitanKeys Debug's block does, and the keyboard clears it as it goes", () => {
  const ime = read("android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt");
  const sets = [...ime.matchAll(/TouchpadFeedHook\.dispatch = \{/g)].map(match => match.index!);
  expect(sets.length).toBe(1);
  const debugBlock = ime.lastIndexOf("if (packageName.endsWith(DEBUG_PACKAGE_SUFFIX)) {", sets[0]);
  expect(debugBlock).toBeGreaterThan(-1);
  // Nothing closes that block between its start and the hook.
  expect(ime.slice(debugBlock, sets[0]).split("{").length).toBe(ime.slice(debugBlock, sets[0]).split("}").length + 1);
  expect(ime).toContain("TouchpadFeedHook.dispatch = null");
  // Nothing else in the app uses it: only its own file and the keyboard, which sets and clears it.
  const files = (folder: string): string[] => readdirSync(join(root, folder), { withFileTypes: true }).flatMap(entry =>
    entry.isDirectory() ? files(join(folder, entry.name)) : /\.(kt|java)$/.test(entry.name) ? [join(folder, entry.name).replaceAll("\\", "/")] : []);
  expect(files("android/kotlin").filter(path => read(path).includes("TouchpadFeedHook")).sort()).toEqual([
    "android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt", "android/kotlin/com/titankeys/keyboard/TouchpadFeedHook.kt"]);
});

// The key feed (KeyFeed.kt, TK_KEYFEED=1 in touch.sh): the same rules. It plays keys, Shift included, into the keyboard's own key
// handling, so TitanKeys must never have it.
test("the key feed is only in TitanKeys Debug, only adb can send to it, and only Debug sets its hook", () => {
  for (const file of ["KeyFeed.kt", "KeyFeedPlan.kt"])
    expect(existsSync(join(root, "android/kotlin/com/titankeys/keyboard", file))).toBe(false);
  expect(read("android/src/main/AndroidManifest.xml")).not.toContain("KeyFeed");
  const entry = read("android/debug/recorder/manifest.xml").match(/<receiver\s+android:name="com\.titankeys\.keyboard\.KeyFeed"[^>]*\/>/)?.[0];
  expect(entry).toBeDefined();
  expect(entry).toContain('android:permission="android.permission.DUMP"');
  expect(read("android/debug/recorder/KeyFeed.kt")).toContain('packageName.endsWith(".debug")');
  const ime = read("android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt");
  const sets = [...ime.matchAll(/KeyFeedHook\.dispatch = \{/g)].map(match => match.index!);
  expect(sets.length).toBe(1);
  const debugBlock = ime.lastIndexOf("if (packageName.endsWith(DEBUG_PACKAGE_SUFFIX)) {", sets[0]);
  expect(debugBlock).toBeGreaterThan(-1);
  expect(ime.slice(debugBlock, sets[0]).split("{").length).toBe(ime.slice(debugBlock, sets[0]).split("}").length + 1);
  expect(ime).toContain("KeyFeedHook.dispatch = null");
});

test("the feed shifts its events as the firmware shifts real ones, by Touchpad.kt's own rule", () => {
  const feed = read("android/debug/recorder/TouchFeed.kt");
  const touchpad = read("android/kotlin/com/titankeys/keyboard/Touchpad.kt");
  for (const part of ['"persist.sys.touchpad_forcescroll"', "1200 -> 240f", "1440 -> 360f", "Surface.ROTATION_0", '"Titan_2"'])
    for (const source of [feed, touchpad]) expect(source).toContain(part);
  expect(feed).toContain("y = at.y * yScale + yOffset");
});

test("only TitanKeys Debug opens the guide at a slide from adb; TitanKeys always starts at the first", () => {
  expect(existsSync(join(root, "android/kotlin/com/titankeys/keyboard/GuideStart.kt"))).toBe(false);
  expect(read("android/src/main/AndroidManifest.xml")).not.toContain("GuideStart");
  const entry = read("android/debug/recorder/manifest.xml").match(/<receiver\s+android:name="com\.titankeys\.keyboard\.GuideStart"[^>]*\/>/)?.[0];
  expect(entry).toContain('android:permission="android.permission.DUMP"');
  expect(read("android/debug/recorder/GuideStart.kt")).toContain('packageName.endsWith(".debug")');
  const guide = read("android/kotlin/com/titankeys/keyboard/GuideActivity.kt");
  expect(guide).toMatch(/private fun debugStartStep\(\): String\? =\s+if \(!packageName\.endsWith\("\.debug"\)\) null/);
  // The one place the guide starts afresh uses it, and nothing else reads the extra.
  expect(guide.match(/DEBUG_START_STEP/g)?.length).toBe(2);
  expect(guide).toContain("flow.start(debugStartStep())");
});

test("Debug queues input only after it verifies the owned editor and hook", () => {
  for (const [name, hook] of [["KeyFeed", "KeyFeedHook"], ["TouchFeed", "TouchpadFeedHook"]]) {
    const source = read(`android/debug/recorder/${name}.kt`);
    expect(source).toContain(`val dispatch = ${hook}.dispatch`);
    expect(source).toContain("dispatch?.let { FeedEditor.capture(context.packageName) }");
    expect(source).toMatch(/if \(intent.getBooleanExtra\(FeedBurst.VERIFY_ONLY, false\)\)\s*return (?:answer\()?FeedBurst.probeAtUptime\(editor != null\)[\s\S]*burst.post\(/);
    expect(source).toMatch(/if \(!burst.begin\(\)\) return (?:answer\()?"Debug editor changed before queueing"[\s\S]*burst.post\(/);
    expect(source).toContain(`{ ${hook}.dispatch }`);
    expect(source).toContain("{ send ->");
    expect(source).toContain("send(event)");
    expect(source).not.toContain("dispatch(event)");
    expect(source).toContain("${FeedBurst.READY}");
  }
});

test("on-device fixtures use the guarded Debug key queue and keep their report private", () => {
  const app = read("android/debug/recorder/OnDeviceTestsActivity.kt");
  expect(read("android/debug/recorder/manifest.xml")).toContain('android:name="com.titankeys.keyboard.OnDeviceTestsActivity"');
  expect(read("android/src/main/AndroidManifest.xml")).not.toContain("OnDeviceTestsActivity");
  expect(app).toContain("KeyFeed().queue(this, Intent().putExtra");
  expect(app).toContain("if (!active || !hasWindowFocus())");
  expect(app).toContain("IME_FLAG_NO_PERSONALIZED_LEARNING");
  expect(app).toContain('AtomicFile(File(filesDir, "on-device-test-results.txt"))');
});

test("the focus guard checks the same owned editor, window and hook before every dispatch", () => {
  const guard = read("android/debug/recorder/FeedFocusGuard.kt");
  expect(guard).toContain("WindowInspector.getGlobalWindowViews()");
  expect(guard).toContain("window.findFocus() === editor");
  expect(guard).toContain("window.windowToken === token && editor.windowToken === token");
  expect(guard).toContain("window.viewTreeObserver === observer.get()");
  expect(guard).toContain("window.context.packageName != packageName || editor.context.packageName != packageName");
  expect(guard).toContain("window.hasWindowFocus() || !editor.isFocused");
  expect(guard).toContain("!editor.onCheckIsTextEditor()");
  expect(guard).toContain("ime.currentInputMethodInfo?.packageName == packageName && ime.isActive(editor) && ime.isAcceptingText");
  expect(guard).toContain("WeakReference(editor)");
  const lease = read("android/debug/recorder/FeedFocusLease.kt");
  expect(lease).toContain("WeakReference(dispatch)");
  expect(lease).toContain("currentDispatch()?.takeIf { it === expectedDispatch.get() }");
  expect(guard).toMatch(/main\.postAtTime\(\{\s*if \(lease.closed\) return@postAtTime\s*val dispatch = lease.checkedDispatch\(\)/);
});

test("transient focus loss, detach and event failure cancel the whole queued burst", () => {
  const guard = read("android/debug/recorder/FeedFocusGuard.kt");
  expect(guard).toContain("if (next !== editorRef.get()) lost()");
  expect(guard).toContain("if (!focused) lost()");
  expect(guard).toContain("onViewDetachedFromWindow(view: View) = lost()");
  expect(guard).toContain("unwatch = editor.watch { finish(cancelled = true) }");
  expect(guard).toMatch(/catch \(error: RuntimeException\) \{[^}]+finish\(cancelled = true\)/);
  expect(guard).toContain("main.removeCallbacksAndMessages(this)");
  expect(guard).toContain("if (lease.sameDispatch() != null)");
  expect(guard).toContain("runCatching(clearHeldState)");
  expect(guard).toContain("if (last) finish(cancelled = false)");
  expect(guard).toContain("it.removeOnGlobalFocusChangeListener(focus)");
  expect(guard).toContain("it.removeOnWindowFocusChangeListener(window)");
  expect(guard).toContain("removeOnAttachStateChangeListener(attachment)");
});

test("feeds report Android uptime for a shared start and reject unbalanced contacts", () => {
  const guard = read("android/debug/recorder/FeedFocusGuard.kt");
  const touch = read("android/debug/recorder/TouchFeed.kt");
  expect(guard).toContain('const val VERIFY_ONLY = "verify_focus_guard"');
  expect(guard).toContain('const val READY = "focus-guard-v1 ready"');
  expect(guard).toContain('if (ready) READY else "focus-guard-v1 available"');
  expect(guard).toContain('uptime=${SystemClock.uptimeMillis()}');
  expect(guard).toContain('intent.getLongExtra("start_uptime", -1).takeIf { it in now..now + maxDelay }');
  expect(touch).toContain("require(frame.millis in previous..MAX_MILLIS.toDouble())");
  expect(touch).toContain("require(!down)");
  expect(touch).toContain("TouchpadFeedHook.cancelContact?.invoke(it)");
  expect(read("android/debug/recorder/KeyFeed.kt")).toContain("KeyFeedHook.cancelHeld?.invoke(downTimes)");
});
