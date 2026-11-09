import { Field, Screen, back } from "ink";
import { deleteRawLog } from "../../lib/atlas";

export default function DeleteRawLog() {
  return <Screen title="Delete all of the log?" subtitle="This removes the full raw input log from this phone. You cannot undo this.">
    <Field label="Delete all" onPress={() => void deleteRawLog().then(back)}>{""}</Field>
  </Screen>;
}
