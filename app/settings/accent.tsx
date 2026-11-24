import { Button, Canvas, Field, Rectangle, Screen, Section, Stack, Text, useAction, useSnapshot } from "ink";
import { playHaptic } from "../../lib/app-haptics";
import { state, update } from "../../lib/titankeys";
import { CREAM } from "../../lib/brand";
import { RoundedFill } from "../../lib/rounded";
import { hueColour } from "../../lib/accent-hue";
import { accentChoices, accentsNote, accentTitle, customTitle, hueOf } from "../../lib/setting-names";

/** Custom's first hue, before one is picked: a sky blue. */
const FIRST_HUE = 210;
/** Ink has no slider: the hue bar is tapped or dragged along in steps of this many degrees, and the arrows move one step. */
const STEP = 15;
const BAR_WIDTH = 576;
const BAR_HEIGHT = 56;
const SEGMENTS = 360 / STEP;
const DOT = 36;

/** A filled round dot of [colour], the swatch beside each choice. */
function Swatch({ colour, size = DOT }: { colour: string; size?: number }) {
  return <Canvas width={size} height={size}>
    <RoundedFill x={0} y={0} width={size} height={size} radius={size / 2} fill={colour} />
  </Canvas>;
}

/**
 * Settings > Accent colour (#111): Off, Orange (the default), the checked presets, the phone's own colour, or Custom, any hue
 * fitted into the readable band. Each choice shows its own colour. A pick takes effect at once, here and in every window as it
 * next draws (AppNativeAdapters' accentColour).
 */
export default function AccentPage() {
  const result = useSnapshot(state);
  const save = useAction(update);
  if (result.status !== "ready") return <Screen title={accentTitle} subtitle={accentsNote("orange")} />;
  const { accentColour: id, accent, accentSwatches: swatches = {} } = result.data;
  const hue = hueOf(id);
  const customHue = hue ?? FIRST_HUE;
  const pick = (accentColour: string) => {
    playHaptic("app_picked");
    void save.run({ accentColour });
  };
  const setHue = (degrees: number) => {
    const next = ((Math.round(degrees) % 360) + 360) % 360;
    if (next === hue) return;
    playHaptic("app_step");
    void save.run({ accentColour: `hue:${next}` });
  };
  const choice = (value: string, label: string, colour: string) =>
    <Stack key={value} axis="horizontal" align="center" gap={18}>
      <Swatch colour={colour} />
      <Button selected={value === id || (value === "custom" && hue !== null)}
        onPress={() => pick(value === "custom" ? `hue:${customHue}` : value)}>{label}</Button>
    </Stack>;
  return <Screen title={accentTitle} subtitle={accentsNote(id)}>
    <Stack gap={47}>
      {accentChoices.map(({ value, label }) => choice(value, label, swatches[value] ?? (value === "off" ? CREAM : accent)))}
      {choice("custom", customTitle, hueColour(customHue))}
    </Stack>
    <Section title={customTitle}>
      <Stack axis="horizontal" align="center" gap={18}>
        <Swatch colour={hueColour(customHue)} size={56} />
        <Text size={28}>{`${customHue}°`}</Text>
      </Stack>
      <Canvas width={BAR_WIDTH} height={BAR_HEIGHT}>
        {Array.from({ length: SEGMENTS }, (_, index) => {
          const degrees = index * STEP;
          const width = BAR_WIDTH / SEGMENTS;
          const marked = hue !== null && Math.round(hue / STEP) % SEGMENTS === index;
          return <Rectangle key={degrees} x={index * width} y={marked ? 0 : 10} width={width} height={marked ? BAR_HEIGHT : BAR_HEIGHT - 20}
            fill={hueColour(degrees)} onPress={() => setHue(degrees)} onDragEnter={() => setHue(degrees)} />;
        })}
      </Canvas>
      <Field label="Hue left" disabled={hue === null} onPress={() => setHue(customHue - STEP)}>{`− ${STEP}°`}</Field>
      <Field label="Hue right" disabled={hue === null} onPress={() => setHue(customHue + STEP)}>{`+ ${STEP}°`}</Field>
    </Section>
  </Screen>;
}
