package com.atlas.keyboard

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import com.atlas.engine.TypingSettings

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
    }

    /**
     * The parts that are not done yet, in the order that setup asks for them. Accessibility is optional: after "No thanks" on
     * its disclosure ([BackDisclosureActivity]), setup goes on without it, and Settings > Setup can still ask again.
     */
    fun missing(context: Context) = SetupPart.entries.filterNot {
        done(context, it) || it == SetupPart.Back && BackDisclosureActivity.declined(context)
    }

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
    }

    /**
     * Opens the best page for [part] that Android allows, through [start], and notes whether it had to fall back to the whole
     * list, so that Settings > Setup can say what to find there ([onList]). Throws when no page opens at all.
     */
    fun open(context: Context, part: SetupPart, start: (Intent) -> Unit) {
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

    fun chooseKeyboard(context: Context) = context.getSystemService(InputMethodManager::class.java).showInputMethodPicker()

    private fun keyboard(context: Context) = ComponentName(context, AtlasIme::class.java)
}
