import { Field, Screen } from "ink";
import { returnToData } from "../../../lib/transfer";

export default function Imported() {
  return <Screen title="Words imported" subtitle="The new words are in your list now.">
    <Field label="Done" onPress={returnToData}>{""}</Field>
  </Screen>;
}
