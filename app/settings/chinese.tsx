import type { ChineseVariant, State } from "../../lib/titankeys";
import { ChoicePage } from "../../lib/choice";

// One setting for every Chinese input method, with no default: the user picks (owner, 2026-11-16). docs/languages.md.
// F (docs/words-m1.md).
const labels: Record<ChineseVariant, string> = {
  zh_hk: "Traditional (Hong Kong)",
  zh_tw: "Traditional (Taiwan)",
  zh_hans: "Simplified (mainland China)",
};
const options = (["zh_hk", "zh_tw", "zh_hans"] as const).map(value => ({ value, label: labels[value] }));

/** "Not picked yet" until the user has: there's no default. */
export const chineseLabel = ({ chineseVariant }: State) => chineseVariant ? labels[chineseVariant] : "Not picked yet";

export default function ChinesePage() {
  return <ChoicePage<ChineseVariant> title="Chinese characters"
    subtitle="Every Chinese input method types in these. In Macau, pick Hong Kong. In Singapore or Malaysia, Simplified."
    options={options}
    read={({ chineseVariant }) => (chineseVariant || undefined) as ChineseVariant}
    write={chineseVariant => ({ chineseVariant })} />;
}
