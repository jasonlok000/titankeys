package com.titankeys.keyboard

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import com.titankeys.engine.TypingSettings

/** Reads each part of setup from the phone, and gives the Settings page that changes it. The app and the guide both use this. */
internal object PhoneSetup {
    fun enabled(context: Context) =
        context.getSystemService(InputMethodManager::class.java).enabledInputMethodList.any { it.component == keyboard(context) }

    fun selected(context: Context) =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let(ComponentName::unflattenFromString) == keyboard(context)

    fun overlay(context: Context) = Settings.canDrawOverlays(context)

    /** Phones without the touchpad have no Scroll assistant, so there it counts as on. */
    fun scrollAssistant() = TypingSettings.phoneScrollAssistant()

    fun done(context: Context, part: SetupPart) = when (part) {
        SetupPart.Enable -> enabled(context)
        SetupPart.Choose -> selected(context)
        SetupPart.Overlay -> overlay(context)
        SetupPart.ScrollAssistant -> scrollAssistant()
        SetupPart.Back -> BackService.enabled(context)
        // Phones without these keys count as done, so they never see the part (PhoneChecks.programmable).
        SetupPart.Keys -> PhoneChecks.programmable(context, PhoneChecks.Key.Sym) && PhoneChecks.programmable(context, PhoneChecks.Key.Fn)
        // Answered once in the guide, either way, or already on from Settings.
        SetupPart.Contacts -> skipped(context, SetupPart.Contacts) || TypingSettings.contactNames(context)
    }

    /**
     * The parts that are not done yet, in the order that setup asks for them. Accessibility is optional: after "Not now" on
     * its disclosure ([BackDisclosureActivity]), setup goes on without it, and Settings > Setup can still ask again. Sym and fn
     * can be skipped (owner, #72), and contact names are asked once (owner, #87). Those two count only for the guide's own setup
     * ([guide]): the front page and the playground leave them to Settings > Setup. See [setupToAsk].
     */
    fun missing(context: Context, guide: Boolean = false) =
        setupToAsk({ done(context, it) }, BackDisclosureActivity.declined(context), guide) { skipped(context, it) }

    /** Setup still has a part that the front page and the playground need: the one answer both read (lib/front.ts). */
    fun toDo(context: Context) = missing(context).isNotEmpty()

    private const val CONFIRMED = "setup_confirmed"
    private const val REPAIR_DUE = "setup_repair_due"
    private const val EDITING = "setup_editing"
    private const val EDIT_STARTED = "setup_edit_started"
    private const val EDIT_WINDOW_MILLIS = 15 * 60_000L

    private fun history(context: Context): SetupHistory {
        val prefs = preferences(context)
        return SetupHistory(prefs.getInt(CONFIRMED, 0) and SetupSetting.mask, prefs.getInt(REPAIR_DUE, 0) and SetupSetting.mask)
    }

    /** Observes the phone when an app window returns. Setup's own settings visit accounts for only the parts it opened. */
    fun observe(context: Context, returned: Boolean = true) {
        val prefs = preferences(context)
        val before = history(context)
        val oldEdit = prefs.getInt(EDITING, 0)
        val edit = oldEdit.takeIf { SystemClock.elapsedRealtime() - prefs.getLong(EDIT_STARTED, 0) in 0L until EDIT_WINDOW_MILLIS } ?: 0
        var unreadEdits = 0
        val after = SetupSetting.entries.fold(before) { tracked, setting ->
            val on = reading(context, setting)
            val edited = edit and setting.bit != 0
            if (edited && on == null) unreadEdits = unreadEdits or setting.bit
            tracked.observed(setting, on, edited)
        }
        if (after != before || returned && oldEdit != unreadEdits) prefs.edit()
            .putInt(CONFIRMED, after.confirmed).putInt(REPAIR_DUE, after.due)
            .apply {
                if (returned && unreadEdits == 0) remove(EDITING).remove(EDIT_STARTED)
                else if (returned) putInt(EDITING, unreadEdits)
            }.apply()
    }

    private fun reading(context: Context, setting: SetupSetting): Boolean? = try {
        when (setting) {
            SetupSetting.Keyboard -> context.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList
                ?.takeIf { it.isNotEmpty() }?.any { it.component == keyboard(context) }
            SetupSetting.DefaultKeyboard -> Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.let(ComponentName::unflattenFromString)?.let { it == keyboard(context) }
            SetupSetting.Overlay -> overlay(context)
            SetupSetting.ScrollAssistant -> if (PhoneChecks.property("ro.agui.touchpad_function") == "yes")
                when (PhoneChecks.property("persist.sys.scroll_assistant")) {
                    "1" -> true
                    "0" -> false
                    else -> null
                } else null
            SetupSetting.Accessibility -> BackService.enabled(context)
            SetupSetting.Sym -> PhoneChecks.keyCheck(context, PhoneChecks.Key.Sym)?.ok
            SetupSetting.Fn -> PhoneChecks.keyCheck(context, PhoneChecks.Key.Fn)?.ok
            SetupSetting.Contacts -> PhoneNameSource.contactsAllowed(context)
        }
    } catch (_: SecurityException) {
        null
    }

    fun repairDue(context: Context): Int {
        val due = history(context).due
        return SetupSetting.entries.fold(0) { bits, setting ->
            if (due and setting.bit != 0 && reading(context, setting) == false) bits or setting.bit else bits
        }
    }

    fun repairShown(context: Context, repairs: Int) = preferences(context).edit()
        .putInt(REPAIR_DUE, history(context).due and repairs.inv()).apply()

