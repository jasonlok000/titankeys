import { resource } from "ink";
import { callNative, onNativeMessage } from "ink/native";

export type HapticPreset = "normal" | "strong";
export type Spelling = "british" | "american";
export type EnterKey = "send" | "newline" | "app";
/** How a message goes out in an app: the field's action, or a plain Enter key. */
export type SendMethod = "action" | "key";
export type EnterApp = { app: string; name: string; method: SendMethod };

export type State = {
  version: string;
  /** Atlas was updated and its release notes haven't been closed yet. */
  whatsNew: boolean;
  phone: string;
  android: string;
  enabled: boolean;
  selected: boolean;
  /** Allowed to display over other apps, which keeps the touchpad with Atlas above text selection popups. */
  overlay: boolean;
  /** The Titan's Scroll assistant switch is on, which powers the keyboard touchpad. */
  scrollAssistant: boolean;
  /** Atlas's accessibility service is on, so one back swipe closes the bar and goes back in any app. */
  backService: boolean;
  learning: boolean;
  /** The letters and space a light touch types, such as `"AI "`. */
  tapKeys: string;
  /** Holding a letter types the symbol printed on its key. */
  holdSymbols: boolean;
  holdSymbolMillis: number;
  /** The keyboard treats fn as Ctrl, so Key Mapper is not needed for this. */
  fnAsCtrl: boolean;
  /** Two quick pushes lock Shift or Alt, so Key Mapper is not needed to stop that. */
  doublePressLocks: boolean;
  autoCapitalise: boolean;
  /** Two quick spaces type a full stop, and typed punctuation moves in front of a space. */
  autoPunctuation: boolean;
  /** Fixes slips in typed words when they end. Swiped words never change. */
  autocorrect: boolean;
  suggestionBar: boolean;
  /** A home screen's search field has no suggestion bar until you type in it. */
  launcherHidesBar: boolean;
  /** Resting a finger on Space starts cursor mode: lean towards either end to move the cursor. */
  cursorControl: boolean;
  /** A swipe on the touchpad types a word, and a flick left from Backspace deletes one. */
  swipeTyping: boolean;
  /** The touchpad overlay stays up when no field is active, so the touchpad does not scroll apps. Only the phone can prove this. */
  touchpadOnlyInFields: boolean;
  /** What Enter does in fields with more than one line. */
  enterKey: EnterKey;
  /** Apps that the user added to the Enter key setting, and built-in apps that the user changed. */
  enterApps: EnterApp[];
  enterAppsBuiltIn: EnterApp[];
  /** Apps with a recent multi-line field, which the user can add. */
  recentEnterApps: { app: string; name: string }[];
  liftOff: boolean;
  liftOffMillis: number;
  haptics: boolean;
  hapticPreset: HapticPreset;
  spelling: Spelling;
  /** Records touches, keys and typed text in a log on this phone. */
  rawLog: boolean;
  rawLogBytes: number;
  words: { word: string; description: string }[];
  blockedWords: string[];
};

export type Settings = Pick<State, "learning" | "tapKeys" | "holdSymbols" | "holdSymbolMillis" | "fnAsCtrl" | "doublePressLocks" | "autoCapitalise" | "autoPunctuation" | "autocorrect" | "suggestionBar" | "launcherHidesBar" | "cursorControl" | "swipeTyping" | "touchpadOnlyInFields" | "enterKey" | "liftOff" | "liftOffMillis" | "haptics" | "hapticPreset" | "spelling" | "rawLog">;

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

export async function setEnterApp(app: string, method: SendMethod) {
  await atlas("setEnterApp", { app, method });
  await state.refresh();
}

export async function removeEnterApp(app: string) {
  await atlas("removeEnterApp", { app });
  await state.refresh();
}

export async function addWord(word: string) {
  try {
    await atlas("addWord", { word });
  } finally {
    await state.refresh();
  }
}

export async function removeWord(word: string) {
  try {
    await atlas("removeWord", { word });
  } finally {
    await state.refresh();
  }
}

export async function blockWord(word: string) {
  try {
    await atlas("blockWord", { word });
  } finally {
    await state.refresh();
  }
}

export async function unblockWord(word: string) {
  try {
    await atlas("unblockWord", { word });
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

/** Opens the file picker. Returns false if the picker was cancelled. */
export async function exportRawLog() {
  return await callNative("atlas", "exportRawLog", {}, { timeoutMs: 600_000 }) === "true";
}

export async function deleteRawLog() {
  try {
    await atlas("deleteRawLog");
  } finally {
    await state.refresh();
  }
}

/** Hides the release notes until the next update. */
export async function markNotesSeen() {
  try {
    await atlas("notesSeen");
  } finally {
    await state.refresh();
  }
}

export const enableKeyboard = () => atlas("enable");
export const chooseKeyboard = () => atlas("choose");
export const allowOverlay = () => atlas("overlay");
export const openScrollAssistant = () => atlas("scrollAssistant");
export const allowBack = () => atlas("backService");

/** Calls `focus` whenever the home window regains focus, so its field can take autofocus again. */
export const onHomeFocus = (focus: () => void) => onNativeMessage("atlas-focus", focus);

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
