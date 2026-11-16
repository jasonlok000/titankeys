import type { State } from "./atlas";

/** What the front page decides from: setup's parts, the guide, the release notes and the phone. */
export type FrontState = Pick<State, "enabled" | "selected" | "overlay" | "scrollAssistant" | "backService" | "backDeclined"
  | "guideDue" | "touchpadFunction" | "device" | "fakeOtherPhone" | "phoneWarningSeen">;

/**
 * A Titan 2 Elite: the touchpad's property says yes and the device is Titan_2. The same pair the touchpad itself needs
 * (TypingSettings.phoneScrollAssistant reads the property, Touchpad.kt the device), so on any other phone it really doesn't work.
 */
export const isTitan2Elite = ({ touchpadFunction, device }: Pick<State, "touchpadFunction" | "device">) =>
  touchpadFunction === "yes" && device === "Titan_2";

/** Another phone, or the debug build's switch that fakes one (Settings > Setup, TitanKeys Debug only). */
export const otherPhone = (state: Pick<State, "touchpadFunction" | "device" | "fakeOtherPhone">) =>
  state.fakeOtherPhone || !isTitan2Elite(state);

/**
 * A part of setup is still to do. Accessibility counts as done after "No thanks" on its disclosure, as the guide's own setup
 * counts it (PhoneSetup.missing): Settings > Setup can still ask again, and the front page never loops back to setup for it.
 */
export const setupToDo = ({ enabled, selected, overlay, scrollAssistant, backService, backDeclined }: FrontState) =>
  !enabled || !selected || !overlay || !scrollAssistant || !backService && !backDeclined;

/**
 * The front page's choice, in order:
 * - `warning`: another phone, once, before anything else. It never blocks: its one button carries on.
 * - `setup`: a part is still to do. The first time, the guide opens from it by itself.
 * - `guide`: setup is done and the guide hasn't opened yet, so it opens once, by itself.
 * - `front`: everything else, which is the release notes, the share offer or the playground (app/index.tsx).
 * Every way out of the guide ("I already know everything", ✕ and Back) brings the app's window to the front, which reads the
 * state again, so with setup done it lands here on `front`.
 */
export function frontChoice(state: FrontState): "warning" | "setup" | "guide" | "front" {
  if (otherPhone(state) && !state.phoneWarningSeen) return "warning";
  if (setupToDo(state)) return "setup";
  if (state.guideDue) return "guide";
  return "front";
}
