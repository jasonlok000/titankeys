import type { State } from "../../lib/atlas";
import { DurationPage, ms } from "../../lib/duration";

export const liftLabel = ({ liftOff, liftOffMillis }: State) => liftOff ? ms(liftOffMillis) : "Off";

/** The keyboard takes 50 to 200 ms (TypingSettings), and 90 is its default. */
export default function Lift() {
  return <DurationPage title="Keep swiping"
    subtitle="Fingers slip. If yours leaves the keys for less than this, the swipe or the cursor carries on."
    min={50} max={200} step={10}
    presets={[{ label: "Short", millis: 60 }, { label: "Medium", millis: 90 }, { label: "Long", millis: 150 }]}
    read={({ liftOff, liftOffMillis }) => liftOff ? liftOffMillis : null}
    write={millis => millis === null ? { liftOff: false } : { liftOff: true, liftOffMillis: millis }} />;
}
