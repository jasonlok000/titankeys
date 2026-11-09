import { Field, Screen, Section, Toggle, replace, useAction, useSnapshot } from "ink";
import { close } from "ink/icons";
import { state, update } from "../../lib/atlas";
import { enterKeyLabel } from "./enter-key";
import { holdLabel } from "./hold";
import { liftLabel } from "./lift";
import { rawLogLabel } from "./rawlog";
import { setupLabel } from "./setup";
import { spellingLabel } from "./spelling";
import { tapKeysLabel } from "./tap-keys";
import { vibrationLabel } from "./vibration";

export default function Settings() {
  const result = useSnapshot(state);
  const save = useAction(update);
  const closeAction = { icon: close, onPress: () => replace("/") };
  if (result.status !== "ready") return <Screen title="Settings" rightAction={closeAction} />;
  const settings = result.data;
  return <Screen title="Settings" rightAction={closeAction}>
    <Section title="Typing">
      <Field label="Spelling" href="/settings/spelling">{spellingLabel(settings)}</Field>
      <Field label="Tap keys" href="/settings/tap-keys">{tapKeysLabel(settings)}</Field>
      <Field label="Hold for symbols" href="/settings/hold">{holdLabel(settings)}</Field>
      <Field label="Enter key" href="/settings/enter-key">{enterKeyLabel(settings)}</Field>
      <Toggle label="Auto-capitalisation" value={settings.autoCapitalise}
        onChange={autoCapitalise => save.run({ autoCapitalise })} />
      <Toggle label="Auto-punctuation" value={settings.autoPunctuation}
        onChange={autoPunctuation => save.run({ autoPunctuation })} />
      <Toggle label="Autocorrect" value={settings.autocorrect}
        onChange={autocorrect => save.run({ autocorrect })} />
      <Toggle label="Suggestion bar" value={settings.suggestionBar}
        onChange={suggestionBar => save.run({ suggestionBar })} />
      <Toggle label="Cursor control" value={settings.cursorControl}
        onChange={cursorControl => save.run({ cursorControl })} />
      <Toggle label="Use fn as Ctrl" value={settings.fnAsCtrl}
        onChange={fnAsCtrl => save.run({ fnAsCtrl })} />
    </Section>
    <Section title="Swipe typing">
      <Field label="Slip recovery period" href="/settings/lift">{liftLabel(settings)}</Field>
      <Field label="Swipe vibration" href="/settings/vibration">{vibrationLabel(settings)}</Field>
    </Section>
    <Section title="Personal">
      <Field label="Your words" href="/settings/data" />
      <Field label="Raw input log" href="/settings/rawlog">{rawLogLabel(settings)}</Field>
    </Section>
    <Section title="Help">
      <Field label="Setup" href="/settings/setup">{setupLabel(settings)}</Field>
      <Field label="Guide" href="/settings/guide" />
      <Field label="Send feedback" href="/settings/feedback" />
      <Field label="About" href="/settings/about" />
    </Section>
  </Screen>;
}
