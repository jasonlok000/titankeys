// Bug hunt: with Smart Launcher's search open, the keys must not be taken for its field once the screen is off or locked, as
// Android can leave the field open behind the lock screen, whose PIN then went to the launcher.
import { expect, test } from "bun:test";

const root = new URL("../../", import.meta.url);
const read = (path: string) => Bun.file(new URL(path, root)).text();

test("Smart Launcher's taken keys stop while the screen is off or locked", async () => {
  const ime = await read("android/kotlin/com/atlas/keyboard/AtlasIme.kt");
  expect(ime).toContain("takenKeys.takes(code, down, watchingKeys && screenInUse())");
  const inUse = ime.slice(ime.indexOf("private fun screenInUse()"), ime.indexOf("private val screenOff"));
  expect(inUse).toContain("isInteractive");
  expect(inUse).toContain("isKeyguardLocked");
});
