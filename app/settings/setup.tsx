import { Field, Screen, Section, useSnapshot } from "ink";
import { allowBack, allowOverlay, chooseKeyboard, enableKeyboard, openScrollAssistant, openShortcutKeys, state, type State } from "../../lib/atlas";
import { Note } from "../../lib/note";

const steps = ({ enabled, selected, overlay, scrollAssistant, backService }: State) =>
  [enabled, selected, overlay, scrollAssistant, backService];

/** The phone's own settings, which TitanKeys reads but never changes (PhoneChecks.kt). */
const phoneSteps = ({ symKey, fnKey, outsideSlidingMode }: State) => [symKey, fnKey, outsideSlidingMode.length === 0];

const status = (done: boolean) => done ? "Done" : "To do";

/** The step names the mode (owner, 2026-11-13: "turn on scroll assistant, let's specify mode 1"). */
export const scrollAssistantStep = "Turn on Scroll assistant, Sliding Mode 1";
export const overlayListNote = "Android opened its whole list. Find TitanKeys Keyboard in it, open it and flip the switch.";
export const backListNote = "Android opened its whole list. Find TitanKeys Keyboard in it and turn it on.";

export const setupLabel = (settings: State) => status([...steps(settings), ...phoneSteps(settings)].every(Boolean));

/** A list of names: "A", "A and B", "A, B and C". */
const names = (list: readonly string[]) => list.length < 2 ? list.join("") : `${list.slice(0, -1).join(", ")} and ${list.at(-1)}`;

export default function Setup() {
  const result = useSnapshot(state);
  if (result.status !== "ready") return <Screen title="Setup" />;
  const settings = result.data;
  const outside = settings.outsideSlidingMode;
  return <Screen title="Setup" subtitle="Make every step say Done. Each one opens the right page.">
    <Section title="Steps">
      <Field label="Turn on TitanKeys Keyboard" onPress={enableKeyboard}>{status(settings.enabled)}</Field>
      <Field label="Make TitanKeys your keyboard" onPress={chooseKeyboard}>{status(settings.selected)}</Field>
      <Field label="Allow display over other apps" onPress={allowOverlay}>{status(settings.overlay)}</Field>
      {/* Android may refuse TitanKeys' own page and show its whole list instead (PhoneSetup.open). */}
      {!settings.overlay && settings.overlayOnList && <Note>{overlayListNote}</Note>}
      <Field label={scrollAssistantStep} onPress={openScrollAssistant}>{status(settings.scrollAssistant)}</Field>
      <Field label="Turn on TitanKeys Keyboard in Accessibility" onPress={allowBack}>{status(settings.backService)}</Field>
      {!settings.backService && settings.backOnList && <Note>{backListNote}</Note>}
    </Section>
    {/* The phone's own settings. TitanKeys only reads them, so each step opens the phone's page for you to change it. */}
    <Section title="Phone settings">
      <Field label="Set Sym to Programmable key" onPress={openShortcutKeys}>{status(settings.symKey)}</Field>
      {!settings.symKey && <Note>Without it, Sym doesn't open the Sym panel. In Shortcut keys, set Sym key to Programmable key.</Note>}
      <Field label="Set fn to Programmable key" onPress={openShortcutKeys}>{status(settings.fnKey)}</Field>
      {!settings.fnKey && <Note>Without it, fn can't work as Ctrl. In Shortcut keys, set fn key to Programmable key.</Note>}
      <Field label="Sliding Mode 1 in your apps" onPress={openScrollAssistant}>{status(outside.length === 0)}</Field>
      <Note>{outside.length === 0
        ? "Swiping only works in apps on Sliding Mode 1. TitanKeys checks each app you type in, and tells you here if one isn't."
        : `Swiping doesn't work in ${names(outside)}. Open Scroll assistant, find ${outside.length === 1 ? "it" : "each one"} and pick Sliding Mode 1.`}</Note>
    </Section>
  </Screen>;
}
