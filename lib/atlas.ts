import { resource } from "ink";
import { callNative, onNativeMessage } from "ink/native";

export type HapticPreset = "normal" | "strong";
export type Spelling = "british" | "american";

export type State = {
  version: string;
  phone: string;
  android: string;
  enabled: boolean;
  selected: boolean;
  learning: boolean;
  /** The profile in use: `"off"`, `"atlas"` or the id of a saved profile. */
  calibration: string;
  profiles: { id: string; name: string }[];
  /** The letters and space a light touch types, such as `"AI "`. */
  tapKeys: string;
  autoCapitalise: boolean;
  suggestionBar: boolean;
  liftOff: boolean;
  liftOffMillis: number;
  haptics: boolean;
  hapticPreset: HapticPreset;
  spelling: Spelling;
  words: { word: string; description: string }[];
};

export type Settings = Pick<State, "learning" | "calibration" | "tapKeys" | "autoCapitalise" | "suggestionBar" | "liftOff" | "liftOffMillis" | "haptics" | "hapticPreset" | "spelling">;

/** Touchpad points from 0 to 1 across the keyboard and down its four rows. */
export type Point = [x: number, y: number];

export type TraceEvent = { event: "touch" | "lift"; points: Point[] };

const atlas = (operation: string, payload: object = {}) => callNative("atlas", operation, payload);

export const state = resource({
  key: () => ["atlas"],
  load: async (): Promise<State> => JSON.parse(await atlas("state")),
})();

// The swipe engine loads in the background; refresh once it is ready or has failed.
onNativeMessage("atlas-state", () => void state.refresh());

export async function update(changes: Partial<Settings>) {
  for (const [key, value] of Object.entries(changes)) await atlas("set", { key, value });
  await state.refresh();
}

export async function removeWord(word: string) {
  try {
    await atlas("removeWord", { word });
  } finally {
    await state.refresh();
  }
}

export async function clearWords() {
  try {
    await atlas("clearWords");
  } finally {
    await state.refresh();
  }
}

export async function transferLearning(mode: "import" | "export") {
  try {
    const result = await callNative("atlas", "transferLearning", { mode }, { timeoutMs: 600_000 });
    return result === "true" ? mode : null;
  } finally {
    if (mode === "import") await state.refresh();
  }
}

export const enableKeyboard = () => atlas("enable");
export const chooseKeyboard = () => atlas("choose");

export const tapKeyLabel = (key: string) => key === " " ? "Space" : key;

/** Waits for letter keys or Space until the returned function is called. */
export function captureKey(pressed: (key: string) => void) {
  const stop = onNativeMessage("atlas-key", message => pressed((message as { key: string }).key));
  void atlas("captureKey");
  return () => {
    stop();
    void atlas("releaseKey");
  };
}

export type Calibration = {
  stage: "touch" | "check" | "passed" | "failed";
  /** The key to touch, counted across the keyboard's rows. */
  key: number;
  keyNumber: number;
  touch: number;
  /** The last touch didn't count. */
  retry: boolean;
  /** Whether the touches made a profile that can be checked again. */
  canRepeat: boolean;
  raw: Point | null;
  adjusted: Point | null;
};

export const beginCalibration = () => atlas("calibrationBegin");
export const repeatCheck = () => atlas("calibrationRepeatCheck");
export const restartCalibration = () => atlas("calibrationRestart");

/** Follows a calibration until the returned function is called. */
export function watchCalibration(listener: (calibration: Calibration) => void) {
  const stop = onNativeMessage("atlas-calibration", message => listener(message as Calibration));
  void atlas("calibrationListen").then(snapshot => listener(JSON.parse(snapshot)));
  return () => {
    stop();
    void atlas("calibrationUnlisten");
  };
}

export async function saveProfile(name: string) {
  await atlas("calibrationSave", { name });
  await state.refresh();
}

export async function deleteProfile(id: string) {
  await atlas("deleteProfile", { id });
  await state.refresh();
}

const traceListeners = new Set<(event: TraceEvent) => void>();
let trace: TraceEvent = { event: "lift", points: [] };
let stopMessages: (() => void) | undefined;

/** Plays native swipe feedback on the current screen until the returned function is called. */
export function previewTouchpad() {
  void atlas("preview");
  return () => {
    void atlas("unpreview");
  };
}

/** Follows the keyboard's touchpad activity until the returned function is called. */
export function watchTrace(listener: (event: TraceEvent) => void) {
  traceListeners.add(listener);
  if (!stopMessages) {
    stopMessages = onNativeMessage("atlas-trace", message => {
      const event = message as TraceEvent & { reset: boolean };
      trace = { event: event.event, points: event.reset ? event.points : trace.points.concat(event.points) };
      for (const receive of traceListeners) receive(trace);
    });
    void atlas("watch");
  }
  listener(trace);
  let stopped = false;
  return () => {
    if (stopped) return;
    stopped = true;
    traceListeners.delete(listener);
    if (traceListeners.size === 0) {
      stopMessages?.();
      stopMessages = undefined;
      trace = { event: "lift", points: [] };
      void atlas("unwatch");
    }
  };
}
