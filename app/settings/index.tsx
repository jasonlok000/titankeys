import { Field, Screen, Section, Toggle, replace, useAction, useSnapshot } from "ink";
import { close } from "ink/icons";
import { state, update } from "../../lib/atlas";
import { liftLabel } from "./lift";
import { hyperKeyLabel } from "./hyper-key";
import { setupLabel } from "./setup";
import { shortcutsLabel } from "./shortcuts";
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
      <Toggle label="Auto-capitalisation" value={settings.autoCapitalise}
        onChange={autoCapitalise => save.run({ autoCapitalise })} />
      <Toggle label="Auto-punctuation" value={settings.autoPunctuation}
        onChange={autoPunctuation => save.run({ autoPunctuation })} />
      <Toggle label="Suggestion bar" value={settings.suggestionBar}
        onChange={suggestionBar => save.run({ suggestionBar })} />
      <Toggle label="Cursor control" value={settings.cursorControl}
        onChange={cursorControl => save.run({ cursorControl })} />
      <Toggle label="Backlight on touch" value={settings.touchLight}
        onChange={touchLight => save.run({ touchLight })} />
    </Section>
    <Section title="Swipe typing">
      <Toggle label="Swipe typing" value={settings.swipeTyping}
        onChange={swipeTyping => save.run({ swipeTyping })} />
      {settings.swipeTyping && <>
        <Field label="Tap keys" href="/settings/tap-keys">{tapKeysLabel(settings)}</Field>
        <Field label="Slip recovery period" href="/settings/lift">{liftLabel(settings)}</Field>
        <Field label="Swipe vibration" href="/settings/vibration">{vibrationLabel(settings)}</Field>
      </>}
    </Section>
    <Section title="Shortcuts">
      <Toggle label="App shortcuts" value={settings.appShortcuts}
        onChange={appShortcuts => save.run({ appShortcuts })} />
      {settings.appShortcuts && <>
        <Field label="Hyper key" href="/settings/hyper-key">{hyperKeyLabel(settings)}</Field>
        <Field label="Apps" href="/settings/shortcuts">{shortcutsLabel(settings)}</Field>
        <Toggle label="Animations" value={settings.shortcutAnimation}
          onChange={shortcutAnimation => save.run({ shortcutAnimation })} />
      </>}
    </Section>
    <Section title="Personal">
      <Field label="Your words" href="/settings/data" />
    </Section>
    <Section title="Help">
      <Field label="Setup" href="/settings/setup">{setupLabel(settings)}</Field>
      <Field label="Guide" href="/settings/guide" />
      <Field label="Send feedback" href="/settings/feedback" />
      <Field label="About" href="/settings/about" />
    </Section>
  </Screen>;
}
