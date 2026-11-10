import { useEffect } from "react";
import { Canvas, CanvasIcon, CanvasText, Rectangle, Screen, Stack, Text, replace, useSnapshot } from "ink";
import { arrowOutward } from "ink/icons";
import { cancelPlayground, markNotesSeen, onAppWindow, openGuide, openPlayground, state } from "../lib/atlas";
import { INK, ORANGE } from "../lib/brand";
import { hasReleaseNotes } from "../lib/release-notes";
import { WhatsNew } from "../lib/whats-new";

const WIDTH = 596;
const REOPEN_DELAY_MILLIS = 600;

function Action({ label, onPress }: { label: string; onPress: () => void }) {
  const height = 108;
  const icon = 30;
  return <Canvas width={WIDTH} height={height}>
    <Rectangle x={0} y={0} width={WIDTH} height={height} fill={ORANGE} onPress={onPress} />
    <CanvasText x={0} y={0} width={WIDTH} height={height} size={32} colour={INK} align="center">{label}</CanvasText>
    <CanvasIcon x={WIDTH - icon - 32} y={(height - icon) / 2} size={icon} name={arrowOutward} colour={INK} />
  </Canvas>;
}

/**
 * Setup is the first slide of the interactive guide: it opens each Settings page and comes back by itself when the switch is on.
 * The first time, the guide opens by itself. After that, this page waits for a touch, so that closing the guide is a way out.
 */
function SetupStart({ first }: { first: boolean }) {
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
  useEffect(() => void openGuide(), []);
  return <Screen />;
}

/**
 * The front page is the playground, a native screen over this one: the guide's look with no steps, to type and swipe freely.
 * Its Guide and Settings buttons come back here to open those pages.
 */
function FrontPage() {
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
        if (action === "settings" || action === "guide") leaving = true;
        if (action === "settings") replace("/settings");
        else if (action === "guide") replace({ path: "/settings/guide", params: { from: "home" } });
        // The playground closed from its setup slide, so this page shows setup again.
        else if (action === "setup") void state.refresh();
        // Something else closed it, perhaps while this window was already in front. A link into the app closes it too, so
        // this waits a moment for such a link to move to its own page first.
        else setTimeout(() => { if (here && !leaving) open(); }, REOPEN_DELAY_MILLIS);
      });
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
  if (result.status !== "ready") return <Screen />;
  const { enabled, selected, overlay, scrollAssistant, backService, version, whatsNew, guideDue } = result.data;
  if (!enabled || !selected || !overlay || !scrollAssistant || !backService) return <SetupStart first={guideDue} />;
  if (guideDue) return <GuideStart />;
  if (whatsNew && hasReleaseNotes(version)) return <WhatsNew version={version} onClose={() => void markNotesSeen()} />;
  return <FrontPage />;
}
