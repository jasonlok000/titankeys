package com.atlas.keyboard

import android.view.KeyEvent
import com.atlas.keyboard.Modifiers.Level

private const val SHIFT = KeyEvent.KEYCODE_SHIFT_LEFT
private const val OTHER_SHIFT = KeyEvent.KEYCODE_SHIFT_RIGHT
private const val ALT = KeyEvent.KEYCODE_ALT_LEFT
private const val SYM = KeyEvent.KEYCODE_SYM
private const val LETTER = KeyEvent.KEYCODE_A

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
    lateSecondPress()
    lockedThenHold()
    bothShiftKeys()
    shortcutWhileHeld()
    altAndSym()
    onlyOneModifier()
    repeatsAndStrayEvents()
    println("Modifier checks passed")
}

private fun same(actual: Any?, expected: Any?, message: String) {
    if (actual != expected) throw AssertionError("$message\n  expected: $expected\n  actual:   $actual")
}

/** Plays key events into one [Modifiers], with a clock that moves forward between them. */
private class Keyboard {
    val modifiers = Modifiers()
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

private fun altAndSym() {
    for ((code, level) in listOf<Pair<Int, Keyboard.() -> Level>>(ALT to { modifiers.alt }, SYM to { modifiers.sym })) {
        Keyboard().run {
            down(code)
            up(code, after = WORD)
            same(level(), Level.Once, "A long press of Alt or Sym must keep the strip open")
            type()
            same(level(), Level.Off, "One press of Alt or Sym must end after one character")
        }
        Keyboard().run {
            down(code)
            type()
            up(code)
            same(level(), Level.Off, "Alt or Sym held for a character must end with the key")
        }
        Keyboard().run {
            press(code)
            idle(GAP)
            down(code)
            type()
            up(code, after = WORD)
            same(level(), Level.Locked, "A double press of Alt or Sym must lock even when held")
        }
    }
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
