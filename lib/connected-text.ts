/**
 * The words of the News and contribution pages, in one place, so that the tone (#54) and other languages (#56) can
 * change them without touching the pages. The disclosure must stay true to the privacy policy (site/privacy) and to
 * what android/kotlin/com/atlas/connected does. The guide's own offer is in GuideActivity (SHARE_HINT, SHARE_OFFER).
 * Page titles stay short: the large header cuts long ones.
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
  title: "Share swipes",
  settingsLabel: "Share swipes",
  subtitle: "Send examples of your swipes to the TitanKeys project, to make swiping better on every Titan 2 Elite. It stays off until you agree.",
  onSubtitle: "Your swipes help make swiping better on every Titan 2 Elite. Thank you.",
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
    "They are used only to train the TitanKeys swipe models. They are kept until they are used, and at most 2 years.",
    "You can stop at any time, and delete everything that you sent. This page shows your contributor ID, so that you can ask for deletion even after you remove TitanKeys.",
  ],
  agree: "I agree",
  decline: "No thanks",
  policy: "Privacy policy",
  about: "What is sent",
  toggle: "Share my swipes",
  statusTitle: "Your swipes",
  sentLine: "Sent",
  waitingLine: "Waiting to send",
  deleteTitle: "Delete",
  deleteWaiting: "Delete waiting swipes",
  deleteAll: "Delete what I sent",
  idTitle: "Your contributor ID",
  idNote: "A random ID for your swipes. Keep a copy: with it, you can ask for deletion even after you remove TitanKeys.",
  copy: "Copy ID",
  copied: "Copied",
  notReady: "Sending starts when the TitanKeys server is ready. Until then, swipes wait on this phone.",
  deletionWaiting: "Your request to delete what you sent waits for a connection.",
  confirmTitle: "Delete your swipes?",
  confirmSubtitle: "This deletes the swipes that wait on this phone, and asks the TitanKeys server to delete all the swipes that you sent. You cannot undo this.",
  confirm: "Delete my swipes",
};

export const swipes = (count: number) => count === 1 ? "1 swipe" : `${count} swipes`;
