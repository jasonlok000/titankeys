import type { State } from "../../lib/atlas";
import { AppListPage, appsLabel } from "../../lib/app-list";

export const hiddenBarLabel = ({ hiddenBarApps }: State) => hiddenBarApps.length ? appsLabel(hiddenBarApps) : "None";

export default function HiddenBar() {
  return <AppListPage title="Hide the bar" list="hiddenBar" apps={({ hiddenBarApps }) => hiddenBarApps} addHref="/settings/hidden-bar-add"
    subtitle="In these apps, the bar above the keys does not show. The keys, holds, Alt and the Sym panel operate as usual." />;
}
