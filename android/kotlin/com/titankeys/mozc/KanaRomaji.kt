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
