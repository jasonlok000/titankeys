import { useEffect, useState } from "react";
import { Field, Screen, navigate, replace } from "ink";
import { type Calibration, repeatCheck, watchCalibration } from "../../../lib/atlas";

export default function Failed() {
  const [calibration, setCalibration] = useState<Calibration>();
  useEffect(() => watchCalibration(setCalibration), []);
  return <Screen title="Calibration failed" subtitle="Every key must pass. Nothing has changed.">
    {calibration?.canRepeat && <Field label="Repeat the check" onPress={() => {
      void repeatCheck().then(() => {
        replace("/settings");
        navigate("/settings/calibration");
        navigate("/settings/calibration/new");
      });
    }}>{""}</Field>}
    <Field label="Start again" href="/settings/calibration/restart">{""}</Field>
  </Screen>;
}
