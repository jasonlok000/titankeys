import { resource } from "ink";
import { callNative, onNativeMessage } from "ink/native";

export type Spelling = "british" | "american" | "canadian";
/** Settings > The bar > Bar text size: how much bigger than normal the bar's words, menu and chips are (FixedScale.Size). */
export type BarTextSize = "small" | "medium" | "large";
/** How many spaces go in after a full stop, a question mark or an exclamation mark. */
export type SentenceSpaces = "one" | "two" | "none";
export type EnterKey = "send" | "newline" | "app";
/** How a message goes out in an app: the field's action, or a plain Enter key. */
export type SendMethod = "action" | "key";
export type EnterApp = { app: string; name: string; method: SendMethod };
/** An app by its package. [installed] is false for an app on a list that is not on this phone. */
export type App = { app: string; name: string; installed?: boolean };
/** A snippet's shortcut is written without its start character. */
export type Snippet = { shortcut: string; text: string };
/** Chat shorthand in lowercase and the full word it's spelled out as. */
export type Shorthand = { shorthand: string; full: string };
/** The starts that Settings offers for a snippet's shortcut, the default first: symbols that Alt types. TypingSettings has the same. */
export const snippetStarts = ["/", "#", "!", "?", "*"] as const;
/** Starts from before 2.1, kept by someone who picked one. */
export const oldSnippetStarts = [";", "\\", ","] as const;
export type SnippetStart = typeof snippetStarts[number] | typeof oldSnippetStarts[number];

