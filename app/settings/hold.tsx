import { useRouteParams } from "ink";
import type { State } from "../../lib/titankeys";
import { DurationPage, ms } from "../../lib/duration";
import { languageValues } from "./languages/index";

/** On or off is [language]'s own where its page shows the row (TypingSettings.PER_LANGUAGE); the time is one for every hold. */
export const holdLabel = (settings: State, language?: string) =>
  (language ? languageValues(settings, language) : settings).holdSymbols ? ms(settings.holdSymbolMillis) : "Off";

/** The keyboard takes 200 to 1000 ms (HoldEngine.clamp), and 400, Medium, is its default (TypingSettings.DEFAULT_HOLD_SYMBOL_MILLIS). */
export default function Hold() {
  const language = useRouteParams().language as string | undefined;
  const on = (holdSymbols: boolean) => language ? { languageSettings: { [language]: { holdSymbols } } } : { holdSymbols };
  return <DurationPage title="Hold for symbols"
    subtitle="Hold a letter to type the symbol printed on it. Pick how long you hold."
    min={200} max={1000} step={50}
    presets={[{ label: "Quick", millis: 250 }, { label: "Medium", millis: 400 }, { label: "Slow", millis: 600 }]}
    read={settings => (language ? languageValues(settings, language) : settings).holdSymbols ? settings.holdSymbolMillis : null}
    write={millis => millis === null ? on(false) : { ...on(true), holdSymbolMillis: millis }} />;
}
