import { Section, Toggle, useAction, useSnapshot } from "ink";
import { type State, state, update } from "../../lib/atlas";
import { AppListPage, appsLabel } from "../../lib/app-list";

export const hiddenBarLabel = ({ suggestionBar, hiddenBarApps }: State) =>
  !suggestionBar ? "All apps" : hiddenBarApps.length ? appsLabel(hiddenBarApps) : "None";

/**
 * Hiding the bar in all apps is the same setting as Show word suggestions, seen the other way round: both leave the keyboard's
 * window one pixel high. With it off, the list decides.
 */
export default function HiddenBar() {
  const result = useSnapshot(state);
  const save = useAction(update);
  return <AppListPage title="Hide the bar" list="hiddenBar" apps={({ hiddenBarApps }) => hiddenBarApps} addHref="/settings/hidden-bar-add"
    subtitle="The bar goes, everything else stays. Keys, holds, Alt and the Sym panel work like always.">
    {result.status === "ready" && <Section title="All apps">
      <Toggle label="Hide the bar in all apps" subtitle="Off, the bar only hides in the apps below."
        value={!result.data.suggestionBar} onChange={hide => save.run({ suggestionBar: !hide })} />
    </Section>}
  </AppListPage>;
}
