/**
 * Settings that show in more than one place, by the one name each has everywhere: Settings, the keyboard's quick settings
 * (android/kotlin/com/titankeys/keyboard/QuickSetting.kt), the fun guide (GuideSteps.kt) and the boring guide.
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

/** Space taps: a double tap moves the cursor, a triple tap selects words. The quick setting turns both (#120). */
export const cursorLabel = "Space taps move and select";

/** Settings' Gestures section: each gesture has its own switch (#120). */
export const doubleSpaceLabel = "Double Space moves the cursor";
export const tripleSpaceLabel = "Triple Space selects";
export const deleteFlickLabel = "Flick from Backspace deletes a word";
export const swipeTrailLabel = "Swipe trail on the bar";

/** The Sym panel stays open after a pick, until Sym or Back (#110). */
export const symPanelStaysLabel = "Keep Sym open after a pick";
export const symPanelStaysNote = "Close it with Sym or Back.";
export const largerEmojiLabel = "Larger emoji";

/** Alt + Backspace as Delete on a PC (owner, 2026-11-13: "alt+backspace = forward delete too"). On to start. */
export const altBackspaceLabel = "Alt + Backspace deletes forward";
export const altBackspaceNote = "Like Delete on a PC. Off, Android deletes the whole line instead.";

/** The two held shortcuts, each a switch, on to start (owner, 2026-11-13: "why is delete a word shift backspace and next keyboard alt sym not toggleable?"). */
export const shiftBackspaceLabel = "Hold Shift + Backspace to delete a word";
export const shiftBackspaceNote = "Off, it deletes one letter, as Backspace does.";
export const altSymLabel = "Hold Alt + Sym for the next keyboard";
export const altSymNote = "Off, Android's own keyboard switcher still works.";

/**
 * Settings > Accent colour (#111). It changes every accent TitanKeys draws: the keyboard, the bar, the guide, the front page,
 * the support page and these pages (owner, 2026-11-13: "literally everything globally"). The ids are Accent.kt's, which holds
 * the colours, each checked to stay readable (tools/checks/accent.test.ts). Off, it roasts you (the Orchestrator's line, the
 * owner may change it).
 */
export const accentTitle = "Accent colour";
export const accentChoices = [
  { value: "off", label: "Off" },
  { value: "orange", label: "Orange" },
  { value: "blue", label: "Blue" },
  { value: "teal", label: "Teal" },
  { value: "green", label: "Green" },
  { value: "purple", label: "Purple" },
  { value: "pink", label: "Pink" },
  { value: "gold", label: "Gold" },
  { value: "phone", label: "Phone colour" },
] as const;
export const accentLabel = (id: string) => accentChoices.find(choice => choice.value === id)?.label ?? "Orange";
export const accentsNote = (id: string) => id === "off"
  ? "No accents anywhere. Boring, just like you."
  : id === "phone"
    ? "Your phone's own colour, nudged lighter or darker so it shows up on black."
    : "Used for the small marks on the keys, the bar, the guide and these pages.";
