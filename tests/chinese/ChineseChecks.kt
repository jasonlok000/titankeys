package com.titankeys.keyboard

import java.io.File

/**
 * Chinese on the bar (docs/languages.md, "Chinese on the bar"): the row's places, the bigger menu's pages, the keys that pick,
 * the marks and the English word. The rules are plain Kotlin (keyboard/Chinese.kt), so they run here without the phone.
 */
fun main() {
    previewLegendsUseTheGuide()
    radicalPreviewFollowsTheFingerAfterTheFirstRest()
    theMenuOpensOnlyWithMore()
    aChoiceIsAsWideAsItNeeds()
    theRowFillsWToOInOrder()
    qAndPHaveNoChoice()
    theKeysThatPick()
    theMenuFillsThreeRowsAPage()
    theMenuTurnsItsPages()
    pShowsTheMenusPage()
    punctuationAndQuotes()
    aSwipeUpOpensTheMenu()
    anEnglishWordOnlyWhenRimeCantRead()
    theEnglishWordIsNeverFirst()
    englishAlternativesUseTheirShownIndex()
    println("Chinese checks passed")
}

private fun check(ok: Boolean, what: String) {
    if (!ok) throw AssertionError(what)
}

private fun <T> same(expected: T, actual: T, what: String) {
    if (expected != actual) throw AssertionError("$what\n  expected: $expected\n  actual:   $actual")
}

fun aChoiceIsAsWideAsItNeeds() {
    same(1, ChineseRow.keys("你"), "one character: one key")
    same(1, ChineseRow.keys("你好"), "two: one key")
    same(2, ChineseRow.keys("屌你老母"), "four: two keys, as the owner asked")
    same(2, ChineseRow.keys("廣東話"), "three: two keys")
    same(3, ChineseRow.keys("我哋今日去飲"), "six: three")
    same(2, ChineseRow.keys("hello"), "a Latin letter is half as wide: hello takes two")
    same(1, ChineseRow.keys("ok"), "and ok one")
    same(1, ChineseRow.keys("，"), "a full-width mark is a character")
    same(1, ChineseRow.keys(""), "never none")
}

fun theRowFillsWToOInOrder() {
    val singles = (1..10).map { "字$it" }
    same((1..8).map { it..it } + listOf(null, null), ChineseRow.place(singles), "eight one-key choices fill W to O, the rest wait for the menu")
    same(listOf(1..2, 3..3, 4..4), ChineseRow.place(listOf("屌你老母", "你", "好")), "a four-character choice takes W and E")
    val tight = List(7) { "字" } + "屌你老母" + "你"
    same((1..7).map { it..it } + listOf(null, 8..8), ChineseRow.place(tight),
        "a choice too wide for the room left waits for the menu, and a shorter one after it takes the room, in order")
    val sentence = listOf("今日はいい天気ですね", "今日は良い天気ですね", "今日", "今日は", "きょう")
    same(listOf(1..5, null, 6..6, 7..8, null), ChineseRow.place(sentence),
        "Japanese: the sentence, then the parts that fit, not the second sentence")
    same(listOf(1..8), ChineseRow.place(listOf("一二三四五六七八九十十一十二十三十四十五十六十七十八十九二十")),
        "a first choice wider than the row takes the whole row")
    same(emptyList<IntRange?>(), ChineseRow.place(emptyList()), "no choices, no places")
}

fun qAndPHaveNoChoice() {
    same(0, ChineseRow.MARK, "the mark sits over Q")
    same(9, ChineseRow.MORE, "the more cue over P")
    for (choices in listOf(List(20) { "字" }, List(5) { "屌你老母" }, listOf("你好", "hello", "我哋"))) {
        val columns = ChineseRow.place(choices)
        check(columns.filterNotNull().none { 0 in it || 9 in it }, "no choice over Q or P: $choices")
    }
    same(8, ChineseRow.ASK, "the row asks the engine for one choice a key at most")
}

fun theKeysThatPick() {
    val columns = ChineseRow.place(listOf("屌你老母", "你", "好"))
    same(0, ChineseRow.choiceAt(ChineseRow.column("W"), columns), "W picks the first")
    same(0, ChineseRow.choiceAt(ChineseRow.column("E"), columns), "so does E, under the same wide choice")
    same(1, ChineseRow.choiceAt(ChineseRow.column("R"), columns), "R the second")
    same(1, ChineseRow.choiceAt(ChineseRow.column("F"), columns), "the second row picks too, as the word menu's")
    same(-1, ChineseRow.choiceAt(ChineseRow.column("Z"), columns), "the third row doesn't")
    same(-1, ChineseRow.choiceAt(ChineseRow.column("Q"), columns), "Q has no choice")
    same(-1, ChineseRow.choiceAt(ChineseRow.column("P"), columns), "nor P")
    same(-1, ChineseRow.choiceAt(ChineseRow.column("U"), columns), "a key under no choice picks nothing")
}