export type State = {
  version: string;
  /** TitanKeys was updated and its release notes haven't been closed yet. */
  whatsNew: boolean;
  /** The interactive guide has not opened yet, so it opens by itself after setup. */
  guideDue: boolean;
  phone: string;
  android: string;
  enabled: boolean;
  selected: boolean;
  /** Allowed to display over other apps, which keeps the touchpad with TitanKeys above text selection popups. */
  overlay: boolean;
  /** The Titan's Scroll assistant switch is on, which powers the keyboard touchpad. */
  scrollAssistant: boolean;
  /** TitanKeys' accessibility service is on, so one back swipe closes the bar and goes back in any app. */
  backService: boolean;
  /** "No thanks" on Accessibility's disclosure: setup goes on without it, as the guide's does (PhoneSetup.missing). */
  backDeclined: boolean;
  /**
   * Setup has a part still to do, as the playground decides it (PhoneSetup.toDo): Keyboard on, chosen, over other apps, Scroll
   * assistant, and Accessibility or its "Not now". The guide's own setup also asks for Sym and fn and contact names, which never
   * count here: Settings > Setup offers them.
   */
  setupToDo: boolean;
  /** The phone's ro.agui.touchpad_function, "yes" on a Titan with the touchpad, else empty. See lib/front.ts. */
  touchpadFunction: string;
  /** Build.DEVICE, "Titan_2" on the Titan 2 Elite. */
  device: string;
  /** TitanKeys Debug only: act as another phone, so the other-phone warning can be checked on the Elite. Always false elsewhere. */
  fakeOtherPhone: boolean;
  /** The other-phone warning has shown once. */
  phoneWarningSeen: boolean;
  /** The last time the overlay step opened, Android showed its whole list instead of TitanKeys' own page. */
  overlayOnList: boolean;
  /** The same for the Accessibility step. */
  backOnList: boolean;
  /** The phone's Sym key is set to Programmable key, so it opens the Sym panel. Read only (PhoneChecks.kt). */
  symKey: boolean;
  /** The phone's fn key is set to Programmable key, so it can work as Ctrl. */
  fnKey: boolean;
  /** The apps the keyboard saw outside Sliding Mode 1 in two fields, where swiping doesn't work, by name (SlidingModeList). */
  outsideSlidingMode: string[];
  learning: boolean;
  /** The letters and space a light touch types, such as `"AI "`. */
  tapKeys: string;
  /** Holding a letter types the symbol printed on its key. */
  holdSymbols: boolean;
  holdSymbolMillis: number;
  /** The keyboard treats fn as Ctrl, so Key Mapper is not needed for this. */
  fnAsCtrl: boolean;
  /** Alt + Backspace deletes forward, like Delete on a PC. Off, Android's own Alt + Backspace deletes the line. */
  altBackspaceForward: boolean;
  /** A held Shift + Backspace deletes a word. Off, it's a plain Backspace. */
  shiftBackspaceWord: boolean;
  /** A held Alt + Sym switches to the next keyboard. Off, Sym does what it would without it. */
  altSymNextKeyboard: boolean;
  /** Two quick pushes lock Shift or Alt, so Key Mapper is not needed to stop that. */
  doublePressLocks: boolean;
  autoCapitalise: boolean;
  /** Two quick spaces type a full stop, and typed punctuation moves in front of a space. */
  autoPunctuation: boolean;
  /** Spaces that go in after a typed . ? or ! when the next letter comes. */
  sentenceSpaces: SentenceSpaces;
  /** A space goes in after a typed comma, semicolon or colon when the next letter comes. */
  commaSpace: boolean;
  /** Fixes slips in typed words when they end. Swiped words never change. */
  autocorrect: boolean;
  /** Each kind of fix's own switch, by its id in FixKind.kt. All are on at first. A write may send only the ones that changed. */
  fixToggles: Record<string, boolean>;
  /** A fix goes in only when it's clearly right: fewer fixes, and fewer wrong ones. Off at first. */
  fixOnlyWhenSure: boolean;
  suggestionBar: boolean;
  /** A home screen's search field has no suggestion bar until you type in it. */
  launcherHidesBar: boolean;
  /** Resting a finger on Space starts cursor mode: lean towards either end to move the cursor. */
  cursorControl: boolean;
  /** A swipe on the touchpad types a word, and a flick left from Backspace deletes one. */
  swipeTyping: boolean;
  /** The touchpad overlay stays up when no field is active, so the touchpad does not scroll apps. Settings shows this reversed, as Scroll assistant. */
  touchpadOnlyInFields: boolean;
  /** A few small marks on the keyboard are orange, such as the key under a finger. Off, they are white or black. */
  orangeAccents: boolean;
  /** The bar's text size: your pick, or Normal ("medium"). */
  barTextSize: BarTextSize;
  /** Swipes and typing know contacts' given and family names. True only while the setting is on and Android allows it. */
  contactNames: boolean;
  /** The setting is on, but Android's Contacts permission was taken away since, so no names are read (lib/system-needs.tsx). */
  contactNamesBlocked: boolean;
  /** Which build this is: "play" for Google Play, which has no typing log, "github" or "debug". */
  build: "play" | "github" | "debug";
  /** What Enter does in fields with more than one line. */
  enterKey: EnterKey;
  /** Apps that the user added to the Enter key setting, and built-in apps that the user changed. */
  enterApps: EnterApp[];
  enterAppsBuiltIn: EnterApp[];
  /** Apps with a recent multi-line field, which the user can add. */
  recentEnterApps: { app: string; name: string }[];
  /** Apps with exact typing: each key goes to the app as it is, with no fixes or suggestions. */
  exactApps: App[];
  /** Apps where the bar is not drawn. The keyboard stays active there, one pixel high. */
  hiddenBarApps: App[];
  /** In exact typing apps (experimental), the physical Alt sends Alt, not symbols. */
  exactRealAlt: boolean;
  /** Apps where a field started recently, newest first, which the user can add to a list. */
  recentApps: App[];
  /** The character that starts a snippet's shortcut. */
  snippetStart: SnippetStart;
  /** The letter key that types each symbol with Alt, read from the phone's own keyboard, such as "/" to "G". */
  altKeys: Record<string, string>;
  /** A name between two colons, such as :smile:, types its emoji. */
  shortcodes: boolean;
  /** Chat shorthand such as "shd" becomes its full word, "should". */
  expandShorthand: boolean;
  /** The listed shorthand that has a full word, in the list's order, and whether the user left it on. */
  shorthand: (Shorthand & { on: boolean })[];
  /** The user's own shorthand, sorted. It comes before a listed one with the same shorthand. */
  ownShorthand: Shorthand[];
  snippets: Snippet[];
  liftOff: boolean;
  liftOffMillis: number;
  /** All haptics: off, nothing vibrates. */
  haptics: boolean;
  /** Each haptic's own switch, by its id in Haptics.kt. A write may send only the ones that changed. */
  hapticToggles: Record<string, boolean>;
  /** TitanKeys Debug's Haptics lab, and null in every other build. */
  hapticsLab: HapticsLab | null;
  spelling: Spelling;
  /** The input methods that are on, by id, in the switch overlay's order. English alone by default (docs/languages.md). */
  inputMethods: string[];
  /** Every input method the app has, English first. */
  availableInputMethods: InputMethodEntry[];
  /** The Chinese variant for every Chinese input method, or "" until the user picks one: there's no default. */
  chineseVariant: ChineseVariant | "";
  /** Records touches, keys and typed text in a log on this phone. */
  /** This build has the full typing log: only TitanKeys Debug does. */
  rawLogAvailable: boolean;
  rawLog: boolean;
  rawLogBytes: number;
  words: { word: string; description: string }[];
  blockedWords: string[];
  /** Each other input method's own words, saved or learned (LearnedWords): only those that have any. */
  languageWords: { inputMethod: string; name: string; nameEnglish: string; words: string[] }[];
};

