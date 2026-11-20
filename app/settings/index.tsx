import { useEffect } from "react";
import { Field, Screen, Section, navigate, replace, setRouteParents, useAction, useSnapshot } from "ink";
import { Toggle } from "../../lib/app-haptics";
import { close } from "ink/icons";
import { showKeyCalibration, state, update } from "../../lib/titankeys";
import { connectedState } from "../../lib/connected";
import { bugText } from "../../lib/bug-text";
import { contributeText, newsText } from "../../lib/connected-text";
import { barTextSizeLabel, barTextSizeTitle } from "./bar-text-size";
import { accentsNote, altBackspaceLabel, altBackspaceNote, altSymLabel, altSymNote, barLabel, cursorLabel, scrollLabel, shiftBackspaceLabel,
  shiftBackspaceNote } from "../../lib/setting-names";
import { parentOf } from "../../lib/settings-tree";
import { contributeLabel } from "./contribute";
import { exactAppsLabel } from "./exact-apps";
import { hapticsLabel } from "./haptics";
import { hiddenBarLabel, hiddenBarTitle } from "./hidden-bar";
import { liftLabel } from "./lift";
import { rawLogLabel } from "./rawlog";
import { setupLabel } from "./setup";
import { snippetsLabel } from "./snippets/index";
import { languagesLabel } from "./languages";
import { tapKeysLabel } from "./tap-keys";
import { RESET_NOTE, resetDone } from "./reset-most";
import { Note } from "../../lib/note";
import { SupportCard } from "../../lib/support-art";
import { openSupport } from "../../lib/support";
import { Needs, needs, useRecheck } from "../../lib/system-needs";

// Links into Settings land with their parent pages under them, so ← and Back go up and never to the front page.
setRouteParents(parentOf);

