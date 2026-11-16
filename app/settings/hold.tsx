import type { State } from "../../lib/titankeys";
import { DurationPage, ms } from "../../lib/duration";

export const holdLabel = ({ holdSymbols, holdSymbolMillis }: State) => holdSymbols ? ms(holdSymbolMillis) : "Off";

/** The keyboard takes 200 to 1000 ms (HoldEngine.clamp), and 400, Medium, is its default (TypingSettings.DEFAULT_HOLD_SYMBOL_MILLIS). */
export default function Hold() {
  return <DurationPage title="Hold for symbols"
    subtitle="Hold a letter to type the symbol printed on it. Pick how long you hold."
    min={200} max={1000} step={50}
    presets={[{ label: "Quick", millis: 250 }, { label: "Medium", millis: 400 }, { label: "Slow", millis: 600 }]}
    read={({ holdSymbols, holdSymbolMillis }) => holdSymbols ? holdSymbolMillis : null}
    write={millis => millis === null ? { holdSymbols: false } : { holdSymbols: true, holdSymbolMillis: millis }} />;
}
