package com.atlas.keyboard

import android.view.KeyEvent
import com.atlas.keyboard.Modifiers.Level
import com.atlas.keyboard.Modifiers.Panel

private const val SHIFT = KeyEvent.KEYCODE_SHIFT_LEFT
private const val OTHER_SHIFT = KeyEvent.KEYCODE_SHIFT_RIGHT
private const val ALT = KeyEvent.KEYCODE_ALT_LEFT
private const val SYM = KeyEvent.KEYCODE_SYM
private const val LETTER = KeyEvent.KEYCODE_A
private const val CTRL = KeyEvent.KEYCODE_CTRL_LEFT
private const val OTHER_CTRL = KeyEvent.KEYCODE_CTRL_RIGHT
private const val FN = KeyEvent.KEYCODE_FUNCTION

/** How long an ordinary press stays down. */
private const val PRESS = 80L
/** The time between the two presses of a double press. */
private const val GAP = 100L
/** Long enough to type a word or draw a swipe. */
private const val WORD = 900L

fun main() {
    onePress()
    slowOnePress()
    holdAndType()
    quickHoldAndType()
    holdAndSwipe()
    holdLimit()
    pressThenHoldAndType()
    pressThenHoldAndSwipe()
    doublePress()
    doublePressAndTypeQuickly()
    doublePressDoesNotLockWhenOff()
    lateSecondPress()
    realHoldThenType()
    realRolledRelease()
    realSinglePress()
    realDoublePress()
    realLongHoldWithRepeats()
    lostRelease()
    discardedKeys()
    lockedThenHold()
    bothShiftKeys()
    shortcutWhileHeld()
    alt()
    symCycle()
    symHasNoLock()
    panelIsAToggle()
    panelStaysWhileTyping()
    closingThePanel()
    panelAndOtherModifiers()
    altOpensQuickSettings()
    altGoesBackFromQuickSettings()
    otherKeysLeaveQuickSettings()
    quickSettingsHoldNoStickyAlt()
    quickSettingsTable()
    panelScrollKeys()
    restingOnP()
    bottomRow()
    onlyOneModifier()
    repeatsAndStrayEvents()
    ctrlOverlayWhileHeld()
    quickShortcutDoesNotFlash()
    keyMakesTheOverlayWaitAgain()
    anotherShortcutCanFollow()
    ctrlIsNotAToggle()
    ctrlCoversTheSymPanel()
    ctrlLeavesStickyKeysAlone()
    bothCtrlKeys()
    fnIsNotCtrlByDefault()
    fnAsCtrl()
    fnAndKeyMapperAreOneCtrl()
    closingTheCtrlOverlay()
    clearingCtrl()
    ctrlThroughAFieldStart()
    ctrlMetaState()
    ctrlActions()
    println("Modifier checks passed")
}

private fun same(actual: Any?, expected: Any?, message: String) {
    if (actual != expected) throw AssertionError("$message\n  expected: $expected\n  actual:   $actual")
}

/** Plays key events into one [Modifiers], with a clock that moves forward between them. */
private class Keyboard {
    val modifiers = Modifiers().apply { doublePressLocks = true }
    private var now = 1_000L

    val shift get() = modifiers.shift
    val overlay get() = modifiers.ctrlOverlay

    fun down(code: Int = SHIFT) = modifiers.down(code, now)

    fun up(code: Int = SHIFT, after: Long = PRESS) {
        idle(after)
        modifiers.up(code, now)
    }

    /** Time passes, and the keyboard's timer for the Ctrl overlay runs if it has come due. */
    fun idle(millis: Long) {
        now += millis
        modifiers.ctrlWaitOver(now)
    }

    /** Pushes a key while Ctrl is down, such as the C of a shortcut. */
    fun push() = modifiers.keyWithCtrl(now)

    /** Holds [code] until the Ctrl overlay shows. */
    fun holdForOverlay(code: Int = CTRL) {
        down(code)
        idle(Modifiers.CTRL_OVERLAY_MILLIS)
    }

    fun press(code: Int = SHIFT) {
        down(code)
        up(code)
    }

    /** Characters or a finished swipe, which all use the modifiers in the same way. */
    fun type(count: Int = 1) = repeat(count) { modifiers.consume() }
}

private fun onePress() = Keyboard().run {
    press()
    same(shift, Level.Once, "One press must stay on after the key is let go")
    type()
    same(shift, Level.Off, "One press must capitalise only the next character")
    type()
    same(shift, Level.Off, "One press must not come back")
}

private fun slowOnePress() = Keyboard().run {
    down()
    up(after = Modifiers.HOLD_MILLIS - 1)
    same(shift, Level.Once, "A press just short of a hold is still one press")
}

