package com.vandam.ink

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import com.titankeys.engine.TypingSettings
import com.titankeys.keyboard.ContactsAsk
import com.titankeys.keyboard.FixedScale
import com.titankeys.keyboard.R

/**
 * Asks Android for the Contacts permission when the user turns on "Names from my contacts", as only an app screen can
 * ask. The setting turns on only once Android allows it. If Android no longer shows its question, because the user
 * declined it twice before, the app's page in Android's settings opens instead, where the permission can be turned on: only
 * on a press after Android has stopped asking, never by itself right after a "Don't allow" (#87, [ContactsAsk]).
 */
class ContactsPermissionActivity : Activity() {
    /** Before asking: whether TitanKeys asked before, and whether Android would show its reason. */
    private var askedBefore = false
    private var reasonBefore = false
    /** Android's question came up over this screen, which pauses it. With no question, Android answered by itself. */
    private var questionShown = false
    private var asking = false

    /** The same sizes at any display size or font size (FixedScale). */
    override fun attachBaseContext(base: Context) = super.attachBaseContext(FixedScale.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            askedBefore = savedInstanceState.getBoolean(ASKED_BEFORE)
            reasonBefore = savedInstanceState.getBoolean(REASON_BEFORE)
            questionShown = savedInstanceState.getBoolean(QUESTION_SHOWN)
            asking = savedInstanceState.getBoolean(ASKING)
            return
        }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            TypingSettings.contactNames(this, true)
            finish()
            return
        }
        val preferences = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        askedBefore = preferences.getBoolean(ASKED, false)
        reasonBefore = shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)
        preferences.edit().putBoolean(ASKED, true).apply()
        asking = true
        requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), ASK)
    }

    override fun onPause() {
        super.onPause()
        if (asking) questionShown = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(ASKED_BEFORE, askedBefore)
        outState.putBoolean(REASON_BEFORE, reasonBefore)
        outState.putBoolean(QUESTION_SHOWN, questionShown)
        outState.putBoolean(ASKING, asking)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != ASK) return
        asking = false
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        TypingSettings.contactNames(this, granted)
        // A "Don't allow" just pressed is the answer: nothing more opens. Only a press Android no longer asks about goes to App info.
        if (ContactsAsk.toSettings(granted, questionShown, askedBefore, reasonBefore)) {
            Toast.makeText(this, R.string.contacts_permission_in_settings, Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
        finish()
    }

    companion object {
        private const val ASK = 1
        private const val PREFERENCES = "contacts_permission"
        /** TitanKeys has asked Android for Contacts before. */
        private const val ASKED = "asked"
        private const val ASKED_BEFORE = "askedBefore"
        private const val REASON_BEFORE = "reasonBefore"
        private const val QUESTION_SHOWN = "questionShown"
        private const val ASKING = "asking"

        internal fun start(activity: Activity) {
            activity.startActivity(Intent(activity, ContactsPermissionActivity::class.java))
        }
    }
}
