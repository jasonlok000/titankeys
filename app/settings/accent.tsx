import { Screen, SettingsChoices, useAction, useSnapshot } from "ink";
import { playHaptic } from "../../lib/app-haptics";
import { state, update } from "../../lib/titankeys";
import { accentChoices, accentsNote, accentTitle } from "../../lib/setting-names";

const options = accentChoices.map(({ value, label }) => ({ value, label }));

/**
 * Settings > Accent colour (#111): Off, Orange (the default), a few checked presets, or the phone's own colour. A pick takes
 * effect at once, here and in every window as it next draws (AppNativeAdapters' accentColour).
 */
export default function AccentPage() {
  const result = useSnapshot(state);
  const save = useAction(update);
  const id = result.status === "ready" ? result.data.accentColour : "orange";
  return <Screen title={accentTitle} subtitle={accentsNote(id)}>
    {result.status === "ready" && <SettingsChoices value={id} options={options}
      onChange={accentColour => {
        playHaptic("app_picked");
        void save.run({ accentColour });
      }} />}
  </Screen>;
}
