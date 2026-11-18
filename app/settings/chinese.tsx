import type { ChineseVariant, State } from "../../lib/titankeys";
import { ChoicePage } from "../../lib/choice";

// One setting for every Chinese input method, with no default: the user picks (owner, 2026-11-16). docs/languages.md.
// F (docs/words-m1.md).
const labels: Record<ChineseVariant, string> = {
  zh_hk: "正體中文（香港）",
  zh_tw: "正體中文（臺灣）",
  zh_hans: "简体中文（中国大陆）",
};
const options = (["zh_hk", "zh_tw", "zh_hans"] as const).map(value => ({ value, label: labels[value] }));

/** "Not picked yet" until the user has: there's no default. */
export const chineseLabel = ({ chineseVariant }: State) => chineseVariant ? labels[chineseVariant] : "尚未選擇";

export default function ChinesePage() {
  return <ChoicePage<ChineseVariant> title="中文字體"
    subtitle="所有中文輸入法都使用這個設定。澳門請選香港；新加坡或馬來西亞請選簡體中文。"
    options={options}
    read={({ chineseVariant }) => (chineseVariant || undefined) as ChineseVariant}
    write={chineseVariant => ({ chineseVariant })} />;
}
