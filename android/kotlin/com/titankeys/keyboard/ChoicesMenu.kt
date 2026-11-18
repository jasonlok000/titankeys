package com.titankeys.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.util.TypedValue
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View

/**
 * The bigger menu of an engine's choices, that holding P opens (docs/languages.md, "Chinese on the bar"), in the Sym panel's
 * place and at its height, so it looks like the emoji and symbol pages: the choices over the three letter rows, each as wide
 * as it needs ([ChineseMenu]), P showing the page, the previous and next page's icons at Backspace and Enter, and the ✕ at
 * Sym. The band along the bottom shows the code being typed. A resting finger highlights the choice over its key; a tap on
 * the screen picks one.
 *
 * It reads the choices from the engine as it needs them, through [source], a page at a time; the keyboard turns the pages
 * ([next], [previous]) and picks ([choice]).
 */
@SuppressLint("ViewConstructor")
class ChoicesMenu(
    context: Context,
    /** A touch on the screen picked the choice at this place in the whole list. */
    private val tapped: (Int) -> Unit,
) : View(context) {
    private var look = Look.of(context)
    private val typeface = resources.getFont(R.font.inter_regular)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()

    /** Up to how many choices from a place, across the engine's pages. */
    private var source: (Int, Int) -> List<String> = { _, _ -> emptyList() }
    /** The choices read so far, and whether the engine has none past them. */
    private val choices = ArrayList<String>()
    private var exhausted = false
    /** Where each page shown so far starts, the open one last. */
    private val starts = ArrayList<Int>()
    /** The code being typed, in the band. */
    private var code = ""
    var revision = 0
        private set

    /** The key under a resting finger, or empty: its choice is highlighted. */
    var hover = ""
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** Opens on the first page of a new list of choices. */
    fun open(code: String, source: (Int, Int) -> List<String>) {
        changedPage()
        this.code = code
        this.source = source
        choices.clear()
        exhausted = false
        starts.clear()
        starts += 0
        fill()
        invalidate()
        observe()
    }

    /** The open page: its cells, as many choices as fit from its start. */
    val page: ChineseMenu.Page get() = ChineseMenu.page(choices, starts.last()) ?: ChineseMenu.Page(emptyList())

    /**
     * Reads from the engine until the open page and the one after it can be laid out, and at least [COUNTED] choices, so P
     * shows how many pages there are from the first ("1/3"), or until the engine has no more. Past that, P shows "n›" until
     * the last is read (phone check, 2026-11-16: it showed "1›" on opening, "n/3" only after paging).
     */
    private fun fill() {
        val want = maxOf(starts.last() + 2 * PER_PAGE, COUNTED)
        while (!exhausted && choices.size < want) {
            val more = source(choices.size, READ)
            choices += more
            if (more.size < READ) exhausted = true
        }
    }

    /** True when a page follows the open one. */
    val hasNext get() = page.end < choices.size
    val hasPrevious get() = starts.size > 1

    /** Turns to the next page. False on the last. */
    fun next(): Boolean {
        if (!hasNext) { observe(); return false }
        changedPage()
        starts += page.end
        fill()
        invalidate()
        observe()
        return true
    }

    /** Turns to the page before. False on the first. */
    /** True on the menu's first page, where there's no page before. */
    val onFirstPage get() = !hasPrevious

    fun previous(): Boolean {
        if (!hasPrevious) { observe(); return false }
        changedPage()
        starts.removeAt(starts.size - 1)
        invalidate()
        observe()
        return true
    }

    /** The place in the whole list of the choice that key [key] picks on the open page, or -1. */
    fun choice(key: String) = ChineseMenu.choice(page, key)

    /** The word actually drawn for a cell, to verify it still belongs to the engine before picking. */
    fun wordAt(index: Int) = choices.getOrNull(index)

    /** P's text: the page, and how many, or "›" while more may follow. */
    private fun position(): String {
        val pages = ChineseMenu.starts(choices).size
        return ChineseMenu.position(starts.size - 1, pages, more = !exhausted)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(ROWS * ROW_HEIGHT + BAND_HEIGHT))
    }

    private fun column(column: Int) = MenuKeys.slot(width.toFloat(), column)

    override fun onDraw(canvas: Canvas) {
        look = Look.of(context)
        canvas.drawColor(look.paper)
        text.typeface = typeface
        val rowHeight = dp(ROW_HEIGHT).toFloat()
        val hovered = if (Layout.isLetter(hover)) choice(hover) else -1
        for (cell in page.cells) {
            val left = column(cell.columns.first).first
            val right = column(cell.columns.last).second
            val top = cell.row * rowHeight
            val marked = cell.index == hovered
            if (marked) {
                box.set(left + dp(2), top + dp(4), right - dp(2), top + rowHeight - dp(4))
                paint.color = GuideMotion.faded(look.accent, PILL_ALPHA)
                canvas.drawRoundRect(box, box.height() / 2, box.height() / 2, paint)
            }
            text.color = if (marked) look.accent else look.ink
            text.textSize = sp(ITEM_SIZE)
            drawCentred(canvas, choices[cell.index], left, right, top + rowHeight / 2)
        }
        // P: the page. Backspace and Enter: the pages before and after. Sym: the ✕.
        text.color = look.muted
        text.textSize = sp(POSITION_SIZE)
        column(9).let { (left, right) -> drawCentred(canvas, position(), left, right, rowHeight / 2) }
        if (hasPrevious) drawIcon(canvas, R.drawable.panel_previous, 9, 1)
        if (hasNext) drawIcon(canvas, R.drawable.panel_next, 9, 2)
        drawIcon(canvas, R.drawable.panel_close, 8, 2)
        // The band: the code being typed.
        text.color = look.muted
        text.textSize = sp(BAND_SIZE)
        drawCentred(canvas, code, column(0).first, column(9).second, ROWS * rowHeight + dp(BAND_HEIGHT) / 2f)
    }

    /** [value] centred between [left] and [right] on [middle], shrunk to fit. */
    private fun drawCentred(canvas: Canvas, value: String, left: Float, right: Float, middle: Float) {
        if (value.isEmpty()) return
        val room = right - left - dp(4)
        val width = text.measureText(value)
        val scale = if (width > room && width > 0f) room / width else 1f
        val baseline = middle - (text.ascent() + text.descent()) / 2
        canvas.save()
        canvas.scale(scale, scale, (left + right) / 2, baseline)
        canvas.drawText(value, (left + right) / 2 - width / 2, baseline, text)
        canvas.restore()
    }

    private fun drawIcon(canvas: Canvas, id: Int, column: Int, row: Int) {
        val drawable = context.getDrawable(id)?.mutate() ?: return
        drawable.setTint(look.ink)
        val size = dp(ICON)
        val (left, right) = column(column)
        val x = Math.round((left + right) / 2 - size / 2f)
        val y = Math.round(row * dp(ROW_HEIGHT) + dp(ROW_HEIGHT) / 2f - size / 2f)
        drawable.setBounds(x, y, x + size, y + size)
        drawable.draw(canvas)
    }

    /** Painted navigation buttons reuse the keyboard's actions, without physical key-up bookkeeping. */
    var control: (Int) -> Unit = {}

    /** The candidate or control pressed; it activates only if the finger lifts there too. */
    private var touched = Int.MIN_VALUE

    private fun changedPage() {
        revision++
        touched = Int.MIN_VALUE
    }

    private fun cellAt(x: Float, y: Float): Int {
        val row = (y / dp(ROW_HEIGHT)).toInt()
        return page.cells.firstOrNull { cell ->
            cell.row == row && x >= column(cell.columns.first).first && x <= column(cell.columns.last).second
        }?.index ?: -1
    }

    private fun targetAt(x: Float, y: Float): Int {
        if (x < 0 || x >= width || y < 0 || y >= height) return Int.MIN_VALUE
        val row = (y / dp(ROW_HEIGHT)).toInt()
        val last = column(9)
        val close = column(8)
        if (x >= last.first && x <= last.second) {
            if (row == 1 && hasPrevious) return -KeyEvent.KEYCODE_DEL
            if (row == 2 && hasNext) return -KeyEvent.KEYCODE_ENTER
        }
        if (row == 2 && x >= close.first && x <= close.second) return -KeyEvent.KEYCODE_SYM
        return cellAt(x, y).takeIf { it >= 0 } ?: Int.MIN_VALUE
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> touched = targetAt(event.x, event.y)
            MotionEvent.ACTION_UP -> {
                val cell = touched
                touched = Int.MIN_VALUE
                if (cell != Int.MIN_VALUE && targetAt(event.x, event.y) == cell) {
                    if (cell >= 0) tapped(cell) else control(-cell)
                }
                observe()
            }
            MotionEvent.ACTION_CANCEL -> touched = Int.MIN_VALUE
        }
        return true
    }

    /** Debug-only numeric evidence: no code, candidates, editor text or app names. */
    private fun observe() {
        if (!context.packageName.endsWith(".debug") ||
            AppBuild.kind(context) != "debug" || starts.isEmpty() || width <= 0 || height <= 0) return
        val at = IntArray(2)
        getLocationOnScreen(at)
        fun centre(col: Int, row: Int): String {
            val cell = column(col)
            return "${at[0] + Math.round((cell.first + cell.second) / 2)},${at[1] + row * dp(ROW_HEIGHT) + dp(ROW_HEIGHT) / 2}"
        }
        Log.i("TitanKeysMenu", "choices page=${starts.size} pages=${ChineseMenu.starts(choices).size}" +
            " previous=${if (hasPrevious) 1 else 0} next=${if (hasNext) 1 else 0} visible=${if (isShown) 1 else 0}" +
            " bounds=${at[0]},${at[1]},$width,$height row=${dp(ROW_HEIGHT)}" +
            " controls=${centre(9, 1)},${centre(9, 2)},${centre(8, 2)}")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { observe() }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // View construction can notify visibility before this class's fields are initialized.
        post { observe() }
    }

    private fun dp(value: Int) = Math.round(value * resources.displayMetrics.density)
    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private companion object {
        /** The Sym panel's rows and band, so the app above doesn't move between the two. */
        const val ROWS = 3
        const val ROW_HEIGHT = 46
        const val BAND_HEIGHT = 32
        /** The most choices a page holds: one a key over the panel's letters. */
        val PER_PAGE = ChineseMenu.ROWS.sumOf { it.last - it.first + 1 }
        /** How many choices each read asks the engine for. */
        const val READ = 48
        /** How many choices the menu reads as it opens: ten pages of one-key choices, cheap for Rime and Mozc alike. */
        val COUNTED = 10 * PER_PAGE
        const val ITEM_SIZE = 20f
        const val POSITION_SIZE = 14f
        const val BAND_SIZE = 15f
        const val ICON = 22
        const val PILL_ALPHA = .35f
    }
}
