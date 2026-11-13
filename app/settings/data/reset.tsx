import { Field, Screen, back } from "ink";
import { clearWords } from "../../../lib/atlas";
import { playHaptic } from "../../../lib/app-haptics";

export default function Reset() {
  return <Screen title="Forget all words?" subtitle="This wipes every word TitanKeys has learned from you. You can't undo it.">
    <Field label="Remove all learned words" onPress={() => void clearWords().then(() => {
      playHaptic("app_deleted");
      back();
    })}>{""}</Field>
  </Screen>;
}
