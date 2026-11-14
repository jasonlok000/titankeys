package com.atlas.keyboard

import android.view.KeyEvent
import com.atlas.engine.EmojiPages
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
    aLostFirstBackspaceDecidesAfresh()
    tappedShiftOrCapsLeavesSteeringAndShortcutsAlone()
    heldShiftBackspaceDeletesWords()
    tappedOrAutomaticShiftLeavesBackspacePlain()
    heldShiftBackspaceAsTheOwnersPhoneSentIt()
    altBackspaceDeletesForward()
    altSymSwitchesTheKeyboard()
    altSymWithThePanelOpenIsAsBefore()
    theShortcutsSwitchOff()
    shortcutsActOncePerPress()
    droppedKeysUseTheirModifiers()
    onePress()
    aLongPushStaysOn()
    holdAndType()
    quickHoldAndType()
    pressThenHoldAndType()
    doublePress()
    doublePressAndTypeQuickly()
    doublePressDoesNotLockWhenOff()
    quickSecondPressIsTheSameForShiftAndAlt()
    slowSecondPressTurnsOffWhenLocksIsOff()
    lateSecondPress()
    realHoldThenType()
    realRolledRelease()
    realSinglePress()
    realDoublePress()
    realLongHoldWithRepeats()
    lostRelease()
    discardedKeys()
    takenKeys()
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
    symbolAndEmojiPages()
    aFingerOnBackOpensNoGroup()
    bottomRow()
    onlyOneModifier()
    repeatsAndStrayEvents()
    ctrlShowsInTheBarAtOnce()
    theShortcutsFitOneLine()
    stickyShiftAndCapsShowInTheBar()
    stickyShiftLeavesTheBarWhenItIsUsed()
    aHeldShiftShowsInTheBar()
    altShowsTheSymbolOfAKey()
    aHeldAltShowsInTheBar()
    theBarShowsEveryModifierThatIsOn()
    ctrlIsNotAToggle()
    ctrlLeavesTheSymPanelAlone()
    ctrlLeavesStickyKeysAlone()
    bothCtrlKeys()
    fnIsNotCtrlByDefault()
    fnAsCtrl()
    fnAndKeyMapperAreOneCtrl()
    closingThePanelLeavesCtrlHeld()
    clearingCtrl()
    ctrlThroughAFieldStart()
    ctrlMetaState()
    ctrlActions()
    steadyKeyIgnoresFlicker()
    cornersKeepContentClear()
    theGuideCanOpenAPanel()
    autoShift()
    chipsBesideTheWords()
    println("Modifier checks passed")
}

/** The interactive guide puts the keyboard in the menu that a step needs. */
private fun theGuideCanOpenAPanel() = Keyboard().run {
    press(SHIFT)
    modifiers.openPanel(Panel.Symbols)
    same(modifiers.panel, Panel.Symbols, "The guide can open the symbols")
    same(shift, Level.Off, "Opening the panel turns Shift off, as Sym does")
    modifiers.openPanel(Panel.Settings)
    modifiers.toggleSettings()
    same(modifiers.panel, Panel.Symbols, "From quick settings that the guide opened, Alt goes back to the symbols")
    modifiers.openPanel(Panel.Closed)
    same(modifiers.panel, Panel.Closed, "The guide can close the panel")
}

private fun steadyKeyIgnoresFlicker() {
    // The debug build's panel check names a key by its key code.
    same(Layout.keyForCode(KeyEvent.KEYCODE_A), "A", "Key code 29 is A")
    same(Layout.keyForCode(KeyEvent.KEYCODE_Z), "Z", "Key code 54 is Z")
    same(Layout.keyForCode(KeyEvent.KEYCODE_DEL), Layout.BACKSPACE, "67 is Backspace")
    same(Layout.keyForCode(KeyEvent.KEYCODE_ENTER), Layout.ENTER, "66 is Enter")
    same(Layout.keyForCode(KeyEvent.KEYCODE_SYM), Layout.SYM, "63 is Sym")
    same(Layout.keyForCode(KeyEvent.KEYCODE_VOLUME_UP), null, "A key the layout lacks is none")
    for (code in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) same(Layout.rows.any { Layout.keyForCode(code) in it }, true, "Every letter is on the layout")
    val steady = SteadyKey(Layout::keyAt)
    same(steady.touch(.15f, .15f), true, "A first touch must mark its key")
    same(steady.key, "W", "The first touch is on W")
    same(steady.touch(.201f, .15f), false, "A finger on the line between two keys must keep the key it had")
    same(steady.key, "W", "The mark must stay on W at the line")
    same(steady.touch(.25f, .15f), true, "A finger well inside the next key must move the mark")
    same(steady.key, "E", "The mark must move to E")
    steady.lift(1_000)
    same(steady.expire(1_100), false, "A short lift must keep the key, as a resting finger lifts often")
    same(steady.key, "E", "The key must stay through a short lift")
    same(steady.touch(.25f, .15f), false, "The finger coming back to the same key changes nothing")
    steady.lift(2_000)
    same(steady.expire(2_000 + SteadyKey.LIFT_MILLIS), true, "A finger that stays off must clear the key")
    same(steady.key, "", "No key must show after the finger has gone")

    // A key pushed under a resting finger, as on the quick settings: the touchpad reports a lift, then nothing until the key is up.
    steady.touch(.25f, .15f)
    steady.keyDown(45)
    steady.lift(3_000)
    same(steady.expire(3_000 + 5 * SteadyKey.LIFT_MILLIS), false, "While a key is down, the mark stays on the resting finger's key")
    same(steady.key, "E", "The marked key stays marked across the toggle")
    same(steady.keyUp(45, 4_000), true, "The last key let go")
    same(steady.touch(.25f, .15f), false, "The resting finger, reported again, keeps its key with no lift needed")
    same(steady.expire(4_000 + SteadyKey.LIFT_MILLIS), false, "and it stays")
    // With no finger left after the key, the mark goes as after any lift.
    steady.keyDown(45)
    steady.lift(5_000)
    steady.keyUp(45, 5_100)
    same(steady.expire(5_100 + SteadyKey.LIFT_MILLIS - 1), false, "Not before the usual wait")
    same(steady.expire(5_100 + SteadyKey.LIFT_MILLIS), true, "A finger that is gone after the key clears the mark")
    // Two keys: only the last one let go starts the wait.
    steady.touch(.25f, .15f)
    steady.keyDown(45); steady.keyDown(59)
    steady.lift(6_000)
    same(steady.keyUp(45, 6_100), false, "One of two keys let go")
    same(steady.expire(6_100 + 5 * SteadyKey.LIFT_MILLIS), false, "The mark stays while the other key is down")
    steady.keysReleased()
    same(steady.expire(6_100 + 6 * SteadyKey.LIFT_MILLIS), true, "When the keyboard hides, no key counts as down")
}

