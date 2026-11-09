package com.atlas.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.atlas.keyboard.EnterKey.Press

private const val SINGLE_LINE = InputType.TYPE_CLASS_TEXT
private const val MULTI_LINE = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
private const val NO_ENTER = EditorInfo.IME_FLAG_NO_ENTER_ACTION
private const val SEND = EditorInfo.IME_ACTION_SEND
private const val NOTES = "com.example.notes"
private const val MAIL = "com.example.mail"

private val chatApps = listOf("com.whatsapp", "org.telegram.messenger", "org.thoughtcrime.securesms", "com.discord",
    "im.vector.app", "com.google.android.apps.messaging", "ch.threema.app", "ch.threema.app.libre",
    "com.instagram.android", "com.facebook.orca")

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
    plainEnterForDiscord()
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
) = press(inputType, imeOptions, actionId, packageName, shift, ctrl)

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
    same(key.press(app, shift = true), Press.NewLine, "Shift has no meaning here, so it must not be used up in $app")
    same(key.press(app, ctrl = true), Press.Send, "Ctrl + Enter sends in $app")
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

private fun plainEnterForDiscord() {
    same(EnterKey.sendsWithKey("com.discord"), true, "Discord needs a plain Enter key")
    for (app in chatApps - "com.discord") same(EnterKey.sendsWithKey(app), false, "$app uses the editor action")
    same(EnterKey.sendsWithKey(null), false, "No package means no plain key")
}
