import { expect, test } from "bun:test";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import * as brand from "../../lib/brand";

// Ink's canvas takes only #RRGGBB colours. Anything else, such as #RRGGBBAA, stops the whole app at start: a transparent fill
// on the setup page's button did that whenever TitanKeys was not the chosen keyboard.
const root = join(import.meta.dir, "../..");
const RRGGBB = /^#[0-9A-Fa-f]{6}$/;

function files(dir: string): string[] {
  return readdirSync(dir).flatMap(name => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return files(path);
    return /\.tsx?$/.test(name) ? [path] : [];
  });
}

test("every colour literal that Ink draws is #RRGGBB", () => {
  const bad: string[] = [];
  const colour = /\b(fill|stroke|colour|innerFill|progressFill)\s*[=:]\s*\{?\s*["'`]([^"'`]*)["'`]/g;
  for (const file of [...files(join(root, "app")), ...files(join(root, "lib"))]) {
    const text = readFileSync(file, "utf8");
    for (const match of text.matchAll(colour)) if (!RRGGBB.test(match[2])) bad.push(`${file}: ${match[1]} ${match[2]}`);
  }
  expect(bad).toEqual([]);
});

test("the brand colours that screens draw with are #RRGGBB", () => {
  for (const [name, value] of Object.entries(brand)) if (typeof value === "string") expect(`${name} ${value}`).toMatch(/ #[0-9A-Fa-f]{6}$/);
});