/**
 * One input method as Settings lists it: its own name, its English name, the overlay's label, its engine, if any, and its
 * language (Serbian's Cyrillic and Latin share one, as do Chinese's Yale, Jyutping and Pinyin).
 */
export type InputMethodEntry = {
  id: string; name: string; nameEnglish: string; label: string; script: string; engine: string | null; language: Language;
  /** A few lines on what's special to typing it, for its language's page (tools/im/howto.json); empty where it has none. */
  howTo: string[];
};

/** A language, as Settings > Languages lists it. Every one but English and Chinese is marked experimental (owner, 2026-11-16). */
export type Language = { id: string; name: string; nameEnglish: string; label: string; script: string; experimental: boolean };

export type ChineseVariant = "zh_hk" | "zh_tw" | "zh_hans";

/**
 * Each pattern family's pattern in use, on and off in ms starting with on, by its id in Haptics.kt, and whether it is one tried
 * in the lab. Each haptic's family and what it plays, by its id.
 */
export type HapticsLab = {
  families: Record<string, { name: string; pattern: number[]; plays: string; tried: boolean }>;
  haptics: Record<string, { family: string; pattern: number[]; plays: string }>;
  /**
   * The lab's experiments, on or off, by id: dotsSpeedUp, "Dots speed up when long", deleteZoneTicks, "Tick at delete zones",
   * and exactKeysTexture, "Exact-typing keys feel like a key (texture)".
   */
  experiments: Record<string, boolean>;
  /** How long a single flick's dots sit on the bar before they go into the caret, in ms, and its default. */
  flickHoldMillis: number;
  flickHoldDefault: number;
  /** What marks the moving end of a triple press's selection, by id, and the marks it can try. */
  selectionEnd: string;
  selectionEnds: { id: string; label: string }[];
};

export type Settings = Pick<State, "learning" | "contactNames" | "tapKeys" | "holdSymbols" | "holdSymbolMillis" | "fnAsCtrl" | "altBackspaceForward" | "shiftBackspaceWord" | "altSymNextKeyboard" | "doublePressLocks" | "autoCapitalise" | "autoPunctuation" | "sentenceSpaces" | "commaSpace" | "autocorrect" | "fixToggles" | "fixOnlyWhenSure" | "suggestionBar" | "launcherHidesBar" | "cursorControl" | "swipeTyping" | "touchpadOnlyInFields" | "orangeAccents" | "barTextSize" | "enterKey" | "snippetStart" | "shortcodes" | "exactRealAlt" | "expandShorthand" | "liftOff" | "liftOffMillis" | "haptics" | "hapticToggles" | "spelling" | "rawLog" | "inputMethods" | "chineseVariant"> & {
  /** Switches listed shorthand on or off, by its shorthand. A write sends only the ones that changed. */
  shorthandToggles: Record<string, boolean>;
};

/**
 * Touchpad points from 0 to 1 across the keyboard and down its four rows, with how thick to draw the trail there: 1 for a
 * touch of the usual size.
 */
export type Point = [x: number, y: number, thickness?: number];

export type TraceEvent = { event: "touch" | "lift"; points: Point[] };

const titankeys = (operation: string, payload: object = {}) => callNative("titankeys", operation, payload);

export const state = resource({
  key: () => ["titankeys"],
  load: async (): Promise<State> => JSON.parse(await titankeys("state")),
})();

// The swipe engine loads in the background; refresh once it is ready or has failed.
onNativeMessage("titankeys-state", () => void state.refresh());

export async function update(changes: Partial<Settings>) {
  for (const [key, value] of Object.entries(changes)) await titankeys("set", { key, value });
  await state.refresh();
}

export async function setEnterApp(app: string, method: SendMethod) {
  await titankeys("setEnterApp", { app, method });
  await state.refresh();
}

export async function removeEnterApp(app: string) {
  await titankeys("removeEnterApp", { app });
  await state.refresh();
}

/** The apps in the phone's launcher, by name, to add to the exact typing list. */
export const installedApps = resource({
  key: () => ["installedApps"],
  load: async (): Promise<App[]> => JSON.parse(await titankeys("installedApps")),
})();

/** The lists of apps in Settings: exact typing, and the apps where the bar is hidden. */
export type AppList = "exact" | "hiddenBar";

