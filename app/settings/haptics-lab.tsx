import { Button, Field, Screen, Section, SettingsChoices, Stack, Toggle, useSnapshot } from "ink";
import { type HapticsLab, playLabFlick, setLabDeleteGlow, setLabExperiment, setLabFlickHold, showHapticsGraph, state } from "../../lib/atlas";
import { hapticGroups } from "../../lib/haptics";

const title = "Haptics lab";
/** The flick dots wait goes from 0 to 600 ms, 25 ms a press, or 100 ms a long press. */
const WAIT_MAX = 600;
const WAIT_STEP = 25;
const WAIT_JUMP = 100;
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
  const fade = lab.heldDeleteFade;
  const wait = lab.flickHoldMillis;
  // Each change plays the dots straight away, so the wait can be felt as it moves.
  const tryWait = async (millis: number | null) => {
    await setLabFlickHold(millis === null ? null : Math.max(0, Math.min(WAIT_MAX, millis)));
    await playLabFlick();
  };
  return <Screen title={title} subtitle={subtitle}>
    <Section title="Lab">
      <Field label="Open the lab" onPress={open}>Practice, live haptics, patterns and notes</Field>
    </Section>
    <Section title="Experiments">
      <Toggle label="Dots speed up when long" value={lab.experiments.dotsSpeedUp ?? true}
        onChange={on => void setLabExperiment("dotsSpeedUp", on)} />
      <Toggle label="Held delete fade" value={lab.experiments.heldDeleteFade ?? false}
        onChange={on => void setLabExperiment("heldDeleteFade", on)} />
      <Field label="Tune the held delete fade" onPress={() => void showHapticsGraph(haptics, "heldDeleteFade")}>
        {fade.on && fade.tried ? `${fade.plays} (tried)` : fade.plays}
      </Field>
      <Field label="Flick dots wait" onPress={() => void playLabFlick()}>
        {wait === lab.flickHoldDefault ? `${wait} ms` : `${wait} ms (tried, default ${lab.flickHoldDefault} ms)`}
      </Field>
      <Stack axis="horizontal" gap={8}>
        <Button disabled={wait <= 0} onPress={() => void tryWait(wait - WAIT_STEP)} onLongPress={() => void tryWait(wait - WAIT_JUMP)}>Shorter</Button>
        <Button disabled={wait >= WAIT_MAX} onPress={() => void tryWait(wait + WAIT_STEP)} onLongPress={() => void tryWait(wait + WAIT_JUMP)}>Longer</Button>
        <Button onPress={() => void playLabFlick()}>Play</Button>
        <Button disabled={wait === lab.flickHoldDefault} onPress={() => void tryWait(null)}>Reset</Button>
      </Stack>
      <Toggle label="Tick at delete zones" value={lab.experiments.deleteZoneTicks ?? true}
        onChange={on => void setLabExperiment("deleteZoneTicks", on)} />
    </Section>
    <Section title="Delete glow">
      <SettingsChoices value={lab.deleteGlow} options={lab.deleteGlows.map(place => ({ value: place.id, label: place.label }))}
        onChange={id => void setLabDeleteGlow(id)} />
    </Section>
    <Section title="Patterns">
      {Object.entries(lab.families).map(([id, family]) =>
        <Field key={id} label={family.name}>{family.tried ? `${family.plays} (tried)` : family.plays}</Field>)}
    </Section>
  </Screen>;
}
