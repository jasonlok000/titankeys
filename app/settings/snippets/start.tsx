import { Canvas, CanvasIcon, CanvasText, Rectangle, Screen, Stack, useAction, useSnapshot } from "ink";
import { check } from "ink/icons";
import { type SnippetStart, snippetStarts, state, update } from "../../../lib/atlas";
import { CREAM, INK, useAccent } from "../../../lib/brand";
import { KeyCaps } from "../../../lib/keyboard";
import { playHaptic } from "../../../lib/app-haptics";

const title = "Start of each shortcut";
const WIDTH = 596;
const HEIGHT = 72;
const SYMBOL = 64;
const TICK = 34;

/**
 * The symbol that starts every snippet's shortcut. Each choice shows how to type it: Alt and the key that the phone's own keyboard
 * types it with. A start from before 2.1 stays on the list for whoever still has it.
 */
export default function SnippetStart() {
  const result = useSnapshot(state);
  const save = useAction(update);
  if (result.status !== "ready") return <Screen title={title} />;
  const { snippetStart, altKeys, holdSymbols } = result.data;
  const starts: SnippetStart[] = [...snippetStarts];
  if (!starts.includes(snippetStart)) starts.push(snippetStart);
  const hold = holdSymbols ? " Holding the letter types it too." : "";
  return <Screen title={title}
    subtitle={`Every shortcut starts with this, like ${snippetStart}addr. Pick one you never start a word with.${hold}`}>
    <Stack gap={24}>
      {starts.map(start => <Choice key={start} start={start} letter={altKeys[start]} selected={start === snippetStart}
        onPress={() => {
          playHaptic("app_picked");
          void save.run({ snippetStart: start });
        }} />)}
    </Stack>
  </Screen>;
}

function Choice({ start, letter, selected, onPress }: { start: string; letter?: string; selected: boolean; onPress: () => void }) {
  const accent = useAccent();
  return <Canvas width={WIDTH} height={HEIGHT}>
    <Rectangle x={0} y={0} width={WIDTH} height={HEIGHT} fill={INK} onPress={onPress} />
    <CanvasText x={0} y={0} width={SYMBOL} height={HEIGHT} size={40} colour={selected ? accent : CREAM} align="start">{start}</CanvasText>
    {KeyCaps({ keys: letter ? ["Alt", letter] : ["Sym"], x: SYMBOL, y: 0, height: HEIGHT, keyPrefix: `cap-${start}` })}
    {selected && <CanvasIcon x={WIDTH - TICK} y={(HEIGHT - TICK) / 2} size={TICK} name={check} colour={accent} />}
  </Canvas>;
}
