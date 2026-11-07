import { Field, Screen } from "ink";

export default function Passed() {
  return <Screen title="Calibration complete" subtitle="All 37 keys passed.">
    <Field label="Save profile" href="/settings/calibration/name">{""}</Field>
    <Field label="Start again" href="/settings/calibration/restart">{""}</Field>
  </Screen>;
}
