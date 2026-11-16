import { Field, Screen, useRouteParams } from "ink";
import { returnToData } from "../../../lib/transfer";

/** After Restore took an Export words or Export snippets file from before 3.0. */
export default function Imported() {
  const { what } = useRouteParams();
  const snippets = what === "snippets";
  return <Screen title={snippets ? "Snippets imported" : "Words imported"}
    subtitle={snippets ? "The snippets are in your list now." : "The new words are in your list now."}>
    {/* F: "Snippets imported" and its subtitle are drafts; the words lines are the old Import words page's. */}
    <Field label="Done" onPress={returnToData}>{""}</Field>
  </Screen>;
}
