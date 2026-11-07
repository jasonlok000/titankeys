import { Field, Screen, back, navigate, replace } from "ink";
import { restartCalibration } from "../../../lib/atlas";

export default function Restart() {
  return <Screen title="Start again?" subtitle="This clears your touches. Your current profile stays active.">
    <Field label="Start again" onPress={() => {
      void restartCalibration().then(() => {
        back();
        replace("/settings");
        navigate("/settings/calibration");
        navigate("/settings/calibration/new");
      });
    }}>{""}</Field>
  </Screen>;
}
