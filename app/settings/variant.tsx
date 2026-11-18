import { Screen, useRouteParams, useSnapshot } from "ink";
import { type InputMethodEntry, type State, state } from "../../lib/titankeys";
import { translator } from "../../lib/language-copy";
import { ChoicePage } from "../../lib/choice";

// One input method's spelling variant (Portuguese's Brasil or Portugal): its own word list and next words. The route's
// `inputMethod` parameter is its id; the first variant is the default. F: draft.

/** The variant [im] types now, by its name, for the field on its language's page. */
export const variantLabel = ({ inputMethodVariants }: State, im: InputMethodEntry) => {
  const id = inputMethodVariants[im.id] ?? im.variants[0]?.id;
  return im.variants.find(variant => variant.id === id)?.name ?? "";
};

export default function VariantPage() {
  const id = useRouteParams().inputMethod;
  const t = translator(id);
  const result = useSnapshot(state);
  const im = result.status === "ready" ? result.data.availableInputMethods.find(each => each.id === id) : undefined;
  if (!im || im.variants.length === 0) return <Screen title={t("Spelling")} />;
  const options = im.variants.map(variant => ({ value: variant.id, label: variant.name }));
  return <ChoicePage<string> title={t("Spelling")} subtitle={t("The words {X} suggests and fixes to, and its next words.").replace("{X}", im.name)}
    options={options}
    read={({ inputMethodVariants }) => inputMethodVariants[im.id] ?? im.variants[0]!.id}
    write={variant => ({ inputMethodVariants: { [im.id]: variant } })} />;
}
