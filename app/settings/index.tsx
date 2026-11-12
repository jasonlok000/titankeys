import { Field, Screen, Section, Toggle, replace, useAction, useSnapshot } from "ink";
import { close } from "ink/icons";
import { showKeyCalibration, state, update } from "../../lib/atlas";
import { connectedState } from "../../lib/connected";
import { bugText } from "../../lib/bug-text";
import { contributeText, newsText } from "../../lib/connected-text";
import { contributeLabel } from "./contribute";
import { exactAppsLabel } from "./exact-apps";
import { hapticsLabel } from "./haptics";
import { hiddenBarLabel } from "./hidden-bar";
import { holdLabel } from "./hold";
import { liftLabel } from "./lift";
import { rawLogLabel } from "./rawlog";
import { sentenceSpacesLabel } from "./sentence-spaces";
import { setupLabel } from "./setup";
import { snippetsLabel } from "./snippets/index";
import { spellingLabel } from "./spelling";
import { tapKeysLabel } from "./tap-keys";

export default function Settings() {
  const result = useSnapshot(state);
  const connected = useSnapshot(connectedState);
  const save = useAction(update);
  const closeAction = { icon: close, onPress: () => replace("/") };
  if (result.status !== "ready") return <Screen title="Settings" rightAction={closeAction} />;
  const settings = result.data;
  return <Screen title="Settings" rightAction={closeAction}>
    <Section title="Typing">
      <Toggle label="Fix typos as you type" value={settings.autocorrect}
        onChange={autocorrect => save.run({ autocorrect })} />
      <Toggle label="Capital letter to start a sentence" value={settings.autoCapitalise}
        onChange={autoCapitalise => save.run({ autoCapitalise })} />
      <Field label="Space after a sentence" href="/settings/sentence-spaces">{sentenceSpacesLabel(settings)}</Field>
      <Toggle label="Space after , ; :" value={settings.commaSpace}
        onChange={commaSpace => save.run({ commaSpace })} />
      <Toggle label="Double Space types a full stop" value={settings.autoPunctuation}
        onChange={autoPunctuation => save.run({ autoPunctuation })} />
      <Field label="English spelling" href="/settings/spelling">{spellingLabel(settings)}</Field>
      <Field label="Hold a letter for its symbol" href="/settings/hold">{holdLabel(settings)}</Field>
      <Field label="Type with a light touch" href="/settings/tap-keys">{tapKeysLabel(settings)}</Field>
      <Field label="Snippets" href="/settings/snippets">{snippetsLabel(settings)}</Field>
      <Toggle label="Type :name: for an emoji" value={settings.shortcodes}
        onChange={shortcodes => save.run({ shortcodes })} />
      <Toggle label="Spell out shorthand, like shd as should" value={settings.expandShorthand}
        onChange={expandShorthand => save.run({ expandShorthand })} />
      <Field label="Exact typing (experimental)" href="/settings/exact-apps">{exactAppsLabel(settings)}</Field>
    </Section>
    <Section title="Suggestions">
      <Toggle label="Show word suggestions" value={settings.suggestionBar}
        onChange={suggestionBar => save.run({ suggestionBar })} />
      <Toggle label="Hide suggestions on an empty home search" value={settings.launcherHidesBar}
        onChange={launcherHidesBar => save.run({ launcherHidesBar })} />
      <Field label="Hide the bar in these apps" href="/settings/hidden-bar">{hiddenBarLabel(settings)}</Field>
    </Section>
    <Section title="Swiping">
      <Toggle label="Swipe to type words" value={settings.swipeTyping}
        onChange={swipeTyping => save.run({ swipeTyping })} />
      <Toggle label="Space taps move the cursor" value={settings.cursorControl}
        onChange={cursorControl => save.run({ cursorControl })} />
      {(settings.swipeTyping || settings.cursorControl)
        && <Field label="Keep swiping after a short lift" href="/settings/lift">{liftLabel(settings)}</Field>}
      <Field label="Haptics" href="/settings/haptics">{hapticsLabel(settings)}</Field>
      {/* The Haptics lab is only in TitanKeys Debug. Other builds get null for it, so the row never shows there. */}
      {settings.hapticsLab && <Field label="Haptics lab" href="/settings/haptics-lab" />}
      {/* Key calibration is debug only too, so it shows with the Haptics lab. */}
      {settings.hapticsLab && <Field label="Key calibration" onPress={() => void showKeyCalibration()} />}
      <Toggle label="Scroll assistant outside text fields" value={!settings.touchpadOnlyInFields}
        onChange={scroll => save.run({ touchpadOnlyInFields: !scroll })} />
    </Section>
    <Section title="Keys">
      <Toggle label="Press twice to lock Shift and Alt" value={settings.doublePressLocks}
        onChange={doublePressLocks => save.run({ doublePressLocks })} />
      <Toggle label="Use fn as Ctrl" value={settings.fnAsCtrl}
        onChange={fnAsCtrl => save.run({ fnAsCtrl })} />
    </Section>
    <Section title="Look">
      <Toggle label="Orange accents" value={settings.orangeAccents}
        onChange={orangeAccents => save.run({ orangeAccents })} />
    </Section>
    <Section title="Your data">
      <Field label="Your words" href="/settings/data" />
      {/* The full typing log is only in TitanKeys Debug; public releases have Record a bug instead. */}
      {settings.rawLogAvailable && <Field label="Typing log" href="/settings/rawlog">{rawLogLabel(settings)}</Field>}
      <Field label={contributeText.settingsLabel} href="/settings/contribute">
        {connected.status === "ready" ? contributeLabel(connected.data.contribute) : ""}</Field>
    </Section>
    <Section title="Help">
      <Field label="Setup" href="/settings/setup">{setupLabel(settings)}</Field>
      <Field label="Guide" href="/settings/guide" />
      <Field label={newsText.title} href="/settings/news" />
      <Field label="Send feedback" href="/settings/feedback" />
      <Field label={bugText.settingsLabel} href="/settings/bug" />
      <Field label="About" href="/settings/about" />
    </Section>
  </Screen>;
}
