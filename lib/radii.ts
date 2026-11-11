/**
 * The corner radii that every rounded thing in TitanKeys uses, in Ink's 1080-pixel-wide frame. The keyboard and the guide use
 * the same numbers from android/kotlin/com/atlas/keyboard/Radii.kt, and tools/checks/radii.test.ts keeps the two the same.
 */
export const RADIUS = {
  /** Chips, key highlights, clip fields, the keys in drawings. */
  small: 16,
  /** Text fields, hints, cards, the top of the keyboard. */
  medium: 32,
  /** Buttons. */
  large: 56,
} as const;