fun theMenuFillsThreeRowsAPage() {
    val singles = List(60) { "字$it" }
    val first = ChineseMenu.page(singles, 0)!!
    same(23, first.cells.size, "a page of one-key choices: W to O, S to L, Z to M")
    same(listOf(1..8, 1..8, 1..7), ChineseMenu.ROWS, "the Sym panel's places")
    same(0, ChineseMenu.choice(first, "W"), "W the first")
    same(8, ChineseMenu.choice(first, "S"), "S the ninth")
    same(16, ChineseMenu.choice(first, "Z"), "Z the seventeenth")
    same(22, ChineseMenu.choice(first, "M"), "M the last")
    same(-1, ChineseMenu.choice(first, "Q"), "Q none")
    same(-1, ChineseMenu.choice(first, "A"), "A none")
    same(-1, ChineseMenu.choice(first, "P"), "P none: it shows the page")
    val wide = listOf("你") + List(3) { "屌你老母" } + listOf("我哋今日去飲")
    val page = ChineseMenu.page(wide, 0)!!
    same(listOf(1..1, 2..3, 4..5, 6..7), page.cells.filter { it.row == 0 }.map { it.columns }, "wide choices side by side")
    same(1..3, page.cells.first { it.index == 4 }.columns, "one that doesn't fit a row starts the next")
    same(1, page.cells.first { it.index == 4 }.row, "on the second row")
    same(null, ChineseMenu.page(wide, 9), "no page past the end")
}

fun theMenuTurnsItsPages() {
    same(listOf(0, 23, 46), ChineseMenu.starts(List(60) { "字" }), "60 one-key choices: three pages")
    same(listOf(0), ChineseMenu.starts(List(5) { "字" }), "a few: one page")
    same(emptyList<Int>(), ChineseMenu.starts(emptyList()), "none: none")
}

fun pShowsTheMenusPage() {
    same("", ChineseMenu.position(0, 1, more = false), "one page: nothing")
    same("2/3", ChineseMenu.position(1, 3, more = false), "the page and how many")
    same("1›", ChineseMenu.position(0, 2, more = true), "while the engine has more: the page and an arrow")
}

fun punctuationAndQuotes() {
    for (c in ",.?!:;'\"[]()-") check(ChineseKeys.isPunctuation(c), "$c is punctuation")
    for (c in "az09 é") check(!ChineseKeys.isPunctuation(c), "'$c' isn't")
    same("「你好」", ChineseKeys.marks("“你好”", "zh_hk"), "Hong Kong quotes with 「」")
    same("『』", ChineseKeys.marks("‘’", "zh_tw"), "Taiwan too, with 『』 inside")
    same("“你好”", ChineseKeys.marks("“你好”", "zh_hans"), "Simplified keeps “”")
    same("“", ChineseKeys.marks("“", null), "no variant: as Rime gives it")
    same("ascii_punct", ChineseKeys.PLAIN_PUNCTUATION, "plain punctuation is Rime's own option")
}

fun aSwipeUpOpensTheMenu() {
    val open = ChineseKeys.Swipe.OpenMenu
    val nothing = ChineseKeys.Swipe.Nothing
    same(open, ChineseKeys.swipe(across = 0.1f, down = -0.8f, menuOpen = false), "a swipe up opens the bigger menu")
    same(open, ChineseKeys.swipe(across = 0f, down = -2f, menuOpen = false), "from the bottom row too: it picks nothing")
    same(nothing, ChineseKeys.swipe(across = 0f, down = 0.8f, menuOpen = false), "down opens nothing")
    same(nothing, ChineseKeys.swipe(across = 1.5f, down = -0.5f, menuOpen = false), "mostly sideways opens nothing")
    // A short flick up, what used to pick, does nothing now: under half a page turn's length.
    same(nothing, ChineseKeys.swipe(across = 0f, down = -0.2f, menuOpen = false), "a short flick up does nothing")
    same(nothing, ChineseKeys.swipe(across = 0f, down = -0.1f, menuOpen = true), "nor with the menu open")
    // With the menu open, up and down turn its pages, as on the Sym panel.
    same(ChineseKeys.Swipe.NextPage, ChineseKeys.swipe(across = 0f, down = -0.8f, menuOpen = true), "up: the next page")
    same(ChineseKeys.Swipe.PageBefore, ChineseKeys.swipe(across = 0f, down = 0.8f, menuOpen = true), "down: the page before")
    same(ChineseKeys.Swipe.CloseMenu, ChineseKeys.swipe(across = 0f, down = 0.8f, menuOpen = true, firstPage = true),
        "down on the first page closes the menu, as Shift does (owner, 2026-11-17)")
    same(ChineseKeys.Swipe.NextPage, ChineseKeys.swipe(across = 0f, down = -0.8f, menuOpen = true, firstPage = true),
        "up on the first page still turns to the next")
    same(nothing, ChineseKeys.swipe(across = 2f, down = 0.5f, menuOpen = true), "sideways turns no page")
}

