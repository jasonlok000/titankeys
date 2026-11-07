import { Field, Screen } from "ink";
import { returnToData } from "../../../lib/transfer";

export default function Imported() {
  return <Screen title="Words imported" subtitle="New words were added to your list.">
    <Field label="Done" onPress={returnToData}>{""}</Field>
  </Screen>;
}