/** The top of Settings: × goes to the front page, and there is no ←. */
export default function Settings() {
  const result = useSnapshot(state);
  const connected = useSnapshot(connectedState);
  const save = useAction(update);
  const closeAction = { icon: close, onPress: () => replace("/") };
  // The note after a reset stays until you leave Settings.
  useEffect(() => () => { resetDone.shown = false; }, []);
  // A setting turned on in Android's own screens shows at once when Settings comes back (lib/system-needs.tsx).
  useRecheck(state);
  if (result.status !== "ready") return <Screen title="Settings" rightAction={closeAction} />;
  const settings = result.data;
  return <Screen title="Settings" rightAction={closeAction}>
    {/* First in Settings (owner, 2026-11-16: "Languages shd be first in settings"). F (docs/words-m1.md). */}
    <Field label="Languages" href="/settings/languages">{languagesLabel(settings)}</Field>
    <Field label="Typing" href="/settings/typing" />
    {/* The same topics, in the same order, as the boring guide's Shortcuts and Symbols sections. */}
    <Section title="Shortcuts">
      <Field label="Snippets" href="/settings/snippets">{snippetsLabel(settings)}</Field>
      <Toggle label="Type :name: for an emoji" value={settings.shortcodes}
        onChange={shortcodes => save.run({ shortcodes })} />
      <Field label="Exact typing (experimental)" href="/settings/exact-apps">{exactAppsLabel(settings)}</Field>
      {/* Built in, each with its switch (owner, 2026-11-13). They count only while the first key is held. */}
      <Toggle label={shiftBackspaceLabel} subtitle={shiftBackspaceNote} value={settings.shiftBackspaceWord}
        onChange={shiftBackspaceWord => save.run({ shiftBackspaceWord })} />
      <Toggle label={altSymLabel} subtitle={altSymNote} value={settings.altSymNextKeyboard}
        onChange={altSymNextKeyboard => save.run({ altSymNextKeyboard })} />
      <Needs list={needs.altSym(settings)} />
    </Section>
    {/* The bar is words, the Shift, Alt and Ctrl chips and the clip offer. Each setting here hides all of it, so each
        says "the bar", and the bar is turned off in this one place only. */}
    <Section title="The bar">
      <Toggle label={barLabel} value={settings.suggestionBar}
        onChange={suggestionBar => save.run({ suggestionBar })} />
      {settings.suggestionBar && <Toggle label="Hide the bar on an empty home search" value={settings.launcherHidesBar}
        onChange={launcherHidesBar => save.run({ launcherHidesBar })} />}
      {settings.suggestionBar && <Field label={hiddenBarTitle} href="/settings/hidden-bar">{hiddenBarLabel(settings)}</Field>}
      {settings.suggestionBar && <Field label={barTextSizeTitle} href="/settings/bar-text-size">{barTextSizeLabel(settings)}</Field>}
    </Section>
    <Section title="Touchpad">
      {/* What stops the touchpad reaching TitanKeys at all (lib/system-needs.tsx). */}
      <Needs list={needs.touchpad(settings)} />

      <Toggle label={cursorLabel} value={settings.cursorControl}
        onChange={cursorControl => save.run({ cursorControl })} />
      {(settings.swipeTyping || settings.cursorControl)
        && <Field label="Keep swiping after a short lift" href="/settings/lift">{liftLabel(settings)}</Field>}
      <Field label="Type with a light touch" href="/settings/tap-keys">{tapKeysLabel(settings)}</Field>
      <Toggle label={scrollLabel} value={!settings.touchpadOnlyInFields}
        onChange={scroll => save.run({ touchpadOnlyInFields: !scroll })} />
      {/* Only Accessibility here: the overlay shows above, and the keyboard itself under Setup. */}
      <Needs list={needs.scrollBlock(settings).filter(need => need.id === "backService")} />
      {/* Key calibration is only in TitanKeys Debug, as the Haptics lab is, so it shows with the Haptics lab. */}
      {settings.hapticsLab && <Field label="Key calibration" onPress={() => void showKeyCalibration()} />}
    </Section>
    <Section title="Keys">
      <Toggle label="Press twice to lock Shift and Alt" value={settings.doublePressLocks}
        onChange={doublePressLocks => save.run({ doublePressLocks })} />
      <Toggle label="Use fn as Ctrl" value={settings.fnAsCtrl}
        onChange={fnAsCtrl => save.run({ fnAsCtrl })} />
      <Needs list={needs.fnAsCtrl(settings)} />
      <Toggle label={altBackspaceLabel} subtitle={altBackspaceNote} value={settings.altBackspaceForward}
        onChange={altBackspaceForward => save.run({ altBackspaceForward })} />
    </Section>
    <Section title="Look and feel">
      <Field label="Haptics" href="/settings/haptics">{hapticsLabel(settings)}</Field>
      {/* The Haptics lab is only in TitanKeys Debug. Other builds get null for it, so the row never shows there. */}
      {settings.hapticsLab && <Field label="Haptics lab" href="/settings/haptics-lab" />}
      <Toggle label="Orange accents" subtitle={accentsNote(settings.orangeAccents)} value={settings.orangeAccents}
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
      <Needs list={needs.keyboard(settings)} />
      <Field label="Guide" href="/settings/guide" />
      <Field label={newsText.title} href="/settings/news" />
      <Field label="Send feedback" href="/settings/feedback" />
      <Field label={bugText.settingsLabel} href="/settings/bug" />
      <Field label="About" href="/settings/about" />
    </Section>
    {/* So many settings, it's easy to mess them up (owner, 2026-11-13: "Two resets. Reset most things and reset literally
        everything"). Plain rows: each asks first, on its own page. */}
    <Section title="Start over">
      <Field label="Reset most things" href="/settings/reset-most" />
      <Field label="Reset literally everything" href="/settings/reset-everything" />
      {resetDone.shown && <Note>{RESET_NOTE}</Note>}
    </Section>
    {/* At the very bottom of Settings (owner, 2026-11-19), opening the support page, the bundled HTML page. F's words. */}
    <SupportCard title="Support TitanKeys" lines={["Buy me a coffee?"]}
      onPress={() => void openSupport("page")} />
  </Screen>;
}
