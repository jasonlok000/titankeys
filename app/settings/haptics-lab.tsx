import { useState } from "react";
import { Field, Screen, Section, SettingsChoices, TextInput, useSnapshot } from "ink";
import { Toggle, playHaptic } from "../../lib/app-haptics";
import { type HapticsLab, playLabFlick, setLabExperiment, setLabFlickHold, setLabSelectionEnd, showHapticsGraph, state } from "../../lib/atlas";
import { hapticGroups } from "../../lib/haptics";

const title = "Haptics lab";
/** The flick dots wait goes from 0 to 600 ms: typed, or 25 ms a press. */
const WAIT_MAX = 600;
const WAIT_STEP = 25;
const subtitle = "Type and swipe in the lab, and watch what the keyboard plays. Change a pattern there and the next one plays it.";

/** TitanKeys Debug only. Every other build shows nothing here, even through a direct link. */
export default function HapticsLabPage() {
  const result = useSnapshot(state);
  if (result.status !== "ready") return <Screen title={title} />;
  const lab = result.data.hapticsLab;
  if (!lab) return <Screen title={title} subtitle="The Haptics lab is only in TitanKeys Debug." />;
  return <Lab lab={lab} />;
}

function Lab({ lab }: { lab: HapticsLab }) {
  // The live timeline, the editors and the notes are in the lab's own screen, which writes the notes file.
  const haptics = hapticGroups.flatMap(group => group.haptics.map(haptic => ({ ...haptic, group: group.title })));
  const open = () => void showHapticsGraph(haptics);
  const wait = lab.flickHoldMillis;
  // The number being typed into the wait's field, until Done applies it; null shows the wait in use.
  const [draft, setDraft] = useState<string | null>(null);
  const typed = draft === null ? null : waitFrom(draft);
  // Each change plays the dots straight away, so the wait can be felt as it moves.
  const tryWait = async (millis: number | null) => {
    setDraft(null);
    await setLabFlickHold(millis === null ? null : Math.max(0, Math.min(WAIT_MAX, millis)));
    await playLabFlick();
  };
  const applyDraft = (text: string) => {
    const millis = waitFrom(text);
    if (millis === null) return;
    void tryWait(millis);
  };
  return <Screen title={title} subtitle={subtitle}>
    <Section title="Lab">
      <Field label="Open the lab" onPress={open}>Practice, live haptics, patterns and notes</Field>
    </Section>
    <Section title="Experiments">
      <Toggle label="Dots speed up when long" value={lab.experiments.dotsSpeedUp ?? true}
        onChange={on => void setLabExperiment("dotsSpeedUp", on)} />
      <TextInput label="Flick dots wait" value={draft ?? String(wait)} onChange={setDraft} onSubmit={applyDraft}
        inputMode="numeric" action="done" suffix="ms" invalid={draft !== null && typed === null}
        error={draft !== null && typed === null ? `A whole number from 0 to ${WAIT_MAX}` : undefined}
        description={wait === lab.flickHoldDefault ? "The default" : `Tried; the default is ${lab.flickHoldDefault} ms`} />
      {/* Rows, as the rest of Settings has, so each reads as something to tap: in a row, Ink's buttons read as one sentence. */}
      <Field label="Play the dots" onPress={() => void playLabFlick()}>{`${wait} ms`}</Field>
      <Field label="Shorter" disabled={wait <= 0} onPress={() => void tryWait(wait - WAIT_STEP)}>{`− ${WAIT_STEP} ms`}</Field>
      <Field label="Longer" disabled={wait >= WAIT_MAX} onPress={() => void tryWait(wait + WAIT_STEP)}>{`+ ${WAIT_STEP} ms`}</Field>
      <Field label="Reset" disabled={wait === lab.flickHoldDefault} onPress={() => void tryWait(null)}>{`${lab.flickHoldDefault} ms`}</Field>
      <Toggle label="Tick at delete zones" value={lab.experiments.deleteZoneTicks ?? true}
        onChange={on => void setLabExperiment("deleteZoneTicks", on)} />
      <Toggle label="Exact-typing keys feel like a key (texture)" value={lab.experiments.exactKeysTexture ?? false}
        onChange={on => void setLabExperiment("exactKeysTexture", on)} />
    </Section>
    {/* What marks the end of the selection that grows as you slide after a triple press. The next selection shows it. */}
    <Section title="Triple press selection end">
      <SettingsChoices value={lab.selectionEnd} options={lab.selectionEnds.map(end => ({ value: end.id, label: end.label }))}
        onChange={id => {
          playHaptic("app_picked");
          void setLabSelectionEnd(id);
        }} />
    </Section>
    <Section title="Patterns">
      {Object.entries(lab.families).map(([id, family]) =>
        <Field key={id} label={family.name}>{family.tried ? `${family.plays} (tried)` : family.plays}</Field>)}
    </Section>
  </Screen>;
}

/** The wait typed into its field, in ms, or null when it is no whole number from 0 to [WAIT_MAX]. */
function waitFrom(text: string): number | null {
  if (!/^\s*\d+\s*$/.test(text)) return null;
  const millis = Number(text.trim());
  return millis <= WAIT_MAX ? millis : null;
}
