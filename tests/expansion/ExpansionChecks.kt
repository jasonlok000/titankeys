package com.atlas.keyboard

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import java.io.File

private fun check(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

private fun <T> same(actual: T, expected: T, message: String) = check(actual == expected, "$message: expected <$expected>, got <$actual>")

private val emoji = Expansions.readShortcodes(File("android/assets/shortcodes.txt").readLines().asSequence())
private val snippets = mapOf("addr" to "1 Main Street\nSpringfield", "sig" to "Best wishes,\nJason", "Sign" to "Signed")

fun main() {
    shortcodeList()
    snippetsExpandOnSpace()
    shortcodesExpandOnColon()
    codesNeedASpaceBefore()
    stripChoices()
    otherStarts()
    labels()
    exactApps()
    exactSymbols()
    exactWrappers()
    recentApps()
    fieldKinds()
    println("Expansion checks passed")
}

/**
 * Fields with no suggestions (issue 45): the bar, the panel, the keys and the cursor stay, but nothing types words, fixes,
 * adds, expands or learns. Only these kinds change, so a normal text field keeps everything.
 */
private fun fieldKinds() {
    val text = InputType.TYPE_CLASS_TEXT
    val quiet = listOf(
        "password" to Field.of(text or InputType.TYPE_TEXT_VARIATION_PASSWORD, 0),
        "visible password" to Field.of(text or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, 0),
        "web password" to Field.of(text or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, 0),
        "email" to Field.of(text or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, 0),
        "web email" to Field.of(text or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, 0),
        "number" to Field.of(InputType.TYPE_CLASS_NUMBER, 0),
        "phone" to Field.of(InputType.TYPE_CLASS_PHONE, 0),
        "date" to Field.of(InputType.TYPE_CLASS_DATETIME, 0),
        "exact typing" to Field.Exact,
    )
    for ((name, field) in quiet) {
        check(!field.suggestions, "$name: no suggestions")
        check(!field.words, "$name: no swipes, taps, delete flick, snippets or shortcodes")
        check(!field.autocorrect && !field.sentenceCapitalisation && !field.fullStops && !field.starters,
            "$name: no fixes, capitals or full stops")
        check(!field.learning, "$name: nothing learned")
    }
    check(Field.Exact.exact && quiet.dropLast(1).none { it.second.exact }, "only exact typing sends every key as a key event")
    val normal = Field.of(text or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 0)
    check(normal.words && normal.suggestions && normal.autocorrect && normal.learning && normal.fullStops && normal.sentenceCapitalisation,
        "a normal text field keeps everything")
    val address = Field.of(text or InputType.TYPE_TEXT_VARIATION_URI, 0)
    check(address.words && address.suggestions && !address.autocorrect, "an address bar keeps its suggestions")
    val private = Field.of(text, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
    check(private.words && private.suggestions && !private.learning, "a private field keeps its words but learns nothing")
}

private fun shortcodeList() {
    same(emoji["smile"], "😄", "smile")
    same(emoji["+1"], "👍", "+1")
    same(emoji["thumbsup"], "👍", "thumbsup")
    check(emoji.size > 1500, "the list has most names")
    check(emoji.keys.all { Expansions.validName(it) }, "every name can be typed")
    same(Expansions.readShortcodes(sequenceOf("# note", "", "😀 grinning Grin", "😃 grinning")), mapOf("grinning" to "😀", "grin" to "😀"),
        "the first emoji keeps a name, and names match in any case")
}

private fun snippetsExpandOnSpace() {
    val expansions = Expansions(snippets, ';', emoji)
    same(expansions.snippet("my address is ;addr"), Expansions.Expansion(";addr", "1 Main Street\nSpringfield ", ";addr "),
        "a shortcut becomes its text with the space, and Backspace gives back the shortcut and the space")
    same(expansions.snippet(";addr")?.text, "1 Main Street\nSpringfield ", "at the start of the text")
    same(expansions.snippet("x\n;sig")?.code, ";sig", "after a new line")
    same(expansions.snippet("hi ;ADDR")?.code, ";ADDR", "in any case")
    same(expansions.snippet("hi ;sign")?.text, "Signed ", "a saved shortcut with capitals")
    same(expansions.snippet("hi ;add"), null, "only a whole shortcut")
    same(expansions.snippet("hi ;addrs"), null, "not a longer one")
    same(expansions.snippet("hi ;"), null, "not the start character alone")
    same(expansions.snippet("hi addr"), null, "not without the start character")
    same(Expansions(emptyMap(), ';', emoji).snippet("hi ;addr"), null, "no snippets")
}

private fun shortcodesExpandOnColon() {
    val expansions = Expansions(emptyMap(), ';', emoji)
    same(expansions.shortcode("nice :smile"), Expansions.Expansion(":smile", "😄", ":smile:"),
        "the closing colon puts in the emoji, and Backspace gives back the whole code")
    same(expansions.shortcode(":+1")?.text, "👍", "a name with a plus")
    same(expansions.shortcode("ok :Smile")?.text, "😄", "in any case")
    same(expansions.shortcode("ok :smil"), null, "not a part of a name")
    same(expansions.shortcode("ok :notanemoji"), null, "not an unknown name")
    same(expansions.shortcode("ok :"), null, "not a colon alone")
    same(Expansions(emptyMap(), ';', emptyMap()).shortcode("ok :smile"), null, "shortcodes off")
}

private fun codesNeedASpaceBefore() {
    val expansions = Expansions(snippets, ';', emoji)
    same(expansions.shortcode("at 12:30"), null, "a time")
    same(expansions.shortcode("see http:smile"), null, "a colon inside a word")
    same(expansions.snippet("a;addr"), null, "a start character inside a word")
    same(expansions.shortcode("(:smile")?.text, "😄", "after an opening bracket")
    same(expansions.snippet("\";addr")?.code, ";addr", "after an opening quote")
    same(expansions.shortcode("ok:smile", freeMark = 2), Expansions.Expansion(":smile", " 😄", " :smile:"),
        "a colon that took the place of a space starts a code, and the space comes back")
    same(expansions.snippet("at;addr", freeMark = 2)?.text, " 1 Main Street\nSpringfield ", "the same for a snippet")
    same(expansions.shortcode("ok:smile", freeMark = 1), null, "only at that place")
}

private fun stripChoices() {
    val expansions = Expansions(snippets, ';', emoji)
    same(expansions.choices("hi ;", 3).map { it.expansion.code }, listOf(";", ";", ";"), "the start character offers the snippets")
    same(expansions.choices("hi ;si", 3).map { it.label }, listOf("Best wishes, Jason", "Signed"), "snippets that start with the letters")
    same(expansions.choices("hi ;sig", 3).first().label, "Best wishes, Jason", "an exact shortcut first")
    val chosen = expansions.choices("hi ;si", 3).first().expansion
    same(chosen, Expansions.Expansion(";si", "Best wishes,\nJason ", ";si"), "a chosen snippet replaces what was typed, and Backspace gives it back")
    same(expansions.choices("hi ;zz", 3), emptyList<Expansions.Choice>(), "no snippet")
    val faces = expansions.choices("so :smil", 3)
    check(faces.isNotEmpty() && faces.all { it.label == it.expansion.text && it.expansion.code == ":smil" }, "emoji for a part of a name")
    same(expansions.choices("so :smile", 3).first().label, "😄", "an exact name first")
    same(faces.map { it.label }.distinct().size, faces.size, "each emoji once")
    same(expansions.choices("so :s", 3), emptyList<Expansions.Choice>(), "one letter is too few")
    same(expansions.choices("so :)", 3), emptyList<Expansions.Choice>(), "a face of its own")
    same(expansions.choices("at 12:30", 3), emptyList<Expansions.Choice>(), "a time")
    same(Expansions(snippets, ';', emptyMap()).choices("so :smil", 3), emptyList<Expansions.Choice>(), "shortcodes off")
}

private fun otherStarts() {
    val expansions = Expansions(snippets, '/', emoji)
    same(expansions.snippet("go /addr")?.code, "/addr", "another start character")
    same(expansions.snippet("go ;addr"), null, "the old one no longer works")
    same(Expansions(snippets, '\\', emoji).snippet("\\sig")?.code, "\\sig", "a backslash")
}

private fun labels() {
    same(Expansions.label("  short  "), "short", "a short text")
    same(Expansions.label("a\nb\tc"), "a b c", "on one line")
    same(Expansions.label("a".repeat(40)).length, 24, "a long text is cut")
    check(Expansions.label("a".repeat(40)).endsWith("…"), "with a mark")
    check(Expansions.validName("addr") && Expansions.validName("e-mail_2+"), "valid shortcuts")
    check(!Expansions.validName("") && !Expansions.validName("my addr") && !Expansions.validName(";addr")
        && !Expansions.validName("a".repeat(41)), "shortcuts that are not valid")
}

private fun exactApps() {
    val apps = setOf("com.termux", "com.anydesk.anydeskandroid")
    check(ExactTyping.applies("com.termux", apps), "Termux")
    // AnyDesk's session view reports no type and no options, the same as a window with no field (phone read, issue 45).
    check(ExactTyping.applies("com.anydesk.anydeskandroid", apps), "every start in a listed app, also AnyDesk's session view")
    check(!ExactTyping.applies("com.android.chrome", apps), "an app that is not listed")
    check(!ExactTyping.applies(null, apps), "no app")
}

private fun exactSymbols() {
    val q = 'q'.code
    val hash = '#'.code
    same(ExactTyping.symbol(KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON, hash, q), "#", "Alt and a letter type its symbol")
    same(ExactTyping.symbol(0, q, q), null, "a letter goes as a key")
    same(ExactTyping.symbol(KeyEvent.META_SHIFT_ON, 'Q'.code, 'Q'.code), null, "Shift and a letter go as a key")
    same(ExactTyping.symbol(KeyEvent.META_ALT_ON or KeyEvent.META_CTRL_ON, hash, q), null, "Ctrl and Alt make a shortcut")
    same(ExactTyping.symbol(KeyEvent.META_ALT_ON, '\t'.code, '\t'.code), null, "Alt and Tab stay a shortcut")
    same(ExactTyping.symbol(KeyEvent.META_ALT_ON, 0, 0), null, "Alt and a key with no character")
    same(ExactTyping.symbol(KeyEvent.META_ALT_ON, q, q), null, "a key with no symbol stays a key")
}

private fun exactWrappers() {
    same(ExactTyping.wrappers(0), emptyList<Int>(), "a plain key")
    same(ExactTyping.wrappers(KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON), listOf(KeyEvent.KEYCODE_SHIFT_LEFT),
        "Shift goes as its own key too, for a remote desktop")
    same(ExactTyping.wrappers(KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON), listOf(KeyEvent.KEYCODE_CTRL_LEFT), "Ctrl")
    same(ExactTyping.wrappers(KeyEvent.META_SHIFT_ON or KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON),
        listOf(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_ALT_LEFT), "Shift, Ctrl and Alt, in that order")
}

private fun recentApps() {
    same(ExactTyping.recentApps(listOf("a", "b"), "b"), listOf("b", "a"), "an app moves to the front")
    same(ExactTyping.recentApps((1..12).map { "$it" }, "new").size, ExactTyping.MAX_RECENT_APPS, "the list stays short")
}
