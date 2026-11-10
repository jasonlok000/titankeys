package com.atlas.keyboard

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import com.atlas.engine.PhoneNames
import com.atlas.engine.SwipeTyping
import com.atlas.engine.TypingSettings
import java.util.concurrent.Executors

/**
 * Gathers the names on this phone for the keyboard ([PhoneNames]): the names of the apps on the home screen, always, and
 * the given and family names of the user's contacts, only while the setting is on and Android allows it. They are kept in
 * memory and handed to [SwipeTyping], never written anywhere. Turning contacts off, or taking the permission away, clears
 * theirs. Android ends the app when a permission is taken away, so the names go with it then too.
 */
class PhoneNameSource(private val context: Context, private val typing: SwipeTyping) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "phone-names").apply { isDaemon = true } }
    @Volatile private var apps: Map<String, String> = emptyMap()
    @Volatile private var contacts: Map<String, String> = emptyMap()
    private var started = false
    private var watchingContacts = false

    private val packages = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshApps()
    }
    private val contactChanges = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) = refreshContacts()
    }
    private val settingChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == TypingSettings.CONTACT_NAMES) refreshContacts()
    }

    fun start() {
        if (started) return
        started = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        context.registerReceiver(packages, filter, Context.RECEIVER_NOT_EXPORTED)
        TypingSettings.settings(context).registerOnSharedPreferenceChangeListener(settingChanged)
        // The base list must be loaded to leave out the words that it already has.
        typing.whenReady { _, _, _ ->
            refreshApps()
            refreshContacts()
        }
    }

    fun stop() {
        if (!started) return
        started = false
        context.unregisterReceiver(packages)
        TypingSettings.settings(context).unregisterOnSharedPreferenceChangeListener(settingChanged)
        watchContacts(false)
    }

    private fun refreshApps() = worker.execute {
        apps = try {
            PhoneNames.words(appLabels(), typing::inBaseList)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Could not read the app names", error)
            emptyMap()
        }
        publish()
    }

    private fun refreshContacts() = worker.execute {
        val allowed = contactsAllowed(context)
        contacts = if (allowed) try {
            PhoneNames.words(contactNames(), typing::inBaseList)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Could not read the contacts' names", error)
            emptyMap()
        } else emptyMap()
        main.post { if (started) watchContacts(allowed) }
        publish()
    }

    private fun publish() = typing.phoneNames(PhoneNames.merged(apps, contacts))

    /** The labels of the apps that the home screen shows, which the manifest's launcher query lets the keyboard see. */
    private fun appLabels(): List<String> {
        val packages = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packages.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0L))
            .filter { it.activityInfo.packageName != context.packageName }
            .map { it.loadLabel(packages).toString() }
    }

    /** Each contact's given and family name. Nothing else about a contact is read. */
    private fun contactNames(): List<String> {
        val names = ArrayList<String>()
        val columns = arrayOf(
            ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
            ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
        )
        context.contentResolver.query(ContactsContract.Data.CONTENT_URI, columns,
            "${ContactsContract.Data.MIMETYPE} = ?", arrayOf(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE),
            null)?.use { rows ->
            while (rows.moveToNext()) for (column in columns.indices) rows.getString(column)?.let(names::add)
        }
        return names
    }

    private fun watchContacts(watch: Boolean) {
        if (watch == watchingContacts) return
        watchingContacts = watch
        if (watch) context.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, contactChanges)
        else context.contentResolver.unregisterContentObserver(contactChanges)
    }

    companion object {
        private const val TAG = "PhoneNames"

        /** The setting is on and Android allows reading contacts. */
        fun contactsAllowed(context: Context) = TypingSettings.contactNames(context)
            && context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }
}
