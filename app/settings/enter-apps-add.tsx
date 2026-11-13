import { Row, Screen, back, useAction, useSnapshot } from "ink";
import { add } from "ink/icons";
import { setEnterApp, state } from "../../lib/atlas";

const title = "Add app";
const subtitle = "Apps where you've typed a message lately. This list stays on your phone.";

export default function AddEnterApp() {
  const result = useSnapshot(state);
  // An app that needs adding does not declare a Send action, so the action is unlikely to do anything there.
  const insert = useAction(async (app: string) => {
    await setEnterApp(app, "key");
    back();
  });
  if (result.status !== "ready") return <Screen title={title} subtitle={subtitle} />;
  const apps = result.data.recentEnterApps;
  return <Screen title={title} subtitle={subtitle}>
    {apps.length
      ? apps.map(({ app, name }) => <Row key={app} title={name} titleMaxLines={2} endIcon={add} onPress={() => insert.run(app)} />)
      : <Row title="No apps to add" subtitle="Type a message in the app, then come back here." wrapSubtitle />}
  </Screen>;
}
