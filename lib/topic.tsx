import { useState } from "react";
import { Row, Stack } from "ink";
import { expandLess, expandMore } from "ink/icons";
import { Note } from "./note";

/**
 * One entry of a written guide. A row holds only one line of text, so the row shows the title, and a touch opens the whole
 * text under it. Another touch closes it again.
 */
export function Topic({ title, children }: { title: string; children: string }) {
  const [open, setOpen] = useState(false);
  return <Stack align="stretch" gap={TEXT_GAP}>
    <Row title={title} titleMaxLines={TITLE_LINES} endIcon={open ? expandLess : expandMore} onPress={() => setOpen(!open)} />
    {open && <Note>{children}</Note>}
  </Stack>;
}

/** Long titles wrap, so that a large font size does not cut them. */
const TITLE_LINES = 3;
/** The room between a title and its text. */
const TEXT_GAP = 8;
