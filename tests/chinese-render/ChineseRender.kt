package com.titankeys.keyboard

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Chinese on the bar, drawn as the Titan 2 Elite shows it (docs/languages.md, "Chinese on the bar"): 1076 px across, 1.5625 px
 * to TitanKeys' dp, the bar 56 dp at the screen's bottom edge. The choices are placed by the app's own rules ([ChineseRow],
 * [ChineseMenu]) over the app's own key columns ([KeyAlignment]), with Strip's and ChoicesMenu's sizes, and the choices are
 * what Rime gave for each code on the PC (2026-11-16, Yale, Hong Kong). The page icons are stand-ins for the app's vector
 * drawables. Usage: run.sh [folder].
 */
private const val WIDTH = 1076
private const val UNIT = 1.5625f
private val BAR = Math.round(StripFit.BAR_DP * UNIT)
private const val ABOVE = 170
private val INK = Color.WHITE
private val MUTED = Color.GRAY
private val ORANGE = Color(0xEF, 0x80, 0x4E)
private val APP = Color(0x26, 0x26, 0x2B)

private lateinit var latin: Font
private lateinit var cjk: Font

private fun px(dp: Float) = dp * UNIT
private fun column(c: Int) = KeyAlignment.column(WIDTH.toFloat(), c)

/** [text] in [size] px, Inter where it has the letter, else the CJK font, as Android falls back. */
private fun Graphics2D.drawMixed(text: String, x: Float, baseline: Float, size: Float): Float {
    var at = x
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        val s = String(Character.toChars(cp))
        val f = (if (latin.canDisplay(cp)) latin else cjk).deriveFont(size)
        font = f
        drawString(s, at, baseline)
        at += fontMetrics.stringWidth(s)
        i += Character.charCount(cp)
    }
    return at - x
}

private fun Graphics2D.widthOf(text: String, size: Float): Float {
    var w = 0f
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        val f = (if (latin.canDisplay(cp)) latin else cjk).deriveFont(size)
        w += getFontMetrics(f).stringWidth(String(Character.toChars(cp)))
        i += Character.charCount(cp)
    }
    return w
}

/** [text] centred between [left] and [right], shrunk to fit, its small letters' middle on [middle]. */
private fun Graphics2D.centred(text: String, left: Float, right: Float, middle: Float, size: Float, colour: Color) {
    val room = right - left - px(4f)
    val w = widthOf(text, size)
    val scaled = if (w > room && w > 0f) size * room / w else size
    color = colour
    drawMixed(text, (left + right) / 2 - widthOf(text, scaled) / 2, middle + scaled * StripFit.X_HEIGHT / 2, scaled)
}

private fun canvas(height: Int): Pair<BufferedImage, Graphics2D> {
    val image = BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.color = APP
    g.fillRect(0, 0, WIDTH, height)
    // The app above: a line of text, so the code's line can be seen over it.
    g.color = Color(0xCC, 0xCC, 0xCC)
    g.drawMixed("聽日幾點見面？", px(16f), ABOVE - px(40f), px(16f))
    return image to g
}

