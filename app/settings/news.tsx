import { Field, Row, Screen, Section, Stack, navigate, openURL, useAction, useSnapshot } from "ink";
import { Toggle } from "../../lib/app-haptics";
import { arrowOutward, chevronRight } from "ink/icons";
import { allowNotifications, connectedState, dismissEntry, type NewsEntry, setAnnouncements } from "../../lib/connected";
import { newsText as text } from "../../lib/connected-text";
import { Note } from "../../lib/note";

export default function News() {
  const result = useSnapshot(connectedState);
  const save = useAction(setAnnouncements);
  if (result.status !== "ready") return <Screen title={text.title} subtitle={text.subtitle} />;
  const state = result.data;
  return <Screen title={text.title} subtitle={text.subtitle}>
    <Section title={text.title}>
      <Toggle label={text.toggle} subtitle={text.toggleNote} value={state.announcements} onChange={on => save.run(on)} />
      {state.announcements && !state.notificationsAllowed && <Stack gap={8}>
        <Field label={text.allow} onPress={() => void allowNotifications()}>{""}</Field>
        <Note>{text.allowReason}</Note>
      </Stack>}
    </Section>
    {state.entries.length === 0
      ? <Note>{text.empty}</Note>
      : state.entries.map(entry => <Entry key={entry.id} entry={entry} />)}
  </Screen>;
}

function Entry({ entry }: { entry: NewsEntry }) {
  const action = entry.action;
  return <Section title={entry.title}>
    <Note>{entry.text}</Note>
    {action && (action.url
      ? <Row title={action.label} endIcon={arrowOutward} onPress={() => void openURL(action.url)} />
      : <Row title={action.label} endIcon={chevronRight} onPress={() => navigate(action.page as never)} />)}
    {entry.dismissed
      ? <Field label={text.hidden}>{""}</Field>
      : <Field label={text.hide} onPress={() => void dismissEntry(entry.id)}>{""}</Field>}
  </Section>;
}
