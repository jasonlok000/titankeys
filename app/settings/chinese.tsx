import type { ChineseVariant, State } from "../../lib/atlas";
import { ChoicePage } from "../../lib/choice";

// One setting for every Chinese input method, with no default: the user picks (owner, 2026-11-16). docs/languages.md.
// F: draft labels and subtitle.
const labels: Record<ChineseVariant, string> = {
  zh_hk: "Traditional (Hong Kong)",
  zh_tw: "Traditional (Taiwan)",
  zh_hans: "Simplified (mainland China)",
};
const options = (["zh_hk", "zh_tw", "zh_hans"] as const).map(value => ({ value, label: labels[value] }));

/** "Pick one" until the user has: there's no default. F: draft. */
export const chineseLabel = ({ chineseVariant }: State) => chineseVariant ? labels[chineseVariant] : "Pick one";

/** Whether an input method that's on is Chinese, so Settings shows this page. */
export const anyChinese = ({ inputMethods, availableInputMethods }: State) =>
  availableInputMethods.some(im => im.engine === "rime" && inputMethods.includes(im.id));

export default function ChinesePage() {
  return <ChoicePage<ChineseVariant> title="Chinese characters"
    subtitle="Pick how your characters come out. You can change it any time."
    options={options}
    read={({ chineseVariant }) => (chineseVariant || undefined) as ChineseVariant}
    write={chineseVariant => ({ chineseVariant })} />;
}
