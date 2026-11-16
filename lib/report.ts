import { resource } from "ink";
import { callNative, onNativeMessage } from "ink/native";

/** The report form (#97). The kinds, in the form's order: ids as Reports.KINDS has them. */
export const reportKinds = [
  { value: "word", label: "Wrong word or fix" },
  { value: "swiping", label: "Swiping" },
  { value: "touchpad", label: "Touchpad and Space taps" },
  { value: "keyboard", label: "Keyboard didn't show, or vanished" },
  { value: "crash", label: "Crash or freeze" },
  { value: "guide", label: "The guide" },
  { value: "settings", label: "Settings" },
  { value: "idea", label: "An idea" },
  { value: "other", label: "Something else" },
] as const;
export type ReportKind = typeof reportKinds[number]["value"];

export type ReportState = {
  /** A finished Record a bug recording is waiting. */
  recording: boolean;
  /** A crash or freeze report is waiting, from the crash offer. */
  crash: boolean;
  froze: boolean;
  /** A report that couldn't be sent waits on the phone, for email. */
  kept: boolean;
  version: string;
  phone: string;
  android: string;
};

const connected = (operation: string, payload: object = {}) => callNative("connected", operation, payload);

export const reportState = resource({
  key: () => ["report"],
  load: async (): Promise<ReportState> => JSON.parse(await connected("reportState")),
})();

onNativeMessage("atlas-window", () => void reportState.refresh());

export type Report = { kind: ReportKind; what: string; expected: string; email: string; recording: boolean; crash: boolean };

/** Seals and sends it. "sent", or "kept" or "large" when it waits on the phone for email instead. */
export async function sendReport(report: Report): Promise<"sent" | "kept" | "large"> {
  try {
    return JSON.parse(await callNative("connected", "reportSend", report, { timeoutMs: 300_000 }));
  } finally {
    await reportState.refresh();
  }
}

/** Opens the mail app with the kept report attached. False if there's no mail app. */
export async function emailReport(kind: ReportKind) {
  try {
    return await connected("reportEmail", { kind }) === "true";
  } finally {
    await reportState.refresh();
  }
}
