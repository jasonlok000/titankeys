/**
 * Every haptic, by its id in Haptics.kt, in the groups of docs/haptics.md. tools/checks/haptics.test.ts makes sure this list
 * and the keyboard's stay the same.
 */
export const hapticGroups: { title: string; haptics: { id: string; label: string }[] }[] = [
  { title: "Swipe", haptics: [
    { id: "swipe_start", label: "Swipe recognised" },
    { id: "swipe_key", label: "Each key a swipe crosses" },
    { id: "tap_key", label: "A light touch types a key" },
  ] },
  { title: "Delete", haptics: [
    { id: "delete_flick", label: "Delete flick" },
    { id: "delete_word", label: "Each word while you hold" },
    { id: "delete_nothing", label: "Nothing left to delete" },
  ] },
  { title: "Space gestures", haptics: [
    { id: "steer", label: "Steering starts" },
    { id: "select", label: "Selecting starts" },
    { id: "word_picked", label: "Double or triple tap picks a word" },
    { id: "cursor_letter", label: "Cursor passes a letter" },
    { id: "cursor_space", label: "Cursor passes a space" },
    { id: "selection_word", label: "Selection grows or shrinks a word" },
    { id: "cursor_end", label: "Cursor hits the start or end" },
  ] },
  { title: "Changes", haptics: [
    { id: "autocorrect", label: "Autocorrect or shorthand on Space" },
    { id: "held_symbol", label: "Held letter becomes its symbol" },
    { id: "expansion", label: "Emoji from :name:, or a snippet" },
  ] },
  { title: "Bar", haptics: [
    { id: "bar_word", label: "Tap a word" },
    { id: "menu_open", label: "Word menu opens" },
    { id: "menu_mark", label: "Marked menu button changes" },
    { id: "word_saved", label: "Save or Undo" },
    { id: "word_forgotten", label: "Forget" },
    { id: "word_not_saved", label: "A word that can't be saved" },
    { id: "clip_offer", label: "Paste the offered clip" },
  ] },
  { title: "Shift and Alt", haptics: [
    { id: "modifier_locked", label: "Locked" },
    { id: "modifier_unlocked", label: "Unlocked" },
  ] },
  { title: "Sym, emoji, quick settings and clipboard", haptics: [
    { id: "panel_mark", label: "Mark moves to another key" },
    { id: "panel_page", label: "New page" },
    { id: "panel_group", label: "New group" },
    { id: "quick_setting", label: "Quick setting switched" },
    { id: "clip_pasted", label: "Paste a clip" },
    { id: "clip_pinned", label: "Pin a clip" },
    { id: "clip_unpinned", label: "Unpin a clip" },
    { id: "clipboard_cleared", label: "Clear the clipboard" },
  ] },
  { title: "Other", haptics: [
    { id: "bug_recording", label: "Record a bug starts or stops" },
    { id: "guide_celebration", label: "Guide celebrations" },
  ] },
];
