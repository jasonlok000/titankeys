import { useState } from "react";
import { Screen, SettingsChoices, TextInput, useAction, useSnapshot } from "ink";
import { playHaptic } from "../../lib/app-haptics";
import { type BarTextSize, type State, state, update } from "../../lib/atlas";
import { MUTED } from "../../lib/brand";

/** Ids apart from the names, so no old stored value means a different size than it says (FixedScale.Size). */
const labels: Record<BarTextSize, string> = { small: "Smaller", medium: "Normal", large: "Bigger" };
const options = (["small", "medium", "large"] as const).map(value => ({ value, label: labels[value] }));

export const barTextSizeTitle = "Bar text size";
export const barTextSizeNote = "Words, menu and chips. The bar keeps its height.";
// F: draft, the try field's sample line. It ends mid-word, so the bar fills with words as the page opens (#86).
export const barTextSizeSample = "Pick a size and watch the bar while you type. Looks good from here, but you deci";

export const barTextSizeLabel = ({ barTextSize }: State) => labels[barTextSize] ?? "Normal";

/**
 * Only what's in the keyboard's bar grows: its words, the word menu, the Shift, Alt and Ctrl chips, the clip offer and its
 * hints (FixedScale.Size). Everyone starts at Normal. The bar keeps its height, and Settings, the panels and the guide don't
 * change. The field at the top opens the keyboard by itself, with a sample line that ends mid-word so the bar has words in it,
 * and the sizes sit under it, so both stay in sight with the keyboard up. The keyboard hears a pick and makes the bar again at
 * once (AtlasIme's barSize listener). Chips and the clip offer show as they normally would: nothing fake is drawn.
 */
export default function BarTextSizePage() {
  const result = useSnapshot(state);
  const save = useAction(update);
  const [text, setText] = useState(barTextSizeSample);
  return <Screen title={barTextSizeTitle} subtitle={barTextSizeNote}>
    <TextInput value={text} onChange={setText} action="return" minLines={2} maxLines={3} autoFocus
      style={{ border: "outline", borderWidth: 1.33, borderColour: MUTED, paddingX: 21, paddingY: 23, fontSize: 30 }} />
    {result.status === "ready" && <SettingsChoices value={result.data.barTextSize} options={options}
      onChange={barTextSize => {
        playHaptic("app_picked");
        void save.run({ barTextSize });
      }} />}
  </Screen>;
}
