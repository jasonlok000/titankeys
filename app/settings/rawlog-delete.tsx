import { Field, Screen, back } from "ink";
import { deleteRawLog } from "../../lib/atlas";

export default function DeleteRawLog() {
  return <Screen title="Delete the log?" subtitle="This removes the whole typing log from this phone. You cannot undo this.">
    <Field label="Delete the log" onPress={() => void deleteRawLog().then(back)}>{""}</Field>
  </Screen>;
}
