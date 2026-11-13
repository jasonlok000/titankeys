import { Field, Screen, Section, SettingsChoices, useAction, useSnapshot } from "ink";
import { type Settings, type State, state, update } from "./atlas";
import { playHaptic } from "./app-haptics";

/** A setting that is off or a number of milliseconds, such as how long to hold a letter for its symbol. */
export type Duration = {
  /** The page's title and subtitle. */
  title: string;
  subtitle: string;
  /** The range the keyboard accepts, and the step of Shorter and Longer. */
  min: number;
  max: number;
  step: number;
  /** A few common choices, with Off first. */
  presets: readonly { label: string; millis: number }[];
  read: (state: State) => number | null;
  write: (millis: number | null) => Partial<Settings>;
};

export const ms = (millis: number) => `${millis} ms`;

/**
 * Off and a few presets to pick from, then Shorter and Longer to fine-tune in steps, in place of a long list of every
 * value. The time in use shows as the first row's value.
 */
export function DurationPage({ title, subtitle, min, max, step, presets, read, write }: Duration) {
  const result = useSnapshot(state);
  const save = useAction(update);
  if (result.status !== "ready") return <Screen title={title} subtitle={subtitle} />;
  const millis = read(result.data);
  const options = [{ value: "off", label: "Off" }, ...presets.map(preset => ({ value: String(preset.millis), label: `${preset.label}, ${ms(preset.millis)}` }))];
  // A time between the presets ticks none of them; the first row still shows it.
  // Each step is a tick, as each one is a small move.
  const set = (next: number) => {
    playHaptic("app_step");
    return save.run(write(Math.max(min, Math.min(max, next))));
  };
  return <Screen title={title} subtitle={subtitle}>
    <Section title="Presets">
      <SettingsChoices value={millis === null ? "off" : String(millis)} options={options}
        onChange={value => {
          playHaptic("app_picked");
          void save.run(write(value === "off" ? null : Number(value)));
        }} />
    </Section>
    <Section title="Fine-tune">
      <Field label="Now">{millis === null ? "Off" : ms(millis)}</Field>
      <Field label="Shorter" disabled={millis === null || millis <= min} onPress={() => millis !== null && void set(millis - step)}>{`− ${ms(step)}`}</Field>
      <Field label="Longer" disabled={millis === null || millis >= max} onPress={() => millis !== null && void set(millis + step)}>{`+ ${ms(step)}`}</Field>
    </Section>
  </Screen>;
}
