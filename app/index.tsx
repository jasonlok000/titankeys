import { useEffect } from "react";
import { Canvas, CanvasIcon, CanvasText, Screen, Stack, Text, back, navigate, replace, useSnapshot } from "ink";
import { arrowOutward } from "ink/icons";
import { cancelPlayground, markNotesSeen, markPhoneWarningSeen, onAppWindow, openGuide, openPlayground, state } from "../lib/titankeys";
import { INK, useAccent } from "../lib/brand";
import { connectedState, declineContributions, setContribute } from "../lib/connected";
import { Disclosure } from "../lib/contribute";
import { frontChoice } from "../lib/front";
import { RADIUS } from "../lib/radii";
import { hasReleaseNotes } from "../lib/release-notes";
import { RoundedFill } from "../lib/rounded";
import { WhatsNew } from "../lib/whats-new";

const WIDTH = 596;
const REOPEN_DELAY_MILLIS = 600;

function Action({ label, onPress }: { label: string; onPress: () => void }) {
  const accent = useAccent();
  const height = 108;
  const icon = 30;
  return <Canvas width={WIDTH} height={height}>
    <RoundedFill x={0} y={0} width={WIDTH} height={height} radius={RADIUS.large} fill={accent} onPress={onPress} />
    <CanvasText x={0} y={0} width={WIDTH} height={height} size={32} colour={INK} align="center">{label}</CanvasText>
    <CanvasIcon x={WIDTH - icon - 32} y={(height - icon) / 2} size={icon} name={arrowOutward} colour={INK} />
  </Canvas>;
}

/**
 * Reads the state again whenever the app's own window is in front, as after the guide closed, and once as the page first shows,
 * so that a refresh that came before it listened is never lost (#71). Without it, a page that opened the guide kept the reading
 * from before, and leaving the guide any way landed on setup again (#70).
 */
export function useFreshState() {
  useEffect(() => {
    void state.refresh();
    return onAppWindow(() => void state.refresh());
  }, []);
}

/**
 * Setup is the first slide of the interactive guide: it opens each Settings page and comes back by itself when the switch is on.
 * The first time, the guide opens by itself. After that, this page waits for a touch, so that closing the guide is a way out.
 */
function SetupStart({ first }: { first: boolean }) {
  useFreshState();
  useEffect(() => {
    if (first) void openGuide();
  }, []);
  return <Screen centered theme="onboarding">
    <Stack align="center" gap={44}>
      <Stack align="center" gap={14}>
        <Text size={60} align="center">Set up TitanKeys</Text>
        <Text size={30} align="center">A few switches in Settings. The guide takes you to each one and brings you back.</Text>
      </Stack>
      <Action label="Start" onPress={() => void openGuide()} />
    </Stack>
  </Screen>;
}

/** With setup done, the interactive guide opens once, by itself, over this ink screen. */
function GuideStart() {
  useFreshState();
  useEffect(() => void openGuide(), []);
  return <Screen />;
}

/**
 * Another phone than the Titan 2 Elite (#75): once, on first launch, before setup. It never blocks anything: its one button
 * carries on, and setup and the keyboard work as they can.
 */
function PhoneWarning() {
  return <Screen centered theme="onboarding">
    <Stack align="center" gap={44}>
      <Stack align="center" gap={14}>
        <Text size={60} align="center">This isn't a Titan 2 Elite</Text>
        <Text size={30} align="center">TitanKeys is made for its touch keys, so swiping, Space taps and flicks won't work here. Typing on the keys should. Carry on at your own risk.</Text>
      </Stack>
      <Action label="Carry on anyway" onPress={() => void markPhoneWarningSeen()} />
    </Stack>
  </Screen>;
}

/**
 * The front page is the playground, a native screen over this one: the guide's look with no steps, to type and swipe freely.
 * Its Guide and Settings buttons come back here to open those pages. The guide's end opens it as /end/play (`fromEnd`): its Back
 * then goes back, to the /end page under it, which opens the end again (owner, 2026-11-13).
 */
export function FrontPage({ fromEnd = false }: { fromEnd?: boolean }) {
  useEffect(() => {
    let here = true;
    // Set as a playground button moves to its page, so that the app's window coming to the front then opens nothing.
    let leaving = false;
    let stop = () => {};
    // The app opens a playground only when none is open, so this can ask each time its own window is in front again,
    // as after something closed the playground, such as a link that opened this screen over it.
    const open = () => {
      stop();
      stop = openPlayground(action => {
        if (!here) return;
        if (action === "settings" || action === "guide" || action === "support") leaving = true;
        if (action === "settings") replace("/settings");
        else if (action === "support") navigate("/support");
        // The guide is a page in Settings, so Ink opens it over the top of Settings (lib/settings-tree.ts): ← goes up there.
        else if (action === "guide") navigate("/settings/guide");
        // The playground closed from its setup slide, so this page shows setup again.
        else if (action === "setup") void state.refresh();
        // The end's playground went back: to the end again, by the /end page under this one.
        else if (action === "back") back();
        // Something else closed it, perhaps while this window was already in front. A link into the app closes it too, so
        // this waits a moment for such a link to move to its own page first.
        else setTimeout(() => { if (here && !leaving) open(); }, REOPEN_DELAY_MILLIS);
      }, fromEnd);
    };
    open();
    const stopWindow = onAppWindow(() => { if (here && !leaving) open(); });
    return () => {
      here = false;
      stop();
      stopWindow();
      cancelPlayground();
    };
  }, []);
  return <Screen />;
}

export default function Home() {
  const result = useSnapshot(state);
  const connected = useSnapshot(connectedState);
  if (result.status !== "ready") return <Screen />;
  const { version, whatsNew, guideDue } = result.data;
  const choice = frontChoice(result.data);
  if (choice === "warning") return <PhoneWarning />;
  if (choice === "setup") return <SetupStart first={guideDue} />;
  if (choice === "guide") return <GuideStart />;
  if (whatsNew && hasReleaseNotes(version)) return <WhatsNew version={version} onClose={() => void markNotesSeen()} />;
  // The guide offers data contributions once, at its end. The answer can change any time in Settings. The playground
  // waits for this answer, so that it does not open only to close again.
  if (connected.status === "loading") return <Screen />;
  if (connected.status === "ready" && connected.data.offerDue)
    return <Disclosure onAgree={() => void setContribute(true)} onDecline={() => void declineContributions()} />;
  return <FrontPage />;
}
