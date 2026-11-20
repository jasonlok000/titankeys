/**
 * Each kind of fix autocorrect makes, by its id in FixKind.kt, in that order, with a typo and its fix as the example. The two that
 * go back to finished words aren't here: Fix earlier words decides them ([earlierChoices]).
 * tools/checks/fixes.test.ts makes sure this list and the keyboard's stay the same.
 */
export const fixKinds: { id: string; label: string; example: string }[] = [
  { id: "typos", label: "Typos", example: "teh → the" },
  { id: "apostrophes", label: "Missing apostrophes", example: "dont → don't, whats → what's" },
  { id: "spacedApostrophes", label: "A space for the apostrophe", example: "don t → don't" },
  { id: "capitalI", label: "Capital I", example: "i → I, i'm → I'm" },
  { id: "nameCapitals", label: "Capitals for names", example: "monday → Monday, iphone → iPhone" },
  { id: "shiftHeld", label: "Shift held too long", example: "THe → The" },
  { id: "doubleLetters", label: "Last letter twice", example: "yeahh → yeah" },
  { id: "missingSpaces", label: "Missing spaces", example: "hellohow → hello how" },
  { id: "nameSlips", label: "Slips on names", example: "Sudnay → Sunday" },
  { id: "recentWords", label: "Words from this chat", example: "Ximwna → Ximena, once Ximena's come up twice" },
];

/** The kinds Fix earlier words decides instead of a switch of their own (TypingSettings.fixOn). */
export const earlierKinds = ["contextApostrophes", "secondLook"];

/** Fix earlier words (owner, 2026-11-20): how far a fix may go back to a finished word. Stand-in wording for F. */
export const earlierChoices: { value: "off" | "small" | "bold"; label: string; example: string }[] = [
  { value: "off", label: "Off", example: "A word stays as you left it once you've moved on." },
  { value: "small", label: "Small fixes", example: "its going → it's going, get dome food → get some food" },
  { value: "bold", label: "Bold", example: "Also their going → they're going, the are → they are, and a fix changed again" },
];
