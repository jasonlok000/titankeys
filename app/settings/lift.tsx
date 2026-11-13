import type { State } from "../../lib/atlas";
import { ChoicePage } from "../../lib/choice";

const durations = Array.from({ length: 16 }, (_, step) => String(50 + step * 10));
const options = [
  { value: "off", label: "Off" },
  ...durations.map(value => ({ value, label: `${value} ms` })),
];

export const liftLabel = ({ liftOff, liftOffMillis }: State) => liftOff ? `${liftOffMillis} ms` : "Off";

export default function Lift() {
  return <ChoicePage title="Keep swiping"
    subtitle="Fingers slip. If yours leaves the keys for less than this, the swipe or the cursor carries on."
    options={options}
    read={({ liftOff, liftOffMillis }) => liftOff ? String(liftOffMillis) : "off"}
    write={value => value === "off" ? { liftOff: false } : { liftOff: true, liftOffMillis: Number(value) }} />;
}
