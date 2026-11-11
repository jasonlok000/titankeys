// Checks that the Haptics page has a switch for every haptic in the keyboard, in the keyboard's groups, and no others.
import { expect, test } from "bun:test";
import { hapticGroups } from "../../lib/haptics";

const root = new URL("../../", import.meta.url);

test("the Haptics page lists every haptic in Haptics.kt", async () => {
  const source = await Bun.file(new URL("android/kotlin/com/atlas/keyboard/Haptics.kt", root)).text();
  const body = source.slice(source.indexOf("enum class Haptic("), source.indexOf("/** Its switch in"));
  const native = [...body.matchAll(/^\s+\w+\("(\w+)", HapticGroup\.(\w+), Family\.\w+\)/gm)].map(match => ({ id: match[1], group: match[2] }));
  expect(native.length).toBeGreaterThan(30);
  const page = hapticGroups.flatMap(group => group.haptics.map(({ id }) => id));
  expect(page).toEqual(native.map(({ id }) => id));
  // The page's groups follow HapticGroup's order, one section each.
  const groups = [...new Set(native.map(({ group }) => group))];
  expect(groups.length).toBe(hapticGroups.length);
  for (const [index, group] of hapticGroups.entries())
    for (const { id } of group.haptics) expect(native.find(haptic => haptic.id === id)?.group).toBe(groups[index]);
});

test("the lab says where each haptic plays", () => {
  for (const { id, about } of hapticGroups.flatMap(group => group.haptics)) {
    expect(about.length, id).toBeGreaterThan(10);
    expect(about.endsWith("."), id).toBe(true);
  }
});
