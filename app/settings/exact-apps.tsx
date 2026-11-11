import { Section, Toggle, useAction, useSnapshot } from "ink";
import { type State, state, update } from "../../lib/atlas";
import { AppListPage, appsLabel } from "../../lib/app-list";

export const exactAppsLabel = ({ exactApps }: State) => appsLabel(exactApps);

export default function ExactApps() {
  const result = useSnapshot(state);
  const save = useAction(update);
  return <AppListPage title="Exact typing (experimental)" list="exact" apps={({ exactApps }) => exactApps} addHref="/settings/exact-apps-add"
    subtitle="Experimental. For terminals, remote desktops and game streaming. Every key goes straight to the app, exactly as you press it. TitanKeys keeps its hands off your text here, and the touchpad stays out of it.">
    {result.status === "ready" && <Section title="Keys">
      <Toggle label="Alt sends Alt, not symbols" subtitle="In these apps, Alt, Shift and Ctrl go to the app as real modifiers. Symbols come from Sym or from holding a key."
        value={result.data.exactRealAlt} onChange={exactRealAlt => save.run({ exactRealAlt })} />
    </Section>}
  </AppListPage>;
}