fun anEnglishWordOnlyWhenRimeCantRead() {
    val words = setOf("hello", "man", "sun", "the", "world", "good", "nei")
    val isWord = { w: String -> w in words }
    check(EnglishOffer.offers("hello", "he l lo", partial = false, choices = 8, isWord), "hello: Rime reads it only as he l lo")
    check(!EnglishOffer.offers("man", "man", partial = false, choices = 8, isWord), "man is a whole syllable: no")
    check(!EnglishOffer.offers("sun", "sun", partial = false, choices = 8, isWord), "Pinyin's sun is a whole syllable: no")
    check(EnglishOffer.offers("sun", "s u n", partial = false, choices = 8, isWord), "Yale reads sun only as s u n: yes")
    check(EnglishOffer.offers("the", "t h e", partial = false, choices = 8, isWord), "the")
    check(EnglishOffer.offers("world", "world", partial = true, choices = 8, isWord), "world: Rime reads only wo")
    check(EnglishOffer.offers("good", "go o d", partial = false, choices = 0, isWord), "good: no choices at all")
    check(!EnglishOffer.offers("neihou", "nei hou", partial = false, choices = 8, isWord), "not a word: no")
    check(!EnglishOffer.offers("nei", "nei", partial = false, choices = 8, isWord), "a word that's a code: no")
    check(!EnglishOffer.offers("a", "a", partial = false, choices = 8) { true }, "one letter: never")
    check(!EnglishOffer.offers("Hello", "he l lo", partial = false, choices = 8) { true }, "only lowercase letters, as typed")
    check(EnglishOffer.wholeSyllables("ng m a"), "one-letter syllables are whole: m, a")
    check(!EnglishOffer.wholeSyllables("ng d"), "a lone consonant isn't")
}

fun theEnglishWordIsNeverFirst() {
    same(listOf("嚱嘍囉", "hello", "嚱"), EnglishOffer.withWord(listOf("嚱嘍囉", "嚱"), "hello"), "second, after Rime's first")
    same(listOf("hello"), EnglishOffer.withWord(emptyList(), "hello"), "alone only when Rime has nothing")
    same(listOf("你"), EnglishOffer.withWord(listOf("你"), null), "no word, the choices as they are")
    same(1, EnglishOffer.PLACE, "the second place")
}

/** Shift, a swipe up and the cue at P need something to add in the bigger menu. */
fun theMenuOpensOnlyWithMore() {
    check(!ChineseRow.Choices(listOf("你", "好")).more, "two choices fit the row: no menu")
    check(!ChineseRow.Choices(emptyList()).more, "no choices: no menu")
    check(!ChineseRow.Choices(List(8) { "字" }).more, "eight fill the row: no menu")
    check(ChineseRow.Choices(List(9) { "字" }).more, "nine go past the row by one")
    check(ChineseRow.Choices(List(7) { "字" } + "一二三").more, "eight can still overflow by width")

    val withEnglish = ChineseRow.Choices(List(8) { "字" }, "hello")
    same((0..6).toList(), withEnglish.shown, "hello takes two columns, leaving room for six engine choices")
    check(withEnglish.more, "the English word displaces two engine choices: the bigger menu adds them")
    check(!ChineseRow.Choices(List(6) { "字" }, "hello").more, "all six engine choices and hello fit: no menu")
    val wholeRow = "一二三四五六七八九十十一十二十三十四十五十六十七十八十九二十"
    check(!ChineseRow.Choices(listOf(wholeRow), "hello").more, "a hidden English word alone does not open an engine menu")
}

fun englishAlternativesUseTheirShownIndex() {
    val alone = ChineseRow.Choices(emptyList(), "hello")
    same(listOf(0), alone.shown, "the English word is visible by itself")
    check(alone.isEnglish(alone.shown.single()), "picking the sole English word types that word")
    check(!alone.more, "the sole English word offers no engine choices to expand")

    val second = ChineseRow.Choices(listOf("嚱嘍囉", "嚱"), "hello")
    check(!second.isEnglish(0) && second.isEnglish(1) && !second.isEnglish(2), "the English word is second when Rime has choices")
    same(0, second.engineIndex(0), "Rime's first choice keeps its index")
    same(1, second.engineIndex(2), "Rime's second choice keeps its index after the inserted English word")
}

