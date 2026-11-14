// Bug hunt #37: with fn as Ctrl (the default), fn never picked a group on the Sym panel, as Ctrl took it first.
import { expect, test } from "bun:test";

const root = new URL("../../", import.meta.url);
const read = (path: string) => Bun.file(new URL(path, root)).text();

test("fn picks a Sym panel group before Ctrl takes it", async () => {
  const ime = await read("android/kotlin/com/atlas/keyboard/AtlasIme.kt");
  const keyDown = ime.slice(ime.indexOf("override fun onKeyDown("), ime.indexOf("override fun onKeyUp("));
  const group = keyDown.indexOf("keyCode == Modifiers.FN && panelOpen && isInputViewShown && categoryKey(event)");
  const ctrl = keyDown.indexOf("if (ctrlKey(event))");
  expect(group).toBeGreaterThan(0);
  expect(ctrl).toBeGreaterThan(0);
  expect(group).toBeLessThan(ctrl);
  // fn is a group key on the panel's bottom row.
  expect(ime).toMatch(/Modifiers\.FN -> Layout\.FN/);
});
