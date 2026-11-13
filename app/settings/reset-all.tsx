import { Canvas, CanvasText, Field, Screen, Stack, back, useAction } from "ink";
import { resetSettings } from "../../lib/atlas";
import { playHaptic } from "../../lib/app-haptics";
import { INK, useAccent } from "../../lib/brand";
import { RADIUS } from "../../lib/radii";
import { RoundedFill } from "../../lib/rounded";

const WIDTH = 596;
const HEIGHT = 96;

/** Shown once under Reset all settings, after a reset, until you leave Settings. */
export const resetDone = { shown: false };

/** The note after a reset, in the owner's tone. */
export const RESET_NOTE = "Back to factory. Try not to break it again.";

/**
 * Asks before Settings > Reset all settings, as the app's own confirm pages do (Forget all words?, Delete the log?): Reset
 * first, filled, and Keep mine to go back. A reset isn't wiping data, so it confirms with Confirm, not Gone (docs/haptics.md).
 */
export default function ResetAll() {
  const accent = useAccent();
  const reset = useAction(async () => {
    // Set first: the reset refreshes Settings, which then shows the note under its row.
    resetDone.shown = true;
    try {
      await resetSettings();
    } catch (error) {
      resetDone.shown = false;
      throw error;
    }
    playHaptic("app_picked");
    back();
  });
  return <Screen title="Reset all settings?"
    subtitle="Every switch and choice goes back to how it started. Your words, snippets and app lists stay.">
    <Stack gap={24}>
      <Canvas width={WIDTH} height={HEIGHT}>
        <RoundedFill x={0} y={0} width={WIDTH} height={HEIGHT} radius={RADIUS.large} fill={accent}
          onPress={() => { if (reset.status !== "pending") void reset.run(); }} />
        <CanvasText x={0} y={0} width={WIDTH} height={HEIGHT} size={32} colour={INK} align="center">Reset</CanvasText>
      </Canvas>
      <Field label="Keep mine" onPress={back} />
    </Stack>
  </Screen>;
}
