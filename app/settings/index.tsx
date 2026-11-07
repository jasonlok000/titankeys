import { Field, Screen, Section, Toggle, replace, useAction, useSnapshot } from "ink";
import { close } from "ink/icons";
import { chooseKeyboard, enableKeyboard, state, tapKeyLabel, update } from "../../lib/atlas";
import { liftLabel } from "./lift";
import { spellingLabel } from "./spelling";
import { vibrationLabel } from "./vibration";

export default function Settings() {
  const result = useSnapshot(state);
  const save = useAction(update);
  const closeAction = { icon: close, onPress: () => replace("/") };
  if (result.status !== "ready") return <Screen title="Settings" rightAction={closeAction} />;
  const settings = result.data;
  return <Screen title="Settings" rightAction={closeAction}>
    <Section title="Setup">
      <Field label="Enable Atlas" onPress={enableKeyboard}>{settings.enabled ? "Enabled" : "Not enabled"}</Field>
      <Field label="Choose Atlas" onPress={chooseKeyboard}>{settings.selected ? "Selected" : "Not selected"}</Field>
    </Section>
    <Section title="Typing">
      <Field label="Spelling" href="/settings/spelling">{spellingLabel(settings)}</Field>
      <Field label="Tap keys" href="/settings/tap-keys">{[...settings.tapKeys].map(tapKeyLabel).join(", ")}</Field>
      <Toggle label="Auto-capitalisation" value={settings.autoCapitalise}
        onChange={autoCapitalise => save.run({ autoCapitalise })} />
      <Toggle label="Suggestion bar" value={settings.suggestionBar}
        onChange={suggestionBar => save.run({ suggestionBar })} />
    </Section>
    <Section title="Swipe typing">
      <Field label="Slip recovery period" href="/settings/lift">{liftLabel(settings)}</Field>
      <Field label="Swipe vibration" href="/settings/vibration">{vibrationLabel(settings)}</Field>
    </Section>
    <Section title="Personal">
      <Field label="Your data" href="/settings/data" />
    </Section>
    <Section title="Help">
      <Field label="Guide" href="/settings/guide" />
      <Field label="Send feedback" href="/settings/feedback" />
      <Field label="About" href="/settings/about" />
    </Section>
  </Screen>;
}
