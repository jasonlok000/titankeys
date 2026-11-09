package com.atlas.keyboard

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

    /** Symbols the keyboard has no key for, offered in the Sym panel. Less common ones fill the page, so no key is blank. */
    fun extraSymbols(): List<String> {
        val map = characterMap
        val printed = HashSet<String>()
        for (letter in 'A'..'Z') {
            letter(letter.toString(), 0, map)?.let(printed::add)
            letter(letter.toString(), KeyEvent.META_ALT_ON, map)?.let(printed::add)
        }
        "0123456789()_-+@*/:#'\"?!,.".forEach { printed += it.toString() }
        fun unprinted(symbols: String) = symbols.map { it.toString() }.filter { it !in printed }.distinct()
        return EmojiPages.fillLastPage(unprinted("&%£$€=×÷;…—•°[]{}<>\\|~^`.,:!?\"'@#*_-()/"), unprinted("¢¥§¶±≠≈≤≥√∞™®©†¿¡«»‹›“”‘’№"))
    }
}
