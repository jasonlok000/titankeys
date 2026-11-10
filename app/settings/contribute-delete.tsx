import { Field, Screen, back } from "ink";
import { deleteContributions } from "../../lib/connected";
import { contributeText as text } from "../../lib/connected-text";

export default function DeleteContributions() {
  return <Screen title={text.deleteTitle} subtitle={text.deleteSubtitle}>
    <Field label={text.deleteConfirm} onPress={() => void deleteContributions().then(back)}>{""}</Field>
  </Screen>;
}
