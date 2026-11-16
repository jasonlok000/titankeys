import { Field, Screen } from "ink";
import { returnToData } from "../../../lib/transfer";

/** After a whole backup's restore: TitanKeys restarted and opened here (BackupActivity). */
export default function Restored() {
  return <Screen title="Restored" subtitle="Your settings, words and lists from the backup are back. Anything here that wasn't in it is still here.">
    <Field label="Done" onPress={returnToData}>{""}</Field>
  </Screen>;
}
