import { Row, Screen, Section, useAction, useSnapshot } from "ink";
import { add, close } from "ink/icons";
import { type State, setExactApp, state } from "../../lib/atlas";

const title = "Exact typing";
const subtitle = "For terminals, remote computers and code. In these apps, TitanKeys does not fix words, add capital letters or full stops, or show suggestions. Each key goes to the app as you push it.";

export const exactAppsLabel = ({ exactApps }: State) => exactApps.length === 1 ? "1 app" : `${exactApps.length} apps`;

export default function ExactApps() {
  const result = useSnapshot(state);
  const change = useAction(setExactApp);
  if (result.status !== "ready") return <Screen title={title} subtitle={subtitle} />;
  return <Screen title={title} subtitle={subtitle}>
    <Section title="Apps">
      <Row title="Add app" endIcon={add} href="/settings/exact-apps-add" />
      {result.data.exactApps.map(({ app, name }) => <Row key={app} title={name} titleMaxLines={2}
        endAction={{ icon: close, onPress: () => change.run(app, false) }} />)}
    </Section>
  </Screen>;
}
