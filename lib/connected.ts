import { resource } from "ink";
import { callNative, onNativeMessage } from "ink/native";

/** What a touch on an announcement does: open a web page (url) or a page in the app (page). One of the two is empty. */
export type NewsAction = { label: string; url: string; page: string };
export type NewsEntry = { id: string; title: string; text: string; action: NewsAction | null; dismissed: boolean };

export type ConnectedState = {
  /** Announcements are on: the app checks the TitanKeys feed once a day. */
  announcements: boolean;
  notificationsAllowed: boolean;
  /** The entries for this version and build now, newest first. */
  entries: NewsEntry[];
  /** The user agreed to send swipe examples. */
  contribute: boolean;
  /** The guide ended and the user has not answered the offer yet. */
  offerDue: boolean;
  queued: number;
  queuedBytes: number;
  sent: number;
  sentAt: number;
  /** A request to delete sent examples waits for a connection. */
  deletionPending: boolean;
  /** The contributions server is set in this build. Until it is, examples wait on the phone. */
  serverReady: boolean;
  privacyUrl: string;
};

const connected = (operation: string, payload: object = {}) => callNative("connected", operation, payload);

export const connectedState = resource({
  key: () => ["connected"],
  load: async (): Promise<ConnectedState> => JSON.parse(await connected("state")),
})();

// The app comes back to the front after the notification prompt or Android's settings; read the state again then.
onNativeMessage("atlas-state", () => void connectedState.refresh());
onNativeMessage("atlas-window", () => void connectedState.refresh());

async function run(operation: string, payload: object = {}) {
  try {
    await connected(operation, payload);
  } finally {
    await connectedState.refresh();
  }
}

export const setAnnouncements = (on: boolean) => run("announcements", { on });
export const allowNotifications = () => run("allowNotifications");
export const dismissEntry = (id: string) => run("dismissEntry", { id });
export const setContribute = (on: boolean) => run("contribute", { on });
export const declineContributions = () => run("declineContributions");
export const deleteQueued = () => run("deleteQueued");
export const deleteContributions = () => run("deleteContributions");

export const PRIVACY_URL = "https://jasonlok000.github.io/titankeys/privacy/";

export const kilobytes = (bytes: number) => bytes < 1024 * 1024 ? `${Math.ceil(bytes / 1024)} KB` : `${(bytes / 1_048_576).toFixed(1)} MB`;
