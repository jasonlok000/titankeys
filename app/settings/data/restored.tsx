import { Field, Screen } from "ink";
import { returnToData } from "../../../lib/transfer";

/** After a whole backup's restore: TitanKeys restarted and opened here (BackupActivity). */
export default function Restored() {
  // F: draft title and subtitle.
  return <Screen title="Restored" subtitle="Your settings, words and lists from the backup are back. Anything you had here that wasn't in it stayed.">
    <Field label="Done" onPress={returnToData}>{""}</Field>
  </Screen>;
}
