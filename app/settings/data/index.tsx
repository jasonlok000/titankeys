import { Field, Screen, Section, navigate, useAction, useSnapshot } from "ink";
import { Toggle } from "../../../lib/app-haptics";
import { state, update } from "../../../lib/titankeys";
import { transfer } from "../../../lib/transfer";
import { Needs, needs, useRecheck } from "../../../lib/system-needs";

export default function YourWords() {
  const result = useSnapshot(state);
  const save = useAction(update);
  useRecheck(state);
  const subtitle = "Your words and the names TitanKeys knows stay on your phone. We never send them anywhere.";
  if (result.status !== "ready") return <Screen title="Your words" subtitle={subtitle} />;
  const { learning, contactNames } = result.data;
  return <Screen title="Your words" subtitle={subtitle}>
    <Section title="Learning">
      <Toggle label="Learn new words I type" value={learning} onChange={learning => save.run({ learning })} />
    </Section>
    <Section title="Names on this phone">
      {/* On while the setting is, even with Android's permission taken away (the line below says so), so it can be turned off and
          the line goes: shown off, it could only ask for the permission again. */}
      <Toggle label="Names from my contacts" value={contactNames || result.data.contactNamesBlocked}
        onChange={contactNames => save.run({ contactNames })} />
      <Needs list={needs.contactNames(result.data)} />
    </Section>
    <Section title="My words">
      <Field label="Saved words" href="/settings/data/saved-words" />
      <Field label="Blocked words" href="/settings/data/blocked-words" />
    </Section>
    <Section title="Backup">
      {/* "Back up everything" and "Restore" are the owner's names (#65). */}
      <Field label="Back up everything" onPress={() => void transfer("backup", "data")} />
      <Field label="Restore" onPress={() => navigate("/settings/data/restore")} />
      <Field label="Remove learned words" href="/settings/data/reset" />
    </Section>
  </Screen>;
}
