package com.titankeys.keyboard

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

/**
 * Tells Titan Tools when you type, so its keyboard light can follow typing, swipes included, instead of the firmware's key
 * presses (owner, 2026-11-22). It says only "typing" or "not typing", never a key or any text. Only an app signed with the same
 * key can hear it: the broadcast needs [PERMISSION], which both apps declare at signature level. Without Titan Tools
 * nothing is sent, and the keyboard works as it always has.
 */
internal class TitanToolsLink(private val now: () -> Long = SystemClock::uptimeMillis) {
    private var typing = false
    private var sentAt = Long.MIN_VALUE / 2

    /** A key or a swipe in a field. Sends "typing" at once, then again at most every [REPEAT_MILLIS] while it goes on. */
    fun active(context: Context) {
        if (typing && now() - sentAt < REPEAT_MILLIS) return
        send(context, true)
    }

    /** The keyboard hid. */
    fun idle(context: Context) {
        if (typing) send(context, false)
    }

    /** What [active] and [idle] would send, for tests: null for nothing. */
    fun next(active: Boolean): Boolean? = when {
        active && typing && now() - sentAt < REPEAT_MILLIS -> null
        active -> true
        typing -> false
        else -> null
    }

    private fun send(context: Context, on: Boolean) {
        typing = on
        sentAt = now()
        try {
            context.sendBroadcast(Intent(ACTION).setPackage(PACKAGE).putExtra("typing", on)
                .putExtra("at", System.currentTimeMillis()), PERMISSION)
        } catch (error: RuntimeException) {
            Log.w("TitanKeys", "Could not tell Titan Tools", error)
        }
    }

    companion object {
        const val PACKAGE = "com.everyonesniche.titantools"
        const val ACTION = "com.everyonesniche.titantools.action.TYPING"
        const val PERMISSION = "com.everyonesniche.titantools.permission.LINK"
        /** Titan Tools counts the link as alive for 10 minutes; a reminder this often keeps a long stretch of typing lit. */
        const val REPEAT_MILLIS = 2_000L
    }
}