export async function setListedApp(list: AppList, app: string, on: boolean) {
  await titankeys("setListedApp", { list, app, on });
  await state.refresh();
}

/** Adds a snippet, or changes the one that had the shortcut [previous]. Fails with a message if the shortcut or text is not valid. */
export async function putSnippet(shortcut: string, text: string, previous?: string) {
  try {
    await titankeys("putSnippet", { shortcut, text, previous: previous ?? "" });
  } finally {
    await state.refresh();
  }
}

/** Adds the user's own shorthand, or changes the one that was [previous]. Fails with a message if it isn't valid. */
export async function putShorthand(shorthand: string, full: string, previous?: string) {
  try {
    await titankeys("putShorthand", { shorthand, full, previous: previous ?? "" });
  } finally {
    await state.refresh();
  }
}

export async function removeShorthand(shorthand: string) {
  await titankeys("removeShorthand", { shorthand });
  await state.refresh();
}

export async function removeSnippet(shortcut: string) {
  await titankeys("removeSnippet", { shortcut });
  await state.refresh();
}

export async function addWord(word: string) {
  try {
    await titankeys("addWord", { word });
  } finally {
    await state.refresh();
  }
}

/** Another input method's own word comes off its saved and learned words (Settings > Your words > Saved words). */
export async function removeLanguageWord(inputMethod: string, word: string) {
  try {
    await titankeys("removeLanguageWord", { inputMethod, word });
  } finally {
    await state.refresh();
  }
}

export async function removeWord(word: string) {
  try {
    await titankeys("removeWord", { word });
  } finally {
    await state.refresh();
  }
}

export async function blockWord(word: string) {
  try {
    await titankeys("blockWord", { word });
  } finally {
    await state.refresh();
  }
}

export async function unblockWord(word: string) {
  try {
    await titankeys("unblockWord", { word });
  } finally {
    await state.refresh();
  }
}

export async function clearWords() {
  try {
    await titankeys("clearWords");
  } finally {
    await state.refresh();
  }
}

/**
 * Settings > Your words > Back up everything and Restore (#65). Opens the file picker. Returns what was done: "backup", or for an
 * old Export words or Export snippets file, "words" or "snippets"; "" if the picker was cancelled. A whole backup's restore
 * restarts TitanKeys on its result page, so it never returns.
 */
export async function backup(mode: "backup" | "restore"): Promise<"backup" | "words" | "snippets" | ""> {
  try {
    return JSON.parse(await callNative("titankeys", "backup", { mode }, { timeoutMs: 600_000 }));
  } finally {
    if (mode === "restore") await state.refresh();
  }
}

/** Our address for feedback (#74), which the report form offers when it can't send (#97). Every public contact address is this one. */
export const FEEDBACK_ADDRESS = "titankeys@everyones-niche.com";

export const copyText = (label: string, text: string) => titankeys("copyText", { label, text });

/** Crashes TitanKeys on purpose, to test the crash offer. TitanKeys Debug only. */
export const forceCrash = () => titankeys("crash");

/**
 * Opens the lab's screen: a practice field, the haptics the keyboard really plays as you type, and an editor and notes for each
 * family. It shows the haptics with these names, groups and words on where each plays. TitanKeys Debug only.
 */
export const showHapticsGraph = (haptics: { id: string; label: string; group: string; about: string }[]) =>
  titankeys("labGraph", { haptics });

/** Opens Key calibration, which lines the drawn keys up with the real keys below the screen. TitanKeys Debug only. */
export const showKeyCalibration = () => titankeys("keyCalibration");

/** Turns one of the Haptics lab's experiments on or off, by its id. TitanKeys Debug only. */
export async function setLabExperiment(id: string, on: boolean) {
  await titankeys("labExperiment", { id, on });
  await state.refresh();
}

/** Tries the mark at a triple press's moving end, by id, or puts the handle back with null. TitanKeys Debug only. */
export async function setLabSelectionEnd(id: string | null) {
  await titankeys("labSelectionEnd", { id });
  await state.refresh();
}

/** Tries how long a single flick's dots sit on the bar, in ms, or puts the default back with null. TitanKeys Debug only. */
export async function setLabFlickHold(millis: number | null) {
  await titankeys("labFlickHold", { millis });
  await state.refresh();
}

/** Plays a flick's dots as they play now, so the wait can be felt without flicking. TitanKeys Debug only. */
export const playLabFlick = () => titankeys("labPlayFlick");

/** Opens the file picker. Returns false if the picker was cancelled. */
export async function exportRawLog() {
  return await callNative("titankeys", "exportRawLog", {}, { timeoutMs: 600_000 }) === "true";
}

