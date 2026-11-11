import { Field, Screen, Section, useSnapshot } from "ink";
import { type HapticsLab, showHapticsGraph, state } from "../../lib/atlas";
import { hapticGroups } from "../../lib/haptics";

const title = "Haptics lab";
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
  const open = () => void showHapticsGraph(hapticGroups.flatMap(group => group.haptics.map(haptic => ({ ...haptic, group: group.title }))));
  return <Screen title={title} subtitle={subtitle}>
    <Section title="Lab">
      <Field label="Open the lab" onPress={open}>Practice, live haptics, patterns and notes</Field>
    </Section>
    <Section title="Patterns">
      {Object.entries(lab.families).map(([id, family]) =>
        <Field key={id} label={family.name}>{family.tried ? `${family.plays} (tried)` : family.plays}</Field>)}
    </Section>
  </Screen>;
}
