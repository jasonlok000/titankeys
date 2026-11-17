/**
 * The words of the News and Share swipes pages, in one place, so that other languages (#56) can change them without
 * touching the pages. The voice is the owner's (#54, voice.md): casual and direct. The Share swipes disclosure and
 * everything about deleting data are a clean zone: casual, never swearing. The disclosure must stay true to the privacy
 * policy (site/privacy) and to what android/kotlin/com/titankeys/connected does. The guide's own offer is in GuideActivity
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

/**
 * The short screen before Sharing turns on (#96): short and plain, as the owner asked ("the user main screen shd be still be simple
 * and straight to the point"), with the full privacy policy one tap away. One switch for everything: all or nothing. A clean
 * zone: serious and plain, no jokes. F: draft.
 */
export const sharePolicyText = {
  title: "Before you share",
  subtitle: "Sharing sends us examples of your swipes and daily totals of how TitanKeys works, so we can make it better. Nothing is sent until you turn it on.",
  sentTitle: "What's shared",
  sent: [
    "Your swipes: how each moved across the keys, and the word you kept, only if it's in the dictionary.",
    "Daily totals, such as fixes undone, swipes retyped, slides skipped, settings changed and crashes.",
    "Versions, your phone model and random IDs.",
  ],
  neverTitle: "Never shared",
  never: [
    "What you type, names, anything from password or private fields, your clipboard, your apps, contacts or location.",
  ],
  how: "It's encrypted on your phone. Turn it off any time in Settings > Sharing, and delete what you've sent.",
  agree: "Turn on sharing",
  decline: "Not now",
  policy: "Read the full policy",
};

/** Settings > Sharing (#96): swipes and daily totals, under one switch. F: draft. */
export const contributeText = {
  title: "Sharing",
  settingsLabel: "Sharing",
  subtitle: "Send us examples of your swipes and daily totals of how TitanKeys works, and we'll use them to make it better on every Titan 2 Elite. It's off until you agree.",
  onSubtitle: "You're helping make TitanKeys better on every Titan 2 Elite. Thanks.",
  sentTitle: "What gets sent",
  sent: [
    "The path of each swipe, with its timing and touch sizes.",
    "The word you kept, but only if it's in the dictionary.",
    "The 18 numbers the swipe engine used to rank the words.",
    "Daily totals of how TitanKeys' features worked: the full list is below, with what each one means.",
    "The TitanKeys, Android and model versions, your phone model, and random IDs.",
  ],
  neverTitle: "What never gets sent",
  never: [
    "The text around the word, or anything you type on the keys.",
    "Names, and any other word that isn't in the dictionary.",
    "Anything from password, email, web address or private fields.",
    "Your clipboard, snippets and shortcuts.",
    "Your apps, your contacts, your location, or what time of day you type.",
  ],
  howTitle: "How it's sent and kept",
  how: [
    "Swipes wait on your phone, up to 4 MB, and go on Wi-Fi only. Daily totals go once a day, on any connection.",
    "Everything is encrypted on your phone first. Only our private key can open it, and it's kept offline.",
    "We only use it to make TitanKeys better. Swipes are kept until they're used, and 2 years at most; daily totals 1 year.",
    "You can stop whenever you want and delete everything you've sent. This page shows your contributor ID, so you can still ask us to delete your swipes after you uninstall TitanKeys.",
  ],
  agree: "I agree",
  decline: "No thanks",
  policy: "Privacy policy",
  about: "What's shared",
  toggle: "Share to improve TitanKeys",
  todayTitle: "Today so far",
  todayEmpty: "Nothing counted yet today.",
  waitingDays: (days: number) => days === 1 ? "1 day waiting to send" : `${days} days waiting to send`,
  countsTitle: "Every daily total",
  countsNote: "Each is a number for the day. We may add totals of these same kinds; they show here first. We'll ask again before we send any other kind of data.",
  guideTitle: "The guide",
  settingsTitle: "Settings you changed",
  settingsNote: "By name only, never what you chose.",
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
  confirmTitle: "Delete what you've shared?",
  confirmSubtitle: "This deletes the swipes waiting on your phone and asks our server to delete everything you've sent, swipes and daily totals. You can't undo it.",
  confirm: "Delete it all",
};

export const swipes = (count: number) => count === 1 ? "1 swipe" : `${count} swipes`;
