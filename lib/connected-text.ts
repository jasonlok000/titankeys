/**
 * The words of the News and Share swipes pages, in one place, so that other languages (#56) can change them without
 * touching the pages. The voice is the owner's (#54, voice.md): casual and direct. The Share swipes disclosure and
 * everything about deleting data are a clean zone: casual, never swearing. The disclosure must stay true to the privacy
 * policy (site/privacy) and to what android/kotlin/com/atlas/connected does. The guide's own offer is in GuideActivity
 * (SHARE_HINT, SHARE_OFFER). Page titles stay short: the large header cuts long ones.
 */
export const newsText = {
  title: "News",
  subtitle: "New versions and anything else worth knowing.",
  toggle: "Show news",
  toggleNote: "TitanKeys checks for news once a day. It doesn't send anything about you.",
  allow: "Allow notifications",
  allowReason: "News shows up as a silent notification, and never while you're typing.",
  empty: "Nothing new right now.",
  hide: "Don't show again",
  hidden: "Hidden",
};

/** The short policy shown before Share swipes turns on. A clean zone: serious and plain, no jokes. */
export const sharePolicyText = {
  title: "Before you share",
  subtitle: "Share swipes sends us examples of your swipes, to train the swipe models. Nothing is sent until you turn it on.",
  sentTitle: "What's sent",
  sent: [
    "How each swipe moved across the keys, with its timing and touch size.",
    "The word you kept, only if it's in the dictionary.",
    "The numbers the swipe engine used to rank the words, without the words.",
    "The app and model versions, your phone model, and a random ID.",
  ],
  neverTitle: "What's never sent",
  never: [
    "What you type, or the text around a word.",
    "Names and other words that aren't in the dictionary.",
    "Anything from password, email, web address or private fields.",
    "Your clipboard, snippets and shortcuts.",
  ],
  how: "It's sent encrypted, on Wi-Fi only. You can turn it off and delete what you've sent any time in Settings > Share swipes.",
  agree: "Turn on Share swipes",
  decline: "Not now",
  policy: "Read the full privacy policy",
};

export const contributeText = {
  title: "Share swipes",
  settingsLabel: "Share swipes",
  subtitle: "Send us examples of your swipes, and we'll use them to make swiping better on every Titan 2 Elite. It's off until you agree.",
  onSubtitle: "Your swipes are helping make swiping better on every Titan 2 Elite. Thanks.",
  sentTitle: "What gets sent",
  sent: [
    "The path of each swipe, with its timing and touch sizes.",
    "The word you kept, but only if it's in the dictionary.",
    "The 18 numbers the swipe engine used to rank the words.",
    "The TitanKeys and model versions, your phone model, and a random ID for your swipes.",
  ],
  neverTitle: "What never gets sent",
  never: [
    "The text around the word, or anything you type on the keys.",
    "Names, and any other word that isn't in the dictionary.",
    "Anything from password, email, web address or private fields.",
    "Your clipboard, snippets and shortcuts.",
  ],
  howTitle: "How it's sent and kept",
  how: [
    "Swipes wait on your phone, up to 4 MB. You can see them and delete them.",
    "They're only sent on Wi-Fi, and they're encrypted on your phone first. Only our private key can open them, and it's kept offline.",
    "We only use them to train the TitanKeys swipe models. We keep them until they're used, and 2 years at most.",
    "You can stop whenever you want and delete everything you've sent. This page shows your contributor ID, so you can still ask us to delete your swipes after you uninstall TitanKeys.",
  ],
  agree: "I agree",
  decline: "No thanks",
  policy: "Privacy policy",
  about: "What gets sent",
  toggle: "Share my swipes",
  statusTitle: "Your swipes",
  sentLine: "Sent",
  waitingLine: "Waiting to send",
  deleteTitle: "Delete",
  deleteWaiting: "Delete waiting swipes",
  deleteAll: "Delete what I've sent",
  idTitle: "Your contributor ID",
  idNote: "A random ID for your swipes. Keep a copy: you'll need it to ask us to delete them if you uninstall TitanKeys.",
  copy: "Copy ID",
  copied: "Copied",
  notReady: "Sending starts once the TitanKeys server is up. Until then, your swipes wait on your phone.",
  deletionWaiting: "Your delete request goes out as soon as you're online.",
  confirmTitle: "Delete your swipes?",
  confirmSubtitle: "This deletes the swipes waiting on your phone and asks our server to delete everything you've sent. You can't undo it.",
  confirm: "Delete my swipes",
};

export const swipes = (count: number) => count === 1 ? "1 swipe" : `${count} swipes`;
