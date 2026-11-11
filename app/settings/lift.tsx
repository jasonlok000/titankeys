import type { State } from "../../lib/atlas";
import { ChoicePage } from "../../lib/choice";

const durations = Array.from({ length: 16 }, (_, step) => String(50 + step * 10));
const options = [
  { value: "off", label: "Off" },
  ...durations.map(value => ({ value, label: `${value} ms` })),
];

export const liftLabel = ({ liftOff, liftOffMillis }: State) => liftOff ? `${liftOffMillis} ms` : "Off";

export default function Lift() {
  return <ChoicePage title="Continue after a lift"
    subtitle="How long you can lift your finger and still finish the word."
    options={options}
    read={({ liftOff, liftOffMillis }) => liftOff ? String(liftOffMillis) : "off"}
    write={value => value === "off" ? { liftOff: false } : { liftOff: true, liftOffMillis: Number(value) }} />;
}