private fun holdAndType() = Keyboard().run {
    down()
    repeat(5) {
        type()
        same(shift, Level.Once, "Shift must stay on for each character while it is held")
        idle(120)
    }
    up()
    same(shift, Level.Off, "Holding Shift for a word must leave the next word normal")
}

private fun quickHoldAndType() = Keyboard().run {
    down()
    type()
    up(after = 60)
    same(shift, Level.Off, "A short hold for one character must also end with the key")
}

private fun holdAndSwipe() = Keyboard().run {
    // Contact drops a swipe made while a key is held, so nothing uses the Shift.
    down()
    up(after = WORD)
    same(shift, Level.Off, "A swipe dropped during a hold must not leave Shift on")
}

private fun holdLimit() = Keyboard().run {
    down()
    up(after = Modifiers.HOLD_MILLIS)
    same(shift, Level.Off, "Shift down for the full hold time counts as held")
}

private fun pressThenHoldAndType() = Keyboard().run {
    press()
    idle(GAP)
    down()
    same(shift, Level.Locked, "A second press in time locks at first")
    type(5)
    same(shift, Level.Locked, "Shift must stay on for the whole word")
    up(after = WORD)
    same(shift, Level.Off, "A press and then a hold must not stay locked after a word")
}

private fun pressThenHoldAndSwipe() = Keyboard().run {
    press()
    idle(GAP)
    down()
    up(after = WORD)
    same(shift, Level.Off, "A press and then a hold must not stay locked after a dropped swipe")
}

private fun doublePress() = Keyboard().run {
    press()
    idle(GAP)
    press()
    same(shift, Level.Locked, "A double press must lock")
    type(12)
    same(shift, Level.Locked, "A lock must stay on while typing")
    idle(WORD)
    press()
    same(shift, Level.Off, "A third press must unlock")
}

private fun doublePressDoesNotLockWhenOff() = Keyboard().run {
    same(Modifiers().doublePressLocks, false, "A double press must not lock by default")
    modifiers.doublePressLocks = false
    press()
    same(shift, Level.Once, "One press must still capitalise the next character")
    idle(GAP)
    press()
    same(shift, Level.Off, "A quick second press must only turn Shift off")
    idle(GAP)
    press()
    same(shift, Level.Once, "A third press must start again")
    type()
    same(shift, Level.Off, "That press must capitalise only the next character")
    idle(WORD)
    press()
    idle(GAP)
    down()
    type(5)
    up(after = WORD)
    same(shift, Level.Off, "A press and then a hold must leave Shift off")
    idle(WORD)
    press(KeyEvent.KEYCODE_ALT_LEFT)
    idle(GAP)
    press(KeyEvent.KEYCODE_ALT_LEFT)
    same(modifiers.alt, Level.Off, "A quick second press must only turn Alt off")
}

private fun doublePressAndTypeQuickly() = Keyboard().run {
    // Fast typists press the next letter before the second press is fully let go.
    press()
    idle(GAP)
    down()
    idle(30)
    type()
    up(after = 50)
    same(shift, Level.Locked, "A letter that overlaps a double press must not undo the lock")
    type(3)
    same(shift, Level.Locked, "The lock must stay on after the overlap")
}

private fun lateSecondPress() = Keyboard().run {
    press()
    idle(Modifiers.DOUBLE_PRESS_MILLIS)
    press()
    same(shift, Level.Off, "A second press after the double press time must turn Shift off")
}

// The orders and times below come from real key presses on the phone. A letter is typed when its key goes down, and its
// release does not reach Modifiers, so only a letter's press appears here.

private fun realHoldThenType() = Keyboard().run {
    modifiers.doublePressLocks = false
    down()
    idle(107)
    type()
    same(shift, Level.Once, "Shift must stay on while it is held")
    idle(128)
    up(after = 121)
    same(shift, Level.Off, "After Shift is let go the next character must be normal")
    idle(240)
    type()
    same(shift, Level.Off, "Typing the next character must not turn Shift on again")
}

private fun realRolledRelease() = Keyboard().run {
    modifiers.doublePressLocks = false
    down()
    idle(56)
    type()
    // The letter's own release comes after Shift's, which Modifiers does not see.
    up(after = 113)
    same(shift, Level.Off, "Shift let go before the letter must still end with the key")
    idle(240)
    type()
    same(shift, Level.Off, "The next character after a rolled release must be normal")
}

private fun realSinglePress() = Keyboard().run {
    modifiers.doublePressLocks = false
    down()
    up(after = 76)
    same(shift, Level.Once, "One real press must stay on for the next character")
    idle(160)
    type()
    same(shift, Level.Off, "One real press must capitalise only the next character")
    idle(250)
    type()
    same(shift, Level.Off, "One real press must not come back")
}

