import { Row, Screen, back, useAction, useRouteParams, useSnapshot } from "ink";
import { close } from "ink/icons";
import { apps, setShortcut, state } from "../../../lib/atlas";

export default function ChooseApp() {
  const { letter } = useRouteParams("/settings/shortcuts/[letter]");
  const settings = useSnapshot(state);
  const result = useSnapshot(apps);
  const save = useAction(async (app: string | null) => {
    await setShortcut(letter, app);
    back();
  });
  const current = settings.status === "ready" && settings.data.shortcuts.some(shortcut => shortcut.letter === letter);
  return <Screen title={`Letter ${letter}`} subtitle={`Choose the app that the hyper key and ${letter} open.`}>
    {current && <Row title="Remove" endIcon={close} onPress={() => save.run(null)} />}
    {result.status === "ready" && result.data.map(({ app, name }) => <Row key={app} title={name} onPress={() => save.run(app)} />)}
  </Screen>;
}
