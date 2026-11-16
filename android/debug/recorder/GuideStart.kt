package com.titankeys.keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * TitanKeys Debug only (tools/debug.sh adds it): opens the guide at one slide, for the touch simulator's checks of that slide
 * (tools/dev/touch/checks.py), so they needn't pass every slide before it. Only adb can send to it: it needs the DUMP permission
 * that the shell holds. The guide itself takes the slide only in TitanKeys Debug (GuideActivity.debugStartStep).
 *
 *   adb shell am broadcast -n com.titankeys.debug/com.titankeys.keyboard.GuideStart --es step steer
 */
class GuideStart : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!context.packageName.endsWith(".debug")) return
        val step = intent.getStringExtra("step")
        if (step == null || GUIDE_STEPS.none { it.id == step }) {
            resultData = "no slide called $step: ${GUIDE_STEPS.joinToString(" ") { it.id }}"
            return
        }
        context.startActivity(Intent(context, GuideActivity::class.java)
            .putExtra(GuideActivity.DEBUG_START_STEP, step)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        Log.d("TitanKeysDebug", "guide opened at $step")
        resultData = "guide opened at $step"
    }
}
