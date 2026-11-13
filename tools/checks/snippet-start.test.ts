// Checks that Settings offers the same snippet starts as the keyboard takes, and that the page gets the keys to type them.
import { expect, test } from "bun:test";
import { oldSnippetStarts, snippetStarts } from "../../lib/atlas";

const root = new URL("../../", import.meta.url);
const read = (path: string) => Bun.file(new URL(path, root)).text();

/** A Java string constant from TypingSettings, with its escapes undone. */
async function javaString(name: string) {
  const settings = await read("android/kotlin/com/atlas/engine/TypingSettings.java");
  const found = settings.match(new RegExp(`String ${name} = "((?:[^"\\\\]|\\\\.)*)";`));
  expect(found, name).not.toBeNull();
  return found![1].replace(/\\(.)/g, "$1");
}

test("Settings offers the starts that the keyboard takes, / first", async () => {
  expect([...snippetStarts].join("")).toBe(await javaString("SNIPPET_STARTS"));
  expect([...oldSnippetStarts].join("")).toBe(await javaString("OLD_SNIPPET_STARTS"));
  expect(snippetStarts[0]).toBe("/");
});

test("no offered start can be part of a shortcut or start an emoji name", () => {
  for (const start of snippetStarts) expect(start, start).not.toMatch(/[\p{L}\p{N}_+\-:'"\s]/u);
});

test("the start page shows how to type each start, read from the phone", async () => {
  expect(await read("android/kotlin/com/vandam/ink/AppNativeAdapters.kt")).toContain('.put("altKeys", altKeys())');
  expect(await read("app/settings/snippets/start.tsx")).toContain("altKeys[start]");
});