/** The bar with the row as Strip draws it: the code's line above, the mark over Q, the choices, P's cue. */
private fun bar(out: File, name: String, mark: String, code: String, listed: List<String>, english: String?, hover: String = "") {
    val (image, g) = canvas(ABOVE + BAR)
    val top = ABOVE.toFloat()
    g.color = Color.BLACK
    g.fillRect(0, ABOVE, WIDTH, BAR)
    val line = top + px(StripFit.LINE_DP)
    val word = px(20f)
    val items = EnglishOffer.withWord(listed.take(ChineseRow.ASK), english)
    val placed = ChineseRow.place(items)
    val shown = items.indices.filter { placed[it] != null }
    val columns = shown.map { placed[it]!! }
    val more = listed.size > shown.count { !(english != null && it == EnglishOffer.PLACE) }
    // The code: a thin line floating just above the bar's left edge, over the app.
    if (code.isNotEmpty()) {
        val height = px(22f)
        val pad = px(8f)
        val left = column(0).first
        val textSize = px(13f)
        val box = RoundRectangle2D.Float(left, top - height - px(3f), g.widthOf(code, textSize) + 2 * pad, height, height, height)
        g.color = Color(0, 0, 0, 230)
        g.fill(box)
        g.color = MUTED
        g.drawMixed(code, left + pad, box.y + height / 2 + textSize * StripFit.X_HEIGHT / 2, textSize)
    }
    if (mark.isNotEmpty()) g.centred(mark, column(ChineseRow.MARK).first, column(ChineseRow.MARK).second, line, word, ORANGE)
    val hovered = if (hover.isEmpty()) -1 else shown.indexOf(ChineseRow.choiceAt(ChineseRow.column(hover), placed))
    for ((index, cols) in columns.withIndex()) {
        val left = column(cols.first).first
        val right = column(cols.last).second
        if (index == hovered) {
            val pill = px(42f).coerceAtMost(2 * minOf(BAR - px(1f) - (line - top), line - top - px(1f)))
            g.color = Color(ORANGE.red, ORANGE.green, ORANGE.blue, (255 * .24f).toInt())
            g.fill(RoundRectangle2D.Float(left + px(2f), line - pill / 2, right - left - px(4f), pill, pill, pill))
        }
        g.centred(items[shown[index]], left, right, line, word, if (index == hovered) ORANGE else INK)
    }
    if (more) g.centred(ChineseRow.MORE_CUE, column(ChineseRow.MORE).first, column(ChineseRow.MORE).second, line, word, MUTED)
    g.dispose()
    ImageIO.write(image, "png", File(out, name))
}

/** The bigger menu as ChoicesMenu draws it, in the Sym panel's place over the keys, with the bar hidden under it. */
private fun menu(out: File, name: String, code: String, choices: List<String>, page: Int, hover: String = "") {
    val rowHeight = px(46f)
    val band = px(32f)
    val height = Math.round(3 * rowHeight + band)
    val (image, g) = canvas(ABOVE + height)
    val top = ABOVE.toFloat()
    g.color = Color.BLACK
    g.fillRect(0, ABOVE, WIDTH, height)
    val starts = ChineseMenu.starts(choices)
    val shown = ChineseMenu.page(choices, starts[page])!!
    val hovered = if (hover.isEmpty()) -1 else ChineseMenu.choice(shown, hover)
    for (cell in shown.cells) {
        val left = column(cell.columns.first).first
        val right = column(cell.columns.last).second
        val rowTop = top + cell.row * rowHeight
        if (cell.index == hovered) {
            g.color = Color(ORANGE.red, ORANGE.green, ORANGE.blue, (255 * .35f).toInt())
            val h = rowHeight - px(8f)
            g.fill(RoundRectangle2D.Float(left + px(2f), rowTop + px(4f), right - left - px(4f), h, h, h))
        }
        g.centred(choices[cell.index], left, right, rowTop + rowHeight / 2, px(20f), if (cell.index == hovered) ORANGE else INK)
    }
    // P: the page; Backspace and Enter: the pages before and after; Sym: the ✕. Stand-in glyphs for the app's icons.
    g.centred(ChineseMenu.position(page, starts.size, more = false), column(9).first, column(9).second, top + rowHeight / 2, px(14f), MUTED)
    g.stroke = BasicStroke(px(2f))
    g.color = INK
    fun chevron(c: Int, row: Int, up: Boolean) {
        val (l, r) = column(c)
        val x = (l + r) / 2
        val y = top + row * rowHeight + rowHeight / 2
        val d = px(6f) * (if (up) -1 else 1)
        g.drawLine(Math.round(x - px(8f)), Math.round(y - d / 2), Math.round(x), Math.round(y + d / 2))
        g.drawLine(Math.round(x), Math.round(y + d / 2), Math.round(x + px(8f)), Math.round(y - d / 2))
    }
    if (page > 0) chevron(9, 1, up = true)
    if (page < starts.size - 1) chevron(9, 2, up = false)
    column(8).let { (l, r) ->
        val x = (l + r) / 2
        val y = top + 2 * rowHeight + rowHeight / 2
        val d = px(7f)
        g.drawLine(Math.round(x - d), Math.round(y - d), Math.round(x + d), Math.round(y + d))
        g.drawLine(Math.round(x - d), Math.round(y + d), Math.round(x + d), Math.round(y - d))
    }
    g.centred(code, column(0).first, column(9).second, top + 3 * rowHeight + band / 2, px(15f), MUTED)
    g.dispose()
    ImageIO.write(image, "png", File(out, name))
}

