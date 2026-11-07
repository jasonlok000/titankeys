import { Screen, type SettingsChoice, SettingsChoices, useAction, useSnapshot } from "ink";
import { type Settings, type State, state, update } from "./atlas";

/** Saves a setting when an option is selected. */
export function ChoicePage<Value extends string>({ title, subtitle, options, read, write }: {
  title: string;
  subtitle?: string;
  options: readonly SettingsChoice<Value>[];
  read: (state: State) => Value;
  write: (value: Value) => Partial<Settings>;
}) {
  const result = useSnapshot(state);
  const save = useAction(update);
  return <Screen title={title} subtitle={subtitle}>
    {result.status === "ready" && <SettingsChoices value={read(result.data)} options={options}
      onChange={value => save.run(write(value))} />}
  </Screen>;
}
