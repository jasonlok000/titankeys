package com.titankeys.mozc

/**
 * Kana back to the romaji that Mozc's QWERTY table types them from (Hepburn, with Mozc's own spellings where it has them:
 * ん as nn, っ doubling the next consonant, ー as -, small kana with x). Used when a pick takes only part of the text: the
 * rest is hiragana, and Enter must still type it as romaji. tests/japanese types public sentences' readings through it.
 * Hiragana and katakana both; anything else (kanji, Latin) passes as it is.
 */
object KanaRomaji {
    private val TWO = mapOf(
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo", "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho", "しぇ" to "she",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho", "ちぇ" to "che", "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
        "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo", "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
        "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo", "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo", "じぇ" to "je", "ぢゃ" to "dya", "ぢゅ" to "dyu", "ぢょ" to "dyo",
        "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo", "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
        "ふぁ" to "fa", "ふぃ" to "fi", "ふぇ" to "fe", "ふぉ" to "fo", "てぃ" to "thi", "でぃ" to "dhi", "とぅ" to "twu",
        "どぅ" to "dwu", "うぃ" to "whi", "うぇ" to "whe", "うぉ" to "who", "ゔぁ" to "va", "ゔぃ" to "vi", "ゔぇ" to "ve",
        "ゔぉ" to "vo", "つぁ" to "tsa", "つぃ" to "tsi", "つぇ" to "tse", "つぉ" to "tso", "いぇ" to "ye",
    )
    private val ONE = mapOf(
        'あ' to "a", 'い' to "i", 'う' to "u", 'え' to "e", 'お' to "o", 'か' to "ka", 'き' to "ki", 'く' to "ku", 'け' to "ke",
        'こ' to "ko", 'さ' to "sa", 'し' to "shi", 'す' to "su", 'せ' to "se", 'そ' to "so", 'た' to "ta", 'ち' to "chi",
        'つ' to "tsu", 'て' to "te", 'と' to "to", 'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
        'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho", 'ま' to "ma", 'み' to "mi", 'む' to "mu",
        'め' to "me", 'も' to "mo", 'や' to "ya", 'ゆ' to "yu", 'よ' to "yo", 'ら' to "ra", 'り' to "ri", 'る' to "ru",
        'れ' to "re", 'ろ' to "ro", 'わ' to "wa", 'ゐ' to "wyi", 'ゑ' to "wye", 'を' to "wo", 'ん' to "nn", 'が' to "ga",
        'ぎ' to "gi", 'ぐ' to "gu", 'げ' to "ge", 'ご' to "go", 'ざ' to "za", 'じ' to "ji", 'ず' to "zu", 'ぜ' to "ze",
        'ぞ' to "zo", 'だ' to "da", 'ぢ' to "di", 'づ' to "du", 'で' to "de", 'ど' to "do", 'ば' to "ba", 'び' to "bi",
        'ぶ' to "bu", 'べ' to "be", 'ぼ' to "bo", 'ぱ' to "pa", 'ぴ' to "pi", 'ぷ' to "pu", 'ぺ' to "pe", 'ぽ' to "po",
        'ゔ' to "vu", 'ぁ' to "xa", 'ぃ' to "xi", 'ぅ' to "xu", 'ぇ' to "xe", 'ぉ' to "xo", 'ゃ' to "xya", 'ゅ' to "xyu",
        'ょ' to "xyo", 'ゎ' to "xwa", 'ゕ' to "xka", 'ゖ' to "xke", 'ー' to "-", '、' to ",", '。' to ".", '？' to "?",
        '！' to "!", '「' to "[", '」' to "]", '・' to "/", '〜' to "~",
    )

    /** Katakana as hiragana: the table is hiragana's. */
    private fun hiragana(c: Char) = if (c in 'ァ'..'ヶ') c - 0x60 else c

    /** The romaji that types [kana], or null if something in it isn't kana or a mark Mozc's table has. */
    fun of(kana: String): String? {
        val text = kana.map(::hiragana).joinToString("")
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == 'っ') {
                // A small tsu doubles the next consonant; before nothing, or a vowel, it's xtu.
                val next = romajiAt(text, i + 1)?.first
                if (next != null && next[0] !in "aeiou-,.?![]/~" && next[0] != 'n') out.append(next[0]) else out.append("xtu")
                i++
                continue
            }
            val (romaji, length) = romajiAt(text, i) ?: return null
            out.append(romaji)
            i += length
        }
        return out.toString()
    }

    private fun romajiAt(text: String, i: Int): Pair<String, Int>? {
        if (i >= text.length) return null
        if (i + 1 < text.length) TWO[text.substring(i, i + 2)]?.let { return it to 2 }
        return ONE[text[i]]?.let { it to 1 }
    }
}

