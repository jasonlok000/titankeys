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
        /** A send that Shift asked for, which uses up a Shift that was pressed once. */
        ShiftSend,
    }

    /** How a message goes out in an app. */
    enum class SendMethod(val id: String) {
        /** The field's editor action. An app that did not build this in returns success and does nothing. */
        EditorAction("action"),
        /** A plain Enter key, for apps that send on the key and not on the action. */
        PlainEnter("key");

        companion object {
            fun of(id: String?) = entries.firstOrNull { it.id == id }
        }
    }

    /** A chat app that the setting knows without being told. */
    data class ChatApp(val packageName: String, val name: String, val method: SendMethod)

    fun press(inputType: Int, imeOptions: Int, actionId: Int, packageName: String?, shift: Boolean, ctrl: Boolean,
              added: Map<String, SendMethod> = emptyMap()): Press {
        // A new line means nothing in a single-line field, so Go, Search, Next and Done stay as they are.
        if (this == AppDecides || !multiLine(inputType)) return Press.FieldDecides
        val sends = packageName in BUILT_IN_METHODS || packageName in added || declaresSend(imeOptions, actionId)
        return when {
            // A half-written note must never be sent somewhere.
            !sends -> if (ctrl) Press.FieldDecides else Press.NewLine
            ctrl -> Press.Send
            // Shift does the opposite of the setting.
            this == AlwaysNewLine -> if (shift) Press.ShiftSend else Press.NewLine
            shift -> Press.ShiftNewLine
            else -> Press.Send
        }
    }

    companion object {
        private const val MAX_RECENT_APPS = 20

        /**
         * Apps with no declared action, such as Jetpack Compose fields, return success from the editor action and do nothing,
         * so they get the plain key. Discord needs it too. Added apps can change this on the phone.
         */
        val BUILT_IN = listOf(
            ChatApp("com.whatsapp", "WhatsApp", SendMethod.EditorAction),
            ChatApp("org.telegram.messenger", "Telegram", SendMethod.EditorAction),
            ChatApp("org.thoughtcrime.securesms", "Signal", SendMethod.EditorAction),
            ChatApp("com.discord", "Discord", SendMethod.PlainEnter),
            ChatApp("im.vector.app", "Element", SendMethod.EditorAction),
            ChatApp("com.google.android.apps.messaging", "Google Messages", SendMethod.EditorAction),
            ChatApp("ch.threema.app", "Threema", SendMethod.EditorAction),
            ChatApp("ch.threema.app.libre", "Threema Libre", SendMethod.EditorAction),
            ChatApp("com.instagram.android", "Instagram", SendMethod.EditorAction),
            ChatApp("com.facebook.orca", "Messenger", SendMethod.EditorAction),
            ChatApp("com.openai.chatgpt", "ChatGPT", SendMethod.PlainEnter),
            ChatApp("com.anthropic.claude", "Claude", SendMethod.PlainEnter),
            ChatApp("com.reddit.frontpage", "Reddit", SendMethod.PlainEnter),
        )
        private val BUILT_IN_METHODS = BUILT_IN.associate { it.packageName to it.method }
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

        /** How an app sends. What the user chose for an app comes before the built-in method. */
        fun sendMethod(packageName: String?, added: Map<String, SendMethod> = emptyMap()) =
            added[packageName] ?: BUILT_IN_METHODS[packageName] ?: SendMethod.EditorAction

        /** Reads the saved apps, each saved as "package=method". A damaged entry is left out. */
        fun apps(saved: Set<String>): Map<String, SendMethod> = saved.mapNotNull { entry ->
            val packageName = entry.substringBefore('=', "")
            val method = SendMethod.of(entry.substringAfter('=', ""))
            if (packageName.isEmpty() || method == null) null else packageName to method
        }.toMap()

        /** The saved apps with [packageName] changed to [method], or removed when [method] is null. */
        fun savedApps(saved: Set<String>, packageName: String, method: SendMethod?): Set<String> =
            saved.filterNot { it.substringBefore('=', "") == packageName }.toSet() +
                listOfNotNull(method?.let { "$packageName=${it.id}" })

        /** The most recent apps first, with [packageName] now the newest. */
        fun recentApps(recent: List<String>, packageName: String): List<String> =
            (listOf(packageName) + recent.filter { it != packageName }).take(MAX_RECENT_APPS)

        /** The setting can apply in a multi-line field. A private field leaves no record that it was used. */
        fun recordsApp(inputType: Int, imeOptions: Int) = multiLine(inputType)
            && imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0
    }
}
