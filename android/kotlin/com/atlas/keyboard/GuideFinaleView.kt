package com.atlas.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View

/**
 * Draws [GuideFinale] on the phone: the summary, the acrostic and the logo, over the end's orange. [box] says where, from its top
 * to its bottom, in this view. [beat] runs at each of the finale's beats, for a haptic, and [over] once it's all done.
 */
internal class GuideFinaleView(context: Context) : View(context) {
    var lines: List<String> = emptyList()
    var box = 0f to 0f
    var beat: (() -> Unit)? = null
    var over: (() -> Unit)? = null
    private var start = -1L
    private var beatsDone = 0
    private var overDone = false
    private val heavy = GuideFonts.heavy(context)
    private val medium = GuideFonts.medium(context)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shape = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mark = path(GuideFinale.MARK_PATH)
    private val line = path(GuideFinale.LINE_PATH)
    private val tPart = polygon(GuideFinale.T_CLIP)
    private val kPart = polygon(GuideFinale.K_CLIP)

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Plays it from the start, or with animations off, shows its last frame at once. */
    fun play() {
        start = GuideMotion.now()
        beatsDone = 0
        overDone = false
        invalidate()
    }

    fun stop() {
        start = -1
        invalidate()
    }

    private val measure = GuideFinale.Measure { words, size, bold ->
        text.typeface = if (bold) heavy else medium
        text.textSize = size
        text.letterSpacing = 0f
        text.measureText(words)
    }

    override fun onDraw(canvas: Canvas) {
        if (start < 0 || lines.isEmpty() || width == 0) return
        val total = GuideFinale.total(lines)
        val at = if (!GuideMotion.enabled) total else ((GuideMotion.now() - start) / ValueAnimator.getDurationScale()).toLong()
        val (top, bottom) = box
        for (piece in GuideFinale.frame(at, lines, width.toFloat(), top, bottom, measure)) when (piece) {
            is GuideFinale.Text -> {
                text.typeface = if (piece.bold) heavy else medium
                text.textSize = piece.size
                text.letterSpacing = if (piece.spacing > 0f) piece.spacing / piece.size else 0f
                text.color = GuideMotion.faded(piece.colour, piece.alpha * ((piece.colour ushr 24) / 255f))
                canvas.drawText(piece.text, piece.x, piece.y, text)
            }
            is GuideFinale.Mark -> {
                val scale = piece.side / (1080f * GuideFinale.MARK_SHAPE)
                canvas.save()
                canvas.translate(piece.x - 540f * scale, piece.y - 540f * scale)
                canvas.scale(scale, scale)
                // The whole mark, or only its T or its K, as the logo walks apart into the name.
                when (piece.part) {
                    GuideFinale.MarkPart.T -> canvas.clipPath(tPart)
                    GuideFinale.MarkPart.K -> canvas.clipPath(kPart)
                    GuideFinale.MarkPart.Whole -> Unit
                }
                if (piece.alpha > 0f) {
                    shape.color = GuideMotion.faded(GuideFinale.INK, piece.alpha)
                    canvas.drawPath(mark, shape)
                }
                if (piece.line > 0f) {
                    shape.color = GuideMotion.faded(GuideFinale.CREAM, piece.line)
                    canvas.drawPath(line, shape)
                }
                canvas.restore()
            }
        }
        val beats = GuideFinale.beats(lines)
        while (beatsDone < beats.size && at >= beats[beatsDone]) {
            beatsDone++
            beat?.invoke()
        }
        if (at >= total) {
            if (!overDone) {
                overDone = true
                post { over?.invoke() }
            }
            return
        }
        postInvalidateOnAnimation()
    }

    private fun polygon(points: FloatArray) = Path().apply {
        moveTo(points[0], points[1])
        for (at in 2 until points.size step 2) lineTo(points[at], points[at + 1])
        close()
    }

    private fun path(data: String) = Path().apply {
        for (points in GuideFinale.shapes(data)) {
            points.forEachIndexed { index, (x, y) -> if (index == 0) moveTo(x, y) else lineTo(x, y) }
            close()
        }
    }
}