fun main(args: Array<String>) {
    val out = File(args[0]).apply { mkdirs() }
    latin = Font.createFont(Font.TRUETYPE_FONT, File(args[1], "inter_regular.otf"))
    cjk = File(args.getOrElse(2) { "" }).takeIf { it.isFile }?.let { Font.createFont(Font.TRUETYPE_FONT, it) } ?: Font(Font.DIALOG, Font.PLAIN, 1)
    // What Rime gave on the PC, Yale, Hong Kong (2026-11-16).
    val diu = listOf("屌你老母", "𨳒你老母", "屌你老母臭閪", "屌你老母個臭花閪", "屌你老母個臭化閪", "屌你老母個仆街仔")
    bar(out, "row-wide.png", "耶", "diu nei lou mou", diu, null)
    val ngo = listOf("我哋今日去飲茶", "我哋", "我地", "俄地", "鵝地", "我爹", "我", "哦")
    bar(out, "row-sentence.png", "耶", "ngo dei gam yat heui yam cha", ngo, null)
    val nei = "你 呢 您 妳 尼 膩 伱 禰 餌 鈮 妮 瀰 伲 怩 隬 抳 胒 狔 誀 柅 馜 籾 眲 跜 镾 蚭 秜 彌 旎 沵 珥 儞 苨 毦 洱 獼 屔 衈 檷 𨉖 㞾 㢱 㳽 䉲 䝚 祢 𤤗 𥿡 𦲁 㛋 䕥 𡛧 𦴪 㛅 㢽 㦐 䎶 䏪 䣵 泥".split(" ")
    bar(out, "row-singles.png", "耶", "nei", nei, null)
    bar(out, "row-hover.png", "耶", "nei", nei, null, hover = "R")
    val hello = listOf("嚱嘍囉", "嚱", "𠺬", "㹃", "𠺪", "𠾍", "He", "hae")
    bar(out, "row-english.png", "耶", "he l lo", hello, "hello")
    bar(out, "row-pinyin.png", "拼", "ni hao", listOf("你好", "擬好", "你", "妮", "尼", "泥", "倪", "霓"), null)
    menu(out, "menu-1.png", "nei", nei, 0, hover = "D")
    menu(out, "menu-2.png", "nei", nei, 1)
    menu(out, "menu-wide.png", "diu nei lou mou", diu + nei.take(14), 0)
    // Japanese on the same bar: what Mozc gave on the PC through mozc_tool, the app's own path (2026-11-16).
    val kyou = "今日はいい天気ですね 今日は良い天気ですね 今日 今日は きょう キョウハイイテンキデスネ きょうは 教は 強は 凶は 経は 教派 キョウ キョ 凶 叶 共 匡 叫 交 向 杏 亨 狂 劫 享 京 供 協 況".split(" ")
    bar(out, "ja-row-sentence.png", "あ", "きょうはいいてんきですね", kyou, null)
    val nihon = "日本 にほん 二本 ２本 2本 ニホン 日本語 日本海 日本橋 二 荷 似 弐 ２ 2 Ⅱ ⅱ ② ² ₂ 丹 煮 仁保 煎 薫 尼 而 爾 餌 迩".split(" ")
    bar(out, "ja-row-words.png", "あ", "にほん", nihon, null, hover = "T")
    menu(out, "ja-menu.png", "きょうはいいてんきですね", kyou, 0, hover = "E")
    println("Chinese and Japanese renders in $out")
}
