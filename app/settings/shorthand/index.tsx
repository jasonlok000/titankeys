import { Row, Screen, Section, Toggle, navigate, useAction, useSnapshot } from "ink";
import { add, close } from "ink/icons";
import { type State, removeShorthand, state, update } from "../../../lib/atlas";

const title = "Shorthand";
const subtitle = "Type shd and press Space, and you get should. Backspace right after puts shd back. Off, it all stays as typed and autocorrect leaves it alone.";

export const shorthandLabel = ({ expandShorthand }: State) => expandShorthand ? "On" : "Off";

export default function Shorthand() {
  const result = useSnapshot(state);
  const save = useAction(update);
  const remove = useAction(removeShorthand);
  if (result.status !== "ready") return <Screen title={title} subtitle={subtitle} />;
  const { expandShorthand, shorthand, ownShorthand } = result.data;
  return <Screen title={title} subtitle={subtitle}>
    <Toggle label="Spell out shorthand" value={expandShorthand} onChange={on => save.run({ expandShorthand: on })} />
    <Section title="Yours">
      <Row title="Add shorthand" endIcon={add} href="/settings/shorthand/edit" />
      {ownShorthand.map(({ shorthand, full }) => <Row key={shorthand} title={shorthand} subtitle={full}
        onPress={() => navigate({ path: "/settings/shorthand/edit", params: { shorthand } })}
        endAction={{ icon: close, onPress: () => remove.run(shorthand) }} />)}
    </Section>
    <Section title="Built in">
      {shorthand.map(({ shorthand, full, on }) => <Toggle key={shorthand} label={shorthand} subtitle={full} value={on}
        disabled={!expandShorthand} onChange={on => save.run({ shorthandToggles: { [shorthand]: on } })} />)}
    </Section>
  </Screen>;
}
