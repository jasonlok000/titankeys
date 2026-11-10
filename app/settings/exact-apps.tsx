import type { State } from "../../lib/atlas";
import { AppListPage, appsLabel } from "../../lib/app-list";

export const exactAppsLabel = ({ exactApps }: State) => appsLabel(exactApps);

export default function ExactApps() {
  return <AppListPage title="Exact typing" list="exact" apps={({ exactApps }) => exactApps} addHref="/settings/exact-apps-add"
    subtitle="For terminals, remote computers and game streaming. In these apps, TitanKeys does not fix words, add capital letters or full stops, or show suggestions. Each key goes to the app as you push it. The touchpad sends nothing to these apps." />;
}
