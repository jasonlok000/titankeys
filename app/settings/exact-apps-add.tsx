import { Row, Screen, back, useAction, useSnapshot } from "ink";
import { add } from "ink/icons";
import { setExactApp, state } from "../../lib/atlas";

const title = "Add app";
const subtitle = "These are apps where you recently typed. TitanKeys keeps this list on this phone.";

export default function AddExactApp() {
  const result = useSnapshot(state);
  const insert = useAction(async (app: string) => {
    await setExactApp(app, true);
    back();
  });
  if (result.status !== "ready") return <Screen title={title} subtitle={subtitle} />;
  const apps = result.data.recentApps;
  return <Screen title={title} subtitle={subtitle}>
    {apps.length
      ? apps.map(({ app, name }) => <Row key={app} title={name} titleMaxLines={2} endIcon={add} onPress={() => insert.run(app)} />)
      : <Row title="No apps to add" subtitle="Type in the app. Then come back to this page." wrapSubtitle />}
  </Screen>;
}
