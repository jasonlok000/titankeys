import { useSnapshot } from "ink";
import { state } from "./atlas";

/**
 * The colours drawn directly by screens, one set for the whole app: pure black with orange accents. theme.ts sets the same
 * values for everything else, and android/kotlin/com/atlas/keyboard/GuideColours mirrors them by hand for the native screens.
 * The orange is named only here, in theme.ts and in Accent.kt (tools/checks/accent.test.ts): a screen asks [useAccent].
 */
/** The one accent: titles, section labels, values and what's on. */
const ORANGE = "#EF804E";
/** Every background. */
export const BLACK = "#000000";
/** Primary text. */
export const CREAM = "#FFF1E6";
/** Secondary text: cream at 60% over black, flattened. */
export const MUTED = "#99918A";
/** Hairlines and outlines: cream at about 15% over black, flattened. */
export const DIVIDER = "#2A2623";
/** Only text on orange buttons. Never a background. */
export const INK = "#160A04";

/**
 * The accent now: orange, or with Settings > Orange accents off, the text's cream (owner, 2026-11-13: off means "literally
 * everything globally"). Ink's own accent follows the same switch through the Ink patch (appAccent).
 */
export function useAccent(): string {
  const result = useSnapshot(state);
  return result.status === "ready" && !result.data.orangeAccents ? CREAM : ORANGE;
}
