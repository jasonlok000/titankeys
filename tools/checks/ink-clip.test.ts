import { expect, test } from "bun:test";
import { createHash } from "node:crypto";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

// A focused field's text drew over the pinned header once it scrolled under it (phone check 54, the report form at font scale
// 2.0; Snippets and Shorthand too). Android draws a focused field with a real EditText over Ink's canvas, and Ink placed it
// without a clip. TitanKeys' Ink patch sends the scrolling area with the editor's place, and the editor shows only what's in it.
const root = join(import.meta.dir, "../..");
const patchPath = join(root, "tools/ink/titankeys.patch");
const patch = readFileSync(patchPath, "utf8");

const CORE = [
  "+        let clip = self.scene.scroll_clip.filter(|_| input.scrolling);",
  '+                "clipTop": clip.map(|clip| clip.y),',
  '+                "clipBottom": clip.map(|clip| clip.y + clip.height),',
];
const ANDROID = [
  '+        clipTop = bounds.optDouble("clipTop", Double.NaN)',
  '+        clipBottom = bounds.optDouble("clipBottom", Double.NaN)',
  "+        editor.clipBounds = android.graphics.Rect(0, top, params.width.coerceAtLeast(0), bottom)",
  "+        editor.isCursorVisible = bottom > top",
  // Again after each layout, as onLayout moves a one-line field by its baseline.
  "+            clipToScroll()",
];

test("TitanKeys' Ink patch clips a focused field's editor to the scrolling area", () => {
  for (const line of [...CORE, ...ANDROID]) expect({ line, inPatch: patch.includes(line) }).toEqual({ line, inPatch: true });
  expect(patch).toContain("+++ b/crates/ink-core/src/native_editor.rs");
  expect(patch).toContain("+++ b/platform/android/app/src/textInput/kotlin/com/vandam/ink/SystemKeyboardAdapter.kt");
});

test("every build carries the patch: verify applies it, and the release and Play builds refuse an Ink without it", () => {
  for (const script of ["tools/release.sh", "tools/play.sh"]) {
    expect(read(script)).toContain('git -C ../ink apply --reverse --check "$patch"');
    expect(read(script)).toContain('patch="$PWD/tools/ink/titankeys.patch"');
  }
});

test("the Ink beside this build copy has the clip, once verify has applied this patch to it", () => {
  // verify.sh applies the patch to ../ink and marks it with the patch's sha256 (as `sha256sum <`). A quick try.sh copy may sit
  // beside an Ink another branch's patch is on; then there's nothing of this patch's to look for.
  const ink = join(root, "../ink");
  const marker = join(ink, ".titankeys-patch");
  const sha = createHash("sha256").update(readFileSync(patchPath)).digest("hex");
  if (!existsSync(marker) || !readFileSync(marker, "utf8").startsWith(sha)) return;
  const core = readFileSync(join(ink, "crates/ink-core/src/native_editor.rs"), "utf8");
  const android = readFileSync(join(ink, "platform/android/app/src/textInput/kotlin/com/vandam/ink/SystemKeyboardAdapter.kt"), "utf8");
  for (const line of CORE) expect({ line, applied: core.includes(line.slice(1)) }).toEqual({ line, applied: true });
  for (const line of ANDROID) expect({ line, applied: android.includes(line.slice(1)) }).toEqual({ line, applied: true });
});

function read(path: string) {
  return readFileSync(join(root, path), "utf8");
}