/** Japanese marks for the punctuation keys, full-width by default as in Chinese: 、。「」・〜ー, and the full-width forms of the rest. */
/**
 * Whether a suggestion holds an emoji, to keep them off the next-word suggestions (owner and Orchestrator, 2026-11-17: as
 * Chinese has none, rime-cantonese's emoji being left out). Unicode's Emoji property: the emoji blocks from U+1F000, the
 * emoji among the symbols and dingbats ([BMP], from Unicode's emoji-data.txt), and the joiner and emoji variation selector
 * that build one. Text symbols such as ♪ and kaomoji such as m(_ _)m aren't emoji, and stay.
 */
object EmojiText {
    private val BMP = arrayOf(
        0x00A9..0x00A9, 0x00AE..0x00AE, 0x203C..0x203C, 0x2049..0x2049, 0x2122..0x2122, 0x2139..0x2139,
        0x2194..0x2199, 0x21A9..0x21AA, 0x231A..0x231B, 0x2328..0x2328, 0x23CF..0x23CF, 0x23E9..0x23F3,
        0x23F8..0x23FA, 0x24C2..0x24C2, 0x25AA..0x25AB, 0x25B6..0x25B6, 0x25C0..0x25C0, 0x25FB..0x25FE,
        0x2600..0x2604, 0x260E..0x260E, 0x2611..0x2611, 0x2614..0x2615, 0x2618..0x2618, 0x261D..0x261D,
        0x2620..0x2620, 0x2622..0x2623, 0x2626..0x2626, 0x262A..0x262A, 0x262E..0x262F, 0x2638..0x263A,
        0x2640..0x2640, 0x2642..0x2642, 0x2648..0x2653, 0x265F..0x2660, 0x2663..0x2663, 0x2665..0x2666,
        0x2668..0x2668, 0x267B..0x267B, 0x267E..0x267F, 0x2692..0x2697, 0x2699..0x2699, 0x269B..0x269C,
        0x26A0..0x26A1, 0x26A7..0x26A7, 0x26AA..0x26AB, 0x26B0..0x26B1, 0x26BD..0x26BE, 0x26C4..0x26C5,
        0x26C8..0x26C8, 0x26CE..0x26CF, 0x26D1..0x26D1, 0x26D3..0x26D4, 0x26E9..0x26EA, 0x26F0..0x26F5,
        0x26F7..0x26FA, 0x26FD..0x26FD, 0x2702..0x2702, 0x2705..0x2705, 0x2708..0x270D, 0x270F..0x270F,
        0x2712..0x2712, 0x2714..0x2714, 0x2716..0x2716, 0x271D..0x271D, 0x2721..0x2721, 0x2728..0x2728,
        0x2733..0x2734, 0x2744..0x2744, 0x2747..0x2747, 0x274C..0x274C, 0x274E..0x274E, 0x2753..0x2755,
        0x2757..0x2757, 0x2763..0x2764, 0x2795..0x2797, 0x27A1..0x27A1, 0x27B0..0x27B0, 0x27BF..0x27BF,
        0x2934..0x2935, 0x2B05..0x2B07, 0x2B1B..0x2B1C, 0x2B50..0x2B50, 0x2B55..0x2B55, 0x3030..0x3030,
        0x303D..0x303D, 0x3297..0x3297, 0x3299..0x3299,
    )

    fun has(text: String): Boolean = text.codePoints().anyMatch { c ->
        c in 0x1F000..0x1FAFF || c == 0x200D || c == 0xFE0F || BMP.any { c in it }
    }
}

object JapaneseMarks {
    fun of(char: Char): String = when (char) {
        ',' -> "、"
        '.' -> "。"
        '[' -> "「"
        ']' -> "」"
        '/' -> "・"
        '~' -> "〜"
        '-' -> "ー"
        in '!'..'~' -> (char + 0xFEE0).toString()
        else -> char.toString()
    }
}
