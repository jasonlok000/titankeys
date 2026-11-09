import type { State } from "../../lib/atlas";
import { ChoicePage } from "../../lib/choice";

const durations = Array.from({ length: 17 }, (_, step) => String(200 + step * 50));
const options = [
  { value: "off", label: "Off" },
  ...durations.map(value => ({ value, label: `${value} ms` })),
];

export const holdLabel = ({ holdSymbols, holdSymbolMillis }: State) => holdSymbols ? `${holdSymbolMillis} ms` : "Off";

export default function Hold() {
  return <ChoicePage title="Hold for symbols"
    subtitle="How long you hold a letter to type the symbol on its key."
    options={options}
    read={({ holdSymbols, holdSymbolMillis }) => holdSymbols ? String(holdSymbolMillis) : "off"}
    write={value => value === "off" ? { holdSymbols: false } : { holdSymbols: true, holdSymbolMillis: Number(value) }} />;
}