private fun realDoublePress() = Keyboard().run {
    same(modifiers.doublePressLocks, true, "This helper starts with the setting on")
    modifiers.doublePressLocks = false
    down()
    up(after = 62)
    idle(110)
    down()
    up(after = 82)
    same(shift, Level.Off, "With the setting off, two quick real presses must leave Shift off")
    idle(120)
    repeat(5) {
        type()
        same(shift, Level.Off, "Each letter after two quick presses must be lowercase")
        idle(120)
    }
}

private fun realLongHoldWithRepeats() = Keyboard().run {
    modifiers.doublePressLocks = false
    down()
    // The key's repeats start after 400 ms and do not reach down, because Modifiers.key drops them.
    idle(1165)
    type()
    idle(100)
    up(after = 200)
    same(shift, Level.Off, "A long hold with repeats must end with the key")
}

private fun lostRelease() = Keyboard().run {
    modifiers.doublePressLocks = false
    down()
    type()
    // The release never reached the keyboard, so this press is the next one.
    idle(2000)
    down()
    same(shift, Level.Once, "A press after a lost release must count as a new press")
    same(modifiers.isHeld(SHIFT), true, "The new press must still be held")
    up(after = 60)
    same(shift, Level.Once, "One press after a lost release must stay on for the next character")
    type()
    same(shift, Level.Off, "That press must capitalise only the next character")
}

private fun discardedKeys() {
    same(Modifiers.survivesDiscard(SHIFT, down = true), true, "A Shift press must survive when typing is dropped")
    same(Modifiers.survivesDiscard(ALT, down = true), true, "An Alt press must survive when typing is dropped")
    same(Modifiers.survivesDiscard(SYM, down = true), true, "A Sym press must survive when typing is dropped")
    same(Modifiers.survivesDiscard(LETTER, down = true), false, "A letter press must go when typing is dropped")
    same(Modifiers.survivesDiscard(LETTER, down = false), true, "A release must always survive")
    same(Modifiers.survivesDiscard(SHIFT, down = false), true, "A Shift release must always survive")
}

private fun lockedThenHold() = Keyboard().run {
    press()
    idle(GAP)
    press()
    idle(WORD)
    down()
    same(shift, Level.Off, "Pressing a locked Shift must unlock it at once")
    type(5)
    up(after = WORD)
    same(shift, Level.Off, "A hold that unlocks must not turn Shift back on")
}

private fun bothShiftKeys() = Keyboard().run {
    down(SHIFT)
    down(OTHER_SHIFT)
    type(5)
    up(OTHER_SHIFT, after = WORD)
    same(shift, Level.Locked, "Shift stays on while the other Shift key is held")
    up(SHIFT)
    same(shift, Level.Off, "Shift ends with the last Shift key")
}

private fun shortcutWhileHeld() = Keyboard().run {
    down()
    modifiers.useHeld()
    same(shift, Level.Once, "A shortcut leaves Shift on while it is held")
    up(after = 60)
    same(shift, Level.Off, "A shortcut uses the held Shift")
}

private fun alt() {
    Keyboard().run {
        down(ALT)
        up(ALT, after = WORD)
        same(modifiers.alt, Level.Once, "A long press of Alt must stay on for the next key")
        type()
        same(modifiers.alt, Level.Off, "One press of Alt must end after one character")
    }
    Keyboard().run {
        down(ALT)
        type()
        up(ALT)
        same(modifiers.alt, Level.Off, "Alt held for a character must end with the key")
    }
    Keyboard().run {
        press(ALT)
        idle(GAP)
        down(ALT)
        type()
        up(ALT, after = WORD)
        same(modifiers.alt, Level.Locked, "A double press of Alt must lock even when held")
    }
}

private fun symCycle() = Keyboard().run {
    same(modifiers.panel, Panel.Closed, "The panel must start closed")
    press(SYM)
    same(modifiers.panel, Panel.Symbols, "The first press of Sym must show symbols")
    idle(WORD)
    press(SYM)
    same(modifiers.panel, Panel.Emoji, "The second press of Sym must show emoji")
    idle(WORD)
    press(SYM)
    same(modifiers.panel, Panel.Closed, "The third press of Sym must go back to normal typing")
    idle(WORD)
    press(SYM)
    same(modifiers.panel, Panel.Symbols, "The cycle must start again")
}

