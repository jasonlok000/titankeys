import { useEffect } from "react";
import { Field, Screen, Section, useAction, useSnapshot } from "ink";
import { Toggle } from "../../lib/app-haptics";
import { allowBack, allowOverlay, chooseKeyboard, enableKeyboard, fakeOtherPhone, onAppWindow, openScrollAssistant, openShortcutKeys, state, update, type State } from "../../lib/atlas";
import { otherPhone } from "../../lib/front";
import { Note } from "../../lib/note";

/** Accessibility counts as done after "No thanks", as on the front page and in the guide (lib/front.ts, PhoneSetup.missing). */
const steps = ({ enabled, selected, overlay, scrollAssistant, backService, backDeclined }: State) =>
  [enabled, selected, overlay, scrollAssistant, backService || backDeclined];

/** The phone's own settings, which TitanKeys reads but never changes (PhoneChecks.kt). */
const phoneSteps = ({ symKey, fnKey, outsideSlidingMode }: State) => [symKey, fnKey, outsideSlidingMode.length === 0];

const status = (done: boolean) => done ? "Done" : "To do";

/** Optional steps show what they are, never Done or To do, so they never make setup look unfinished (#87). */
const onOff = (on: boolean) => on ? "On" : "Off";

/** The step names the mode (owner, 2026-11-13: "turn on scroll assistant, let's specify mode 1"). */
export const scrollAssistantStep = "Turn on Scroll assistant, Sliding Mode 1";
export const overlayListNote = "Android opened its whole list. Find TitanKeys Keyboard in it, open it and flip the switch.";
export const backListNote = "Android opened its whole list. Find TitanKeys Keyboard in it and turn it on.";
// F: draft, the line that stays at the top of Settings > Setup on phones other than the Titan 2 Elite (#75).
export const otherPhoneNote = "This isn't a Titan 2 Elite, so swiping and the touch keys won't work. Everything else does.";
export const contactsStep = "Names from my contacts";

export const setupLabel = (settings: State) => status([...steps(settings), ...phoneSteps(settings)].every(Boolean));

/** A list of names: "A", "A and B", "A, B and C". */
const names = (list: readonly string[]) => list.length < 2 ? list.join("") : `${list.slice(0, -1).join(", ")} and ${list.at(-1)}`;

export default function Setup() {
  const result = useSnapshot(state);
  const save = useAction(update);
  // Each step opens a page elsewhere, such as Android's contacts prompt: read it all again as this page comes back.
  useEffect(() => onAppWindow(() => void state.refresh()), []);
  if (result.status !== "ready") return <Screen title="Setup" />;
  const settings = result.data;
  const outside = settings.outsideSlidingMode;
  return <Screen title="Setup" subtitle="Make every step say Done. Each one opens the right page.">
    {otherPhone(settings) && <Note>{otherPhoneNote}</Note>}
    <Section title="Steps">
      <Field label="Turn on TitanKeys Keyboard" onPress={enableKeyboard}>{status(settings.enabled)}</Field>
      <Field label="Make TitanKeys your keyboard" onPress={chooseKeyboard}>{status(settings.selected)}</Field>
      <Field label="Allow display over other apps" onPress={allowOverlay}>{status(settings.overlay)}</Field>
      {/* Android may refuse TitanKeys' own page and show its whole list instead (PhoneSetup.open). */}
      {!settings.overlay && settings.overlayOnList && <Note>{overlayListNote}</Note>}
      {/* One step for Scroll assistant and each app's Sliding Mode 1 (owner, 2026-11-14: a second row for the apps was
          "redundant to the scroll assistant up top"): done only when every app TitanKeys typed in is on Sliding Mode 1. */}
      <Field label={scrollAssistantStep} onPress={openScrollAssistant}>{status(settings.scrollAssistant && outside.length === 0)}</Field>
      {outside.length > 0 && <Note>{`Swiping doesn't work in ${names(outside)}. Open Scroll assistant, find ${outside.length === 1 ? "it" : "each one"} and pick Sliding Mode 1.`}</Note>}
      <Field label="Turn on TitanKeys Keyboard in Accessibility" onPress={allowBack}>{status(settings.backService)}</Field>
      {!settings.backService && settings.backOnList && <Note>{backListNote}</Note>}
    </Section>
    {/* The phone's own settings. TitanKeys only reads them, so each step opens the phone's page for you to change it. */}
    <Section title="Phone settings">
      <Field label="Set Sym to Programmable key" onPress={openShortcutKeys}>{status(settings.symKey)}</Field>
      {!settings.symKey && <Note>Without it, Sym doesn't open the Sym panel. In Shortcut keys, set Sym key to Programmable key.</Note>}
      <Field label="Set fn to Programmable key" onPress={openShortcutKeys}>{status(settings.fnKey)}</Field>
      {!settings.fnKey && <Note>Without it, fn can't work as Ctrl. In Shortcut keys, set fn key to Programmable key.</Note>}
    </Section>
    {/* The guide's setup asks for it last, after saying what it's for. Here it flips the same setting as Settings > Your words:
        turning it on asks Android first (ContactsPermissionActivity), and it's On only once Android allows it. */}
    <Section title="Optional">
      <Field label={contactsStep} onPress={() => void save.run({ contactNames: !settings.contactNames })}>{onOff(settings.contactNames)}</Field>
    </Section>
    {/* TitanKeys Debug only, never in a release: fake another phone so the warning (#75) can be checked on the Elite. */}
    {settings.build === "debug" && <Section title="Debug">
      <Toggle label="Fake another phone" value={settings.fakeOtherPhone} onChange={on => void fakeOtherPhone(on)} />
    </Section>}
  </Screen>;
}
