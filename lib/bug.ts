import { resource } from "ink";
import { callNative, onNativeMessage } from "ink/native";

export type BugSummary = { millis: number; fields: number; touches: number; swipes: number; keys: number; words: string[]; bytes: number };
export type BugState = {
  recording: boolean;
  /** Milliseconds since it started, while it records. */
  elapsed: number;
  maxMillis: number;
  notificationsAllowed: boolean;
  /** The finished recording, if there is one. */
  summary?: BugSummary;
};

const bug = (operation: string, payload: object = {}) => callNative("connected", operation, payload);

export const bugState = resource({
  key: () => ["bug"],
  load: async (): Promise<BugState> => JSON.parse(await bug("bugState")),
})();

// Back from the app where the bug happened, or from the share sheet: read the state again.
onNativeMessage("atlas-state", () => void bugState.refresh());
onNativeMessage("atlas-window", () => void bugState.refresh());

async function run(operation: string, payload: object = {}) {
  try {
    await bug(operation, payload);
  } finally {
    await bugState.refresh();
  }
}

export const startBug = () => run("bugStart");
export const stopBug = () => run("bugStop");
export const deleteBug = () => run("bugDelete");
export const shareBug = (subject: string, text: string) => run("bugShare", { subject, text });
