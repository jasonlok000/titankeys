import { expect, test } from "bun:test";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { parentOf } from "../../lib/settings-tree";

// ← goes up to a page's parent in Settings and × leaves Settings (lib/settings-tree.ts). These keep every page in one
// tree under /settings, and keep × on the top page only.
const root = join(import.meta.dir, "../..");
const settings = join(root, "app/settings");

function pages(dir: string): string[] {
  return readdirSync(dir).flatMap(name => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return pages(path);
    return name.endsWith(".tsx") && !name.startsWith("_") ? [path] : [];
  });
}

/** A page file's route, as Ink's file router names it. */
const route = (file: string) => {
  const path = "/settings/" + relative(settings, file).replaceAll("\\", "/").replace(/\.tsx$/, "");
  return path.replace(/\/index$/, "");
};

const routes = pages(settings).map(route);

test("every Settings page goes up to a real page, and reaches the top of Settings", () => {
  for (const path of routes) {
    const chain = [path];
    for (let parent = parentOf(path); parent; parent = parentOf(parent)) {
      expect(routes).toContain(parent);
      expect(chain).not.toContain(parent);
      chain.push(parent);
    }
    expect(chain.at(-1)).toBe("/settings");
  }
});

test("the top of Settings has no parent, and pages outside Settings are not in the tree", () => {
  expect(parentOf("/settings")).toBeNull();
  expect(parentOf("/")).toBeUndefined();
  expect(parentOf("/settingsx")).toBeUndefined();
});

test("deep links land under the right parents", () => {
  expect(parentOf("/settings/news")).toBe("/settings");
  expect(parentOf("/settings/share-policy")).toBe("/settings/contribute");
  expect(parentOf("/settings/guide-boring")).toBe("/settings/guide");
  expect(parentOf("/settings/data/reset")).toBe("/settings/data");
  expect(parentOf("/settings/snippets/edit")).toBe("/settings/snippets");
});

test("only the top of Settings has ×; every other page gets Ink's ← alone", () => {
  for (const file of pages(settings)) {
    const text = readFileSync(file, "utf8");
    const closes = /\bicon:\s*close\b/.test(text) && /rightAction=/.test(text);
    expect(`${route(file)} ${closes}`).toBe(`${route(file)} ${route(file) === "/settings"}`);
  }
  // The top of Settings hands the tree to Ink, so links from outside get their parents.
  expect(readFileSync(join(settings, "index.tsx"), "utf8")).toContain("setRouteParents(parentOf)");
});
