/**
 * Each kind of fix autocorrect makes, by its id in FixKind.kt, in that order, with a typo and its fix as the example.
 * tools/checks/fixes.test.ts makes sure this list and the keyboard's stay the same.
 */
export const fixKinds: { id: string; label: string; example: string }[] = [
  { id: "typos", label: "Typos", example: "teh → the" },
  { id: "apostrophes", label: "Missing apostrophes", example: "dont → don't, whats → what's" },
  { id: "contextApostrophes", label: "Use context for its, lets, your…", example: "its going to rain → it's going to rain. It waits for the next two words." },
  { id: "spacedApostrophes", label: "A space for the apostrophe", example: "don t → don't" },
  { id: "capitalI", label: "Capital I", example: "i → I, i'm → I'm" },
  { id: "nameCapitals", label: "Capitals for names", example: "monday → Monday, iphone → iPhone" },
  { id: "shiftHeld", label: "Shift held too long", example: "THe → The" },
  { id: "doubleLetters", label: "Last letter twice", example: "yeahh → yeah" },
  { id: "missingSpaces", label: "Missing spaces", example: "hellohow → hello how" },
  { id: "nameSlips", label: "Slips on names", example: "Sudnay → Sunday" },
  { id: "recentWords", label: "Words from this chat", example: "Ximwna → Ximena, once Ximena's come up twice" },
  { id: "secondLook", label: "Second look after the next word", example: "get dome food → get some food" },
];
