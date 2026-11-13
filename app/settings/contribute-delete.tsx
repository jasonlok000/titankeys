import { Field, Screen, back } from "ink";
import { deleteContributions } from "../../lib/connected";
import { contributeText as text } from "../../lib/connected-text";
import { playHaptic } from "../../lib/app-haptics";

export default function DeleteContributions() {
  return <Screen title={text.confirmTitle} subtitle={text.confirmSubtitle}>
    <Field label={text.confirm} onPress={() => void deleteContributions().then(() => {
      playHaptic("app_deleted");
      back();
    })}>{""}</Field>
  </Screen>;
}