private fun cornersKeepContentClear() {
    val corners = Corners(100f)
    same(corners.inset(0f), 100, "At the bottom edge content must keep the whole radius from the side")
    same(corners.inset(50f), 14, "Halfway up the corner content must keep the width of the curve there")
    same(corners.inset(100f), 0, "Above the corner nothing must move")
    same(Corners.None.inset(0f), 0, "With square corners nothing must move")
}

private fun same(actual: Any?, expected: Any?, message: String) {
    if (actual != expected) throw AssertionError("$message\n  expected: $expected\n  actual:   $actual")
}

/** Plays key events into one [Modifiers], with a clock that moves forward between them. */
private class Keyboard {
    val modifiers = Modifiers().apply { doublePressLocks = true }
    private var now = 1_000L

    val shift get() = modifiers.shift

    fun down(code: Int = SHIFT) = modifiers.down(code, now)

    fun up(code: Int = SHIFT, after: Long = PRESS) {
        idle(after)
        modifiers.up(code, now)
    }

    fun idle(millis: Long) {
        now += millis
    }

    /** The modifier bar, with a finger on [hover]. Here Alt types % on Q and nothing on the other keys. */
    fun bar(hover: String = "") = ModifierBar.of(modifiers, hover) { key -> "%".takeIf { key == "Q" } }

    fun press(code: Int = SHIFT) {
        down(code)
        up(code)
    }

    /** Characters or a finished swipe, which all use the modifiers in the same way. */
    fun type(count: Int = 1) = repeat(count) { modifiers.consume() }

    /** What Backspace does, as the keyboard decides it: [meta] is the key's own meta state. */
    fun backspaceDoes(meta: Int = 0, altForward: Boolean = true, shiftWord: Boolean = true) =
        modifiers.backspaceDoes(meta, altForward, shiftWord).also { modifiers.afterBackspace() }

    /** Backspace as the keyboard handles it: true when it deletes a word. [ctrl] is the key's own Ctrl. */
    fun backspace(ctrl: Boolean = false) =
        (backspaceDoes(if (ctrl) KeyEvent.META_CTRL_ON else 0, altForward = false) == Modifiers.Backspace.Word)
}

/**
 * Bug hunt #4: in "hello world|", a tapped Shift then a steer selected text, and the next letter replaced it; Caps did it on every
 * steer and turned Ctrl + Z into redo. A steer or a shortcut takes no Shift that is only waiting for a letter.
 */
private fun tappedShiftOrCapsLeavesSteeringAndShortcutsAlone() {
    val shiftMeta = KeyEvent.META_SHIFT_MASK or KeyEvent.META_CAPS_LOCK_ON
    fun shifted(meta: Int) = meta and shiftMeta != 0
    Keyboard().run {
        press(SHIFT)
        same(shift, Level.Once, "A tap turns Shift on for one letter")
        same(shifted(modifiers.commandMeta(textKey = false, steer = true)), false, "A steer after a tapped Shift must not select")
        same(shift, Level.Once, "The steer leaves Shift on for the next letter")
        same(shifted(modifiers.commandMeta(textKey = true)), true, "The next letter still gets its capital")
        same(shifted(modifiers.commandMeta(textKey = false)), true, "A tapped Shift still goes with a shortcut, as Ctrl + Shift + Z")
    }
    Keyboard().run {
        press(SHIFT)
        idle(GAP)
        press(SHIFT)
        same(shift, Level.Locked, "A double press locks Shift")
        same(shifted(modifiers.commandMeta(textKey = false, steer = true)), false, "Caps must not turn a steer into a selection")
        same(shifted(modifiers.commandMeta(textKey = false)), false, "Caps must not turn Ctrl + Z into redo, nor Tab into Shift + Tab")
        same(shifted(modifiers.commandMeta(textKey = true)), true, "Caps still gives capitals")
    }
    Keyboard().run {
        down(SHIFT)
        idle(WORD)
        same(shifted(modifiers.commandMeta(textKey = false, steer = true)), true, "A Shift held down still selects as you steer")
        same(shifted(modifiers.commandMeta(textKey = false)), true, "A Shift held down still goes with a shortcut")
        up(SHIFT)
    }
    Keyboard().run {
        modifiers.autoShift(sentenceStart = true)
        same(shifted(modifiers.commandMeta(textKey = false)), false, "The automatic Shift still never goes with a command")
    }
}

/** Bug hunt #38: a Backspace whose first event was dropped went on with the last press's choice, a word or a forward delete. */
private fun aLostFirstBackspaceDecidesAfresh() = Keyboard().run {
    val alt = KeyEvent.META_ALT_ON
    same(modifiers.backspacePress(0, alt, altForward = true), Modifiers.Backspace.Forward, "Alt + Backspace deletes forward")
    same(modifiers.backspacePress(1, 0, altForward = true), Modifiers.Backspace.Forward, "Its repeats go on forward, Alt or not")
    modifiers.backspaceReleased()
    same(modifiers.backspacePress(1, 0, altForward = true), Modifiers.Backspace.Letter,
        "The next press, its first event lost, deletes a letter, not forward as the last press did")
    modifiers.backspaceReleased()
    same(modifiers.backspacePress(0, KeyEvent.META_CTRL_ON, altForward = true), Modifiers.Backspace.Word, "Ctrl + Backspace deletes a word")
    modifiers.backspaceReleased()
    same(modifiers.backspacePress(2, 0, altForward = true), Modifiers.Backspace.Letter, "Nor a word after a word delete")
}

private fun heldShiftBackspaceDeletesWords() = Keyboard().run {
    down(SHIFT)
    idle(WORD)
    same(backspace(), true, "Backspace with Shift held must delete a word")
    repeat(3) {
        idle(50)
        same(backspace(), true, "Holding Backspace with Shift held must go on deleting words")
    }
    up(SHIFT)
    same(shift, Level.Off, "Shift must go off when it's let go after deleting words")
    same(backspace(), false, "After Shift is let go, Backspace must delete one character again")
}

/**
 * Held Shift + Backspace in the events' real order, from the owner's typing log (2026-11-11, "In " then Shift held and
 * Backspace pressed): Shift down with Shift's meta set, Backspace down 32 ms later with Shift's meta, Backspace up, Shift up.
 * On build #5 that deleted one letter: Shift's own press never reached the modifiers, as when Key Mapper takes a Shift press
 * and sends its own (the same log has its injected Shift down with no meta and Enter with Shift's, at one instant). Backspace
 * still carries the held Shift in its meta state, which Android sets from the keys physically down.
 */
