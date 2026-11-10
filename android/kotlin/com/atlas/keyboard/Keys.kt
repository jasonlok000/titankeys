package com.atlas.keyboard

import android.graphics.Paint
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.atlas.engine.EmojiPages

/** Characters printed on the Titan keys, read from the keyboard's own character map. */
object Keys {
    private val characterMap: KeyCharacterMap
        get() = InputDevice.getDeviceIds().asSequence().mapNotNull(InputDevice::getDevice)
            .firstOrNull { it.name == "TitanKey" }?.keyCharacterMap ?: KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)

    /** The character a touchpad tap on a letter key types with the given modifiers. */
    fun letter(key: String, metaState: Int, map: KeyCharacterMap = characterMap): String? {
        val code = map.get(KeyEvent.KEYCODE_A + (key[0] - 'A'), metaState)
        // Accent keys combine with the next character instead of typing one.
        return if (code == 0 || code and KeyCharacterMap.COMBINING_ACCENT != 0) null else String(Character.toChars(code))
    }

    /**
     * The groups of symbols in the Sym panel, in order. The most used come first, and the other groups go by kind. A group's
     * first symbol is its mark in the strip of groups.
     */
    private val SYMBOL_GROUPS = listOf(
        "Common" to "&%£$€=×÷;…—•°[]{}<>\\|~^`.,:!?\"'@#*_-()/¢¥§¶±≠≈≤≥√∞™®©†¿¡«»‹›“”‘’№",
        "Punctuation" to "–·‡‰‱′″‴‽※⁂¦‖¨´¯¸ªº‚„",
        "Currency" to "₹₩₽₺₫₱₪₦₴₿฿ƒ¤₡₲₸₭",
        "Numbers" to "½⅓⅔¼¾⅛⅜⅝⅞¹²³⁴⁵⁶⁷⁸⁹⁰ⁿ₀₁₂₃₄₅₆₇₈₉",
        "Maths" to "∑∓∏∫∂∆∇∝∈∉∋∩∪⊂⊃∀∃∄∅¬∧∨⊕⊗≡≢≪≫∼≃≅∴∵∠⊥∥",
        "Greek" to "αβγδεζηθκλμνξπρστφχψωΓΘΛΞΠΦΨΩ",
        "Arrows" to "←→↑↓↔↕↖↗↘↙⇐⇒⇑⇓⇔↩↪↺↻➜",
        "Shapes" to "★☆●○◎■□▪▫▲△▼▽►◄◆◇♠♣♥♦♤♧♡♢♩♪♫♬♭♮♯✓✗☐☑☒✦✧❖⌘⌥⇧⏎",
    )

    /** The symbols that the keyboard has no key for and that the phone can draw, in their groups for the Sym panel. */
    fun symbolGroups(): List<EmojiPages.Group> {
        val map = characterMap
        val printed = HashSet<String>()
        for (letter in 'A'..'Z') {
            letter(letter.toString(), 0, map)?.let(printed::add)
            letter(letter.toString(), KeyEvent.META_ALT_ON, map)?.let(printed::add)
        }
        "0123456789()_-+@*/:#'\"?!,.".forEach { printed += it.toString() }
        val paint = Paint()
        return SYMBOL_GROUPS.mapNotNull { (name, symbols) ->
            val shown = symbols.map { it.toString() }.filter { it !in printed && paint.hasGlyph(it) }.distinct()
            if (shown.isEmpty()) null else EmojiPages.Group(name, shown)
        }
    }
}