private fun symHasNoLock() = Keyboard().run {
    press(SYM)
    idle(GAP)
    press(SYM)
    same(modifiers.panel, Panel.Emoji, "A quick second press of Sym must move on, not lock")
    idle(GAP)
    press(SYM)
    same(modifiers.panel, Panel.Closed, "A quick third press of Sym must close the panel")
}

private fun panelIsAToggle() {
    Keyboard().run {
        down(SYM)
        same(modifiers.panel, Panel.Symbols, "The panel must open when Sym goes down")
        same(modifiers.isHeld(SYM), true, "Sym must count as held while it is down")
        up(SYM, after = WORD)
        same(modifiers.panel, Panel.Symbols, "The panel must stay open after Sym is let go")
        same(modifiers.isHeld(SYM), false, "Sym must not count as held after it is let go")
    }
    Keyboard().run {
        down(SYM)
        down(SYM)
        same(modifiers.panel, Panel.Symbols, "A second down without an up must not move the panel on")
    }
    Keyboard().run {
        press(SYM)
        up(SYM, after = WORD)
        same(modifiers.panel, Panel.Symbols, "An up without a down must change nothing")
    }
}

private fun panelStaysWhileTyping() = Keyboard().run {
    press(SYM)
    type(3)
    same(modifiers.panel, Panel.Symbols, "Only a pick closes the panel, not other typing")
    down(SYM)
    type()
    up(SYM)
    same(modifiers.panel, Panel.Emoji, "Holding Sym while typing must not close the panel")
}

private fun closingThePanel() {
    Keyboard().run {
        press(SYM)
        idle(WORD)
        press(SYM)
        modifiers.closePanel()
        same(modifiers.panel, Panel.Closed, "A pick must go back to normal typing")
        idle(WORD)
        press(SYM)
        same(modifiers.panel, Panel.Symbols, "After a pick the cycle must start at symbols")
    }
    Keyboard().run {
        press(SYM)
        modifiers.clear()
        same(modifiers.panel, Panel.Closed, "Clear must close the panel")
        down(SYM)
        same(modifiers.panel, Panel.Symbols, "Clear must forget that Sym was down")
    }
}

private fun panelAndOtherModifiers() {
    Keyboard().run {
        press(SHIFT)
        idle(WORD)
        press(SYM)
        same(shift, Level.Off, "Sym must turn Shift off")
        same(modifiers.panel, Panel.Symbols, "Sym must open the panel with Shift on")
    }
    Keyboard().run {
        press(ALT)
        idle(WORD)
        press(SYM)
        same(modifiers.alt, Level.Off, "Sym must turn Alt off")
    }
    Keyboard().run {
        press(SYM)
        idle(WORD)
        press(SHIFT)
        same(modifiers.panel, Panel.Closed, "Shift must close the panel")
        same(shift, Level.Once, "Shift must be on after it closes the panel")
    }
    Keyboard().run {
        down(SYM)
        press(SHIFT)
        type()
        same(shift, Level.Off, "A held Sym must not keep one press of Shift on")
    }
}

private fun altOpensQuickSettings() {
    Keyboard().run {
        press(SYM)
        idle(WORD)
        press(ALT)
        same(modifiers.panel, Panel.Settings, "Alt must open the quick settings from the symbols")
        same(modifiers.alt, Level.Off, "Alt must not turn on when it opens the quick settings")
    }
    Keyboard().run {
        press(SYM)
        idle(WORD)
        press(SYM)
        idle(WORD)
        press(ALT)
        same(modifiers.panel, Panel.Settings, "Alt must open the quick settings from the emoji")
    }
    Keyboard().run {
        press(ALT)
        same(modifiers.panel, Panel.Closed, "Alt must not open the panel when the panel is closed")
        same(modifiers.alt, Level.Once, "Alt must still turn on when the panel is closed")
    }
    Keyboard().run {
        press(SYM)
        modifiers.toggleSettings()
        same(modifiers.panel, Panel.Settings, "The Settings key on the screen must do what Alt does")
        modifiers.closePanel()
        modifiers.toggleSettings()
        same(modifiers.panel, Panel.Closed, "The Settings key must do nothing when the panel is closed")
    }
}

private fun altGoesBackFromQuickSettings() {
    Keyboard().run {
        press(SYM)
        idle(WORD)
        press(ALT)
        idle(WORD)
        press(ALT)
        same(modifiers.panel, Panel.Symbols, "A second Alt must go back to the symbols")
        same(modifiers.alt, Level.Off, "A second Alt must not turn Alt on")
    }
    Keyboard().run {
        press(SYM)
        idle(WORD)
        press(SYM)
        idle(WORD)
        press(ALT)
        idle(WORD)
        press(ALT)
        same(modifiers.panel, Panel.Emoji, "A second Alt must go back to the emoji")
    }
    Keyboard().run {
        press(SYM)
        idle(WORD)
        press(ALT)
        idle(GAP)
        press(ALT)
        idle(GAP)
        press(ALT)
        same(modifiers.panel, Panel.Settings, "A quick third Alt must open the quick settings again, and must not lock Alt")
        same(modifiers.alt, Level.Off, "Quick Alt pushes must not turn Alt on while the panel is open")
    }
}

