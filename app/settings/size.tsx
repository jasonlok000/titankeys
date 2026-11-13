import type { Size, State } from "../../lib/atlas";
import { ChoicePage } from "../../lib/choice";

const labels: Record<Size, string> = { normal: "Normal", bigger: "Bigger", biggest: "Biggest" };
const options = (["normal", "bigger", "biggest"] as const).map(value => ({ value, label: labels[value] }));

export const sizeLabel = ({ size }: State) => labels[size] ?? "Normal";

/**
 * Everything TitanKeys draws, bigger: the bar, the word menu, the panels, the guide, the front page and these pages
 * (FixedScale.Size). Settings grows at once, the rest the next time it shows. The keys' places don't move.
 */
export default function SizePage() {
  return <ChoicePage title="Size"
    subtitle="Squinting? Everything TitanKeys draws gets bigger, the bar too. The keyboard catches up the next time it opens."
    options={options}
    read={({ size }) => size}
    write={size => ({ size })} />;
}