private fun heldShiftBackspaceAsTheOwnersPhoneSentIt() {
    val shiftHeld = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
    Keyboard().run {
        down(SHIFT)
        idle(32)
        same(backspaceDoes(shiftHeld), Modifiers.Backspace.Word, "Shift held, as logged, then Backspace must delete a word")
        up(SHIFT)
    }
    Keyboard().run {
        // Shift's own press is lost; only Backspace says Shift is down.
        same(backspaceDoes(shiftHeld), Modifiers.Backspace.Word, "A Backspace that says Shift is held must delete a word")
        idle(50)
        same(backspaceDoes(shiftHeld), Modifiers.Backspace.Word, "Its repeats must go on deleting words")
        same(shift, Level.Off, "It must not turn Shift on")
    }
    Keyboard().run {
        // Key Mapper's own tap of Shift arrives, then the held Shift's Backspace: still a word.
        press(SHIFT)
        same(backspaceDoes(shiftHeld), Modifiers.Backspace.Word, "A re-sent Shift tap must not stop a held Shift's word delete")
    }
    Keyboard().run {
        // A tapped Shift: Backspace comes with no Shift of its own, and stays a plain Backspace.
        press(SHIFT)
        same(backspaceDoes(0), Modifiers.Backspace.Letter, "A tapped Shift must leave Backspace a plain Backspace")
    }
}

/** Alt + Backspace, tapped or held, deletes forward with the setting on, and goes to the app with it off. A word delete wins. */
private fun altBackspaceDeletesForward() {
    val altHeld = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
    Keyboard().run {
        press(ALT)
        same(backspaceDoes(), Modifiers.Backspace.Forward, "A tapped Alt, then Backspace, must delete forward")
        same(modifiers.alt, Level.Off, "A forward delete must use a tapped Alt up")
        same(backspaceDoes(), Modifiers.Backspace.Letter, "The next Backspace must be plain again")
    }
    Keyboard().run {
        down(ALT)
        same(backspaceDoes(altHeld), Modifiers.Backspace.Forward, "Alt held with Backspace must delete forward")
        idle(50)
        same(backspaceDoes(altHeld), Modifiers.Backspace.Forward, "Holding them must go on deleting forward")
        up(ALT)
    }
    Keyboard().run {
        press(ALT)
        same(backspaceDoes(altForward = false), Modifiers.Backspace.AltToApp, "With the setting off, Alt + Backspace must go to the app")
        same(modifiers.alt, Level.Off, "Alt + Backspace to the app must still use Alt up")
    }
    Keyboard().run {
        down(ALT)
        down(SHIFT)
        same(backspaceDoes(altHeld or KeyEvent.META_SHIFT_ON), Modifiers.Backspace.Word, "Shift and Alt held: the word delete wins")
        same(backspaceDoes(altHeld or KeyEvent.META_CTRL_ON), Modifiers.Backspace.Word, "Ctrl and Alt: the word delete wins")
    }
}

private fun tappedOrAutomaticShiftLeavesBackspacePlain() {
    Keyboard().run {
        press(SHIFT)
        same(backspace(), false, "A tapped Shift must leave Backspace a plain Backspace")
        same(shift, Level.Once, "A plain Backspace must leave a tapped Shift on, as before")
    }
    Keyboard().run {
        modifiers.autoShift(sentenceStart = true)
        same(modifiers.autoShifted, true, "Shift must come on by itself at a sentence start")
        same(backspace(), false, "An automatic Shift must leave Backspace a plain Backspace")
    }
    Keyboard().run {
        same(backspace(ctrl = true), true, "Ctrl + Backspace must still delete a word")
        press(ALT)
        same(backspace(), false, "Alt, then Backspace is not a TitanKeys word delete")
        same(modifiers.alt, Level.Off, "Alt + Backspace must use Alt up")
    }
}

private fun altSymSwitchesTheKeyboard() {
    Keyboard().run {
        down(ALT)
        idle(GAP)
        press(SYM)
        same(modifiers.takeKeyboardSwitch(), true, "Sym with Alt held must switch to the next keyboard")
        same(modifiers.takeKeyboardSwitch(), false, "One push of Sym must switch only once")
        same(modifiers.panel, Panel.Closed, "Alt + Sym must not open the Sym panel")
        up(ALT)
        same(modifiers.alt, Level.Off, "Alt must count as used by Alt + Sym, so it goes off")
        idle(WORD)
        press(SYM)
        same(modifiers.panel, Panel.Symbols, "Sym alone must open the panel again afterwards")
        same(modifiers.takeKeyboardSwitch(), false, "Sym alone must not switch keyboards")
    }
    Keyboard().run {
        press(ALT)
        idle(WORD)
        press(SYM)
        same(modifiers.takeKeyboardSwitch(), false, "A tapped Alt, then Sym, must not switch keyboards")
        same(modifiers.panel, Panel.Symbols, "A tapped Alt, then Sym, must open the panel as before")
    }
    Keyboard().run {
        // Bug hunt #24: Alt's own press is lost, as Key Mapper once took a Shift press; only Sym says Alt is down.
        modifiers.down(SYM, 1_000L, KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON)
        same(modifiers.takeKeyboardSwitch(), true, "A Sym that says Alt is held must switch keyboards")
        same(modifiers.panel, Panel.Closed, "It must not open the Sym panel")
        modifiers.up(SYM, 1_080L)
    }
}

private fun altSymWithThePanelOpenIsAsBefore() = Keyboard().run {
    press(SYM)
    same(modifiers.panel, Panel.Symbols, "Sym must open the panel")
    idle(WORD)
    down(ALT)
    same(modifiers.panel, Panel.Settings, "Alt with the panel open must open the quick settings, as before")
    idle(GAP)
    press(SYM)
    same(modifiers.takeKeyboardSwitch(), false, "With the panel open, Alt + Sym must not switch keyboards")
    same(modifiers.panel, Panel.Closed, "With the panel open, Sym must move the panel on as before")
    up(ALT)
}

private fun onePress() = Keyboard().run {
    press()
    same(shift, Level.Once, "One press must stay on after the key is let go")
    type()
    same(shift, Level.Off, "One press must capitalise only the next character")
    type()
    same(shift, Level.Off, "One press must not come back")
}

