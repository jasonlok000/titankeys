package com.atlas.keyboard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/** Reads each part of setup from the phone, and gives the Settings page that changes it. The app and the guide both use this. */
internal object PhoneSetup {
    fun enabled(context: Context) =
        context.getSystemService(InputMethodManager::class.java).enabledInputMethodList.any { it.component == keyboard(context) }

    fun selected(context: Context) =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let(ComponentName::unflattenFromString) == keyboard(context)

    fun overlay(context: Context) = Settings.canDrawOverlays(context)

    /** Phones without the touchpad have no Scroll assistant, so there it counts as on. */
    fun scrollAssistant(): Boolean {
        val get = Class.forName("android.os.SystemProperties").getMethod("get", String::class.java, String::class.java)
        if (get.invoke(null, "ro.agui.touchpad_function", "") != "yes") return true
        return get.invoke(null, "persist.sys.scroll_assistant", "0") == "1"
    }

    fun done(context: Context, part: SetupPart) = when (part) {
        SetupPart.Enable -> enabled(context)
        SetupPart.Choose -> selected(context)
        SetupPart.Overlay -> overlay(context)
        SetupPart.ScrollAssistant -> scrollAssistant()
        SetupPart.Back -> BackService.enabled(context)
    }

    /** The parts that are not done yet, in the order that setup asks for them. */
    fun missing(context: Context) = SetupPart.entries.filterNot { done(context, it) }

    /** The Settings page that changes [part]. Choosing the keyboard is a list over the screen, so it has no page. */
    fun page(context: Context, part: SetupPart): Intent? = when (part) {
        SetupPart.Enable -> Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
        SetupPart.Choose -> null
        SetupPart.Overlay -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        SetupPart.ScrollAssistant -> Intent().setClassName("com.agui.settings", "com.agui.settings.touchpad.ScrollAssistantActivity")
        SetupPart.Back -> {
            // The page for a single service is closed to apps, so the list opens with TitanKeys highlighted.
            val service = ComponentName(context, BackService::class.java).flattenToString()
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .putExtra(":settings:fragment_args_key", service)
                .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", service) })
        }
    }

    fun chooseKeyboard(context: Context) = context.getSystemService(InputMethodManager::class.java).showInputMethodPicker()

    private fun keyboard(context: Context) = ComponentName(context, AtlasIme::class.java)
}
