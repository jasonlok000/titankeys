package com.atlas.keyboard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface

/** The word menu's [MenuCanvas] on an Android [Canvas], in dp: the strip scales the canvas by the density first. */
class AndroidMenuCanvas(typeface: Typeface) : MenuCanvas {
    lateinit var canvas: Canvas
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textAlign = Paint.Align.LEFT
        strokeJoin = Paint.Join.ROUND
    }

    override fun save() {
        canvas.save()
    }

    override fun restore() = canvas.restore()
    override fun translate(x: Float, y: Float) = canvas.translate(x, y)
    override fun rotate(radians: Float) = canvas.rotate(Math.toDegrees(radians.toDouble()).toFloat())
    override fun scale(x: Float, y: Float) = canvas.scale(x, y)

    override fun clip(path: MenuPath) {
        canvas.clipPath(path(path))
    }

    override fun fill(path: MenuPath, colour: Int) {
        if (colour ushr 24 == 0) return
        fillPaint.color = colour
        canvas.drawPath(path(path), fillPaint)
    }

    override fun stroke(path: MenuPath, width: Float, colour: Int) {
        if (colour ushr 24 == 0) return
        strokePaint.color = colour
        strokePaint.strokeWidth = width
        canvas.drawPath(path(path), strokePaint)
    }

    override fun circle(x: Float, y: Float, radius: Float, colour: Int) {
        if (colour ushr 24 == 0 || radius <= 0f) return
        fillPaint.color = colour
        canvas.drawCircle(x, y, radius, fillPaint)
    }

    override fun rect(left: Float, top: Float, right: Float, bottom: Float, colour: Int) {
        if (colour ushr 24 == 0) return
        fillPaint.color = colour
        canvas.drawRect(left, top, right, bottom, fillPaint)
    }

    override fun text(text: String, x: Float, y: Float, size: Float, fill: Int, outline: Int, outlineWidth: Float, centred: Boolean) {
        if (size <= 0f) return
        textPaint.textSize = size
        var left = x
        var baseline = y
        if (centred) {
            left = x - textPaint.measureText(text) / 2
            baseline = y - (textPaint.ascent() + textPaint.descent()) / 2
        }
        if (fill ushr 24 != 0) {
            textPaint.style = Paint.Style.FILL
            textPaint.color = fill
            canvas.drawText(text, left, baseline, textPaint)
        }
        if (outline ushr 24 != 0 && outlineWidth > 0f) {
            textPaint.style = Paint.Style.STROKE
            textPaint.strokeWidth = outlineWidth
            textPaint.color = outline
            canvas.drawText(text, left, baseline, textPaint)
        }
    }

    /** The width of [text] in the menu's font at [size] dp. */
    fun measure(text: String, size: Float = MenuScene.FONT_DP): Float {
        textPaint.textSize = size
        return textPaint.measureText(text)
    }

    private fun path(path: MenuPath): Path = (path.cache as? Path) ?: Path().also { p ->
        for (op in path.ops) when (op) {
            is MenuPath.Move -> p.moveTo(op.x, op.y)
            is MenuPath.Line -> p.lineTo(op.x, op.y)
            is MenuPath.Cubic -> p.cubicTo(op.x1, op.y1, op.x2, op.y2, op.x, op.y)
            MenuPath.Close -> p.close()
        }
        path.cache = p
    }
}
