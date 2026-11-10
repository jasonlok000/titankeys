import { Row, Screen, Section, Toggle, useAction, useSnapshot } from "ink";
import { add, close } from "ink/icons";
import { type State, setExactApp, state, update } from "../../lib/atlas";

const title = "Exact typing";
const subtitle = "For terminals, remote computers and game streaming. In these apps, TitanKeys does not fix words, add capital letters or full stops, or show suggestions. Each key goes to the app as you push it.";

export const exactAppsLabel = ({ exactApps }: State) => {
  const count = exactApps.filter(app => app.installed !== false).length;
  return count === 1 ? "1 app on this phone" : `${count} apps on this phone`;
};

export default function ExactApps() {
  const result = useSnapshot(state);
  const change = useAction(setExactApp);
  const save = useAction(update);
  if (result.status !== "ready") return <Screen title={title} subtitle={subtitle} />;
  const { exactApps, exactTouchpad } = result.data;
  return <Screen title={title} subtitle={subtitle}>
    <Section title="Touchpad">
      <Toggle label="Touchpad moves the cursor" subtitle="Lean on the space bar to send the arrow keys. Off, the touchpad sends nothing to these apps."
        value={exactTouchpad} onChange={exactTouchpad => save.run({ exactTouchpad })} />
    </Section>
    <Section title="Apps">
      <Row title="Add app" endIcon={add} href="/settings/exact-apps-add" />
      {exactApps.map(({ app, name, installed }) => <Row key={app} title={name} titleMaxLines={2}
        subtitle={installed === false ? "Not on this phone" : undefined}
        endAction={{ icon: close, onPress: () => change.run(app, false) }} />)}
    </Section>
  </Screen>;
}