private fun aLongPushStaysOn() {
    for (code in listOf(SHIFT, ALT)) Keyboard().run {
        fun level() = if (code == SHIFT) shift else modifiers.alt
        down(code)
        up(code, after = WORD)
        same(level(), Level.Once, "A long push that typed nothing must stay on for the next key")
        idle(WORD)
        press(code)
        same(level(), Level.Off, "The next push must turn the key off")
    }
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

private fun pressThenHoldAndType() = Keyboard().run {
    press()
    idle(GAP)
    down()
    same(shift, Level.Locked, "A second press in time locks at first")
    type(5)
    same(shift, Level.Locked, "Shift must stay on for the whole word")
    up(after = WORD)
    same(shift, Level.Locked, "A lock must stay after the key is let go, as it does for Alt")
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
    same(shift, Level.Once, "A quick second press must leave Shift on for one character")
    idle(GAP)
    press()
    same(shift, Level.Off, "A third press must turn Shift off")
    idle(GAP)
    press()
    same(shift, Level.Once, "The next press must start again")
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
    press(ALT)
    idle(GAP)
    press(ALT)
    same(modifiers.alt, Level.Once, "A quick second press must leave Alt on for one character")
}

private fun quickSecondPressIsTheSameForShiftAndAlt() {
    for (code in listOf(SHIFT, ALT)) Keyboard().run {
        modifiers.doublePressLocks = false
        fun level() = if (code == SHIFT) shift else modifiers.alt
        press(code)
        idle(GAP)
        press(code)
        same(level(), Level.Once, "Two quick pushes must leave the key on")
        idle(WORD)
        same(level(), Level.Once, "The key must stay on until a character is typed, however long it takes")
        type()
        same(level(), Level.Off, "Two quick pushes must apply to exactly the next character")
        type(3)
        same(level(), Level.Off, "The key must not come back after that character")
        idle(WORD)
        press(code)
        same(level(), Level.Once, "A later press must start a new one")
    }
}

private fun slowSecondPressTurnsOffWhenLocksIsOff() {
    for (code in listOf(SHIFT, ALT)) Keyboard().run {
        modifiers.doublePressLocks = false
        fun level() = if (code == SHIFT) shift else modifiers.alt
        press(code)
        idle(Modifiers.DOUBLE_PRESS_MILLIS)
        press(code)
        same(level(), Level.Off, "A slow second push must turn the key off")
        type(2)
        same(level(), Level.Off, "The key must stay off after a slow second push")
    }
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
    same(shift, Level.Once, "With the setting off, two quick real presses must stay on for one character")
    idle(120)
    type()
    same(shift, Level.Off, "Two quick real presses must capitalise only the next character")
    repeat(4) {
        idle(120)
        type()
        same(shift, Level.Off, "Each letter after that must be lowercase")
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

/**
 * Smart Launcher's search field takes key presses before the keyboard (owner, 2026-11-13): the keyboard takes them back, a press
 * whole, so the launcher never types a letter of its own and TitanKeys' typing, fixes and Sym run there.
 */
private fun takenKeys() {
    val keys = TakenKeys()
    same(keys.takes(LETTER, pressed = true, taking = true), true, "A letter goes to the keyboard")
    same(keys.takes(LETTER, pressed = true, taking = true), true, "Its repeats go to the keyboard too")
    same(keys.holding, true, "A taken key that is down is still heard")
    same(keys.takes(LETTER, pressed = false, taking = true), true, "Its release goes with it")
    same(keys.holding, false, "Nothing is down once it is let go")
    same(keys.takes(SYM, pressed = true, taking = true), true, "Sym goes to the keyboard, so its panel opens")
    same(keys.takes(SYM, pressed = false, taking = false), true, "Sym's release goes with it, even once the field has closed")
    same(keys.takes(SHIFT, pressed = true, taking = true), true, "Shift goes to the keyboard, so Shift + Backspace deletes a word")
    same(keys.takes(SHIFT, pressed = false, taking = true), true, "Shift's release goes with it")
    same(keys.takes(KeyEvent.KEYCODE_BACK, pressed = true, taking = true), false, "Back stays with the app")
    same(keys.takes(KeyEvent.KEYCODE_VOLUME_UP, pressed = true, taking = true), false, "The phone's own keys stay with the app")
    same(keys.takes(LETTER, pressed = true, taking = false), false, "Outside the field the app keeps its keys")
    same(keys.takes(LETTER, pressed = false, taking = true), false, "A release whose press the app had stays the app's")
    keys.takes(LETTER, pressed = true, taking = true)
    keys.clear()
    same(keys.holding, false, "With the Back service off, nothing is held for it")
    // The square Recents key stopped working in Smart Launcher when every key but the system ones was taken (owner, 2026-11-13).
    for (code in listOf(KeyEvent.KEYCODE_APP_SWITCH, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_CAMERA))
        same(keys.takes(code, pressed = true, taking = true), false, "Key $code goes on to the app and Android")
    for (code in listOf(KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_ALT_LEFT,
        KeyEvent.KEYCODE_FUNCTION, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_COMMA, KeyEvent.KEYCODE_1)) {
        same(keys.takes(code, pressed = true, taking = true), true, "Key $code is typing, so the keyboard takes it")
        keys.takes(code, pressed = false, taking = true)
    }
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
    modifiers.doublePressLocks = false
    down(SHIFT)
    down(OTHER_SHIFT)
    type(5)
    up(OTHER_SHIFT, after = WORD)
    same(shift, Level.Once, "Shift stays on while the other Shift key is held")
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
        down(CTRL)
        same(modifiers.panel, Panel.Settings, "Ctrl must leave the quick settings open")
        up(CTRL)
        same(modifiers.panel, Panel.Settings, "The quick settings must stay when Ctrl is let go")
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
        "F" to QuickSetting.Autocorrect, "S" to QuickSetting.SwipeTyping, "W" to QuickSetting.SuggestionBar,
        "C" to QuickSetting.AutoCapitalise, "D" to QuickSetting.AutoPunctuation, "V" to QuickSetting.Vibration,
        "H" to QuickSetting.HoldSymbols, "M" to QuickSetting.CursorControl, "L" to QuickSetting.DoublePressLocks,
        "R" to QuickSetting.ScrollAssistant, "O" to QuickSetting.OpenSettings, "X" to QuickSetting.Clipboard,
        "K" to QuickSetting.ChangeKeyboard,
    )
    same(QuickSetting.entries.associateBy { it.key }, expected, "Each item must be on the letter it is named after, and on one key only")
    same(QuickSetting.on("F"), QuickSetting.Autocorrect, "A letter must find its item")
    same(QuickSetting.on("Q"), null, "A key with no item must find nothing")
    same(QuickSetting.on(Layout.ALT), null, "The Alt place has no setting")
    for (item in QuickSetting.entries) {
        same(Layout.isLetter(item.key), true, "${item.title} must be on a letter key of the panel")
        same(item.label.length <= 5 && '\n' !in item.label, true, "${item.title} must have a short label on one line")
        same(item.title.isNotBlank() && item.title.length >= item.label.length, true, "${item.title} must have its full name")
    }
}

private fun panelScrollKeys() {
    for (panel in listOf(Panel.Emoji, Panel.Symbols)) {
        same(Modifiers.panelScroll(KeyEvent.KEYCODE_DEL, panel), 1f, "On $panel Backspace must do what a swipe down does")
        same(Modifiers.panelScroll(KeyEvent.KEYCODE_ENTER, panel), -1f, "On $panel Enter must do what a swipe up does")
        same(Modifiers.panelScroll(KeyEvent.KEYCODE_NUMPAD_ENTER, panel), -1f, "On $panel the other Enter key must do the same")
    }
    val keys = listOf(KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)
    for (code in keys) {
        same(Modifiers.panelScroll(code, Panel.Settings), 0f, "On the quick settings page key $code must do nothing and not reach the text")
        same(Modifiers.panelScroll(code, Panel.Closed), null, "With the panel closed key $code must reach the text")
    }
    for (panel in Panel.entries) {
        same(Modifiers.panelScroll(LETTER, panel), null, "A letter must not turn the page")
        same(Modifiers.panelScroll(KeyEvent.KEYCODE_SPACE, panel), null, "Space must not turn the page")
    }
    same(Layout.rows[1].last(), Layout.BACKSPACE, "Backspace must be at the end of the second row")
    same(Layout.rows[2].last(), Layout.ENTER, "Enter must be at the end of the third row")
}

/** Holding Ctrl + V pasted about 25 times (owner, 2026-11-12). Each shortcut acts once a press; moving and deleting keep going. */
private fun shortcutsActOncePerPress() {
    for (letter in "ACXVZYSF") {
        val code = KeyEvent.KEYCODE_A + (letter - 'A')
        same(Modifiers.shortcutRepeats(code), false, "Ctrl + $letter held must act once")
    }
    for (code in listOf(KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_1)) {
        same(Modifiers.shortcutRepeats(code), false, "Ctrl with key $code held must act once")
    }
    for (code in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL)) {
        same(Modifiers.shortcutRepeats(code), true, "Ctrl with key $code held must keep moving or deleting")
    }
}

/**
 * Typing that an outside change drops still applies Shift, Alt and Sym and every release, but a dropped key uses up its Alt as a
 * typed one would. Alt + Z with Z dropped left Alt on, so the next key typed its symbol: a stray "7" after a word (2026-11-12).
 */
private fun droppedKeysUseTheirModifiers() {
    same(Modifiers.dropUsesModifiers(LETTER, 0), true, "A dropped letter uses its modifiers")
    same(Modifiers.dropUsesModifiers(LETTER, 1), false, "A dropped repeat does not")
    same(Modifiers.dropUsesModifiers(KeyEvent.KEYCODE_ALT_LEFT, 0), false, "Alt itself is never dropped")
    same(Modifiers.dropUsesModifiers(KeyEvent.KEYCODE_SYM, 0), false, "Nor is Sym")
    // Alt held for Z, with Z dropped: Alt goes off with its release.
    Modifiers().run {
        down(KeyEvent.KEYCODE_ALT_LEFT, 0L)
        consume()
        up(KeyEvent.KEYCODE_ALT_LEFT, 100L)
        same(alt, Modifiers.Level.Off, "Alt held for a dropped key ends with its release")
    }
    // Alt pushed for Z, with Z dropped: the push is used up.
    Modifiers().run {
        down(KeyEvent.KEYCODE_ALT_LEFT, 0L)
        up(KeyEvent.KEYCODE_ALT_LEFT, 50L)
        consume()
        same(alt, Modifiers.Level.Off, "An Alt push for a dropped key is used up")
    }
    // A locked Alt stays locked.
    Modifiers().run {
        doublePressLocks = true
        down(KeyEvent.KEYCODE_ALT_LEFT, 0L); up(KeyEvent.KEYCODE_ALT_LEFT, 20L)
        down(KeyEvent.KEYCODE_ALT_LEFT, 100L); up(KeyEvent.KEYCODE_ALT_LEFT, 120L)
        consume()
        same(alt, Modifiers.Level.Locked, "A locked Alt stays locked after a dropped key")
    }
}

private fun restingOnP() {
    same(Layout.restingKeyAt(.95f, .15f), "P", "The middle of P must be P")
    same(Layout.restingKeyAt(.88f, .15f), "P", "A finger at the left of P, read at the right of O, must be P")
    same(Layout.restingKeyAt(.86f, .15f), "O", "A finger on O must still be O")
    same(Layout.restingKeyAt(.95f, .28f), "P", "A finger low on P, read at the top of Backspace, must be P")
    same(Layout.restingKeyAt(.95f, .4f), "⌫", "A finger in the middle of Backspace must stay Backspace")
    same(Layout.restingKeyAt(.05f, .15f), "Q", "Other keys must not change")
    same(Layout.restingKeyAt(.88f, .4f), "L", "The key under O must not change")
    same(Layout.restingKeyAt(.88f, .65f), Layout.SYM, "The right of Sym must stay Sym")
    same(Layout.restingKeyAt(.95f, .65f), Layout.ENTER, "Enter must stay Enter")
    same(Layout.restingKeyAt(.45f, .85f), Layout.SPACE, "The bottom row must not change")
    same(Layout.restingKeyAt(Float.NaN, .15f), "", "A place off the keyboard must be no key")
}

/** The symbol and emoji pages read the place under a finger as it is, and hold nothing in the last column. */
private fun symbolAndEmojiPages() {
    same(Layout.panelLetters.size, EmojiPages.PER_PAGE, "A page must hold one item for each letter key that shows one")
    for (key in listOf("Q", "A", "P")) same(key in Layout.panelLetters, false, "$key must hold no symbol or emoji")
    same(Layout.letterKeys.size, 26, "Every letter key must still have a place in the panel")
    same(Layout.panelLetters.take(9), listOf("W", "E", "R", "T", "Y", "U", "I", "O", "S"), "The items must go on from O to S")
    for (key in listOf("Q", "A", "P")) same(QuickSetting.on(key), null, "The quick settings page must leave $key empty, where the touchpad reads badly")
    same(Layout.PAGE_POSITION, "P", "P must show the page position")
    for (key in listOf(Layout.ALT, Layout.SYM, Layout.BACKSPACE, Layout.ENTER)) for (itemPage in listOf(true, false))
        same(Layout.restMarks(key, itemPage), false, "A finger on the icon at $key must mark nothing")
    same(Layout.restMarks("P", itemPage = true), false, "P must show no mark on a page of symbols or emoji")
    same(Layout.restMarks("P", itemPage = false), true, "P must show a mark on the quick settings page")
    same(Layout.restMarks("W", itemPage = true), true, "A letter key must show a mark")
    same(Layout.keyAt(.88f, .15f), "O", "The right of O must be O")
    same(Layout.keyAt(.88f, .4f), "L", "The right of L must be L")
    same(Layout.keyAt(.88f, .65f), Layout.SYM, "The right of Sym must be Sym")
    same(Layout.keyAt(.95f, .15f), "P", "The middle of P must be P")
    same(Layout.keyAt(.95f, .28f), Layout.BACKSPACE, "The top of Backspace must be Backspace")
}

/** Real pushes of Back land on the bottom row first, between .12 and .34 across, and moved at most .055 before the key went down. */
private fun aFingerOnBackOpensNoGroup() {
    for (landed in listOf(.119f, .222f, .264f, .336f)) {
        same(Layout.isBottomRow(.87f), true, "A finger on its way to Back is on the bottom row")
        same(Layout.movedAlongBottomRow(landed, landed), false, "A finger that only lands must open no group")
        same(Layout.movedAlongBottomRow(landed, landed + .055f), false, "The most that a real push of Back moved must open no group")
        same(Layout.movedAlongBottomRow(landed, landed - .055f), false, "The same to the left must open no group")
        same(Layout.movedAlongBottomRow(landed, landed + .1f), true, "A finger that moves one key along the row must choose a group")
        same(Layout.movedAlongBottomRow(landed, landed - .1f), true, "The same to the left must choose a group")
    }
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

private fun ctrlShowsInTheBarAtOnce() = Keyboard().run {
    same(bar(), null, "With no modifier on there must be no bar, so the words show")
    down(CTRL)
    same(modifiers.ctrlHeld, true, "Ctrl must count as held while it is down")
    same(bar()?.chips, listOf(ModifierBar.CTRL), "Ctrl must show as soon as it goes down")
    same(bar()?.hint, ModifierBar.SHORTCUTS, "With no finger on a key, the bar must list the shortcuts")
    same(bar("C")?.hint, "C Copy", "A finger on a key with a shortcut must show that shortcut")
    same(bar("Q")?.hint, ModifierBar.SHORTCUTS, "A finger on a key with no shortcut must leave the list")
    idle(WORD)
    same(bar()?.chips, listOf(ModifierBar.CTRL), "Ctrl must stay in the bar for as long as it is down")
    up(CTRL)
    same(bar(), null, "The words must come back when Ctrl is let go")
    same(modifiers.ctrlHeld, false, "Ctrl must not stay on after it is let go")
}

private fun theShortcutsFitOneLine() {
    same(ModifierBar.SHORTCUTS, "A all  C copy  X cut  V paste  Z undo  Y redo", "The six shortcuts must be short")
}

private fun stickyShiftAndCapsShowInTheBar() = Keyboard().run {
    press(SHIFT)
    same(bar()?.chips, listOf(ModifierBar.SHIFT), "A press of Shift must show Shift")
    same(bar()?.hint, "", "With no finger on a key there is nothing more to show")
    same(bar("Q")?.hint, "Q", "A finger on a letter must show its capital")
    same(bar(Layout.SPACE)?.hint, "", "A finger on a key that is not a letter must show nothing")
    idle(GAP)
    press(SHIFT)
    same(shift, Level.Locked, "A quick second press must lock Shift")
    same(bar()?.chips, listOf(ModifierBar.CAPS), "A locked Shift must show as Caps and not as Shift")
    same(bar("Q")?.hint, "Q", "A finger on a letter must show its capital with Caps")
    idle(WORD)
    press(SHIFT)
    same(bar(), null, "The words must come back when Shift goes off")
}

private fun stickyShiftLeavesTheBarWhenItIsUsed() = Keyboard().run {
    press(SHIFT)
    same(bar()?.chips, listOf(ModifierBar.SHIFT), "A press of Shift must show Shift")
    same(bar()?.held, emptySet<String>(), "A Shift that was pushed and let go must not show as held")
    type()
    same(bar(), null, "The words must come back after the next letter")
}

private fun aHeldShiftShowsInTheBar() = Keyboard().run {
    down(SHIFT)
    same(bar()?.chips, listOf(ModifierBar.SHIFT), "A Shift that is held must show at once")
    same(bar()?.held, setOf(ModifierBar.SHIFT), "A Shift that a finger holds must show as held")
    same(bar("Q")?.hint, "Q", "A finger on a letter must show that letter while Shift is held")
    type()
    same(bar()?.chips, listOf(ModifierBar.SHIFT), "Shift must still show while it is held")
    up(SHIFT)
    same(bar(), null, "The words must come back when Shift is let go after a letter")
}

private fun altShowsTheSymbolOfAKey() = Keyboard().run {
    press(ALT)
    same(bar()?.chips, listOf(ModifierBar.ALT), "A press of Alt must show Alt")
    same(bar("Q")?.hint, "%", "A finger on a letter must show the symbol that Alt types")
    same(bar("W")?.hint, "", "A letter with no symbol must show nothing")
    type()
    same(bar(), null, "The words must come back after the next letter")
}

private fun aHeldAltShowsInTheBar() = Keyboard().run {
    down(ALT)
    same(bar()?.chips, listOf(ModifierBar.ALT), "An Alt that is held must show at once")
    same(bar()?.held, setOf(ModifierBar.ALT), "An Alt that a finger holds must show as held")
    type()
    up(ALT)
    same(bar(), null, "The words must come back when Alt is let go after a character")
}

private fun theBarShowsEveryModifierThatIsOn() = Keyboard().run {
    press(SHIFT)
    down(CTRL)
    same(bar()?.chips, listOf(ModifierBar.CTRL, ModifierBar.SHIFT), "Ctrl and Shift must both show, Ctrl first")
    same(bar()?.hint, ModifierBar.SHORTCUTS, "With Ctrl held the bar must list the shortcuts")
}

private fun ctrlIsNotAToggle() = Keyboard().run {
    press(CTRL)
    idle(WORD)
    same(bar(), null, "A press of Ctrl must not leave the bar on")
    same(modifiers.ctrlHeld, false, "A press of Ctrl must not stay on")
    down(CTRL)
    same(modifiers.ctrlHeld, true, "A second hold must count as held")
    up(CTRL)
    same(modifiers.ctrlHeld, false, "A second hold must end when Ctrl is let go")
}

private fun ctrlLeavesTheSymPanelAlone() {
    Keyboard().run {
        press(SYM)
        idle(WORD)
        down(CTRL)
        same(modifiers.panel, Panel.Symbols, "Ctrl must leave the Sym panel open")
        up(CTRL)
        same(modifiers.panel, Panel.Symbols, "The Sym panel must stay when Ctrl is let go")
    }
    Keyboard().run {
        down(CTRL)
        press(SYM)
        same(modifiers.ctrlHeld, true, "Sym must not end a held Ctrl")
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
    down(OTHER_CTRL)
    same(modifiers.ctrlHeld, true, "Right Ctrl must also count as held")
    down(CTRL)
    up(OTHER_CTRL)
    same(modifiers.ctrlHeld, true, "Ctrl must stay held while the other Ctrl key is down")
    up(CTRL)
    same(modifiers.ctrlHeld, false, "Ctrl must end with the last Ctrl key")
}

private fun fnIsNotCtrlByDefault() = Keyboard().run {
    same(modifiers.fnIsCtrl, false, "fn must not be Ctrl until the user turns the setting on")
    same(modifiers.isCtrl(FN), false, "With the setting off, fn must not count as Ctrl")
    down(FN)
    idle(WORD)
    same(modifiers.ctrlHeld, false, "With the setting off, fn must not hold Ctrl")
    same(bar(), null, "With the setting off, fn must not show in the bar")
    down(CTRL)
    same(modifiers.ctrlHeld, true, "With the setting off, Key Mapper's Ctrl must still count")
}

private fun fnAsCtrl() = Keyboard().run {
    modifiers.fnIsCtrl = true
    same(modifiers.isCtrl(FN), true, "With the setting on, fn must count as Ctrl")
    same(Modifiers.isModifier(FN), false, "fn must not join the sticky modifiers")
    down(FN)
    same(bar()?.chips, listOf(ModifierBar.CTRL), "With the setting on, holding fn must show Ctrl")
    same(shift, Level.Off, "fn as Ctrl must not turn Shift on")
    up(FN)
    same(bar(), null, "With the setting on, letting go of fn must bring the words back")
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
        up(FN)
        same(modifiers.ctrlHeld, true, "Ctrl must stay held while Key Mapper's Ctrl is down")
        up(CTRL)
        same(modifiers.ctrlHeld, false, "Ctrl must end when both are let go")
    }
    Keyboard().run {
        modifiers.fnIsCtrl = true
        down(CTRL)
        down(FN)
        up(CTRL)
        up(FN)
        same(modifiers.ctrlHeld, false, "The order of release must not leave Ctrl on")
    }
}

private fun closingThePanelLeavesCtrlHeld() = Keyboard().run {
    down(CTRL)
    modifiers.closePanel()
    same(modifiers.ctrlHeld, true, "Hiding the keyboard must not change what is physically held")
    up(CTRL)
    same(modifiers.ctrlHeld, false, "Letting go of Ctrl must still end it")
}

private fun clearingCtrl() {
    Keyboard().run {
        down(CTRL)
        modifiers.clear()
        same(modifiers.ctrlHeld, false, "Clear must forget Ctrl")
        up(CTRL, after = WORD)
        same(modifiers.ctrlHeld, false, "An up after clear must change nothing")
    }
    Keyboard().run {
        down(CTRL)
        modifiers.clear(keepCtrl = true)
        same(modifiers.ctrlHeld, true, "A field that restarts must leave Ctrl held")
        up(CTRL)
        same(modifiers.ctrlHeld, false, "Letting go of Ctrl must still end it after a restart")
    }
}

private fun ctrlThroughAFieldStart() {
    Keyboard().run {
        down(CTRL)
        modifiers.fieldStarted(restarting = true, editing = true)
        same(modifiers.ctrlHeld, true, "A restart of a field that takes text must leave Ctrl held")
    }
    Keyboard().run {
        down(CTRL)
        modifiers.fieldStarted(restarting = false, editing = true)
        same(modifiers.ctrlHeld, false, "A new field must forget Ctrl")
    }
    Keyboard().run {
        down(CTRL)
        modifiers.fieldStarted(restarting = true, editing = false)
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

/**
 * Shift comes on by itself where a sentence starts, as one push would. A push turns it off for that letter, and it comes back at
 * the next sentence start, also after deleting back to one.
 */
private fun autoShift() {
    // Ctrl over the automatic capital: the bar is Ctrl's, and its shortcuts get no Shift, until Ctrl lets go.
    Keyboard().run {
        modifiers.autoShift(sentenceStart = true)
        down(CTRL)
        same(bar()?.chips, listOf(ModifierBar.CTRL), "With Ctrl down, the bar shows Ctrl only, not the automatic Shift")
        same(bar()?.replacesWords, true, "Ctrl's own view, as always")
        same(bar()?.autoCapital, false, "No arrow while Ctrl is down")
        same(bar("C")?.hint, "C Copy", "A finger on C shows Ctrl's shortcut")
        same(modifiers.autoShiftWaits, true, "Ctrl's shortcuts get no Shift from the automatic capital: it waits")
        up(CTRL)
        same(modifiers.autoShifted, true, "When Ctrl lets go, the automatic capital is back")
        same(bar()?.autoCapital, true, "and its arrow shows again")
    }
    Keyboard().run {
        modifiers.autoShift(sentenceStart = true)
        same(shift, Level.Once, "At a sentence start Shift comes on by itself")
        same(modifiers.autoShifted, true, "It says that it came on by itself")
        same(bar()?.chips, listOf(ModifierBar.SHIFT), "The bar shows it like one push of Shift")
        same(bar()?.replacesWords, false, "The words stay on the bar, with only the arrow")
        same(bar("H")?.replacesWords, false, "A finger resting on a letter shows no preview and no Shift view, as with Shift off")
        same(bar("H")?.autoCapital, true, "The arrow stays while the finger rests")
        type()
        same(shift, Level.Off, "The first letter uses it up")
        modifiers.autoShift(sentenceStart = false)
        same(shift, Level.Off, "Inside the sentence it stays off")
        modifiers.autoShift(sentenceStart = true)
        same(shift, Level.Once, "Deleting back to the sentence start turns it on again")
    }
    Keyboard().run {
        modifiers.autoShift(sentenceStart = true)
        press()
        same(shift, Level.Off, "A push turns the automatic Shift off, so the letter stays lowercase")
        same(modifiers.autoShifted, false, "It is no longer on by itself")
        modifiers.autoShift(sentenceStart = true)
        same(shift, Level.Off, "It stays off at the same sentence start")
        idle(GAP)
        press()
        same(shift, Level.Once, "A quick second push is a fresh push of Shift, not a lock")
        same(modifiers.autoShifted, false, "That Shift is the user's own")
        same(bar("H")?.replacesWords, true, "A real push of Shift shows the key under a resting finger, as always")
        modifiers.autoShift(sentenceStart = false)
        same(shift, Level.Once, "The user's own Shift stays when the sentence start is left")
        type()
        modifiers.autoShift(sentenceStart = false)
        modifiers.autoShift(sentenceStart = true)
        same(shift, Level.Once, "After the letter, the next sentence start turns it on again")
        same(modifiers.autoShifted, true, "On by itself again")
    }
    Keyboard().run {
        modifiers.autoShift(sentenceStart = true)
        modifiers.autoShift(sentenceStart = false)
        same(shift, Level.Off, "Moving the cursor away from a sentence start turns the automatic Shift off")
    }
    Keyboard().run {
        modifiers.autoShift(sentenceStart = true)
        press(ALT)
        same(shift, Level.Off, "Alt replaces the automatic Shift")
        same(modifiers.alt, Level.Once, "and Alt is on")
        modifiers.autoShift(sentenceStart = true)
        same(shift, Level.Off, "It does not come back while Alt is on")
        type()
        modifiers.autoShift(sentenceStart = true)
        same(shift, Level.Once, "Once Alt is used up, a sentence start still turns it on")
    }
    Keyboard().run {
        modifiers.autoShift(sentenceStart = true)
        press(SYM)
        same(shift, Level.Off, "The panel replaces the automatic Shift")
        modifiers.autoShift(sentenceStart = true)
        same(shift, Level.Off, "It stays off while the panel is open")
        modifiers.closePanel()
        modifiers.autoShift(sentenceStart = true)
        same(shift, Level.Once, "Closing the panel at a sentence start turns it on again")
    }
    Keyboard().run {
        down(CTRL)
        modifiers.autoShift(sentenceStart = true)
        same(shift, Level.Off, "It does not come on while Ctrl is held")
        up(CTRL)
        down()
        same(shift, Level.Once, "A held Shift is the user's own")
        modifiers.autoShift(sentenceStart = true)
        same(modifiers.autoShifted, false, "It does not take over a held Shift")
        up()
    }
    Keyboard().run {
        modifiers.autoShift(sentenceStart = true)
        down()
        same(shift, Level.Off, "Holding Shift on an automatic Shift turns it off; the held key gives the capital itself")
        type()
        up()
        same(shift, Level.Off, "Nothing is left on after the held Shift")
    }
    Keyboard().run {
        modifiers.autoShift(sentenceStart = true)
        modifiers.fieldStarted(restarting = false, editing = true)
        same(shift, Level.Off, "A new field starts with nothing on, until it is checked again")
        modifiers.autoShift(sentenceStart = true)
        same(shift, Level.Once, "A new field that starts a sentence turns it on")
    }
}

/**
 * Only the Shift that a sentence start turns on keeps the words, with a small capital as its mark. Shift, Alt and Ctrl that you
 * press or hold take the whole bar, as do a finger resting on a letter.
 */
private fun chipsBesideTheWords() {
    Keyboard().run {
        modifiers.autoShift(sentenceStart = true)
        same(bar()?.replacesWords, false, "At a sentence start the automatic Shift keeps the suggestions")
        same(bar()?.autoCapital, true, "and the strip marks it with a small capital")
        same(bar("H")?.replacesWords, false, "A finger resting on a letter keeps the words too: no preview and no Shift view")
        same(bar(Layout.SPACE)?.replacesWords, false, "A finger on Space keeps the words")
    }
    Keyboard().run {
        press()
        same(bar()?.replacesWords, true, "One push of Shift takes the whole bar")
        same(bar()?.autoCapital, false, "It is no automatic capital")
        press()
        same(bar()?.chips, listOf(ModifierBar.CAPS), "A lock shows Caps")
        same(bar()?.replacesWords, true, "Caps takes the whole bar too")
    }
    Keyboard().run {
        press(ALT)
        same(bar()?.replacesWords, true, "One push of Alt takes the whole bar")
        same(bar("Q")?.hint, "%", "A finger on a letter shows what Alt types there")
    }
    Keyboard().run {
        modifiers.autoShift(sentenceStart = true)
        press(ALT)
        same(bar()?.autoCapital, false, "Alt with the automatic Shift is no longer only the capital")
        same(bar()?.replacesWords, true, "so it takes the whole bar")
    }
    Keyboard().run {
        down(CTRL)
        same(bar()?.replacesWords, true, "Ctrl shows its shortcuts in the place of the words")
        up(CTRL)
        same(bar(), null, "Nothing on shows the words alone")
    }
}

/**
 * Each held shortcut has its switch, on to start (owner, 2026-11-13: "why is delete a word shift backspace and next keyboard alt
 * sym not toggleable?"). Off, the keys do what they would without it: Shift + Backspace is a plain Backspace, and Sym with Alt
 * held opens its panel with no keyboard switch. Ctrl + Backspace's word delete and Alt + Backspace's own switch are untouched.
 */
private fun theShortcutsSwitchOff() {
    val shiftHeld = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
    Keyboard().run {
        down(SHIFT)
        idle(WORD)
        same(backspaceDoes(shiftHeld, shiftWord = false), Modifiers.Backspace.Letter, "Off, a held Shift + Backspace deletes one letter")
        same(backspaceDoes(shiftHeld, shiftWord = false), Modifiers.Backspace.Letter, "and holding it goes on letter by letter")
        same(backspaceDoes(shiftHeld or KeyEvent.META_CTRL_ON, shiftWord = false), Modifiers.Backspace.Word, "Ctrl + Backspace still deletes a word")
        same(backspaceDoes(shiftHeld, shiftWord = true), Modifiers.Backspace.Word, "On, it deletes a word")
    }
    Keyboard().run {
        // Only the meta says Shift, as from the owner's phone: off, that's plain too.
        same(backspaceDoes(shiftHeld, shiftWord = false), Modifiers.Backspace.Letter, "Off, Shift's meta alone is plain too")
    }
    Keyboard().run {
        press(ALT)
        same(backspaceDoes(shiftWord = false), Modifiers.Backspace.Forward, "Alt + Backspace keeps its own switch")
    }
    Keyboard().run {
        modifiers.altSymSwitches = false
        down(ALT)
        idle(GAP)
        press(SYM)
        same(modifiers.takeKeyboardSwitch(), false, "Off, Alt + Sym never switches keyboards")
        same(modifiers.panel, Panel.Symbols, "and Sym opens its panel, as it would without the shortcut")
        up(ALT)
    }
    Keyboard().run {
        same(modifiers.altSymSwitches, true, "On to start")
        down(ALT)
        idle(GAP)
        press(SYM)
        same(modifiers.takeKeyboardSwitch(), true, "On, Alt + Sym switches")
    }
}