export async function deleteRawLog() {
  try {
    await titankeys("deleteRawLog");
  } finally {
    await state.refresh();
  }
}

/** Hides the release notes until the next update. */
export async function markNotesSeen() {
  try {
    await titankeys("notesSeen");
  } finally {
    await state.refresh();
  }
}


/** Opens the interactive guide. It starts where you stopped, or from the beginning if you finished it. */
export const openGuide = () => titankeys("guide");

/**
 * Settings > Reset most things: every switch, stepper and choice goes back to how it started (SettingsReset.kt). Words,
 * snippets, shorthand, app lists and the rest of what's yours stay. Settings shows it at once.
 */
export async function resetSettings() {
  await titankeys("resetSettings");
  await state.refresh();
}

/**
 * Settings > Reset literally everything: settings and everything that's yours, as if freshly installed (FreshStart.kt).
 * TitanKeys restarts on its front page, so nothing here runs after it.
 */
export const wipeEverything = () => titankeys("wipeEverything");
/**
 * Opens the playground, and tells where its buttons asked to go: "setup" when it closed from its setup slide, and "closed"
 * when something else closed it, such as a link that opened the app's own screen over it.
 */
export type PlaygroundExit = "guide" | "settings" | "setup" | "closed" | "back";

/**
 * Opens the playground, and tells `closed` how it closed. One the guide's end opened (`fromEnd`) closes with "back" on Back, to go
 * back to the end, where the front page's leaves the app.
 */
export function openPlayground(closed: (action: PlaygroundExit) => void, fromEnd = false) {
  // The answer comes as a message, because a native request held open while you play would time out.
  const stop = onNativeMessage("titankeys-playground", message => {
    stop();
    closed((message as { action: PlaygroundExit }).action);
  });
  void titankeys(fromEnd ? "playgroundFromEnd" : "playground");
  return stop;
}

/** Opens the guide at its end screen, as it was left: Back from a page the end opened (app/end/index.tsx). */
export const openGuideEnd = () => titankeys("guideEnd");

/** The front page went away: a playground that waits for the guide to close is not wanted any more. */
export const cancelPlayground = () => void titankeys("playgroundCancel");
export const enableKeyboard = () => titankeys("enable");
export const chooseKeyboard = () => titankeys("choose");
export const allowOverlay = () => titankeys("overlay");
export const openScrollAssistant = () => titankeys("scrollAssistant");
export const allowBack = () => titankeys("backService");

/** The other-phone warning's button: it never shows again. */
export async function markPhoneWarningSeen() {
  await titankeys("phoneWarningSeen");
  await state.refresh();
}

/** TitanKeys Debug only: fake another phone, or stop. Turning it on shows the warning once more. */
export async function fakeOtherPhone(on: boolean) {
  await titankeys("fakeOtherPhone", { on });
  await state.refresh();
}
/** Opens the phone's Shortcut keys page, for the Sym and fn keys. */
export const openShortcutKeys = () => titankeys("shortcutKeys");

/** Calls `front` whenever the app's own window is in front, as when the playground over it has closed. */
export const onAppWindow = (front: () => void) => onNativeMessage("titankeys-window", front);

/** Calls `focus` whenever the home window regains focus, so its field can take autofocus again. */
export const onHomeFocus = (focus: () => void) => onNativeMessage("titankeys-focus", focus);

export const tapKeyLabel = (key: string) => key === " " ? "Space" : key;

/** Waits for letter keys or Space until the returned function is called. */
export function captureKey(pressed: (key: string) => void) {
  const stop = onNativeMessage("titankeys-key", message => pressed((message as { key: string }).key));
  void titankeys("captureKey");
  return () => {
    stop();
    void titankeys("releaseKey");
  };
}

const traceListeners = new Set<(event: TraceEvent) => void>();
let trace: TraceEvent = { event: "lift", points: [] };
let stopMessages: (() => void) | undefined;

/** Plays native swipe feedback on the current screen until the returned function is called. */
export function previewTouchpad() {
  void titankeys("preview");
  return () => {
    void titankeys("unpreview");
  };
}

/** Follows the keyboard's touchpad activity until the returned function is called. */
export function watchTrace(listener: (event: TraceEvent) => void) {
  traceListeners.add(listener);
  if (!stopMessages) {
    stopMessages = onNativeMessage("titankeys-trace", message => {
      const event = message as TraceEvent & { reset: boolean };
      trace = { event: event.event, points: event.reset ? event.points : trace.points.concat(event.points) };
      for (const receive of traceListeners) receive(trace);
    });
    void titankeys("watch");
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
      void titankeys("unwatch");
    }
  };
}
