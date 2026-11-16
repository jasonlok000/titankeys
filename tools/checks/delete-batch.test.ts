// A delete, a flick's included (#89), reads the field and then deletes: both in one batch edit, so the app can't change the
// text in between and have the delete take something other than what was read (review of M2).
import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

const editor = readFileSync(join(import.meta.dir, "../../android/kotlin/com/titankeys/keyboard/ConnectionEditor.kt"), "utf8");

test("the delete's reads and its delete run inside one batch edit", () => {
  const delete_ = editor.slice(editor.indexOf("override fun delete("), editor.indexOf("private fun deleteInBatch("));
  expect(delete_).toMatch(/connection\.beginBatchEdit\(\)\s+return try \{\s+deleteInBatch\(connection, word, selectionStart, selectionEnd\)\s+\} finally \{\s+connection\.endBatchEdit\(\)\s+\}/);
  // Every read and delete is in the batched part, none before it.
  const before = delete_.slice(0, delete_.indexOf("beginBatchEdit"));
  for (const call of ["getTextBeforeCursor", "getSelectedText", "deleteSurroundingText", "commitText", "wordToTake"])
    expect(before).not.toContain(call);
  const batched = editor.slice(editor.indexOf("private fun deleteInBatch("), editor.indexOf("override fun enter("));
  for (const call of ["wordToTake()", "deleteSurroundingText(taken.length, 0)", "getTextBeforeCursor(256, 0)", "getSelectedText(0)"])
    expect(batched).toContain(call);
});
