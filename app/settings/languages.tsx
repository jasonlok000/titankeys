import { Screen, Section, useAction, useSnapshot } from "ink";
import { type InputMethodEntry, type State, state, update } from "../../lib/titankeys";
import { Toggle } from "../../lib/app-haptics";
import { Note } from "../../lib/note";

/** The most input methods on at once: the switch overlay's places, W to O (InputMethod.MAX_ON). */
const MAX_ON = 8;

/** Settings' value for Input methods: the names of the ones that are on, in order. */
export const inputMethodsLabel = ({ inputMethods, availableInputMethods }: State) =>
  inputMethods.map(id => availableInputMethods.find(im => im.id === id)?.name ?? id).join(", ");

// Each script's header (tools/checks/im.test.ts: every input method's script has one). F: draft for Georgian and Korean.
const SCRIPTS: Record<string, string> = {
  Latn: "Latin letters", Cyrl: "Cyrillic", Grek: "Greek", Geor: "Georgian", Kore: "Korean", Hani: "Chinese",
};

/**
 * Every input method, with a switch. The ones that are on come first, in the overlay's order, which is the order they were
 * turned on. Holding Sym switches between them. English can't go off while it's the only one, and eight is the most.
 */
export default function LanguagesPage() {
  const result = useSnapshot(state);
  const save = useAction(update);
  // F (docs/words-m1.md).
  const title = "Input methods";
  const subtitle = "Turn on the ones you type in. Hold Sym to hop between them.";
  if (result.status !== "ready") return <Screen title={title} subtitle={subtitle} />;
  const { inputMethods, availableInputMethods } = result.data;
  const byId = new Map(availableInputMethods.map(im => [im.id, im]));
  const on = inputMethods.map(id => byId.get(id)).filter((im): im is InputMethodEntry => im !== undefined);
  const off = availableInputMethods.filter(im => !inputMethods.includes(im.id));
  const full = inputMethods.length >= MAX_ON;
  const set = (id: string, value: boolean) => {
    const next = value ? [...inputMethods, id] : inputMethods.filter(other => other !== id);
    if (next.length === 0 || next.length > MAX_ON) return;
    void save.run({ inputMethods: next });
  };
  const row = (im: InputMethodEntry, value: boolean) =>
    <Toggle key={im.id} label={im.name} subtitle={im.name === im.nameEnglish ? undefined : im.nameEnglish} value={value}
      onChange={next => set(im.id, next)} />;
  const groups = new Map<string, InputMethodEntry[]>();
  for (const im of off) groups.set(im.script, [...(groups.get(im.script) ?? []), im]);
  return <Screen title={title} subtitle={subtitle}>
    <Section title="On">
      {on.map(im => row(im, true))}
      {on.length === 1 && <Note>Just one, so Sym has nothing to switch to. Turn on another.</Note>}
    </Section>
    {full && <Note>Eight at a time, tops. Turn one off first.</Note>}
    {[...groups].map(([script, ims]) =>
      <Section key={script} title={SCRIPTS[script] ?? script}>{ims.map(im => row(im, false))}</Section>)}
  </Screen>;
}
