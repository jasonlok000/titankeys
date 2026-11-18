import { Field, Screen, Section, SettingsChoices, useAction, useSnapshot } from "ink";
import { type Settings, type State, state, update } from "./titankeys";
import { translator } from "./language-copy";
import { playHaptic } from "./app-haptics";

/** A setting that is off or a number of milliseconds, such as how long to hold a letter for its symbol. */
export type Duration = {
  /** The page's title and subtitle. */
  language?: string;
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

export const ms = (millis: number, language?: string) => `${millis} ${translator(language)("ms")}`;

/**
 * Off and a few presets to pick from, then Shorter and Longer to fine-tune in steps, in place of a long list of every
 * value. The time in use shows as the first row's value.
 */
export function DurationPage({ language, title, subtitle, min, max, step, presets, read, write }: Duration) {
  const t = translator(language);
  const result = useSnapshot(state);
  const save = useAction(update);
  if (result.status !== "ready") return <Screen title={t(title)} subtitle={t(subtitle)} />;
  const millis = read(result.data);
  const options = [{ value: "off", label: t("Off") }, ...presets.map(preset => ({ value: String(preset.millis), label: `${t(preset.label)}, ${ms(preset.millis, language)}` }))];
  // A time between the presets ticks none of them; the first row still shows it.
  // Each step is a tick, as each one is a small move.
  const set = (next: number) => {
    playHaptic("app_step");
    return save.run(write(Math.max(min, Math.min(max, next))));
  };
  return <Screen title={t(title)} subtitle={t(subtitle)}>
    <Section title={t("Presets")}>
      <SettingsChoices value={millis === null ? "off" : String(millis)} options={options}
        onChange={value => {
          playHaptic("app_picked");
          void save.run(write(value === "off" ? null : Number(value)));
        }} />
    </Section>
    <Section title={t("Fine-tune")}>
      <Field label={t("Now")}>{millis === null ? t("Off") : ms(millis, language)}</Field>
      <Field label={t("Shorter")} disabled={millis === null || millis <= min} onPress={() => millis !== null && void set(millis - step)}>{`− ${ms(step, language)}`}</Field>
      <Field label={t("Longer")} disabled={millis === null || millis >= max} onPress={() => millis !== null && void set(millis + step)}>{`+ ${ms(step, language)}`}</Field>
    </Section>
  </Screen>;
}
