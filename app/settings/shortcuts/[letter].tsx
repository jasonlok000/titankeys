import { Row, Screen, back, useAction, useRouteParams, useSnapshot } from "ink";
import { apps, setShortcut } from "../../../lib/atlas";

export default function ChooseApp() {
  const { letter } = useRouteParams("/settings/shortcuts/[letter]");
  const result = useSnapshot(apps);
  const save = useAction(async (app: string) => {
    await setShortcut(letter, app);
    back();
  });
  return <Screen title={`Shortcut ${letter}`} subtitle="Choose the app to open.">
    {result.status === "ready" && result.data.map(({ app, name }) => <Row key={app} title={name} onPress={() => save.run(app)} />)}
  </Screen>;
}
