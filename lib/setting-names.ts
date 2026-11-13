/**
 * Settings that show in more than one place, by the one name each has everywhere: Settings, the keyboard's quick settings
 * (android/kotlin/com/atlas/keyboard/QuickSetting.kt), the fun guide (GuideSteps.kt) and the boring guide.
 * tools/checks/setting-names.test.ts keeps them the same.
 */

/** Turns the whole bar on or off: words, the Shift, Alt and Ctrl chips and the clip offer. */
export const barLabel = "Show the bar";

/**
 * The phone's Scroll assistant for apps: with it on, the keys scroll apps when no text field is active. Inside a text field
 * TitanKeys's own touchpad features take over either way. It has no note under it (owner, 2026-11-12), and until it's
 * changed it follows the phone's own Scroll assistant (TypingSettings.touchpadOnlyInFields).
 */
export const scrollLabel = "Scroll assistant";

/** Space taps: a double tap moves the cursor, a triple tap selects words. */
export const cursorLabel = "Space taps move and select";

/**
 * The note under Orange accents. It changes every orange TitanKeys draws: the keyboard, the bar, the guide, the front page and
 * these pages (owner, 2026-11-13: "literally everything globally"). Turned off, it roasts you (the Orchestrator's line, the
 * owner may change it); turned back on, it says what the switch does.
 */
export const accentsNote = (on: boolean) => on
  ? "Orange marks everywhere TitanKeys draws: the keys, the bar, the guide and these pages. Off, they match the text."
  : "Orange is off everywhere. Boring, just like you.";
