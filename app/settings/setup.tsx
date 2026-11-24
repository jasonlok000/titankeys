import { useEffect } from "react";
import { Field, Screen, Section, useAction, useSnapshot } from "ink";
import { Toggle } from "../../lib/app-haptics";
import { fakeOtherPhone, onAppWindow, openSetupStep, setSetupContacts, state, type State } from "../../lib/titankeys";
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

export const overlayListNote = "Android opened its whole list. Find TitanKeys Keyboard in it, open it and flip the switch.";
export const backListNote = "Android opened its whole list. Find TitanKeys Keyboard in it and turn it on.";
// The line that stays at the top of Settings > Setup on phones other than the Titan 2 Elite (#75).
export const otherPhoneNote = "This isn't a Titan 2 Elite, so swiping, Space taps and flicks won't work here.";
export const contactsStep = "Names from my contacts";

export const setupLabel = (settings: State) => status([...steps(settings), ...phoneSteps(settings)].every(Boolean));

/** A list of names: "A", "A and B", "A, B and C". */
const names = (list: readonly string[]) => list.length < 2 ? list.join("") : `${list.slice(0, -1).join(", ")} and ${list.at(-1)}`;

export default function Setup() {
  const result = useSnapshot(state);
  const save = useAction(setSetupContacts);
  // Each step opens a page elsewhere, such as Android's contacts prompt: read it all again as this page comes back.
  useEffect(() => onAppWindow(() => void state.refresh()), []);
  if (result.status !== "ready") return <Screen title="Setup" />;
  const settings = result.data;
  const outside = settings.outsideSlidingMode;
  const scrollAssistantStep = !settings.scrollAssistant ? "Turn on Scroll assistant, Sliding Mode 1"
    : outside.length ? `Put ${names(outside)} on Sliding Mode 1` : "Scroll assistant, Sliding Mode 1";
  return <Screen title="Setup" subtitle="Make every step say Done. Each one opens the right page.">
    {otherPhone(settings) && <Note>{otherPhoneNote}</Note>}
    <Section title="Steps">
      <Field label="Turn on TitanKeys Keyboard" onPress={() => void openSetupStep("enable")}>{status(settings.enabled)}</Field>
      <Field label="Make TitanKeys your keyboard" onPress={() => void openSetupStep("choose")}>{status(settings.selected)}</Field>
      <Field label="Allow display over other apps" onPress={() => void openSetupStep("overlay")}>{status(settings.overlay)}</Field>
      {/* Android may refuse TitanKeys' own page and show its whole list instead (PhoneSetup.open). */}
      {!settings.overlay && settings.overlayOnList && <Note>{overlayListNote}</Note>}
      {/* One step for Scroll assistant and each app's Sliding Mode 1 (owner, 2026-11-14: a second row for the apps was
          "redundant to the scroll assistant up top"): done only when every app TitanKeys typed in is on Sliding Mode 1. */}
      <Field label={scrollAssistantStep} onPress={() => void openSetupStep("scrollAssistant")}>{status(settings.scrollAssistant && outside.length === 0)}</Field>
      {outside.length > 0 && <Note>{`Swiping doesn't work in ${names(outside)}. Open Scroll assistant, find ${outside.length === 1 ? "it" : "each one"} and pick Sliding Mode 1.`}</Note>}
      <Field label="Turn on TitanKeys Keyboard in Accessibility" onPress={() => void openSetupStep("backService")}>{status(settings.backService)}</Field>
      {!settings.backService && settings.backOnList && <Note>{backListNote}</Note>}
    </Section>
    {/* The phone's own settings. TitanKeys only reads them, so each step opens the phone's page for you to change it. */}
    <Section title="Phone settings">
      <Field label="Sym works as Sym" onPress={() => void openSetupStep("shortcutKeys")}>{status(settings.symKey)}</Field>
      {!settings.symKey && <Note>{"Sym isn't set up, so it can't open TitanKeys' symbols. Tap above, then in phone Settings > Shortcut keys > Sym key, pick Programmable key."}</Note>}
      <Field label="fn works as Ctrl" onPress={() => void openSetupStep("shortcutKeys")}>{status(settings.fnKey)}</Field>
      {!settings.fnKey && <Note>{"fn isn't set up, so it can't work as Ctrl (fn+C copies). Tap above, then in phone Settings > Shortcut keys > fn key, pick Programmable key."}</Note>}
    </Section>
    {/* The guide's setup asks for it last, after saying what it's for. Here it flips the same setting as Settings > Your words:
        turning it on asks Android first (ContactsPermissionActivity), and it's On only once Android allows it. */}
    <Section title="Optional">
      <Field label={contactsStep} onPress={() => void save.run(!settings.contactNames)}>{onOff(settings.contactNames)}</Field>
    </Section>
    {/* TitanKeys Debug only, never in a release: fake another phone so the warning (#75) can be checked on the Elite. */}
    {settings.build === "debug" && <Section title="Debug">
      <Toggle label="Fake another phone" value={settings.fakeOtherPhone} onChange={on => void fakeOtherPhone(on)} />
    </Section>}
  </Screen>;
}
