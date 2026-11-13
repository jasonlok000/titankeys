import type { BarTextSize, State } from "../../lib/atlas";
import { ChoicePage } from "../../lib/choice";

/** Ids apart from the names, so no old stored value means a different size than it says (FixedScale.Size). */
const labels: Record<BarTextSize, string> = { small: "Smaller", medium: "Normal", large: "Bigger" };
const options = (["small", "medium", "large"] as const).map(value => ({ value, label: labels[value] }));

export const barTextSizeTitle = "Bar text size";
export const barTextSizeNote = "Words, menu and chips. The bar keeps its height.";

export const barTextSizeLabel = ({ barTextSize }: State) => labels[barTextSize] ?? "Normal";

/**
 * Only what's in the keyboard's bar grows: its words, the word menu, the Shift, Alt and Ctrl chips, the clip offer and its
 * hints (FixedScale.Size). Everyone starts at Normal. The bar keeps its height, and Settings, the panels and the guide don't
 * change. The keyboard catches up the next time it shows.
 */
export default function BarTextSizePage() {
  return <ChoicePage title={barTextSizeTitle}
    subtitle={barTextSizeNote}
    options={options}
    read={({ barTextSize }) => barTextSize}
    write={barTextSize => ({ barTextSize })} />;
}
