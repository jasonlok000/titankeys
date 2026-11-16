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
    "Check what's in it, then send it to us or share it wherever you like.",
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
    "TitanKeys only sends it if you press Send in the report form, sealed so only we can read it. Otherwise it stays on your phone.",
  ],
  // "Send it" opens the report form (#97); "Share elsewhere" keeps the share sheet (#74).
  send: "Send it",
  share: "Share elsewhere",
  shareSubject: "TitanKeys bug recording",
  shareText: "What went wrong:\n\n",
  again: "Record again",
  delete: "Delete it",
};

export const duration = (millis: number) => {
  const seconds = Math.round(millis / 1000);
  return seconds < 60 ? `${seconds} s` : `${Math.floor(seconds / 60)} min ${seconds % 60} s`;
};
