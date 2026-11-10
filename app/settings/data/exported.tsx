import { Field, Screen } from "ink";
import { returnToData } from "../../../lib/transfer";

export default function Exported() {
  return <Screen title="Words exported" subtitle="Your backup file is saved. Keep it somewhere safe.">
    <Field label="Done" onPress={returnToData}>{""}</Field>
  </Screen>;
}
