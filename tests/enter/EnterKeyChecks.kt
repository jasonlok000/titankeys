package com.atlas.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.atlas.keyboard.EnterKey.Press
import com.atlas.keyboard.EnterKey.SendMethod

private const val SINGLE_LINE = InputType.TYPE_CLASS_TEXT
private const val MULTI_LINE = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
private const val NO_ENTER = EditorInfo.IME_FLAG_NO_ENTER_ACTION
private const val SEND = EditorInfo.IME_ACTION_SEND
private const val NOTES = "com.example.notes"
private const val MAIL = "com.example.mail"
private const val ADDED = "com.example.chat"

private val chatApps = listOf("com.whatsapp", "org.telegram.messenger", "org.thoughtcrime.securesms", "com.discord",
    "im.vector.app", "com.google.android.apps.messaging", "ch.threema.app", "ch.threema.app.libre",
    "com.instagram.android", "com.facebook.orca", "com.openai.chatgpt", "com.anthropic.claude", "com.reddit.frontpage")
private val plainEnterApps = listOf("com.discord", "com.openai.chatgpt", "com.anthropic.claude", "com.reddit.frontpage")

/** Every action Android gives a field, with and without the flag that multi-line text views always have. */
private val allOptions = listOf(EditorInfo.IME_ACTION_UNSPECIFIED, EditorInfo.IME_ACTION_NONE, EditorInfo.IME_ACTION_GO,
    EditorInfo.IME_ACTION_SEARCH, SEND, EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_DONE,
    EditorInfo.IME_ACTION_PREVIOUS).flatMap { listOf(it, it or NO_ENTER) }

fun main() {
    defaultOption()
    alwaysSendInChatApps()
    alwaysNewLineInChatApps()
    appDecidesEverywhere()
    singleLineFields()
    declaredSend()
    notesNeverSend()
    otherInputTypes()
    sendActions()
    sendMethods()
    addedApps()
    savedApps()
    recentApps()
    println("Enter key checks passed")
}

private fun same(actual: Any?, expected: Any?, message: String) {
    if (actual != expected) throw AssertionError("$message\n  expected: $expected\n  actual:   $actual")
}

private fun EnterKey.press(
    packageName: String?,
    inputType: Int = MULTI_LINE,
    imeOptions: Int = NO_ENTER,
    actionId: Int = 0,
    shift: Boolean = false,
    ctrl: Boolean = false,
    added: Map<String, SendMethod> = emptyMap(),
) = press(inputType, imeOptions, actionId, packageName, shift, ctrl, added)

private fun defaultOption() {
    same(EnterKey.of(null), EnterKey.AlwaysSend, "Always send is the default")
    same(EnterKey.of("unknown"), EnterKey.AlwaysSend, "An unknown value falls back to the default")
    for (option in EnterKey.entries) same(EnterKey.of(option.id), option, "Each option must read back")
}

private fun alwaysSendInChatApps() = chatApps.forEach { app ->
    val key = EnterKey.AlwaysSend
    same(key.press(app), Press.Send, "Enter sends in $app")
    same(key.press(app, shift = true), Press.ShiftNewLine, "Shift + Enter adds a new line in $app")
    same(key.press(app, ctrl = true), Press.Send, "Ctrl + Enter also sends in $app")
    same(key.press(app, shift = true, ctrl = true), Press.Send, "Ctrl wins over Shift in $app")
}

private fun alwaysNewLineInChatApps() = chatApps.forEach { app ->
    val key = EnterKey.AlwaysNewLine
    same(key.press(app), Press.NewLine, "Enter adds a new line in $app")
    same(key.press(app, shift = true), Press.ShiftSend, "Shift + Enter sends in $app")
    same(key.press(app, ctrl = true), Press.Send, "Ctrl + Enter sends in $app")
    same(key.press(app, shift = true, ctrl = true), Press.Send, "Ctrl wins over Shift in $app")
}

private fun appDecidesEverywhere() {
    for (app in chatApps + NOTES) for (options in allOptions) for (inputType in listOf(SINGLE_LINE, MULTI_LINE))
        for (shift in listOf(false, true)) for (ctrl in listOf(false, true))
            same(EnterKey.AppDecides.press(app, inputType, options, shift = shift, ctrl = ctrl), Press.FieldDecides,
                "App decides changes nothing in $app")
}