private fun previewLegendsUseTheGuide() {
    val korean = method("ko")
    same(true, korean.keyPreviewDefault, "Korean preview defaults on")
    same("ㅂ", korean.keyPreview('Q', false), "Korean Q")
    same("ㅃ", korean.keyPreview('Q', true), "Korean Shift+Q")
    val russian = method("ru")
    same(true, russian.keyPreviewDefault, "Russian preview defaults on")
    val preview = KeyPreview()
    for (resting in listOf(false, true)) {
        if (resting && preview.follow("Q", russian.keyPreview('Q', false))) preview.rest(russian.keyPreview('Q', false))
        preview.consume("Q")
        same("й", russian.letter('q', false), "Russian Q is the same immediate letter with or without a preview")
        same("", preview.shown, "Russian press clears the ghost")
        preview.clear()
    }
    same("Й", russian.keyPreview('Q', true), "Russian Shift+Q")
    val thai = method("th")
    same(true, thai.keyPreviewDefault, "Thai preview defaults on")
    same("ๆ", thai.keyPreview('Q', false), "Thai Q")
    same("๐", thai.keyPreview('Q', true), "Thai Shift+Q")
    same("ๅ", thai.keyPreview('Q', false, hold = true), "Thai held Q")
    same("+", thai.keyPreview('Q', true, hold = true), "Thai held Shift+Q")
    val zhuyin = method("zh-zhuyin")
    same(true, zhuyin.keyPreviewDefault, "Zhuyin preview defaults on")
    same("ㄆ", zhuyin.keyPreview('P', false), "Hsu P")
    same("", zhuyin.keyPreview('Q', false), "Hsu Q has no mapped symbol")
    same("弓", method("zh-quick").keyPreview('N', false), "Quick's N")
    same("難", method("zh-cangjie").keyPreview('X', false), "Cangjie's X")
    same(false, method("yue-yale").keyPreviewDefault, "Yale uses the printed letters")
    same(false, InputMethod.ENGLISH.keyPreviewDefault, "English preview defaults off")
}

private fun method(id: String) = InputMethod.parse(File("android/assets/im/$id/im.json").readText())

private fun radicalPreviewFollowsTheFingerAfterTheFirstRest() {
    for (schema in listOf("zh-quick", "zh-cangjie")) {
        val method = method(schema)
        val hover = SteadyKey(Layout::keyAt)
        val preview = KeyPreview()
        fun radical() = hover.key.singleOrNull()?.let { method.keyPreview(it, false) }.orEmpty()
        fun follow() = preview.follow(hover.key, radical())
        fun landOn(letter: Char) {
            val (x, y) = Layout.centre(letter)
            hover.touch(x, y)
        }

        landOn('N')
        same(true, follow(), "$schema waits to show the first key")
        same("", preview.shown, "$schema does not flash during a short touch")
        preview.rest(radical())
        same("弓", preview.shown, "$schema previews the first rested radical")
        landOn('M')
        same(false, follow(), "$schema does not wait again when the finger slides")
        same("一", preview.shown, "$schema replaces the old radical on the same update")

        hover.lift(200)
        same(false, hover.expire(240), "$schema keeps the hover during a brief lift")
        same("一", preview.shown, "$schema keeps the last radical during the brief lift")
        landOn('M')
        same(false, follow(), "$schema resumes on the same key with no delay")
        same("一", preview.shown, "$schema still previews M")

        landOn('H')
        follow()
        same("竹", preview.shown, "$schema can keep hovering while preparing to press")
        same(false, preview.consume("N"), "$schema does not consume H when typing a different key")
        same("竹", preview.shown, "$schema keeps H while a different radical is typed")
        same(true, preview.consume("H"), "$schema consumes H as the typed code is updated")
        same("", preview.shown, "$schema never shows a second 竹 alongside the typed 竹")
        same(false, follow(), "$schema does not schedule another preview on the pressed key")
        same("", preview.shown, "$schema keeps the pressed preview hidden during the same contact")
        landOn('M')
        follow()
        same("一", preview.shown, "$schema resumes previewing on the next key")
        landOn('H')
        follow()
        same("竹", preview.shown, "$schema previews H again after moving away and back")

        hover.lift(400)
        if (hover.expire(400 + SteadyKey.LIFT_MILLIS)) preview.clear()
        same("", preview.shown, "$schema clears the radical after a real lift")
        landOn('M')
        same(true, follow(), "$schema waits for a rest on the next touch")
        same("", preview.shown, "$schema does not bring back the old preview on landing")
    }
}
