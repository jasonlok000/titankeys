/**
 * The words of Record a bug (#64), in one place for other languages (#56). The owner's voice (#54, voice.md), except the
 * warning about what the file shows and the delete text: those are a clean zone, plain and without jokes. The notification's
 * words are in android/kotlin/com/atlas/keyboard/BugRecording.kt (BugText).
 */
export const bugText = {
  title: "Record a bug",
  settingsLabel: "Record a bug",
  subtitle: "Something broke? Record it happening, then send it to us.",
  howTitle: "How it works",
  how: [
    "Press Record a bug.",
    "Go and do the thing that broke.",
    "Come back here and press Stop. It stops by itself after 3 minutes.",
    // F: draft, was "Check what's in it, then share it wherever you like."
    "Check what's in it, then email it to us or share it wherever you like.",
  ],
  // Clean zone: plain, no jokes. The privacy policy says the same (site/privacy/index.html).
  beforeRecord: "It doesn't save your text, but the keys and word choices it records can show most of what you typed while it ran. Only share it with people you'd show that to.",
  record: "Record a bug",
  recordingTitle: "Recording",
  left: (seconds: number) => `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")} left`,
  stop: "Stop",
  notifications: "Allow notifications to see a timer in the status bar while it records.",
  allow: "Allow notifications",
  doneTitle: "What's in it",
  length: "Length",
  touches: "Touches",
  swipes: "Swipes",
  keys: "Keys pressed",
  typedTitle: "What the keyboard typed",
  nothingTyped: "Nothing.",
  // Clean zone: plain, no jokes.
  warningTitle: "Before you share it",
  warning: [
    "The file has every touch and key press you made while it was on. From those, someone can work out what you typed, including the words above.",
    "Only share it with people you'd show that text to.",
    "Password and private fields are never recorded, and neither is the text that was already in a field.",
    "TitanKeys doesn't upload it. It goes only where you send it.",
  ],
  // F's lines for email (docs/words-m1.md, #74).
  email: "Email it",
  share: "Share elsewhere",
  zipping: "Zipping it so it fits in an email.",
  noMail: (address: string) => `No mail app here. Send it to ${address} from wherever you do email.`,
  noMailRecording: "Or press Share elsewhere to send the file another way.",
  copyAddress: "Copy address",
  copied: "Copied.",
  emailSubject: (version: string) => `TitanKeys bug recording, ${version}`,
  emailText: (details: string) => `What went wrong:\n\n\n${details}`,
  shareSubject: "TitanKeys bug recording",
  shareText: "What went wrong:\n\n",
  again: "Record again",
  delete: "Delete it",
};

export const duration = (millis: number) => {
  const seconds = Math.round(millis / 1000);
  return seconds < 60 ? `${seconds} s` : `${Math.floor(seconds / 60)} min ${seconds % 60} s`;
};
