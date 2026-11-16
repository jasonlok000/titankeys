package com.titankeys.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import com.titankeys.connected.Connected

/**
 * Ends TitanKeys' old process and opens the front page in a new one. It runs in a process of its own (":restart" in the
 * manifest), so it outlives the one it ends, and it ends itself once the front page is on its way. The system binds the
 * keyboard and the Accessibility service again, as it does after a crash.
 */
class FreshStartActivity : Activity() {
    override fun attachBaseContext(base: Context) = super.attachBaseContext(FixedScale.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val old = intent.getIntExtra(PID, -1)
        if (old > 0 && old != Process.myPid()) Process.killProcess(old)
        startActivity(Connected.pageIntent(this, intent.getStringExtra(PAGE) ?: "/").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
        Process.killProcess(Process.myPid())
    }

    companion object {
        const val PID = "pid"
        /** The page to open, if not the front page: Restore opens its own result page (#65). */
        const val PAGE = "page"
    }
}
