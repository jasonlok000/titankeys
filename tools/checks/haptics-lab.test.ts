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
  expect(operations.map(match => match[1]).sort()).toEqual(["labExperiment", "labFlickHold", "labGraph", "labPlayFlick", "labSelectionEnd"]);
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

test("the graph screen shows nothing outside a debug package", () => {
  const screen = read("android/kotlin/com/atlas/keyboard/HapticsLabActivity.kt");
  const onCreate = screen.slice(screen.indexOf("override fun onCreate("));
  // The check comes before anything is built or shown, and a build without the lab closes the screen.
  expect(onCreate).toMatch(/^override fun onCreate\(savedInstanceState: Bundle\?\) \{\s+super\.onCreate\(savedInstanceState\)\s+\/\/[^\n]*\n\s+if \(!HapticsLab\.available\(this\)\) \{\s+finish\(\)\s+return\s+\}/);
  expect(onCreate.indexOf("HapticsLab.available(this)")).toBeLessThan(onCreate.indexOf("setContentView"));
  // Other apps can't open it, and only the lab, which the adapter reaches through lab(), starts it.
  const manifest = read("android/src/main/AndroidManifest.xml");
  expect(manifest).toMatch(/android:name="com\.atlas\.keyboard\.HapticsLabActivity"\s+android:exported="false"/);
  const users = [...new Bun.Glob("**/*.{kt,java}").scanSync(join(root, "android"))].filter(path =>
    !path.endsWith("HapticsLabActivity.kt") && read(join("android", path)).includes("HapticsLabActivity"));
  expect(users.map(path => path.replaceAll("\\", "/"))).toEqual(["kotlin/com/atlas/keyboard/HapticsLab.kt"]);
});

test("only a debug package reads or saves a tried pattern", () => {
  const haptics = read("android/kotlin/com/atlas/keyboard/Haptics.kt");
  expect(haptics).toContain('private fun debug(context: Context) = context.packageName.endsWith(".debug")');
  // Every other build plays the rulebook's pattern before it looks at the stored ones.
  expect(haptics).toMatch(/fun pattern\(context: Context, family: Family\): List<Long> \{\s+if \(!debug\(context\)\) return family\.pattern\n/);
  expect(haptics).toMatch(/fun tryPattern\(context: Context, family: Family, pattern: List<Long>\?\) \{\s+check\(debug\(context\)\)/);
  // The stored patterns are read in one place, and only the lab tries one.
  expect(haptics.match(/getString\(patternKey/g)?.length).toBe(1);
  const users = [...new Bun.Glob("**/*.{kt,java}").scanSync(join(root, "android"))].filter(path =>
    read(join("android", path)).includes("Haptics.tryPattern"));
  expect(users.map(path => path.replaceAll("\\", "/"))).toEqual(["kotlin/com/atlas/keyboard/HapticsLab.kt"]);
});

test("every haptic is our own waveform, with none of the phone's ready-made effects", () => {
  for (const path of new Bun.Glob("**/*.{kt,java}").scanSync(join(root, "android"))) {
    const source = read(join("android", path));
    expect(source.includes("createPredefined"), path).toBe(false);
    expect(source.includes("startComposition"), path).toBe(false);
    expect(/VibrationEffect\.EFFECT_/.test(source), path).toBe(false);
  }
  const haptics = read("android/kotlin/com/atlas/keyboard/Haptics.kt");
  expect(haptics).toContain("VibrationEffect.createWaveform(");
  expect(haptics).toContain("VibrationEffect.createOneShot(pattern[0], VibrationEffect.DEFAULT_AMPLITUDE)");
});

test("the old adb length receivers are gone", () => {
  const ime = read("android/kotlin/com/atlas/keyboard/AtlasIme.kt");
  expect(ime).not.toContain("MOVING_MILLIS");
  expect(ime).not.toContain("STEP_MILLIS");
});

test("the lab shows what the keyboard really plays, from a feed only the debug build fills", () => {
  const haptics = read("android/kotlin/com/atlas/keyboard/Haptics.kt");
  // The keyboard and the lab share one process, so the feed is a plain object. Nothing else may run them apart.
  expect(read("android/src/main/AndroidManifest.xml")).not.toContain("android:process");
  // The feed starts off, and only a debug package turns it on and records into it.
  expect(haptics).toMatch(/object HapticFeed \{\s+@Volatile\s+var enabled = false/);
  expect(haptics).toMatch(/fun record\(event: HapticEvent\) \{\s+if \(!enabled\) return/);
  expect(haptics).toContain("private val feeds = debug(context)");
  expect(haptics).toContain("if (feeds) HapticFeed.enabled = true");
  expect(haptics.match(/HapticFeed\.record\(/g)?.length).toBe(1);
  expect(haptics).toContain("if (feeds) HapticFeed.record(");
  const users = [...new Bun.Glob("**/*.{kt,java}").scanSync(join(root, "android"))].filter(path =>
    !path.endsWith("Haptics.kt") && read(join("android", path)).includes("HapticFeed"));
  expect(users.map(path => path.replaceAll("\\", "/"))).toEqual(["kotlin/com/atlas/keyboard/HapticsLabActivity.kt"]);
  // The lab stops listening as it goes, so the keyboard never holds on to a closed screen.
  const screen = read("android/kotlin/com/atlas/keyboard/HapticsLabActivity.kt");
  expect(screen).toMatch(/override fun onPause\(\) \{[^}]*HapticFeed\.stop\(\)/);
  expect(screen).toContain("HapticFeed.listen {");
});

test("the lab has a practice field, a live timeline and an editor with small Play and Reset", () => {
  const screen = read("android/kotlin/com/atlas/keyboard/HapticsLabActivity.kt");
  expect(screen).toContain('val practice = field("Type, swipe, steer, open panels and menus here.", 3)');
  expect(screen).toContain('card("Last 5 seconds", timeline, key())');
  // Every haptic in the list opens its family's editor: a held delete's word opens Hit's.
  expect(screen).toContain("setOnClickListener { edit(event.haptic.family, event.haptic) }");
  expect(screen).toContain('button("Play", "Play ${family.label} alone") { lab.preview(family) }');
  expect(screen).toContain('button("Reset", "Reset ${family.label}")');
  // Nothing plays a haptic alone but the editors' Play.
  expect(screen.match(/lab\.preview\(/g)?.length).toBe(1);
  expect(screen.match(/lab\.playHold\(/g)?.length).toBe(1);
  expect(screen).not.toContain("lab.play(");
  // The sliders keep every gap at the rulebook's 35 ms or more.
  expect(read("android/kotlin/com/atlas/keyboard/Haptics.kt")).toContain("val GAP_MILLIS = MIN_GAP_MILLIS..200L");
});

test("two taps above Space play no only in the guide's practice field", () => {
  const ime = read("android/kotlin/com/atlas/keyboard/AtlasIme.kt");
  expect(ime).not.toContain("Haptic.TwoTapsAbove");
  // The play sits after the check that returns outside the guide's field.
  const block = ime.slice(ime.indexOf("private val confirmTwoAbove = Runnable {"));
  const body = block.slice(0, block.indexOf("\n    }\n"));
  expect(body).toMatch(/if \(!inGuide \|\| !cursorGesture\.confirmTwoAbove\(\)\) return@Runnable[\s\S]*haptics\.play\(Haptic\.NothingToPick\)/);
  // And the runnable is only ever posted in the guide's field.
  expect(ime).toContain("else if (inGuide && cursorGesture.twoAbove) main.postAtTime(confirmTwoAbove,");
  expect(ime.match(/confirmTwoAbove,/g)?.length).toBe(1);
});

test("steering ticks when it picks up after a slip, and exact typing's chips play", () => {
  const ime = read("android/kotlin/com/atlas/keyboard/AtlasIme.kt");
  expect(ime).toMatch(/if \(cursor\.resume\(first, composer\.liftOffMillis\.toLong\(\)\)\) \{[^}]*haptics\.play\(Haptic\.SteerResumed\)/);
  expect(ime).toContain("haptics.play(Haptic.chipTapped(wasLocked = sticky.level(key) == ExactTyping.Level.Locked))");
  // The level is read before the tap changes it.
  expect(ime.indexOf("Haptic.chipTapped(")).toBeLessThan(ime.indexOf("sticky.tap(key)"));
});

test("any key or touch cuts a flick's dots and the dot ticks still to come, at the same point", () => {
  const ime = read("android/kotlin/com/atlas/keyboard/AtlasIme.kt");
  expect(ime).toMatch(/private fun interruptGesture\(\) \{\s+strip\?\.gesture\?\.interrupt\(\)\s+haptics\.cutDots\(\)\s+\}/);
  // Nothing else cuts the dots, so the ticks can't outlive them: a key, a touch that's no delete, a word's outcome, the cursor.
  expect(ime.match(/gesture\?\.interrupt\(\)/g)?.length).toBe(1);
  expect(ime.match(/interruptGesture\(\)/g)?.length).toBe(5);
  // A delete's dots wait for what the next touch turns out to be: another delete joins them, anything else cuts them.
  expect(ime).toContain("if (strip?.gesture?.softDelete != true) interruptGesture()");
  expect(ime).toContain("if (!(outcome is Contact.Outcome.Swipe && outcome.deletesWord) && strip?.gesture?.softDelete == true) interruptGesture()");
  // A hold takes the bar from them, and their ticks stop with it.
  expect(ime).toMatch(/haptics\.cutDots\(\)\s+\/\/ The contact was a delete[\s\S]{0,700}strip\?\.gesture\?\.deleteHeld\(/);
});

test("taps that find no word, and steering with no text, do nothing but play Nothing to pick", () => {
  const ime = read("android/kotlin/com/atlas/keyboard/AtlasIme.kt");
  // A pick plays Lock only when it found a word.
  expect(ime).toContain("haptics.play(Haptic.picked(found = word != null))");
  expect(ime).not.toContain("haptics.play(Haptic.WordPicked)\n        if (word == null)");
  // A triple tap with nothing in front picks nothing, and its held touch neither registers nor drags.
  expect(ime).toMatch(/nothingHere\(words = true\) -> nothingToPick\(\)/);
  expect(ime).toMatch(/if \(taps == 0 \|\| gestureRegistered \|\| gestureEmpty\) return/);
  expect(ime).toMatch(/if \(gestureEmpty\) \{\s*contact\.cancelActive\(\)\s*return\s*\}/);
  // Steering with no text says no once, at the hold or at a slide that comes first.
  expect(ime).toMatch(/if \(!words && nothingHere\(words = false\)\) \{\s*nothingToPick\(\)/);
  expect(ime).toContain("if (!gestureEmpty && !gestureRegistered && !drag.words && nothingHere(words = false)) nothingToPick()");
  // A new touch starts fresh.
  expect(ime).toMatch(/gestureRegistered = false\s*gestureEmpty = false/);
  // Its own switch in Space gestures, with the lab's words.
  const lib = read("lib/haptics.ts");
  expect(lib).toContain('id: "nothing_to_pick", label: "Nothing to pick"');
  expect(lib).not.toContain("two_taps_above");
  expect(read("android/kotlin/com/atlas/keyboard/HapticTimeline.kt")).toContain('Haptic.NothingToPick -> "Taps found no word"');
});

test("a held delete's words hit at the hold's own pace, through their own switch", () => {
  const haptics = read("android/kotlin/com/atlas/keyboard/Haptics.kt");
  const ime = read("android/kotlin/com/atlas/keyboard/AtlasIme.kt");
  expect(ime).toContain("haptics.heldDeleteWord(word, deleteHold.pace, deleteHold.repeatMillis)");
  expect(ime).not.toContain("haptics.play(Haptic.DeleteWord)");
  // One hit per word, as HoldHit shortens it, through the switches and the gate.
  expect(haptics).toMatch(/fun heldDeleteWord\(word: Int, pace: Float, repeatMillis: Long\): HapticResult \{\s+val hit = pattern\(context, Family\.Hit\)\.first\(\)\s+return attempt\(Haptic\.DeleteWord, listOf\(HoldHit\.millis\(hit, pace, repeatMillis\)\), word\)/);
  expect(haptics).toContain('DeleteWord("delete_word", HapticGroup.Delete, Family.Hit)');
  // Hit is texture, so the 300 ms repeat rule never drops a word, and a wall's quiet still silences it.
  expect(haptics).toContain("val texture get() = this == Texture || this == Boundary || this == Crunch || this == Hit");
  // The lab can play a whole hold from Hit's editor.
  const screen = read("android/kotlin/com/atlas/keyboard/HapticsLabActivity.kt");
  expect(screen).toContain("if (family == Family.Hit) {");
  expect(screen).toContain('button("Play a hold", ');
});

test("the held delete fade is gone, so an old stored setting does nothing", () => {
  // It was stored on the owner's phone: with no code that reads it, the stored value can do nothing.
  const sources = ["android", "app", "lib"].flatMap(dir =>
    [...new Bun.Glob("**/*.{kt,java,ts,tsx}").scanSync(join(root, dir))].map(path => join(dir, path)));
  for (const path of sources) {
    const text = read(path);
    for (const word of ["debug_pattern_held_delete_fade", "debug_held_delete_fade_on", "heldDeleteFading", "fun fadeOn(", "Thinned"])
      expect(text.includes(word), `${path} has ${word}`).toBe(false);
  }
  // Nothing opens the lab's screen on an experiment any more.
  expect(read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt")).toContain('lab().showGraph(data.getJSONArray("haptics").toString())');
  expect(read("android/kotlin/com/atlas/keyboard/HapticsLabActivity.kt")).not.toContain("OPEN");
});

test("a held delete waits 400 ms, with no slider for it", () => {
  // The owner tried 100 ms and went back to 400 (2026-11-12): it is simply the pause, in every build.
  const swipe = read("android/kotlin/com/atlas/keyboard/DeleteSwipe.kt");
  expect(swipe).toContain("private const val HOLD_MILLIS = 400L");
  expect(swipe).toContain("internal fun deleteHoldPauseMillis() = HOLD_MILLIS");
  for (const path of ["android/kotlin/com/atlas/keyboard/Haptics.kt", "android/kotlin/com/atlas/keyboard/HapticsLab.kt",
    "android/kotlin/com/atlas/keyboard/HapticsLabActivity.kt", "android/kotlin/com/atlas/keyboard/AtlasIme.kt",
    "android/src/main/res/values/strings.xml", "lib/atlas.ts"]) expect(read(path)).not.toMatch(/readHoldPause|tryHoldPause|HOLD_PAUSE|hold_pause|holdPause/);
});

test("only a debug package reads or saves a tried flick dots wait, and the lab's page tries and plays it", () => {
  const haptics = read("android/kotlin/com/atlas/keyboard/Haptics.kt");
  // Every other build returns before it looks at the stored wait, so the bar keeps its default.
  expect(haptics).toMatch(/fun readFlickHold\(context: Context\): Long\? \{\s+if \(!debug\(context\)\) return null\n/);
  expect(haptics).toMatch(/fun tryFlickHold\(context: Context, millis: Long\?\) \{\s+check\(debug\(context\)\)/);
  expect(haptics.match(/getLong\(FLICK_HOLD_KEY/g)?.length).toBe(1);
  // Only what Haptics reads sets the bar's wait.
  for (const path of ["AtlasIme.kt", "HapticsLab.kt"].map(name => `android/kotlin/com/atlas/keyboard/${name}`))
    for (const line of read(path).split("\n").filter(line => line.includes("GestureDisplay.triedFlickHold = ")))
      expect(line).toContain("= Haptics.readFlickHold(");
  const display = read("android/kotlin/com/atlas/keyboard/GestureDisplay.kt");
  expect(display).toContain("fun flickHoldMillis() = triedFlickHold ?: FLICK_HOLD");
  expect(haptics).toContain("val FLICK_HOLD_RANGE = 0L..600L");
  // Only a single flick's dots wait the tried time; a lifted hold's keep the default.
  expect(display).toContain("val wait = if (b == 0 && fromHold) FLICK_HOLD else flickHoldMillis()");
  expect(read("android/kotlin/com/atlas/keyboard/AtlasIme.kt")).toContain("GestureDisplay.triedFlickHold = Haptics.readFlickHold(this)?.toFloat()");
  // On the lab's main page, in Experiments, where the owner looks, with Play.
  const page = read("app/settings/haptics-lab.tsx");
  const experiments = page.slice(page.indexOf('<Section title="Experiments">'), page.indexOf('<Section title="Patterns">'));
  // Its value can be typed, 0 to 600 ms, applied on Done, and Shorter, Longer, Play and Reset stay.
  expect(experiments).toContain('<TextInput label="Flick dots wait"');
  expect(experiments).toContain('inputMode="numeric" action="done"');
  expect(experiments).toContain("onSubmit={applyDraft}");
  // Shorter, Longer, Play and Reset are rows, so they read as things to tap, never one line of words.
  for (const row of ['<Field label="Play the dots"', '<Field label="Shorter"', '<Field label="Longer"', '<Field label="Reset"']) expect(experiments).toContain(row);
  expect(experiments).not.toContain('<Stack axis="horizontal"');

  expect(experiments).toContain('<Toggle label="Tick at delete zones"');
  // One delete glow, along the bottom from the corner: no list of places any more, and no shortcuts experiment.
  expect(page).not.toContain('<Section title="Delete glow">');
  expect(haptics).not.toContain("fun deleteGlow(");
  expect(read("android/kotlin/com/atlas/keyboard/GestureDisplay.kt")).not.toContain("enum class DeleteGlow");
  expect(experiments).not.toContain("Haptics for shortcuts");
  expect(haptics).not.toContain("fun shortcutHaptics(");
  expect(page).toContain("const WAIT_MAX = 600;");
});

test("the lab's page has no fade switch, and its experiments keep one operation", () => {
  const lab = read("android/kotlin/com/atlas/keyboard/HapticsLab.kt");
  expect(lab).toContain("DOTS_SPEED_UP -> Haptics.tryDotsSpeedUp(context, on)");
  expect(lab).toContain("ZONE_TICKS -> Haptics.tryZoneTicks(context, on)");
  expect(lab.toLowerCase()).not.toContain("fade");
  const page = read("app/settings/haptics-lab.tsx");
  const experiments = page.slice(page.indexOf('<Section title="Experiments">'), page.indexOf('<Section title="Patterns">'));
  expect(experiments).toContain('<Toggle label="Dots speed up when long"');
  expect(page.toLowerCase()).not.toContain("fade");
  expect(read("lib/atlas.ts").toLowerCase()).not.toContain("fade");
  // One place for experiments: the lab's screen has no switch of its own.
  const screen = read("android/kotlin/com/atlas/keyboard/HapticsLabActivity.kt");
  expect(screen).not.toContain("Switch(");
  expect(screen.toLowerCase()).not.toContain("fade");
});

test("the removed shortcuts experiment leaves nothing that reads its stored switch", () => {
  // It was on on the owner's phone: with no code that reads it, the stored value can do nothing.
  const sources = ["android", "app", "lib"].flatMap(dir =>
    [...new Bun.Glob("**/*.{kt,java,ts,tsx}").scanSync(join(root, dir))].map(path => join(dir, path)));
  for (const path of sources) {
    const text = read(path);
    expect(text.includes("debug_shortcut_haptics") || text.includes("shortcutHaptics")).toBe(false);
  }
});

test("the lab tries the mark at a triple press's moving end, and only the debug build reads it", () => {
  const page = read("app/settings/haptics-lab.tsx");
  expect(page).toContain('<Section title="Triple press selection end">');
  expect(page).toContain("onChange={id => void setLabSelectionEnd(id)}");
  const haptics = read("android/kotlin/com/atlas/keyboard/Haptics.kt");
  expect(haptics).toMatch(/fun selectionEndId\(context: Context\): String\? \{\s+if \(!debug\(context\)\) return null/);
  // Every selection a Space gesture makes takes the mark in use as it starts.
  const ime = read("android/kotlin/com/atlas/keyboard/AtlasIme.kt");
  expect(ime.match(/GestureDisplay\.selectionEnd = SelectionEnd\.stored\(Haptics\.selectionEndId\(this\)\)/g)?.length).toBe(3);
});

test("the lab can try exact typing's keys as texture, off by default, and only the debug build reads it", () => {
  const page = read("app/settings/haptics-lab.tsx");
  expect(page).toContain('<Toggle label="Exact-typing keys feel like a key (texture)" value={lab.experiments.exactKeysTexture ?? false}');
  const haptics = read("android/kotlin/com/atlas/keyboard/Haptics.kt");
  expect(haptics).toContain("fun exactKeysTexture(context: Context) = debug(context) && preferences(context).getBoolean(EXACT_KEYS_TEXTURE, false)");
  expect(read("android/kotlin/com/atlas/keyboard/HapticsLab.kt")).toContain("EXACT_KEYS_TEXTURE -> Haptics.tryExactKeysTexture(context, on)");
  // The bar's keys go through it.
  expect(read("android/kotlin/com/atlas/keyboard/AtlasIme.kt")).toContain("haptics.exactKey()");
});

test("a delete with nothing says no once, a hold that ran out hits the wall, and switches lock on and release off", () => {
  const ime = read("android/kotlin/com/atlas/keyboard/AtlasIme.kt");
  expect(ime).toContain("if (nothingLeft && deleteWall.reached()) haptics.play(Haptic.deleteFoundNothing(tookAny = false))");
  expect(ime).toContain("if (deleteWall.reached()) haptics.wall(Haptic.deleteFoundNothing(tookAny = true))");
  expect(ime).toContain("haptics.play(Haptic.quickSetting(on = quickSettingOn(item)))");
  const adapter = read("android/kotlin/com/vandam/ink/ConnectedAdapter.kt");
  expect(adapter).toContain("haptics.play(Haptic.bugRecording(started = true))");
  expect(adapter).toContain("haptics.play(Haptic.bugRecording(started = false))");
  expect(read("android/kotlin/com/atlas/keyboard/BugRecording.kt")).toContain("Haptics(context).play(Haptic.bugRecording(started = false))");
});
