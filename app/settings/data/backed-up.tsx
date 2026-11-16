import { Field, Screen } from "ink";
import { returnToData } from "../../../lib/transfer";

export default function BackedUp() {
  return <Screen title="Backed up" subtitle="Saved. Keep it somewhere safe. It holds your words, so only share it with people you'd show them to.">
    <Field label="Done" onPress={returnToData}>{""}</Field>
  </Screen>;
}