private fun otherKeysLeaveQuickSettings() {
    Keyboard().run {
        press(SYM)
        idle(WORD)
        press(ALT)
        idle(WORD)
        press(SYM)
        same(modifiers.panel, Panel.Closed, "Sym must close the panel from the quick settings")
        idle(WORD)
        press(SYM)
        same(modifiers.panel, Panel.Symbols, "After that the cycle must start at the symbols")
    }
    Keyboard().run {
        press(SYM)
        idle(WORD)
        press(ALT)
        idle(WORD)
        press(SHIFT)
        same(modifiers.panel, Panel.Closed, "Shift must close the panel from the quick settings")
        same(shift, Level.Once, "Shift must be on after it closes the quick settings")
    }
    Keyboard().run {
        press(SYM)
        idle(WORD)
        press(ALT)
        modifiers.closePanel()
        same(modifiers.panel, Panel.Closed, "A push on the quick settings page must go back to normal typing")
    }
    Keyboard().run {
        press(SYM)
        idle(WORD)
        press(ALT)
        modifiers.clear()
        same(modifiers.panel, Panel.Closed, "A new field must close the quick settings")
    }
    Keyboard().run {
        press(SYM)
        idle(WORD)
        press(ALT)
        holdForOverlay()
        same(overlay, true, "The Ctrl overlay must show over the quick settings")
        same(modifiers.panel, Panel.Settings, "The quick settings must wait under the Ctrl overlay")
        up(CTRL)
        same(modifiers.panel, Panel.Settings, "The quick settings must show again when Ctrl is let go")
    }
}

private fun quickSettingsHoldNoStickyAlt() = Keyboard().run {
    press(SYM)
    idle(WORD)
    down(ALT)
    same(modifiers.panel, Panel.Settings, "The page must open when Alt goes down")
    modifiers.closePanel()
    up(ALT)
    same(modifiers.alt, Level.Off, "Alt let go after a push on the page must leave Alt off")
    idle(WORD)
    press(ALT)
    same(modifiers.alt, Level.Once, "The next Alt must be a normal Alt")
    same(modifiers.panel, Panel.Closed, "The next Alt must not open the panel")
}

private fun quickSettingsTable() {
    val expected = mapOf(
        "A" to QuickSetting.Autocorrect, "S" to QuickSetting.SwipeTyping, "W" to QuickSetting.SuggestionBar,
        "C" to QuickSetting.AutoCapitalise, "P" to QuickSetting.AutoPunctuation, "V" to QuickSetting.Vibration,
        "H" to QuickSetting.HoldSymbols, "M" to QuickSetting.CursorControl, "L" to QuickSetting.DoublePressLocks,
        "T" to QuickSetting.TouchpadOnlyInFields, "O" to QuickSetting.OpenSettings, "K" to QuickSetting.ChangeKeyboard,
    )
    same(QuickSetting.entries.associateBy { it.key }, expected, "Each item must be on the letter it is named after, and on one key only")
    same(QuickSetting.on("A"), QuickSetting.Autocorrect, "A letter must find its item")
    same(QuickSetting.on("Q"), null, "A key with no item must find nothing")
    same(QuickSetting.on(Layout.ALT), null, "The Alt place has no setting")
    for (item in QuickSetting.entries) {
        same(item.key in Layout.panelLetters, true, "${item.title} must be on a letter key of the panel")
        same(item.label.length <= 5 && '\n' !in item.label, true, "${item.title} must have a short label on one line")
        same(item.title.isNotBlank() && item.title.length >= item.label.length, true, "${item.title} must have its full name")
    }
}

private fun panelScrollKeys() {
    same(Modifiers.panelScroll(KeyEvent.KEYCODE_DEL), -1f, "Backspace must do what a swipe up does")
    same(Modifiers.panelScroll(KeyEvent.KEYCODE_ENTER), 1f, "Enter must do what a swipe down does")
    same(Modifiers.panelScroll(KeyEvent.KEYCODE_NUMPAD_ENTER), 1f, "The other Enter key must do the same")
    same(Modifiers.panelScroll(LETTER), null, "A letter must not turn the page")
    same(Modifiers.panelScroll(KeyEvent.KEYCODE_SPACE), null, "Space must not turn the page")
}

