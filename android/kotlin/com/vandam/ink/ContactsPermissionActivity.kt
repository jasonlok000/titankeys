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
import com.atlas.engine.TypingSettings
import com.atlas.keyboard.FixedScale
import com.atlas.keyboard.R

/**
 * Asks Android for the Contacts permission when the user turns on "Names from my contacts", as only an app screen can
 * ask. The setting turns on only once Android allows it. If Android no longer shows its question, because the user
 * declined it before, the app's page in Android's settings opens instead, where the permission can be turned on.
 */
class ContactsPermissionActivity : Activity() {
    /** The same sizes at any display size or font size (FixedScale). */
    override fun attachBaseContext(base: Context) = super.attachBaseContext(FixedScale.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            TypingSettings.contactNames(this, true)
            finish()
            return
        }
        requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), ASK)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != ASK) return
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        TypingSettings.contactNames(this, granted)
        if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
            Toast.makeText(this, R.string.contacts_permission_in_settings, Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
        finish()
    }

    companion object {
        private const val ASK = 1

        internal fun start(activity: Activity) {
            activity.startActivity(Intent(activity, ContactsPermissionActivity::class.java))
        }
    }
}
