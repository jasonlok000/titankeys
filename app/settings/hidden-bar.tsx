import { useSnapshot } from "ink";
import { type State, state } from "../../lib/atlas";
import { AppListPage, appsLabel } from "../../lib/app-list";
import { Note } from "../../lib/note";
import { barLabel } from "../../lib/setting-names";

/** The row's name in Settings and the boring guide. The page's own title is shorter, to fit the header. */
export const hiddenBarTitle = "Hide the bar in these apps";

export const hiddenBarLabel = ({ hiddenBarApps }: State) => hiddenBarApps.length ? appsLabel(hiddenBarApps) : "None";

/**
 * The apps where the bar hides. Hiding it everywhere is Show the bar in Settings, in one place only, so this page has no
 * switch of its own. Settings shows this row only while the bar is on.
 */
export default function HiddenBar() {
  const result = useSnapshot(state);
  return <AppListPage title="Hide the bar" list="hiddenBar" apps={({ hiddenBarApps }) => hiddenBarApps} addHref="/settings/hidden-bar-add"
    subtitle="The bar goes in these apps, everything else stays. Keys, holds, Alt and the Sym panel work like always.">
    {result.status === "ready" && !result.data.suggestionBar
      && <Note>{`The bar is off in every app right now. Turn on ${barLabel} in Settings to use this list.`}</Note>}
  </AppListPage>;
}