private fun restingOnP() {
    same(Layout.restingKeyAt(.95f, .15f), "P", "The middle of P must be P")
    same(Layout.restingKeyAt(.88f, .15f), "P", "A finger at the left of P, read at the right of O, must be P")
    same(Layout.restingKeyAt(.86f, .15f), "O", "A finger on O must still be O")
    same(Layout.restingKeyAt(.95f, .28f), "P", "A finger low on P, read at the top of Backspace, must be P")
    same(Layout.restingKeyAt(.95f, .4f), "⌫", "A finger in the middle of Backspace must stay Backspace")
    same(Layout.restingKeyAt(.05f, .15f), "Q", "Other keys must not change")
    same(Layout.restingKeyAt(.88f, .4f), "L", "The key under O must not change")
    same(Layout.restingKeyAt(.45f, .85f), Layout.SPACE, "The bottom row must not change")
    same(Layout.restingKeyAt(Float.NaN, .15f), "", "A place off the keyboard must be no key")
}

private fun bottomRow() {
    same(Layout.isBottomRow(.74f), false, "The last letter row must not be the bottom row")
    same(Layout.isBottomRow(.75f), true, "The bottom row must start at three quarters of the height")
    same(Layout.isBottomRow(1f), true, "The bottom edge is on the bottom row")
    same(Layout.keyAt(.5f, .8f), Layout.SPACE, "Space must be on the bottom row")
    val space = Layout.bottomSpan(Layout.SPACE)
    same(kotlin.math.abs(space.first - .36f) < 1e-5f, true, "Space must start at its place on the row")
    same(kotlin.math.abs(space.second - .64f) < 1e-5f, true, "Space must end at its place on the row")
    val fn = Layout.bottomSpan(Layout.FN)
    same(kotlin.math.abs(fn.first - .76f) < 1e-5f && kotlin.math.abs(fn.second - .88f) < 1e-5f, true, "fn must cover its place on the row")
}

private fun onlyOneModifier() = Keyboard().run {
    press(SHIFT)
    idle(WORD)
    press(ALT)
    same(shift, Level.Off, "Alt must turn Shift off")
    same(modifiers.alt, Level.Once, "Alt must be on")
}

private fun repeatsAndStrayEvents() {
    Keyboard().run {
        down()
        down()
        same(shift, Level.Once, "A second down without an up must not count as a double press")
    }
    Keyboard().run {
        press()
        up(after = WORD)
        same(shift, Level.Once, "An up without a down must change nothing")
    }
    Keyboard().run {
        down()
        same(modifiers.isHeld(SHIFT), true, "Shift must count as held while it is down")
        modifiers.clear()
        same(modifiers.isHeld(SHIFT), false, "Clear must forget held keys")
        up(after = WORD)
        same(shift, Level.Off, "An up after clear must change nothing")
    }
    Keyboard().run {
        down(LETTER)
        same(modifiers.isHeld(LETTER), false, "A letter is not a modifier")
        same(shift, Level.Off, "A letter must not turn Shift on")
        same(Modifiers.isModifier(SHIFT) && Modifiers.isModifier(ALT) && Modifiers.isModifier(SYM), true, "Shift, Alt and Sym are modifiers")
        same(Modifiers.isModifier(LETTER), false, "A letter is not a modifier")
    }
}

private fun ctrlOverlayWhileHeld() = Keyboard().run {
    down(CTRL)
    same(modifiers.ctrlHeld, true, "Ctrl must count as held while it is down")
    idle(Modifiers.CTRL_OVERLAY_MILLIS - 1)
    same(overlay, false, "The overlay must wait for the full time")
    idle(1)
    same(overlay, true, "The overlay must show when Ctrl has been held for the full time")
    same(modifiers.ctrlOverlayAt, null, "The overlay must have no timer once it shows")
    idle(WORD)
    same(overlay, true, "The overlay must stay for as long as Ctrl is down")
    up(CTRL)
    same(overlay, false, "The overlay must close when Ctrl is let go")
    same(modifiers.ctrlHeld, false, "Ctrl must not stay on after it is let go")
}

private fun quickShortcutDoesNotFlash() = Keyboard().run {
    // A quick Ctrl + C: Ctrl is let go 240 ms after it went down, which is longer than the overlay's wait.
    down(CTRL)
    idle(80)
    push()
    idle(160)
    same(overlay, false, "A quick shortcut must not flash the overlay")
    up(CTRL, after = 0)
    same(overlay, false, "The overlay must stay closed after a quick shortcut")
    same(modifiers.ctrlOverlayAt, null, "Letting go of Ctrl must end the wait")
    idle(WORD)
    same(overlay, false, "The overlay must not show after Ctrl is let go")
}

