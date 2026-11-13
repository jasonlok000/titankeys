package com.atlas.keyboard

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D

/** The word menu's [MenuCanvas] on Java2D, for the checks' renders: the same drawing as the strip's, in dp scaled by [graphics]. */
class AwtMenuCanvas(private val g: Graphics2D, private val font: Font) : MenuCanvas {
    private val transforms = ArrayList<AffineTransform>()
    private val clips = ArrayList<Shape?>()

    override fun save() {
        transforms += g.transform
        clips += g.clip
    }

    override fun restore() {
        g.transform = transforms.removeAt(transforms.size - 1)
        g.clip = clips.removeAt(clips.size - 1)
    }

    override fun translate(x: Float, y: Float) = g.translate(x.toDouble(), y.toDouble())
    override fun rotate(radians: Float) = g.rotate(radians.toDouble())
    override fun scale(x: Float, y: Float) = g.scale(x.toDouble(), y.toDouble())
    override fun clip(path: MenuPath) = g.clip(shape(path))

    override fun fill(path: MenuPath, colour: Int) {
        if (colour ushr 24 == 0) return
        g.color = Color(colour, true)
        g.fill(shape(path))
    }

    override fun stroke(path: MenuPath, width: Float, colour: Int) {
        if (colour ushr 24 == 0) return
        g.color = Color(colour, true)
        g.stroke = BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(shape(path))
    }

    override fun circle(x: Float, y: Float, radius: Float, colour: Int) {
        if (colour ushr 24 == 0 || radius <= 0f) return
        g.color = Color(colour, true)
        g.fill(Ellipse2D.Float(x - radius, y - radius, radius * 2, radius * 2))
    }

    override fun rect(left: Float, top: Float, right: Float, bottom: Float, colour: Int) {
        if (colour ushr 24 == 0) return
        g.color = Color(colour, true)
        g.fill(Rectangle2D.Float(left, top, right - left, bottom - top))
    }

    override fun text(text: String, x: Float, y: Float, size: Float, colour: Int, centred: Boolean) {
        if (size <= 0f || colour ushr 24 == 0) return
        val sized = font.deriveFont(size)
        val glyphs = sized.createGlyphVector(g.fontRenderContext, text)
        var left = x
        var baseline = y
        if (centred) {
            val metrics = sized.getLineMetrics(text, g.fontRenderContext)
            left = x - glyphs.logicalBounds.width.toFloat() / 2
            baseline = y + (metrics.ascent - metrics.descent) / 2
        }
        g.color = Color(colour, true)
        g.fill(glyphs.getOutline(left, baseline))
    }

    /** The width of [text] in the menu's font at [size] dp. */
    fun measure(text: String, size: Float = MenuScene.FONT_DP): Float =
        font.deriveFont(size).createGlyphVector(g.fontRenderContext, text).logicalBounds.width.toFloat()

    companion object {
        fun shape(path: MenuPath): Shape = (path.cache as? Path2D.Float) ?: Path2D.Float().also { p ->
            for (op in path.ops) when (op) {
                is MenuPath.Move -> p.moveTo(op.x, op.y)
                is MenuPath.Line -> p.lineTo(op.x, op.y)
                is MenuPath.Cubic -> p.curveTo(op.x1, op.y1, op.x2, op.y2, op.x, op.y)
                MenuPath.Close -> p.closePath()
            }
            path.cache = p
        }
    }
}
