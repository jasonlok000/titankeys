import { useSnapshot } from "ink";
import { type BarTextSize, type State, state } from "../../lib/atlas";
import { ChoicePage } from "../../lib/choice";

const labels: Record<BarTextSize, string> = { normal: "Normal", bigger: "Bigger", biggest: "Biggest" };
const options = (["normal", "bigger", "biggest"] as const).map(value => ({ value, label: labels[value] }));

export const barTextSizeTitle = "Bar text size";
export const barTextSizeNote = "Words, menu and chips. The bar keeps its height.";
/** Shown until you pick a size, and again after a reset. */
export const followsPhoneNote = "Follows your phone's text size until you pick one.";

/** The size, and "(phone)" while it follows the phone's text size. */
export const barTextSizeLabel = ({ barTextSize, barTextSizePicked }: State) =>
  (labels[barTextSize] ?? "Normal") + (barTextSizePicked ? "" : " (phone)");

/**
 * Only what's in the keyboard's bar grows: its words, the word menu, the Shift, Alt and Ctrl chips, the clip offer and its
 * hints (FixedScale.Size). The bar keeps its height, and Settings, the panels and the guide stay at Normal. The keyboard
 * catches up the next time it shows.
 */
export default function BarTextSizePage() {
  const result = useSnapshot(state);
  const following = result.status === "ready" && !result.data.barTextSizePicked;
  return <ChoicePage title={barTextSizeTitle}
    subtitle={following ? `${barTextSizeNote}\n${followsPhoneNote}` : barTextSizeNote}
    options={options}
    read={({ barTextSize }) => barTextSize}
    write={barTextSize => ({ barTextSize })} />;
}
