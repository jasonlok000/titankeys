package com.atlas.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * The Enter key setting: one rule for every app. The way a message is sent and the list of chat apps
 * come from Pastiera's Enter handling (GPL-3.0, https://github.com/palsoftware/pastiera).
 */
enum class EnterKey(val id: String) {
    AlwaysSend("send"),
    AlwaysNewLine("newline"),
    AppDecides("app");

    /** What one press of Enter does. */
    enum class Press {
        /** The setting does not apply, so Enter follows the field and Ctrl + Enter stays a shortcut for the app. */
        FieldDecides,
        NewLine,
        /** A new line that Shift asked for, which uses up a Shift that was pressed once. */
        ShiftNewLine,
        Send,
    }

    fun press(inputType: Int, imeOptions: Int, actionId: Int, packageName: String?, shift: Boolean, ctrl: Boolean): Press {
        // A new line means nothing in a single-line field, so Go, Search, Next and Done stay as they are.
        if (this == AppDecides || !multiLine(inputType)) return Press.FieldDecides
        val sends = packageName in CHAT_APPS || declaresSend(imeOptions, actionId)
        return when {
            // A half-written note must never be sent somewhere.
            !sends -> if (ctrl) Press.FieldDecides else Press.NewLine
            ctrl -> Press.Send
            this == AlwaysNewLine -> Press.NewLine
            shift -> Press.ShiftNewLine
            else -> Press.Send
        }
    }

    companion object {
        private const val DISCORD = "com.discord"
        private val CHAT_APPS = setOf(
            "com.whatsapp",
            "org.telegram.messenger",
            "org.thoughtcrime.securesms",
            DISCORD,
            "im.vector.app",
            "com.google.android.apps.messaging",
            "ch.threema.app",
            "ch.threema.app.libre",
            "com.instagram.android",
            "com.facebook.orca",
        )
        private val FIELD_ACTIONS = setOf(EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_DONE, EditorInfo.IME_ACTION_PREVIOUS)

        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: AlwaysSend

        private fun multiLine(inputType: Int) = inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT
            && inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0

        /** Android sets IME_FLAG_NO_ENTER_ACTION on every multi-line text view, so a field can have the flag and still declare Send. */
        private fun declaresSend(imeOptions: Int, actionId: Int) = imeOptions and EditorInfo.IME_MASK_ACTION == EditorInfo.IME_ACTION_SEND
            || actionId == EditorInfo.IME_ACTION_SEND

        /** The field's own action, or Send when it reports none. */
        fun sendAction(imeOptions: Int, actionId: Int): Int {
            if (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return EditorInfo.IME_ACTION_SEND
            val action = if (actionId != 0) actionId else imeOptions and EditorInfo.IME_MASK_ACTION
            return if (action in FIELD_ACTIONS) action else EditorInfo.IME_ACTION_SEND
        }

        /** Discord sends on a plain Enter key, not on an editor action. */
        fun sendsWithKey(packageName: String?) = packageName == DISCORD
    }
}
