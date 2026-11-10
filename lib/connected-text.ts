/**
 * The words of the News and contribution pages, in one place, so that the tone (#54) and other languages (#56) can
 * change them without touching the pages. The disclosure must stay true to the privacy policy (site/privacy) and to
 * what android/kotlin/com/atlas/connected does.
 */
export const newsText = {
  title: "News",
  subtitle: "New versions and other news from TitanKeys.",
  toggle: "Show news",
  toggleNote: "TitanKeys checks its website once a day. It sends nothing about you.",
  allow: "Allow notifications",
  allowReason: "News shows as a silent notification, and never while you type.",
  empty: "No news now.",
  hide: "Don't show again",
  hidden: "Hidden",
};

export const contributeText = {
  title: "Help train swipe typing",
  settingsLabel: "Help train swipe typing",
  subtitle: "Send examples of your swipes to the TitanKeys project, to make swipe typing better on every Titan 2 Elite. It stays off until you agree.",
  sentTitle: "What is sent",
  sent: [
    "The path of each swipe, with its times and touch sizes.",
    "The word that you kept, only if it is in the dictionary.",
    "The 18 numbers that the swipe engine used to rank the words.",
    "The TitanKeys and model versions, the phone model, and a random ID for your examples.",
  ],
  neverTitle: "What is never sent",
  never: [
    "The text around the word, and the text that you type with the keys.",
    "Names and other words that are not in the dictionary.",
    "Anything from password, email, web address or private fields.",
    "The clipboard, snippets and shortcuts.",
  ],
  howTitle: "How it is sent and kept",
  how: [
    "Examples wait on this phone, up to 4 MB. You can see and delete them.",
    "They go on Wi-Fi only, encrypted on this phone. Only the project's private key, kept offline, can open them.",
    "They are used only to train the TitanKeys swipe models, and are kept for at most 2 years.",
    "You can stop at any time, and delete everything that you sent.",
  ],
  agree: "I agree",
  decline: "No thanks",
  policy: "Privacy policy",
  toggle: "Send swipe examples",
  waitingTitle: "On this phone",
  waiting: "Waiting to send",
  deleteWaiting: "Delete waiting examples",
  sentTitleOn: "Sent",
  sentCount: "Examples sent",
  deleteAll: "Delete my contributions",
  notReady: "Sending starts when the TitanKeys server is ready. Until then, examples wait on this phone.",
  deletionWaiting: "Your request to delete what you sent waits for a connection.",
  deleteTitle: "Delete your contributions?",
  deleteSubtitle: "This deletes the examples that wait on this phone, and asks the TitanKeys server to delete all the examples that you sent. You cannot undo this.",
  deleteConfirm: "Delete my contributions",
};

export const swipes = (count: number) => count === 1 ? "1 example" : `${count} examples`;
