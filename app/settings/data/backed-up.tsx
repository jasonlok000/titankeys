import { Field, Screen } from "ink";
import { returnToData } from "../../../lib/transfer";

export default function BackedUp() {
  // F: draft title and subtitle.
  return <Screen title="Backed up" subtitle="Your backup file is saved. Keep it somewhere safe. It holds your words, so only share it with people you'd show them to.">
    <Field label="Done" onPress={returnToData}>{""}</Field>
  </Screen>;
}