private fun singleLineFields() {
    for (key in EnterKey.entries) for (app in chatApps + NOTES) for (options in allOptions)
        for (shift in listOf(false, true)) for (ctrl in listOf(false, true))
            same(key.press(app, SINGLE_LINE, options, shift = shift, ctrl = ctrl), Press.FieldDecides,
                "A single-line field keeps its own action in $app with $key")
}

private fun declaredSend() {
    for (options in listOf(SEND, SEND or NO_ENTER)) {
        same(EnterKey.AlwaysSend.press(MAIL, imeOptions = options), Press.Send, "A field that declares Send sends")
        same(EnterKey.AlwaysSend.press(MAIL, imeOptions = options, shift = true), Press.ShiftNewLine, "Shift + Enter adds a new line")
        same(EnterKey.AlwaysNewLine.press(MAIL, imeOptions = options), Press.NewLine, "Always new line covers all multi-line fields")
        same(EnterKey.AlwaysNewLine.press(MAIL, imeOptions = options, ctrl = true), Press.Send, "Ctrl + Enter sends")
        same(EnterKey.AlwaysNewLine.press(MAIL, imeOptions = options, shift = true), Press.ShiftSend, "Shift + Enter sends")
    }
    same(EnterKey.AlwaysSend.press(MAIL, actionId = SEND), Press.Send, "A custom action of Send counts as declaring Send")
}

private fun notesNeverSend() {
    val notes = allOptions.filter { it and EditorInfo.IME_MASK_ACTION != SEND }
    for (key in listOf(EnterKey.AlwaysSend, EnterKey.AlwaysNewLine)) for (app in listOf(NOTES, null)) for (options in notes) {
        same(key.press(app, imeOptions = options), Press.NewLine, "Enter adds a new line in a note with $key")
        same(key.press(app, imeOptions = options, shift = true), Press.NewLine, "Shift + Enter adds a new line in a note with $key")
        same(key.press(app, imeOptions = options, ctrl = true), Press.FieldDecides, "Ctrl + Enter stays with the app in a note with $key")
        same(key.press(app, imeOptions = options, shift = true, ctrl = true), Press.FieldDecides, "A note never sends with $key")
    }
}

private fun otherInputTypes() {
    val app = chatApps.first()
    val number = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_TEXT_FLAG_MULTI_LINE
    same(EnterKey.AlwaysSend.press(app, number), Press.FieldDecides, "Only text fields can be multi-line")
    // This flag asks for many lines only in a full-screen keyboard, and the view itself has one line.
    val fullScreenOnly = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE
    same(EnterKey.AlwaysSend.press(app, fullScreenOnly), Press.FieldDecides, "A field for a full-screen keyboard has one line")
    same(EnterKey.AlwaysSend.press(app, 0), Press.FieldDecides, "A field with no type has no setting to apply")
}

private fun sendActions() {
    same(EnterKey.sendAction(NO_ENTER, 0), SEND, "A field with no Enter action sends with Send")
    same(EnterKey.sendAction(EditorInfo.IME_ACTION_DONE or NO_ENTER, 0), SEND, "The flag hides the action of the field")
    same(EnterKey.sendAction(EditorInfo.IME_ACTION_UNSPECIFIED, 0), SEND, "No action at all sends with Send")
    same(EnterKey.sendAction(EditorInfo.IME_ACTION_NONE, 0), SEND, "An action of None sends with Send")
    same(EnterKey.sendAction(SEND, 0), SEND, "A declared Send stays Send")
    for (action in listOf(EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_NEXT,
        EditorInfo.IME_ACTION_DONE, EditorInfo.IME_ACTION_PREVIOUS))
        same(EnterKey.sendAction(action, 0), action, "The field's own action runs")
    same(EnterKey.sendAction(EditorInfo.IME_ACTION_DONE, EditorInfo.IME_ACTION_GO), EditorInfo.IME_ACTION_GO, "A custom action comes first")
    same(EnterKey.sendAction(EditorInfo.IME_ACTION_DONE, 1234), SEND, "A custom action with no known meaning sends with Send")
}

private fun sendMethods() {
    for (app in plainEnterApps) same(EnterKey.sendMethod(app), SendMethod.PlainEnter, "$app sends with a plain Enter key")
    for (app in chatApps - plainEnterApps.toSet()) same(EnterKey.sendMethod(app), SendMethod.EditorAction, "$app uses the editor action")
    same(EnterKey.sendMethod("com.whatsapp"), SendMethod.EditorAction, "WhatsApp already works with the editor action")
    same(EnterKey.sendMethod(null), SendMethod.EditorAction, "No package means no plain key")
    same(EnterKey.sendMethod(NOTES), SendMethod.EditorAction, "An app that nobody added uses the editor action")
    same(EnterKey.BUILT_IN.map { it.packageName }.sorted(), chatApps.sorted(), "The settings page lists every built-in app")
}

