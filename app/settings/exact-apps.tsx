import type { State } from "../../lib/atlas";
import { AppListPage, appsLabel } from "../../lib/app-list";

export const exactAppsLabel = ({ exactApps }: State) => appsLabel(exactApps);

export default function ExactApps() {
  return <AppListPage title="Exact typing" list="exact" apps={({ exactApps }) => exactApps} addHref="/settings/exact-apps-add"
    subtitle="For terminals, remote desktops and game streaming. Every key goes straight to the app, exactly as you press it. TitanKeys keeps its hands off your text here, and the touchpad stays out of it." />;
}
