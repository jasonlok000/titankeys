import { Field, Screen, back } from "ink";
import { clearWords } from "../../../lib/atlas";

export default function Reset() {
  return <Screen title="Remove all learned words?" subtitle="This removes every word that TitanKeys has learned from you. You cannot undo this.">
    <Field label="Remove all learned words" onPress={() => void clearWords().then(back)}>{""}</Field>
  </Screen>;
}
