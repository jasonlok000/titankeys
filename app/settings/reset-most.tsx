import { back } from "ink";
import { resetSettings } from "../../lib/atlas";
import { playHaptic } from "../../lib/app-haptics";
import { ConfirmPage } from "../../lib/confirm";

/** Shown once under the reset rows, after Reset most things, until you leave Settings. */
export const resetDone = { shown: false };

/** The note after Reset most things, in the owner's tone. */
export const RESET_NOTE = "Back to factory. Try not to break it again.";

/**
 * Settings > Reset most things: every switch, stepper and choice goes back to how it started, and what's yours stays
 * (SettingsReset.kt). It wipes no data, so it confirms with Confirm, not Gone (docs/haptics.md).
 */
export default function ResetMost() {
  return <ConfirmPage title="Reset most things?"
    body="Every switch and choice goes back to how it started. Your words, snippets and app lists stay."
    action="Reset" cancel="Keep mine"
    run={async () => {
      // Set first: the reset refreshes Settings, which then shows the note under its rows.
      resetDone.shown = true;
      try {
        await resetSettings();
      } catch (error) {
        resetDone.shown = false;
        throw error;
      }
      playHaptic("app_picked");
      back();
    }} />;
}