private fun keyMakesTheOverlayWaitAgain() = Keyboard().run {
    down(CTRL)
    idle(100)
    push()
    idle(Modifiers.CTRL_OVERLAY_MILLIS - 1)
    same(overlay, false, "A key pushed before the overlay shows must start the wait again")
    idle(1)
    same(overlay, true, "The overlay must show if Ctrl stays down after the key")
}

private fun anotherShortcutCanFollow() = Keyboard().run {
    holdForOverlay()
    repeat(3) {
        push()
        idle(WORD)
        same(overlay, true, "The overlay must stay after a shortcut, so another can follow")
    }
    up(CTRL)
    same(overlay, false, "Only letting go of Ctrl closes the overlay")
}

private fun ctrlIsNotAToggle() = Keyboard().run {
    press(CTRL)
    idle(WORD)
    same(overlay, false, "A press of Ctrl must not open the overlay")
    same(modifiers.ctrlHeld, false, "A press of Ctrl must not stay on")
    holdForOverlay()
    up(CTRL)
    down(CTRL)
    same(overlay, false, "A second press must start a new wait, not act as a toggle")
    idle(Modifiers.CTRL_OVERLAY_MILLIS)
    same(overlay, true, "A second hold must show the overlay again")
}

private fun ctrlCoversTheSymPanel() {
    Keyboard().run {
        press(SYM)
        idle(WORD)
        holdForOverlay()
        same(overlay, true, "The overlay must show over the Sym panel")
        same(modifiers.panel, Panel.Symbols, "The Sym panel must wait under the overlay")
        up(CTRL)
        same(overlay, false, "The overlay must close when Ctrl is let go")
        same(modifiers.panel, Panel.Symbols, "The Sym panel must show again when Ctrl is let go")
    }
    Keyboard().run {
        holdForOverlay()
        press(SYM)
        same(overlay, true, "Sym must not close the overlay while Ctrl is down")
    }
}

private fun ctrlLeavesStickyKeysAlone() {
    Keyboard().run {
        press(SHIFT)
        idle(WORD)
        press(CTRL)
        same(shift, Level.Once, "Ctrl must leave Shift on")
    }
    Keyboard().run {
        press(ALT)
        idle(WORD)
        press(CTRL)
        same(modifiers.alt, Level.Once, "Ctrl must leave Alt on")
    }
}

private fun bothCtrlKeys() = Keyboard().run {
    holdForOverlay(OTHER_CTRL)
    same(overlay, true, "Right Ctrl must also show the overlay")
    down(CTRL)
    up(OTHER_CTRL)
    same(overlay, true, "The overlay must stay while the other Ctrl key is down")
    up(CTRL)
    same(overlay, false, "The overlay must close with the last Ctrl key")
}

private fun fnIsNotCtrlByDefault() = Keyboard().run {
    same(modifiers.fnIsCtrl, false, "fn must not be Ctrl until the user turns the setting on")
    same(modifiers.isCtrl(FN), false, "With the setting off, fn must not count as Ctrl")
    down(FN)
    idle(WORD)
    same(modifiers.ctrlHeld, false, "With the setting off, fn must not hold Ctrl")
    same(overlay, false, "With the setting off, fn must not open the overlay")
    holdForOverlay()
    same(overlay, true, "With the setting off, the overlay must open for Key Mapper's Ctrl")
}

private fun fnAsCtrl() = Keyboard().run {
    modifiers.fnIsCtrl = true
    same(modifiers.isCtrl(FN), true, "With the setting on, fn must count as Ctrl")
    same(Modifiers.isModifier(FN), false, "fn must not join the sticky modifiers")
    holdForOverlay(FN)
    same(overlay, true, "With the setting on, holding fn must show the overlay")
    same(shift, Level.Off, "fn as Ctrl must not turn Shift on")
    up(FN)
    same(overlay, false, "With the setting on, letting go of fn must close the overlay")
    same(modifiers.ctrlHeld, false, "With the setting on, fn must not stay on")
}

private fun fnAndKeyMapperAreOneCtrl() {
    Keyboard().run {
        // If Key Mapper leaves fn in place, the keyboard hears both fn and Key Mapper's Ctrl.
        modifiers.fnIsCtrl = true
        down(FN)
        idle(150)
        down(CTRL)
        idle(50)
        same(overlay, true, "A second Ctrl must not start the wait again")
        up(FN)
        same(overlay, true, "The overlay must stay while Key Mapper's Ctrl is down")
        same(modifiers.ctrlHeld, true, "Ctrl must stay held while Key Mapper's Ctrl is down")
        up(CTRL)
        same(overlay, false, "The overlay must close when both are let go")
        same(modifiers.ctrlHeld, false, "Ctrl must end when both are let go")
    }
    Keyboard().run {
        modifiers.fnIsCtrl = true
        holdForOverlay(CTRL)
        down(FN)
        up(CTRL)
        up(FN)
        same(modifiers.ctrlHeld, false, "The order of release must not leave Ctrl on")
    }
}

