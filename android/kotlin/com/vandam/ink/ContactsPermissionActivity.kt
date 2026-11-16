package com.vandam.ink

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import com.titankeys.engine.TypingSettings
import com.titankeys.keyboard.ContactsAsk
import com.titankeys.keyboard.FixedScale
import com.titankeys.keyboard.R

/**
 * Asks Android for the Contacts permission when the user turns on "Names from my contacts", as only an app screen can
 * ask. The setting turns on only once Android allows it. Once Android no longer asks, after two noes, a press opens the app's
 * page in Android's settings instead, where the permission can be turned on: never by itself right after a "Don't allow" (#87,
 * [ContactsAsk], which counts the noes from Android's reason read before and after each ask).
 */
class ContactsPermissionActivity : Activity() {
    /** Before asking: Android had stopped asking, as TitanKeys counted it, and Android would show its reason. */
    private var stoppedBefore = false
    private var reasonBefore = false
    /** When the ask went out, to tell Android's own answer, at once, from someone's. */
    private var askedAt = 0L

    /** The same sizes at any display size or font size (FixedScale). */
    override fun attachBaseContext(base: Context) = super.attachBaseContext(FixedScale.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            stoppedBefore = savedInstanceState.getBoolean(STOPPED_BEFORE)
            reasonBefore = savedInstanceState.getBoolean(REASON_BEFORE)
            askedAt = savedInstanceState.getLong(ASKED_AT)
            return
        }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            TypingSettings.contactNames(this, true)
            finish()
            return
        }
        stoppedBefore = preferences().getBoolean(STOPPED, false)
        reasonBefore = shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)
        askedAt = SystemClock.elapsedRealtime()
        requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), ASK)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STOPPED_BEFORE, stoppedBefore)
        outState.putBoolean(REASON_BEFORE, reasonBefore)
        outState.putLong(ASKED_AT, askedAt)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != ASK) return
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        TypingSettings.contactNames(this, granted)
        val reasonAfter = shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)
        val outcome = ContactsAsk.answered(granted, stoppedBefore, reasonBefore, reasonAfter, SystemClock.elapsedRealtime() - askedAt)
        preferences().edit().putBoolean(STOPPED, outcome.stopped).apply()
        // A "Don't allow" just pressed is the answer: nothing more opens. Only a press Android no longer asks about goes to App info.
        if (outcome.toSettings) {
            Toast.makeText(this, R.string.contacts_permission_in_settings, Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
        finish()
    }

    private fun preferences() = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    companion object {
        private const val ASK = 1
        private const val PREFERENCES = "contacts_permission"
        /** Android has stopped asking for Contacts: two noes, as counted by [ContactsAsk]. Cleared once it's allowed. */
        private const val STOPPED = "stopped"
        private const val STOPPED_BEFORE = "stoppedBefore"
        private const val REASON_BEFORE = "reasonBefore"
        private const val ASKED_AT = "askedAt"

        internal fun start(activity: Activity) {
            activity.startActivity(Intent(activity, ContactsPermissionActivity::class.java))
        }
    }
}
