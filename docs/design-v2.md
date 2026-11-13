# TitanKeys 2.1: the v2 look (guide, playground, word menu)

Written by the Orchestrator on 2026-11-12, 23:00, while the owner was away.

The owner asked for this round to be built straight to "10/10, absolutely perfect", with no mockups first. Every choice below is
the **Orchestrator's decision. The owner has not approved it.** The owner reviews it on the finished build. Owner decisions that
are already settled are marked (Owner).

## 1. One size system for everything TitanKeys draws

- **Sizes don't change with the phone's display size or font size (Owner).** Every TitanKeys window works in the same units: one
  dp is the screen's width ÷ 688.6, which is 1.5625 px on the Titan 2 Elite. Fonts are never scaled by the system font size.
  - Implementation: a single helper (`FixedScale`) wraps each window's context with a configuration that has
    `fontScale = 1` and `densityDpi` from the screen width. It covers the keyboard service, the guide and playground, Ink's
    activity, and the debug screens.
- **Spacing scale:** 4, 8, 12, 16, 24, 32, 48 dp. Nothing in between.
- **Type scale (Inter):**
  - **Display:** 30 dp, ExtraBold, UPPER CASE, letter spacing −0.01 em. Slide titles only.
  - **Title:** 22 dp, Bold. The playground wordmark, dialog titles.
  - **Body:** 16 dp, Medium. Instructions, chips.
  - **Field:** 22 dp, Medium. What you type in the guide and playground.
  - **Label:** 12 dp, Bold, UPPER CASE, letter spacing +0.08 em. The chapter label, small buttons.
  - A line that's too long shrinks to fit, down to 80% of its size, and never wraps unless its role says so.
- **Colours:**
  - Black #000 background.
  - Cream #FFF1E6 text.
  - Muted #99918A secondary text.
  - Surface #161210 for fields and cards.
  - Divider #2A2623.
  - **Orange #EF804E means one thing: what to do now or what just happened** — the keys this slide wants, the part of the line to
    type next, the caret, the primary button (Next), the pass state. Orange is never decoration: no orange titles, labels or
    progress bars.
  - On the orange pass screen, every text is ink #160A04.

## 2. The guide

- **Top bar (48 dp):**
  - On the left, in Label style: the chapter's name in cream and its place, muted (`DELETE · 2/4`).
  - On the right, ✕ only, a 24 dp icon in a 48 dp target.
  - No slide ticks.
  - "Start again" moves into the ✕ pop-up ("Leaving already?"), which offers Keep going, Start again and Leave.
- **The content block:** the title, then 12 dp, the instruction, then 24 dp, the field. 24 dp side margins. The block sits centred in
  the free room between the top bar and the keyboard, with at least 24 dp above and below.
  - **Title:** Display, cream, upper case, one line.
  - **Instruction:** Body, muted. A hint chip replaces it for up to 3 s, in the same place, same size.
  - **Field: exactly two lines, always (Owner).** Surface background, 16 dp corners, 16 dp padding. Typed text in cream, the rest
    of the line in muted, and the word to act on underlined in orange.
- **Skip and Next:** one quiet text button, right-aligned, 16 dp under the field.
  - "Skip" is muted Label, shown after 10 s on the slide or after a hint chip.
  - When the slide passes, it becomes **Next**: an orange pill with an ink label, in the same place.
  - Never in the top bar.
- **Keyboard drawing (Owner: same width, 60% height):**
  - Edge to edge across the full screen width, never clipped by margins.
  - The real shape: square keys, no gap between keys, 10.5 px between rows, drawn at 60% of the height.
  - Neighbours read as separate keys through a 3% inset each side.
  - Idle keys: a cream outline at 16% opacity over the surface at 50%. Labels cream at 70%.
  - The keys this slide is about are orange, with ink labels. A pressed key glows.
  - Corner symbols only show on the hold-for-symbol slides.
- **Motion:**
  - One move for every change: 220 ms with ease-out, fading and rising 8 dp.
  - Slide to slide: the content block fades and rises. The keyboard stays put.
  - The pass: the screen floods orange for 400 ms, then settles. Text turns ink.

## 3. The playground (the front page)

- **Header:** 56 dp, with the wordmark "TitanKeys" in Title style on the left, in cream with "Keys" in orange. Guide and Settings are
  outline pills on the right (Label style, muted outline). The header never changes size, including when a panel opens.
- **The line to type along to:** Body, two lines. Typed words in cream, the rest muted, and the current word underlined in orange.
- **The text box:** the same as the guide's field, three lines.
- **Words a minute:** a small Label counter in the header's empty middle. It **starts only once a real word is being typed** (a
  letter key or a swipe), not on a touch, Space, Backspace or a modifier (Owner).
- **The warm-up to orange:** the background warms with speed as before. Once it's past the point where cream text drops below
  4.5:1 contrast, every text turns ink, so it's **always readable (Owner)**.
- **Keyboard:** the same drawing as the guide's, at 60%, edge to edge.
- **The text:** 162 lines in the owner's tone. Every line comes once before any repeats.

## 4. The word menu on the bar

- **Places (Owner):** I, O and Back sit on the centres of the I, O and Backspace columns, a key apart. Back no longer dodges the
  corner.
- **Hover stays (Owner):** after a press, the pill stays on the pressed key and shows its next step. It goes when the finger lifts,
  as on the Sym panel.
- **States (Owner):**
  - A dictionary word counts as Saved. A word you saved counts as Saved. A favourite is a favourite.
  - Going down from Saved takes the word out (for a dictionary word, out of the dictionary).
  - Symbols and emoji get no Save at all.
- **Save, a particle burst that lands:**
  - Hearts work because a heart is a bold, filled, instantly readable shape. The ribbon failed because it's thin.
  - Save's particles are **bold filled four-point sparkles**: the "this is kept, it shines" shape. 8 to 10 of them, the same physics
    as Favourite's, but bursting **outward in a ring and then drifting up**, slightly bigger at the start. The word pops like
    Favourite's.
  - Once saved, a faint sparkle twinkles by the word now and then, as the ambient status.
  - The icon stays as it is. The owner said the icon is fine and a floppy disk isn't needed.
- **Unsave:** the inverse, quieter. The word dims for a moment, and a few sparkles fall from it with gravity and fade. No shake.
- **The heart breaking (Owner: "slightly bad"):**
  - A zigzag crack draws down the heart in 120 ms.
  - The two halves tilt apart, with 4 tiny shards spraying out.
  - The halves fall with gravity, spinning slightly, and fade.
  - The word dips 2 dp and comes back, instead of shaking.
- Everything stays orange on a white word. Each is roughly symmetric, and still with motion off.

## 5. Settings

- **Scroll assistant:**
  - The explanation under the switch goes.
  - The default follows the phone's own Scroll assistant (`persist.sys.scroll_assistant`): on if the phone's is on (Owner).
- **Orange accents** must apply everywhere the setting says it does (the owner's bug report).
