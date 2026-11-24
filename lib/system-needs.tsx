import { useEffect } from "react";
import { Field } from "ink";
import { allowBack, allowOverlay, chooseKeyboard, enableKeyboard, onAppWindow, openScrollAssistant, openShortcutKeys, type State,
  update } from "./titankeys";
import { Note } from "./note";

/**
 * What a feature needs from the phone that TitanKeys can't turn on itself (owner, 2026-11-17: "if a feature is not going to be
 * working because a system settings or service is not on it shd say so explicitly and prompt for that to be switched or be turned
 * on"). Each feature's own row in Settings shows what's off under it, with a button to the exact system screen. TitanKeys only reads
 * these: the user turns them on. Nothing shows while you type. The list of every dependency is in docs/system-settings.md.
 * Every line is F: draft.
 */
export type Need = { id: string; line: string; button: string; open: () => unknown };

const NEEDS = {
  enabled: { id: "enabled", line: "TitanKeys Keyboard is off in Android, so none of this works yet.", button: "Turn on TitanKeys Keyboard",
    open: enableKeyboard },
  selected: { id: "selected", line: "TitanKeys isn't your keyboard right now, so none of this works yet.", button: "Make TitanKeys your keyboard",
    open: chooseKeyboard },
  overlay: { id: "overlay", line: "Display over other apps is off, so the touchpad can't reach TitanKeys.", button: "Allow display over other apps",
    open: allowOverlay },
  scrollAssistant: { id: "scrollAssistant", line: "The phone's Scroll assistant is off, so the touchpad doesn't type.",
    button: "Open Scroll assistant", open: openScrollAssistant },
  backService: { id: "backService", line: "Blocking needs TitanKeys Keyboard on in Accessibility. It's off, so apps still scroll.",
    button: "Turn it on in Accessibility", open: allowBack },
  fnKey: { id: "fnKey", line: "fn can't work as Ctrl (fn+C to copy). In phone Settings > Shortcut keys > fn key, pick Programmable key.", button: "Open Shortcut keys", open: openShortcutKeys },
  symKey: { id: "symKey", line: "Sym can't open TitanKeys' symbols. In phone Settings > Shortcut keys > Sym key, pick Programmable key.", button: "Open Shortcut keys",
    open: openShortcutKeys },
  contacts: { id: "contacts", line: "Android's Contacts permission is off, so TitanKeys can't read the names.", button: "Allow contacts",
    open: () => update({ contactNames: true }) },
} satisfies Record<string, Need>;

/** The features that a phone setting can stop, each with what it needs that's off now. Empty when all is on. */
export const needs = {
  /** The keyboard as a whole: Settings' Setup row. */
  keyboard: (s: State): Need[] => [...when(!s.enabled, NEEDS.enabled), ...when(s.enabled && !s.selected, NEEDS.selected)],
  /** Everything the touchpad does: swiping, the cursor, Space taps, flicks. */
  touchpad: (s: State): Need[] => [...when(!s.overlay, NEEDS.overlay), ...when(!s.scrollAssistant, NEEDS.scrollAssistant)],
  /** Scroll assistant off in TitanKeys, which blocks the phone's scrolling outside text fields (TouchpadHold.wanted). */
  scrollBlock: (s: State): Need[] => s.touchpadOnlyInFields
    ? [...when(!s.selected, NEEDS.selected), ...when(!s.overlay, NEEDS.overlay), ...when(!s.backService, NEEDS.backService)] : [],
  fnAsCtrl: (s: State): Need[] => s.fnAsCtrl && !s.fnKey ? [NEEDS.fnKey] : [],
  altSym: (s: State): Need[] => s.altSymNextKeyboard && !s.symKey ? [NEEDS.symKey] : [],
  contactNames: (s: State): Need[] => s.contactNamesBlocked ? [NEEDS.contacts] : [],
};

/** [need] when [off], or nothing. */
function when(off: boolean, need: Need): Need[] {
  return off ? [need] : [];
}

/** What's off, under a feature's row: a plain line and the button that opens the system screen to turn it on. */
export function Needs({ list }: { list: readonly Need[] }) {
  return <>{list.map(need => <Field key={need.id} label={need.button} onPress={() => void need.open()}>{""}</Field>)
    .flatMap((button, index) => [<Note key={`${list[index].id}-line`}>{list[index].line}</Note>, button])}</>;
}

/** Reads the phone's state again each time the app comes back to the front, so a warning goes once its setting is on. */
export function useRecheck(resource: { refresh: () => unknown }) {
  useEffect(() => onAppWindow(() => void resource.refresh()), []);
}
