import { type ComponentProps } from "react";
import { Toggle as InkToggle } from "ink";
import { callNative } from "ink/native";

/**
 * The haptics of TitanKeys' own screens (docs/haptics.md, The app's own controls), by their ids in Haptics.kt. A press on the
 * touchscreen has no click of its own, so a control that changes something says what changed. Navigation and plain buttons
 * stay quiet. Each plays through the keyboard's switches, so All haptics and its own switch in Settings > Haptics apply.
 */
export type AppHaptic = "app_switch_on" | "app_switch_off" | "app_step" | "app_picked" | "app_deleted" | "app_refused";

/** Plays [id]. A haptic that can't play is no reason to hold up what the control does, so a failure is ignored. */
export function playHaptic(id: AppHaptic) {
  callNative("atlas", "haptic", { id }).catch(() => undefined);
}

/** A switch turned on locks in; turned off, it lets go. */
export const switched = (on: boolean) => playHaptic(on ? "app_switch_on" : "app_switch_off");

/** Runs [work], and plays no if the app refuses it, such as a shortcut it can't take. The error still reaches the page. */
export async function refusedOnError<T>(work: () => Promise<T>): Promise<T> {
  try {
    return await work();
  } catch (error) {
    playHaptic("app_refused");
    throw error;
  }
}

/** Ink's switch row, with Lock as it turns on and Release as it turns off. */
export function Toggle(props: ComponentProps<typeof InkToggle>) {
  return <InkToggle {...props} onChange={value => {
    switched(value);
    props.onChange(value);
  }} />;
}
