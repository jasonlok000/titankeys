import { Row, Screen, Section, useAction, useSnapshot } from "ink";
import { add, close } from "ink/icons";
import { type EnterApp, type SendMethod, removeEnterApp, setEnterApp, state } from "../../lib/atlas";

const title = "Message apps";
const subtitle = "The Enter key setting works in these apps, and in any field that has a Send action. Everywhere else, Enter adds a new line. Pick an app to change how TitanKeys sends in it.";
const sendLabels: Record<SendMethod, string> = { action: "Sends with the app's Send action", key: "Sends with Enter" };
const other = (method: SendMethod): SendMethod => method === "action" ? "key" : "action";

export default function EnterApps() {
  const result = useSnapshot(state);
  const change = useAction(setEnterApp);
  const remove = useAction(removeEnterApp);
  if (result.status !== "ready") return <Screen title={title} subtitle={subtitle} />;
  const { enterApps, enterAppsBuiltIn } = result.data;
  const select = ({ app, method }: EnterApp) => () => change.run(app, other(method));
  return <Screen title={title} subtitle={subtitle}>
    <Section title="Your apps">
      <Row title="Add app" endIcon={add} href="/settings/enter-apps-add" />
      {enterApps.map(entry => <Row key={entry.app} title={entry.name} titleMaxLines={2}
        subtitle={sendLabels[entry.method]} wrapSubtitle onPress={select(entry)}
        endAction={{ icon: close, onPress: () => remove.run(entry.app) }} />)}
    </Section>
    <Section title="Included apps">
      {enterAppsBuiltIn.map(entry => <Row key={entry.app} title={entry.name} titleMaxLines={2}
        subtitle={sendLabels[entry.method]} wrapSubtitle onPress={select(entry)} />)}
    </Section>
  </Screen>;
}
