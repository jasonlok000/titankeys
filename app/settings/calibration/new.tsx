import { useEffect, useState } from "react";
import { Screen, Stack, Text, back, replace } from "ink";
import { type Calibration, watchCalibration } from "../../../lib/atlas";
import { KeyboardDiagram, keyName } from "../../../lib/keyboard";

function instruction({ stage, touch, retry }: Calibration) {
  if (retry) return "Try again. Hold lightly, keep still, then lift.";
  return stage === "touch" ? `Touch ${touch} of 3. Hold lightly, then lift.` : "Ring: your touch. Dot: after calibration.";
}

export default function NewProfile() {
  const [calibration, setCalibration] = useState<Calibration>();
  useEffect(() => watchCalibration(setCalibration), []);
  const stage = calibration?.stage;
  useEffect(() => {
    if (stage !== "passed" && stage !== "failed") return;
    // These results have no way back, so they start a new stack.
    back();
    back();
    replace(stage === "passed" ? "/settings/calibration/passed" : "/settings/calibration/failed");
  }, [stage]);
  if (!calibration || (stage !== "touch" && stage !== "check")) return <Screen title="Touch each key" />;
  const touching = stage === "touch";
  return <Screen title={touching ? "Touch each key" : "Check each key"}
    subtitle={`Step ${touching ? 1 : 2} of 3. Key ${calibration.keyNumber} of 37.`}>
    <Stack gap={36}>
      <Stack align="center" gap={12}>
        <Text size={80} align="center" maxLines={1}>{keyName(calibration.key)}</Text>
        <Text align="center">{instruction(calibration)}</Text>
      </Stack>
      <KeyboardDiagram target={{ key: calibration.key, style: touching ? "current" : "check" }}
        ring={calibration.raw} dot={calibration.adjusted} />
    </Stack>
  </Screen>;
}
