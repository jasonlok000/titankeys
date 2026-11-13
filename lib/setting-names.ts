/**
 * Settings that show in more than one place, by the one name each has everywhere: Settings, the keyboard's quick settings
 * (android/kotlin/com/atlas/keyboard/QuickSetting.kt), the fun guide (GuideSteps.kt) and the boring guide.
 * tools/checks/setting-names.test.ts keeps them the same.
 */

/** Turns the whole bar on or off: words, the Shift, Alt and Ctrl chips and the clip offer. */
export const barLabel = "Show the bar";

/**
 * The phone's Scroll assistant for apps: with it on, the keys scroll apps when no text field is active. Inside a text field
 * TitanKeys's own touchpad features take over either way.
 */
export const scrollLabel = "Scroll assistant";
export const scrollNote = "Scrolls apps with the keys when you're not typing.";

/** Space taps: a double tap moves the cursor, a triple tap selects words. */
export const cursorLabel = "Space taps move and select";