private fun addedApps() {
    val added = mapOf(ADDED to SendMethod.PlainEnter)
    same(EnterKey.AlwaysNewLine.press(ADDED, added = added), Press.NewLine, "Enter adds a new line in an added app")
    same(EnterKey.AlwaysNewLine.press(ADDED, shift = true, added = added), Press.ShiftSend, "Shift + Enter sends in an added app")
    same(EnterKey.AlwaysNewLine.press(ADDED, ctrl = true, added = added), Press.Send, "Ctrl + Enter sends in an added app")
    same(EnterKey.AlwaysSend.press(ADDED, added = added), Press.Send, "Enter sends in an added app")
    same(EnterKey.AlwaysSend.press(ADDED, shift = true, added = added), Press.ShiftNewLine, "Shift + Enter adds a new line in an added app")
    same(EnterKey.AlwaysNewLine.press(ADDED, shift = true), Press.NewLine, "After it is removed, the app never sends")
    same(EnterKey.AppDecides.press(ADDED, shift = true, added = added), Press.FieldDecides, "App decides changes nothing in an added app")
    same(EnterKey.AlwaysSend.press(ADDED, SINGLE_LINE, added = added), Press.FieldDecides, "A single-line field keeps its action in an added app")
    for (key in listOf(EnterKey.AlwaysSend, EnterKey.AlwaysNewLine)) {
        same(key.press(NOTES, added = added), Press.NewLine, "Adding one app does not make a note send with $key")
        same(key.press(NOTES, shift = true, added = added), Press.NewLine, "Shift + Enter in a note still adds a new line with $key")
    }
    same(EnterKey.sendMethod(ADDED, added), SendMethod.PlainEnter, "An added app uses the method chosen for it")
    same(EnterKey.sendMethod(ADDED, mapOf(ADDED to SendMethod.EditorAction)), SendMethod.EditorAction, "The other method can be chosen")
    same(EnterKey.sendMethod("com.openai.chatgpt", mapOf("com.openai.chatgpt" to SendMethod.EditorAction)), SendMethod.EditorAction,
        "A choice comes before the built-in method")
    same(EnterKey.sendMethod("com.whatsapp", added), SendMethod.EditorAction, "Adding another app changes nothing for WhatsApp")
}

private fun savedApps() {
    same(EnterKey.apps(setOf("a.b=key", "c.d=action")), mapOf("a.b" to SendMethod.PlainEnter, "c.d" to SendMethod.EditorAction), "Saved apps read back")
    same(EnterKey.apps(setOf("a.b=other", "=key", "no.method", "c.d=")), emptyMap<String, SendMethod>(), "A damaged entry is left out")
    same(EnterKey.apps(emptySet()), emptyMap<String, SendMethod>(), "No saved apps")
    val one = EnterKey.savedApps(emptySet(), "a.b", SendMethod.PlainEnter)
    same(one, setOf("a.b=key"), "An app is added")
    same(EnterKey.savedApps(one, "a.b", SendMethod.EditorAction), setOf("a.b=action"), "An app has one method at a time")
    same(EnterKey.savedApps(one, "c.d", SendMethod.EditorAction), setOf("a.b=key", "c.d=action"), "Other apps stay")
    same(EnterKey.savedApps(one, "a.b", null), emptySet<String>(), "An app is removed")
}

private fun recentApps() {
    same(EnterKey.recentApps(listOf("a", "b"), "c"), listOf("c", "a", "b"), "The newest app comes first")
    same(EnterKey.recentApps(listOf("a", "b"), "b"), listOf("b", "a"), "An app shows one time")
    same(EnterKey.recentApps(List(40) { "app$it" }, "new").size, 20, "The list has a limit")
    same(EnterKey.recordsApp(MULTI_LINE, NO_ENTER), true, "A multi-line field can use the setting")
    same(EnterKey.recordsApp(SINGLE_LINE, 0), false, "A single-line field cannot use the setting")
    same(EnterKey.recordsApp(MULTI_LINE, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING), false, "A private field is not recorded")
}
