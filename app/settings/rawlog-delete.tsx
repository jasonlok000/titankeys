import { Field, Screen, back } from "ink";
import { deleteRawLog } from "../../lib/atlas";

export default function DeleteRawLog() {
  return <Screen title="Delete the log?" subtitle="This deletes the whole typing log from your phone. You can't undo it.">
    <Field label="Delete the log" onPress={() => void deleteRawLog().then(back)}>{""}</Field>
  </Screen>;
}