private fun closingTheCtrlOverlay() = Keyboard().run {
    holdForOverlay()
    modifiers.closePanel()
    same(overlay, false, "Hiding the keyboard must close the overlay")
    idle(WORD)
    same(overlay, false, "The overlay must stay closed for the rest of this hold")
    same(modifiers.ctrlHeld, true, "Closing the overlay must not change what is physically held")
    up(CTRL)
    holdForOverlay()
    same(overlay, true, "The next hold must show the overlay")
}

private fun clearingCtrl() {
    Keyboard().run {
        holdForOverlay()
        modifiers.clear()
        same(overlay, false, "Clear must close the overlay")
        same(modifiers.ctrlHeld, false, "Clear must forget Ctrl")
        up(CTRL, after = WORD)
        same(overlay, false, "An up after clear must change nothing")
    }
    Keyboard().run {
        down(CTRL)
        modifiers.clear()
        idle(WORD)
        same(overlay, false, "Clear must end the wait")
    }
    Keyboard().run {
        holdForOverlay()
        modifiers.clear(keepCtrl = true)
        same(overlay, true, "A field that restarts must leave the overlay open")
        same(modifiers.ctrlHeld, true, "A field that restarts must leave Ctrl held")
        up(CTRL)
        same(overlay, false, "Letting go of Ctrl must still close the overlay after a restart")
    }
    Keyboard().run {
        down(CTRL)
        modifiers.clear(keepCtrl = true)
        idle(Modifiers.CTRL_OVERLAY_MILLIS)
        same(overlay, true, "A field that restarts must not end the wait")
    }
}

private fun ctrlThroughAFieldStart() {
    Keyboard().run {
        holdForOverlay()
        modifiers.fieldStarted(restarting = true, editing = true)
        same(overlay, true, "A restart of a field that takes text must leave the overlay open")
        same(modifiers.ctrlHeld, true, "A restart of a field that takes text must leave Ctrl held")
    }
    Keyboard().run {
        holdForOverlay()
        modifiers.fieldStarted(restarting = false, editing = true)
        same(overlay, false, "A new field must close the overlay")
        same(modifiers.ctrlHeld, false, "A new field must forget Ctrl")
    }
    Keyboard().run {
        holdForOverlay()
        modifiers.fieldStarted(restarting = true, editing = false)
        same(overlay, false, "A restart as a field that takes no text must close the overlay")
        same(modifiers.ctrlHeld, false, "A restart as a field that takes no text must forget Ctrl")
        // The keyboard does not hear Ctrl let go here. The field then comes back.
        modifiers.fieldStarted(restarting = true, editing = true)
        same(modifiers.ctrlHeld, false, "Ctrl must not stay on when the field takes text again")
    }
    Keyboard().run {
        modifiers.fnIsCtrl = true
        down(FN)
        modifiers.fieldStarted(restarting = true, editing = false)
        same(modifiers.ctrlHeld, false, "A restart as a field that takes no text must also forget fn as Ctrl")
    }
}

private fun ctrlMetaState() {
    val fromFn = Modifiers.withCtrl(KeyEvent.META_FUNCTION_ON or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON)
    same(fromFn and KeyEvent.META_CTRL_ON != 0, true, "A key pushed with fn as Ctrl must carry Ctrl")
    same(fromFn and KeyEvent.META_FUNCTION_ON != 0, false, "A key pushed with fn as Ctrl must not also carry fn")
    same(fromFn and KeyEvent.META_SHIFT_ON != 0, true, "Other modifiers must stay on the key")
    same(Modifiers.withCtrl(Modifiers.withCtrl(0)), Modifiers.withCtrl(0), "Ctrl from fn and Ctrl from Key Mapper must not add up")
}

private fun ctrlActions() {
    same(Modifiers.CTRL_ACTIONS["C"], "Copy", "C must copy")
    same(Modifiers.CTRL_ACTIONS.keys, setOf("A", "C", "X", "V", "Z", "Y"), "The overlay must show the field shortcuts and no other keys")
    same(Modifiers.CTRL_ACTIONS.keys.all { it.length == 1 && it[0] in 'A'..'Z' }, true, "Each shortcut must be on a letter key")
}
