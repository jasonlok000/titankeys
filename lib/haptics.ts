/**
 * Every haptic, by its id in Haptics.kt, in the groups of docs/haptics.md. tools/checks/haptics.test.ts makes sure this list
 * and the keyboard's stay the same. [about] says where each one plays, for the debug Haptics lab.
 */
export const hapticGroups: { title: string; haptics: { id: string; label: string; about: string }[] }[] = [
  { title: "Swipe", haptics: [
    { id: "swipe_start", label: "Swipe recognised", about: "When your finger has gone far enough to count as a swipe. It's the first tick." },
    { id: "swipe_key", label: "Each key a swipe crosses", about: "Each key your swipe crosses, so you can swipe without looking." },
    { id: "tap_key", label: "A light touch types a key", about: "When a light touch on the touchpad types a key. The touchpad has no click, so this stands in." },
  ] },
  { title: "Delete", haptics: [
    { id: "delete_flick", label: "Delete flick", about: "When you flick left to delete a word. The second pulse lands as the dots pop." },
    { id: "delete_dots_land", label: "Delete dots land", about: "When the dots of a flick, or of a long delete as you lift, go into the cursor. A light tick for every dot as it lands." },
    { id: "delete_word", label: "Each word while you hold", about: "Each word that goes while you keep your finger down after a flick. In TitanKeys Debug, with the lab's fade on, the ticks get shorter as more go, then only every other one plays." },
    { id: "delete_nothing", label: "Nothing left to delete", about: "When you flick or hold to delete and there's nothing left." },
  ] },
  { title: "Space gestures", haptics: [
    { id: "space_tap", label: "Each counted Space tap", about: "Each tap on Space that counts toward a double or triple tap, so you feel it land." },
    { id: "steer", label: "Steering starts", about: "When you hold Space and the cursor starts to follow your finger." },
    { id: "steer_resumed", label: "Steering picks up after a slip", about: "When your finger slips off and lands again in time, and you're still steering." },
    { id: "select", label: "Selecting starts", about: "When you tap, tap, then hold Space and selecting starts." },
    { id: "word_picked", label: "Double or triple tap picks a word", about: "When a double or triple tap on Space picks the word at the cursor." },
    { id: "nothing_to_pick", label: "Nothing to pick", about: "When a double or triple tap finds no word, or you steer with no text. Nothing happens, and this says why." },
    { id: "cursor_letter", label: "Cursor passes a letter", about: "Each letter the cursor passes while you steer." },
    { id: "cursor_space", label: "Cursor passes a space", about: "Each space or line end the cursor passes while you steer, so you can count words." },
    { id: "selection_word", label: "Selection grows or shrinks a word", about: "When the selection grabs one more word, or lets one go." },
    { id: "cursor_end", label: "Cursor hits the start or end", about: "When the cursor hits the start or end of the text and can't go further." },
  ] },
  { title: "Changes", haptics: [
    { id: "autocorrect", label: "Autocorrect or shorthand on Space", about: "When autocorrect changes a word as you press Space, or shorthand gets spelled out." },
    { id: "autocorrect_undone", label: "Backspace undoes a change", about: "When you press Backspace right after a change and get back what you typed." },
    { id: "held_symbol", label: "Held letter becomes its symbol", about: "When you hold a letter and it turns into its symbol." },
    { id: "expansion", label: "Emoji from :name:, or a snippet", about: "When a :name: turns into its emoji, or a snippet's shortcut into its text." },
  ] },
  { title: "Bar", haptics: [
    { id: "bar_word", label: "Tap a word", about: "When you tap a word on the bar to put it in." },
    { id: "menu_open", label: "Word menu opens", about: "When you hold a word on the bar and its menu opens." },
    { id: "menu_mark", label: "Marked menu button changes", about: "When your finger moves the mark to another button in the word menu." },
    { id: "menu_closed", label: "Word menu closed", about: "When you press Back and the word menu closes without a pick." },
    { id: "word_saved", label: "Save", about: "When you save a word from the word menu, with I or the Save button. Two light taps settle into a firm one: kept." },
    { id: "word_forgotten", label: "Forget", about: "When you delete a word from the word menu, with O or the Forget button. A firm hit that trails off: gone." },
    { id: "forget_undone", label: "Undo after Forget", about: "When you press Undo right after Forget and the word is back." },
    { id: "word_not_saved", label: "A word that can't be saved", about: "When Save goes wrong and the word isn't saved." },
    { id: "clip_offer", label: "Paste the offered clip", about: "When you paste the clip the bar offers after you copy something." },
    { id: "exact_key", label: "Exact typing bar: a key", about: "When you press a key on exact typing's bar, or Type clip." },
    { id: "exact_lock", label: "Exact typing bar: a lock", about: "When a long press locks a key on exact typing's bar." },
    { id: "exact_chip", label: "Exact typing bar: a chip", about: "When you tap Shift, Alt or Ctrl on exact typing's bar to turn it on or off." },
    { id: "exact_unlock", label: "Exact typing bar: unlock", about: "When you tap a locked Shift, Alt or Ctrl on exact typing's bar and it unlocks." },
  ] },
  { title: "Shift and Alt", haptics: [
    { id: "modifier_locked", label: "Locked", about: "When Shift or Alt locks on." },
    { id: "modifier_unlocked", label: "Unlocked", about: "When a locked Shift or Alt goes off." },
  ] },
  { title: "Sym, emoji, quick settings and clipboard", haptics: [
    { id: "panel_mark", label: "Mark moves to another key", about: "When your finger moves the mark to another key on a panel." },
    { id: "panel_page", label: "New page", about: "When a panel turns to a new page." },
    { id: "panel_group", label: "New group", about: "When the emoji panel jumps to a new group." },
    { id: "quick_setting", label: "Quick setting switched", about: "When you switch something on the quick settings page." },
    { id: "clip_pasted", label: "Paste a clip", about: "When you paste a clip from the clipboard page." },
    { id: "clip_pinned", label: "Pin a clip", about: "When you pin a clip so it stays." },
    { id: "clip_unpinned", label: "Unpin a clip", about: "When you unpin a clip." },
    { id: "clipboard_cleared", label: "Clear the clipboard", about: "When you clear the clipboard and the clips are gone." },
  ] },
  { title: "Other", haptics: [
    { id: "bug_recording", label: "Record a bug starts or stops", about: "When Record a bug starts, and when it stops." },
    { id: "guide_celebration", label: "Guide celebrations", about: "When you finish a part of the guide." },
  ] },
];
