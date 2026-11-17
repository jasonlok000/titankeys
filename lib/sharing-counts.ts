/**
 * Every daily total that Sharing sends (#96, docs/analytics.md §3), with what it means, for Settings > Sharing > What's shared.
 * The keys are the payload's. A part in angle brackets stands for its bands: <length> is a word's letters (1-2, 3-4, 5-7, 8+),
 * <place> a place on the bar (1 is the first), <band> a time in milliseconds. tools/checks/sharing.test.ts checks that the
 * keyboard sends no count missing here, so a new count can't ship unlisted. Each is a number for one day, counted only in fields
 * that allow learning. F: draft.
 */
export type SharedCount = { key: string; meaning: string };

export const SHARED_COUNTS: readonly { title: string; counts: readonly SharedCount[] }[] = [
  {
    title: "Swipes",
    counts: [
      { key: "swipe.kept.<length>", meaning: "Swiped words kept as they came out." },
      { key: "swipe.picked.<length>", meaning: "Swiped words swapped for another word from the bar." },
      { key: "swipe.retyped.<length>", meaning: "Swiped words deleted and typed again as another word." },
      { key: "speed.swipe.<band>", meaning: "Swipes, by how long the word took to show after the finger lifted." },
    ],
  },
  {
    title: "Joined swipes",
    counts: [
      { key: "join.total", meaning: "Swipes joined to the one before into one word." },
      { key: "join.undone", meaning: "Joined words then swapped from the bar or typed again." },
      { key: "join.keptApart", meaning: "Quick swipes kept as two words where they could have joined." },
      { key: "join.keptApart.redone", meaning: "Of those, the second word then swapped or typed again." },
    ],
  },
  {
    title: "Fixes",
    counts: [
      { key: "autocorrect.made", meaning: "Typos autocorrect fixed." },
      { key: "autocorrect.undone", meaning: "Of those, undone with Backspace right after." },
      { key: "apostrophe.made", meaning: "Apostrophes put in, as were to we're." },
      { key: "apostrophe.undone", meaning: "Of those, undone right after." },
      { key: "capital.made", meaning: "Capitals put in, as i to I." },
      { key: "capital.undone", meaning: "Of those, undone right after." },
    ],
  },
  {
    title: "The bar",
    counts: [
      { key: "bar.place.<place>", meaning: "Words tapped on the bar, by their place on it." },
    ],
  },
  {
    title: "Chinese and Japanese",
    counts: [
      { key: "zh.pick.space", meaning: "Chinese choices picked with Space." },
      { key: "zh.pick.tap", meaning: "Chinese choices picked with a tap on the bar." },
      { key: "zh.pick.menu", meaning: "Chinese choices picked from the bigger menu." },
      { key: "zh.bigMenu", meaning: "Times the bigger menu opened in Chinese." },
      { key: "ja.pick.space", meaning: "Japanese choices picked with Space." },
      { key: "ja.pick.tap", meaning: "Japanese choices picked with a tap on the bar." },
      { key: "ja.pick.menu", meaning: "Japanese choices picked from the bigger menu." },
      { key: "ja.bigMenu", meaning: "Times the bigger menu opened in Japanese." },
    ],
  },
  {
    title: "Gestures",
    counts: [
      { key: "gesture.flickDelete", meaning: "Words deleted with a flick." },
      { key: "gesture.deleteHold", meaning: "Flicks held down to keep deleting." },
      { key: "gesture.doubleSpace", meaning: "Double taps on Space." },
      { key: "gesture.tripleSpace", meaning: "Triple taps on Space." },
      { key: "gesture.steer", meaning: "Times the cursor was steered from Space." },
      { key: "gesture.selectWords", meaning: "Times words were selected by steering after three taps." },
    ],
  },
  {
    title: "Speed",
    counts: [
      { key: "speed.open.<band>", meaning: "Times the keys showed for a new field, by how long they took." },
    ],
  },
  {
    title: "Crashes",
    counts: [
      { key: "crash.count", meaning: "Times TitanKeys crashed, with nothing about the crash." },
      { key: "freeze.count", meaning: "Times TitanKeys froze, with nothing about it." },
    ],
  },
];

/** The other parts of a day, besides the counts. */
export const SHARED_PARTS: readonly SharedCount[] = [
  { key: "guide", meaning: "The furthest guide slide you reached, whether you finished, and the slides you skipped." },
  { key: "settings", meaning: "The settings you changed, by name only, never what you chose. Sent only on a day they change." },
  { key: "facts", meaning: "The TitanKeys, Android and model versions, your phone model, whether it came from GitHub or Google Play, the date, and a random ID that changes every 30 days." },
];

/** The meaning of a count's key as the payload has it, as "swipe.kept.5-7", or null if it isn't listed. */
export function meaningOf(key: string): string | null {
  for (const group of SHARED_COUNTS) for (const count of group.counts) {
    const pattern = new RegExp("^" + count.key.replace(/[.+]/g, m => `\\${m}`).replace(/<[a-z]+>/g, "[^.]+") + "$");
    if (pattern.test(key)) return count.meaning;
  }
  return null;
}
