import { Canvas, CanvasText, Rectangle, Screen, Section, Stack, Text, navigate, useSnapshot } from "ink";
import { type State, state } from "../../../lib/titankeys";
import { CREAM, DIVIDER, INK, useAccent } from "../../../lib/brand";
import { RADIUS } from "../../../lib/radii";
import { RoundedFill, RoundedOutline } from "../../../lib/rounded";
import { Note } from "../../../lib/note";
import { languagesOf, languagesOn, listMethodCount, stageOf, type LanguageRow } from "../../../lib/language-page";
import pageText from "../../../tools/im/page-text.json";

/**
 * The most languages on, and the most input methods on within one (owner, 2026-11-17: "Don't allow selecting more than 4"),
 * as the keyboard's InputMethod.MAX_LANGUAGES and MAX_PER_LANGUAGE. No other total. A setting with more keeps them.
 */
export const MAX_LANGUAGES = 4;
export const MAX_PER_LANGUAGE = 4;

/**
 * Scripts whose letters Ink can't place yet: it draws text without shaping, so Devanagari's vowel signs would sit after
 * their letter (हिन्दी). Their names show in English until it can (docs/languages.md, "Hindi").
 */
const UNSHAPED = new Set(["Deva"]);

/** The name Settings shows for a language or an input method: its own, or English's where Ink can't draw its script. */
export const shownName = (name: string, nameEnglish: string, script: string) => UNSHAPED.has(script) ? nameEnglish : name;

/** Settings' value for Languages: the names of the ones that are on, in order. */
export const languagesLabel = (settings: State) =>
  languagesOn(settings).map(({ language }) => shownName(language.name, language.nameEnglish, language.script)).join(", ");

const { list, status } = pageText;

function LanguageItem({ row, accent, onPress }: { row: LanguageRow; accent: string; onPress: () => void }) {
  const { language, methods, on } = row;
  const title = shownName(language.name, language.nameEnglish, language.script);
  const subtitle = [title === language.nameEnglish ? undefined : language.nameEnglish, listMethodCount(methods.length, on.length)]
    .filter(Boolean).join(" · ");
  const stage = stageOf(language);
  const ready = stage === "Polished";
  const value = on.length ? list.on : status[stage];
  const width = { Polished: 110, Tested: 98, Experimental: 146 }[stage];
  const pill = { x: 1, y: 1, width: width - 2, height: 32, radius: RADIUS.small };
  return <Stack align="stretch" gap={10} onPress={onPress}
    accessibilityLabel={title} accessibilityValue={[subtitle, value].filter(Boolean).join(", ")}>
    <Stack axis="horizontal" align="center" justify="space-between" gap={14}>
      <Stack>
        <Stack axis="horizontal" align="center" gap={14}>
          <Canvas width={59} height={51}>
            <RoundedOutline x={1} y={1} width={57} height={49} radius={RADIUS.small} stroke={DIVIDER} strokeWidth={2} />
            <CanvasText x={1} y={1} width={57} height={49} size={25} colour={CREAM} align="center">{language.label}</CanvasText>
          </Canvas>
          <Stack gap={2}>
            <Text size={30}>{title}</Text>
            {subtitle && <Note>{subtitle}</Note>}
          </Stack>
        </Stack>
      </Stack>
      {on.length > 0 ? <Text size={26} tone="accent">{value}</Text> : <Canvas width={width} height={34}>
        {ready ? <RoundedFill {...pill} fill={accent} /> : <RoundedOutline {...pill} stroke={accent} strokeWidth={1.5} />}
        <CanvasText x={pill.x} y={pill.y} width={pill.width} height={pill.height} size={21}
          colour={ready ? INK : accent} align="center">{value}</CanvasText>
      </Canvas>}
    </Stack>
    <Canvas width={596} height={2}><Rectangle x={0} y={0} width={596} height={2} fill={DIVIDER} /></Canvas>
  </Stack>;
}

/**
 * Every language. The ones that are on come first, in the switcher's order. Each opens its own page, where its input methods
 * are turned on and its own options are (the Gboard way: owner, 2026-11-16).
 */
export default function LanguagesPage() {
  const result = useSnapshot(state);
  const accent = useAccent();
  const title = list.title;
  if (result.status !== "ready") return <Screen title={title} />;
  const rows = languagesOf(result.data);
  const on = languagesOn(result.data);
  const row = (each: LanguageRow) => <LanguageItem key={each.language.id} row={each} accent={accent}
    onPress={() => navigate({ path: "/settings/languages/language", params: { language: each.language.id } })} />;
  const limit = list.limit.replace("{max}", String(MAX_LANGUAGES));
  return <Screen title={title}>
    <Section title={list.on}>
      {on.map(row)}
      <Note accessible>{limit}</Note>
      {result.data.inputMethods.length === 1 && <Note accessible>{list.onlyOne}</Note>}
    </Section>
    <Section title={list.all}>{rows.filter(each => each.on.length === 0).map(row)}</Section>
  </Screen>;
}
