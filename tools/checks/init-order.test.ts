// Kotlin runs a class's property initialisers and init blocks top to bottom. A function that an init block calls, however deep,
// still sees every property declared below that init as unset: an object is null, a number is 0. The keyboard crashed opening
// on every phone when Strip's init rendered and the word menu's eases, declared further down, were still null (2026-11-12).
// verify passed, since no Kotlin check can build a View. So this reads the source: whatever a class-level init can reach, through
// the functions and property accessors it calls, must be declared above that init, or start as null, which it already is.
import { expect, test } from "bun:test";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

const root = join(import.meta.dir, "../..");
const folder = join(root, "android/kotlin/com/atlas/keyboard");

/** The lines from [start] to where its braces close. */
const block = (lines: string[], start: number) => {
  let depth = 0;
  let opened = false;
  const out: string[] = [];
  for (const line of lines.slice(start)) {
    out.push(line);
    for (const c of line) {
      if (c === "{") { depth++; opened = true; }
      else if (c === "}") depth--;
    }
    if (opened && depth <= 0) break;
    // An expression body with no braces ends where the next member starts.
    if (!opened && out.length > 1 && /^ {4}\S/.test(line)) { out.pop(); break; }
  }
  return out.join("\n");
};

/** Properties declared below a class-level init that the init can reach and that don't start as null. */
export const unsafe = (source: string) => {
  // Comments name functions they don't call ("see [drawMenu]"), and strings hold words: neither is reached.
  const code = source.replace(/\/\*[\s\S]*?\*\//g, block => block.replace(/[^\n]/g, " "))
    .replace(/\/\/[^\n]*/g, "").replace(/"(?:[^"\\\n]|\\.)*"/g, '""');
  const lines = code.split("\n");
  const init = lines.findIndex(line => line.startsWith("    init {"));
  if (init < 0) return [];
  const members = new Map<string, string[]>();
  const add = (name: string, body: string) => members.set(name, [...(members.get(name) ?? []), body]);
  const declared = new Map<string, { line: number; text: string }>();
  lines.forEach((line, index) => {
    const fun = /^ {4}(?:[a-z]+ )*fun (?:[\w.<>]+\.)?(\w+)\s*[(<]/.exec(line);
    if (fun) add(fun[1], block(lines, index));
    const property = /^ {4}(?:[a-z]+ )*(?:val|var) (\w+)/.exec(line);
    if (property) {
      add(property[1], block(lines, index));
      declared.set(property[1], { line: index + 1, text: line.trim() });
    }
  });
  const reached = new Set<string>();
  const todo = [block(lines, init)];
  while (todo.length) {
    for (const name of todo.pop()!.match(/\b[A-Za-z_]\w*\b/g) ?? []) {
      if (reached.has(name)) continue;
      reached.add(name);
      for (const body of members.get(name) ?? []) todo.push(body);
    }
  }
  return [...declared].filter(([name, { line, text }]) => line > init + 1 && reached.has(name) && !startsUnset(text))
    .map(([name, { line, text }]) => `${line}: ${name}: ${text}`);
};

/**
 * Whether a property's unset value is already its starting one, so being early does no harm: null, 0 or false, a constant, or
 * one with no field at all, worked out by its getter. Anything else, an object, a string, or a number other than 0, is wrong
 * until its line runs.
 */
const startsUnset = (text: string) => /^(?:[a-z]+ )*const /.test(text) || /\bget\(\)/.test(text)
  || /=\s*(?:null|false|0|0L|0f|0\.0|0\.0f)\s*$/.test(text) || !text.includes("=");

test("whatever a View's init reaches is declared above it, or starts as null", () => {
  const found: string[] = [];
  for (const file of readdirSync(folder).filter(name => name.endsWith(".kt"))) {
    const source = readFileSync(join(folder, file), "utf8");
    // Views, which Android builds as the keyboard opens. Their constructors take lambdas, so only the superclass is matched.
    if (!/\)\s*:\s*[\w.]*(?:View|Layout)\(/.test(source)) continue;
    for (const line of unsafe(source)) found.push(`${file}:${line}`);
  }
  expect(found).toEqual([]);
});

test("the check catches the crash it was made for: an ease declared below Strip's init", () => {
  const source = [
    "class Strip(context: Context) : LinearLayout(context) {",
    "    init {",
    "        render()",
    "    }",
    "    private fun render() {",
    "        wordEase.jump(0f)",
    "    }",
    "    private val wordEase = Ease()",
    "    private var demo: Demo? = null",
    "}",
  ].join("\n");
  expect(unsafe(source)).toEqual(["8: wordEase: private val wordEase = Ease()"]);
});