    fun mask(part: SetupPart) = when (part) {
        SetupPart.Enable -> SetupSetting.Keyboard.bit
        SetupPart.Choose -> SetupSetting.DefaultKeyboard.bit
        SetupPart.Overlay -> SetupSetting.Overlay.bit
        SetupPart.ScrollAssistant -> SetupSetting.ScrollAssistant.bit
        SetupPart.Back -> SetupSetting.Accessibility.bit
        SetupPart.Keys -> SetupSetting.Sym.bit or SetupSetting.Fn.bit
        SetupPart.Contacts -> SetupSetting.Contacts.bit
    }

    fun needsRepair(context: Context, part: SetupPart, repairs: Int): Boolean {
        val parts = mask(part) and repairs
        return SetupSetting.entries.any { parts and it.bit != 0 && reading(context, it) == false }
    }

    private fun editing(context: Context, part: SetupPart) {
        // Disabling the keyboard here can also make Android choose another default.
        val related = if (part == SetupPart.Enable) SetupSetting.DefaultKeyboard.bit else 0
        preferences(context).edit().putInt(EDITING, mask(part) or related)
            .putLong(EDIT_STARTED, SystemClock.elapsedRealtime()).apply()
    }

    fun declined(context: Context, part: SetupPart) {
        val kept = history(context).forget(mask(part))
        preferences(context).edit().putInt(CONFIRMED, kept.confirmed).putInt(REPAIR_DUE, kept.due).apply()
    }

    /** [part] was skipped in the guide's setup, or for contact names, answered: the guide doesn't ask again. */
    fun skipped(context: Context, part: SetupPart) = preferences(context).getBoolean(skipKey(part), false)

    fun skip(context: Context, part: SetupPart) {
        declined(context, part)
        preferences(context).edit().putBoolean(skipKey(part), true).apply()
    }

    private fun skipKey(part: SetupPart) = "skipped_${part.name.lowercase()}"

    /**
     * The Settings pages that change [part], best first: TitanKeys' own page with its switch where Android lets an app open it,
     * then Android's whole list (owner, 2026-11-13: "display over other apps we can't scroll to it the user has to find it
     * manually?"). Choosing the keyboard is a list over the screen, so it has none.
     */
    fun pages(context: Context, part: SetupPart): List<Intent> = when (part) {
        SetupPart.Enable -> listOf(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        SetupPart.Choose -> emptyList()
        // Since Android 11 ACTION_MANAGE_OVERLAY_PERMISSION ignores the package and opens the list. The hidden per-app action
        // opens TitanKeys' own page from adb; an app may not be allowed to start it.
        SetupPart.Overlay -> listOf(
            Intent(MANAGE_APP_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
        SetupPart.ScrollAssistant -> listOf(Intent().setClassName("com.agui.settings", "com.agui.settings.touchpad.ScrollAssistantActivity"))
        SetupPart.Back -> {
            val service = ComponentName(context, BackService::class.java).flattenToString()
            // The list, with TitanKeys highlighted where the phone supports it.
            val list = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .putExtra(":settings:fragment_args_key", service)
                .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", service) })
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                listOf(Intent(ACCESSIBILITY_DETAILS_SETTINGS).putExtra(Intent.EXTRA_COMPONENT_NAME, service), list)
            else listOf(list)
        }
        SetupPart.Keys -> listOf(PhoneChecks.shortcutKeysPage(context))
        // Android's own question, from an app screen: the guide starts ContactsPermissionActivity itself.
        SetupPart.Contacts -> emptyList()
    }

    /**
     * Opens the best page for [part] that Android allows, through [start], and notes whether it had to fall back to the whole
     * list, so that Settings > Setup can say what to find there ([onList]). Throws when no page opens at all.
     */
    fun open(context: Context, part: SetupPart, fromSetup: Boolean = true, start: (Intent) -> Unit) {
        val pages = pages(context, part)
        var refusal: RuntimeException? = null
        for ((index, page) in pages.withIndex()) {
            try {
                start(page)
            } catch (error: ActivityNotFoundException) {
                refusal = error
                continue
            } catch (error: SecurityException) {
                refusal = error
                continue
            }
            if (fromSetup) editing(context, part)
            if (part in LISTED) preferences(context).edit().putBoolean(onListKey(part), pages.size > 1 && index == pages.lastIndex).apply()
            return
        }
        throw refusal ?: ActivityNotFoundException("No page for $part")
    }

    /** The last time [part] opened, Android showed its whole list instead of TitanKeys' own page. */
    fun onList(context: Context, part: SetupPart) = preferences(context).getBoolean(onListKey(part), false)

    /** The parts whose own page Android may refuse, for a list instead. */
    private val LISTED = setOf(SetupPart.Overlay, SetupPart.Back)

    /** Hidden in Android's Settings: the overlay page for one app. */
    private const val MANAGE_APP_OVERLAY_PERMISSION = "android.settings.MANAGE_APP_OVERLAY_PERMISSION"

    /** Settings.ACTION_ACCESSIBILITY_DETAILS_SETTINGS (Android 13), by its value: the SDK TitanKeys compiles with lacks it. */
    private const val ACCESSIBILITY_DETAILS_SETTINGS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"

    private fun onListKey(part: SetupPart) = "on_list_${part.name.lowercase()}"

    /** Kept with the phone checks, so Reset literally everything clears it with them. */
    private fun preferences(context: Context) = context.getSharedPreferences("phone_checks", Context.MODE_PRIVATE)

    fun chooseKeyboard(context: Context, fromSetup: Boolean = true) {
        context.getSystemService(InputMethodManager::class.java).showInputMethodPicker()
        if (fromSetup) editing(context, SetupPart.Choose)
    }

    private fun keyboard(context: Context) = ComponentName(context, TitanKeysIme::class.java)
}
