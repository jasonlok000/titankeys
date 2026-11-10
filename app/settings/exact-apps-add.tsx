import { Row, Screen, Section, back, useAction, useSnapshot } from "ink";
import { add } from "ink/icons";
import { type App, installedApps, setExactApp, state } from "../../lib/atlas";

const title = "Add app";

export default function AddExactApp() {
  const result = useSnapshot(state);
  const apps = useSnapshot(installedApps);
  const insert = useAction(async (app: string) => {
    await setExactApp(app, true);
    back();
  });
  if (result.status !== "ready") return <Screen title={title} />;
  const listed = new Set(result.data.exactApps.map(({ app }) => app));
  const row = ({ app, name }: App) => <Row key={app} title={name} titleMaxLines={2} endIcon={add} onPress={() => insert.run(app)} />;
  const recent = result.data.recentApps;
  return <Screen title={title}>
    {recent.length > 0 && <Section title="Recently used">{recent.map(row)}</Section>}
    <Section title="All apps">
      {apps.status === "ready"
        ? apps.data.filter(({ app }) => !listed.has(app)).map(row)
        : <Row title={apps.status === "error" ? "Could not read the apps" : "Reading the apps…"} />}
    </Section>
  </Screen>;
}
