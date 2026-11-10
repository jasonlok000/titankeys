import type { ReactNode } from "react";
import { Row, Screen, Section, back, useAction, useSnapshot } from "ink";
import { add, close } from "ink/icons";
import { type App, type AppList, type State, installedApps, setListedApp, state } from "./atlas";

/** The apps on a list, as Settings shows them on its row. Apps that are not on this phone do not count. */
export const appsLabel = (apps: readonly App[]) => {
  const count = apps.filter(app => app.installed !== false).length;
  return count === 1 ? "1 app" : `${count} apps`;
};

/** A list of apps in Settings: add any app, and remove any app, also one that was on the list at first. */
export function AppListPage({ title, subtitle, list, apps, addHref, children }: {
  title: string;
  subtitle: string;
  list: AppList;
  apps: (state: State) => readonly App[];
  addHref: string;
  children?: ReactNode;
}) {
  const result = useSnapshot(state);
  const change = useAction(setListedApp);
  if (result.status !== "ready") return <Screen title={title} subtitle={subtitle} />;
  return <Screen title={title} subtitle={subtitle}>
    {children}
    <Section title="Apps">
      <Row title="Add app" endIcon={add} href={addHref} />
      {apps(result.data).map(({ app, name, installed }) => <Row key={app} title={name} titleMaxLines={2}
        subtitle={installed === false ? "Not on this phone" : undefined}
        endAction={{ icon: close, onPress: () => change.run(list, app, false) }} />)}
    </Section>
  </Screen>;
}

/** Adds an app to a list: the apps used recently first, then every app in the launcher. */
export function AddAppPage({ list, apps }: { list: AppList; apps: (state: State) => readonly App[] }) {
  const title = "Add app";
  const result = useSnapshot(state);
  const installed = useSnapshot(installedApps);
  const insert = useAction(async (app: string) => {
    await setListedApp(list, app, true);
    back();
  });
  if (result.status !== "ready") return <Screen title={title} />;
  const listed = new Set(apps(result.data).map(({ app }) => app));
  const row = ({ app, name }: App) => <Row key={app} title={name} titleMaxLines={2} endIcon={add} onPress={() => insert.run(app)} />;
  const recent = result.data.recentApps.filter(({ app }) => !listed.has(app));
  return <Screen title={title}>
    {recent.length > 0 && <Section title="Recently used">{recent.map(row)}</Section>}
    <Section title="All apps">
      {installed.status === "ready"
        ? installed.data.filter(({ app }) => !listed.has(app)).map(row)
        : <Row title={installed.status === "error" ? "Could not read the apps" : "Reading the apps…"} />}
    </Section>
  </Screen>;
}
