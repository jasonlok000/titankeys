import { Field, Screen, back } from "ink";
import { deleteRawLog } from "../../lib/atlas";
import { playHaptic } from "../../lib/app-haptics";

export default function DeleteRawLog() {
  return <Screen title="Delete the log?" subtitle="This deletes the whole typing log from your phone. You can't undo it.">
    <Field label="Delete the log" onPress={() => void deleteRawLog().then(() => {
      playHaptic("app_deleted");
      back();
    })}>{""}</Field>
  </Screen>;
}
