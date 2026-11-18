package com.titankeys.keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.ViewConfiguration

/**
 * TitanKeys Debug only (tools/debug.sh adds it): the touch simulator's keys without the injector's twin of the keyboard. The
 * twin is a second keyboard to Android, and on the Titan 2 Elite its Shift took the field's focus, so no check could press
 * Shift (Thai's Shift layer, Chinese's Shift menu, Georgian's Shift + W). This builds the key events the phone's own keyboard
 * would send, from its device, with the meta state of the modifiers held and Android's repeats ([KeyFeedPlan]), and hands them
 * to the keyboard's own onKeyDown and onKeyUp (KeyFeedHook); a key the keyboard doesn't take goes on to the app. Only adb can
 * send to it: it needs the DUMP permission that the shell holds.
 *
 *   adb shell am broadcast -n com.titankeys.debug/com.titankeys.keyboard.KeyFeed --es keys "D 0 SHIFT_RIGHT;D 60 Q;U 140 Q;U 200 SHIFT_RIGHT"
 *
 * --el delay MS starts that much later. --el start_uptime MS sets a common start with TouchFeed. The reply says what was queued.
 * --ez verify_focus_guard true checks the focused editor and hook without scheduling anything.
 */
class KeyFeed : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!context.packageName.endsWith(".debug")) return
        val dispatch = KeyFeedHook.dispatch
        val editor = dispatch?.let { FeedEditor.capture(context.packageName) }
        if (intent.getBooleanExtra(FeedBurst.VERIFY_ONLY, false))
            return answer(FeedBurst.probeAtUptime(editor != null))
        if (dispatch == null) return answer("no Debug keyboard hook")
        if (editor == null) return answer("no focused active Debug editor")
        val steps = KeyFeedPlan.read(intent.getStringExtra("keys").orEmpty(), ViewConfiguration.getKeyRepeatTimeout().toLong(),
            ViewConfiguration.getKeyRepeatDelay().toLong()) ?: return answer("bad keys")
        val codes = steps.map { it.name }.distinct().associateWith { KeyEvent.keyCodeFromString("KEYCODE_$it") }
        codes.entries.firstOrNull { it.value == KeyEvent.KEYCODE_UNKNOWN }?.let { return answer("no key called ${it.key}") }
        val device = keyboard(context)
        val start = FeedBurst.startTime(intent, KeyFeedPlan.MAX_MILLIS) ?: return answer("start_uptime out of range")
        val downTimes = HashMap<Int, Long>()
        val burst = FeedBurst("key", editor, dispatch, { KeyFeedHook.dispatch }) {
            if (downTimes.isNotEmpty()) KeyFeedHook.cancelHeld?.invoke(downTimes)
        }
        if (!burst.begin()) return answer("Debug editor changed before queueing")
        for ((index, step) in steps.withIndex()) {
            burst.post(start + step.millis, index == steps.lastIndex) { send ->
                val now = SystemClock.uptimeMillis()
                val code = codes.getValue(step.name)
                if (step.down && step.repeat == 0) downTimes[code] = now
                val event = KeyEvent(downTimes[code] ?: now, now, if (step.down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP,
                    code, step.repeat, step.meta, device?.id ?: KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                    KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD)
                val taken = send(event)
                if (!step.down) downTimes.remove(code)
                Log.d(TAG, "key feed: $step ${if (taken) "taken by the keyboard" else "on to the app"}")
            }
        }
        answer("queued ${steps.size} key events, ${steps.last().millis} ms, on device ${device?.id ?: "none (no $DEVICE)"}; ${FeedBurst.READY}")
    }

    private fun answer(text: String) {
        Log.d(TAG, "key feed: $text")
        resultData = text
    }

    /** The phone's own keyboard, whose id the events carry, as its real keys' do. */
    private fun keyboard(context: Context): InputDevice? {
        val input = context.getSystemService(InputManager::class.java)
        return input.inputDeviceIds.asSequence().mapNotNull(input::getInputDevice).firstOrNull { it.name == DEVICE }
    }

    private companion object {
        const val TAG = "TitanKeysDebug"
        const val DEVICE = "TitanKey"
    }
}
