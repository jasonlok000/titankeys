package com.atlas.keyboard

import android.view.KeyEvent
import com.atlas.keyboard.HyperKey.Action

private const val HYPER = 340
private const val S = KeyEvent.KEYCODE_S

private class Keys {
    val hyper = HyperKey()
    fun down(code: Int, time: Long, repeat: Int = 0) = hyper.key(HYPER, code, true, repeat, time)
    fun up(code: Int, time: Long) = hyper.key(HYPER, code, false, 0, time)
}

private fun check(name: String, actual: Any?, expected: Any?) {
    if (actual != expected) throw AssertionError("$name: got $actual, expected $expected")
}

fun main() {
    Keys().run {
        check("hold: Hyper down is taken", down(HYPER, 0), Action.Take)
        check("hold: letter opens", down(S, 100), Action.Open('S'))
        check("hold: letter repeat is taken", down(S, 600, repeat = 1), Action.Take)
        check("hold: letter up is taken", up(S, 700), Action.Take)
        check("hold: Hyper up is taken", up(HYPER, 800), Action.Take)
        check("hold: letter after release types", down(S, 900), Action.Pass)
    }
    Keys().run {
        down(HYPER, 0)
        check("hold: two letters open", down(S, 100), Action.Open('S'))
        up(S, 150)
        check("hold: second letter opens", down(KeyEvent.KEYCODE_M, 200), Action.Open('M'))
    }
    Keys().run {
        down(HYPER, 0)
        up(HYPER, 50)
        check("tap: letter within 1 second opens", down(S, 50 + HyperKey.TAP_MILLIS), Action.Open('S'))
        up(S, 1100)
        check("tap: only one letter", down(S, 1200), Action.Pass)
    }
    Keys().run {
        down(HYPER, 0)
        up(HYPER, 50)
        check("tap: letter after 1 second types", down(S, 51 + HyperKey.TAP_MILLIS), Action.Pass)
        check("tap: its release passes", up(S, 1100), Action.Pass)
    }
    Keys().run {
        down(HYPER, 0)
        up(HYPER, 50)
        check("tap again: cancel is taken", down(HYPER, 300), Action.Take)
        check("tap again: release is taken", up(HYPER, 350), Action.Take)
        check("tap again: letter types", down(S, 400), Action.Pass)
    }
    Keys().run {
        down(HYPER, 0)
        up(HYPER, 50)
        check("tap: other key passes", down(KeyEvent.KEYCODE_SPACE, 100), Action.Pass)
        check("tap: other key cancels", down(S, 200), Action.Pass)
    }
    Keys().run {
        down(HYPER, 0)
        down(S, 100)
        up(S, 150)
        up(HYPER, 200)
        check("hold used: release does not wait for a letter", down(S, 300), Action.Pass)
    }
    Keys().run {
        check("Hyper repeat is taken", down(HYPER, 0).let { down(HYPER, 500, repeat = 1) }, Action.Take)
        check("other keys pass while held", down(KeyEvent.KEYCODE_ENTER, 600), Action.Pass)
    }
    Keys().run {
        down(HYPER, 0)
        hyper.reset()
        check("reset: letter types", down(S, 100), Action.Pass)
    }

    check("FUNC3 can be Hyper", HyperKey.problem(HYPER), null)
    for (code in listOf(KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT, KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_SYM, 342,
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_POWER))
        if (HyperKey.problem(code) == null) throw AssertionError("Key $code has a job, so it cannot be Hyper")
    check("Sym says why", HyperKey.problem(KeyEvent.KEYCODE_SYM), "Sym shows emoji. Choose a different key.")
    check("Alt says why", HyperKey.problem(KeyEvent.KEYCODE_ALT_LEFT), "Alt types symbols. Choose a different key.")
    println("Hyper key checks passed")
}
