// Stand-ins for the few android.graphics parts that GestureDrawing uses, drawn with Java's own 2D graphics, so a check can render
// the bar to a picture on a computer. Only render.sh builds them; the app never does.
package android.graphics

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

open class Shader { enum class TileMode { CLAMP } }

class LinearGradient(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val from: Int, val to: Int, mode: Shader.TileMode) : Shader()

class RectF {
    var left = 0f; var top = 0f; var right = 0f; var bottom = 0f
    fun set(l: Float, t: Float, r: Float, b: Float) { left = l; top = t; right = r; bottom = b }
}

class Paint(flags: Int = 0) {
    enum class Style { FILL, STROKE }
    enum class Cap { ROUND }
    enum class Join { ROUND }
    var color = 0
    /** As Android: setting alpha changes only the colour's alpha. */
    var alpha: Int
        get() = color ushr 24
        set(value) { color = (color and 0x00FFFFFF) or (value.coerceIn(0, 255) shl 24) }
    var shader: Shader? = null
    var strokeWidth = 1f
    var style = Style.FILL
    var strokeCap = Cap.ROUND
    var strokeJoin = Join.ROUND

    companion object { const val ANTI_ALIAS_FLAG = 1 }
}

class Canvas(private val image: BufferedImage) {
    private val g: Graphics2D = image.createGraphics().apply {
        setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
    }
    private val saved = ArrayList<AffineTransform>()
    private val layers = ArrayList<Triple<BufferedImage, Graphics2D, Int>>()
    private var target = g

    private fun paint(paint: Paint) {
        val c = paint.color
        target.color = java.awt.Color(c shr 16 and 0xFF, c shr 8 and 0xFF, c and 0xFF, c ushr 24)
        val shader = paint.shader
        if (shader is LinearGradient) {
            fun awt(v: Int, alpha: Int) = java.awt.Color(v shr 16 and 0xFF, v shr 8 and 0xFF, v and 0xFF, ((v ushr 24) * alpha / 255))
            target.paint = java.awt.GradientPaint(shader.x0, shader.y0, awt(shader.from, paint.alpha), shader.x1, shader.y1, awt(shader.to, paint.alpha))
        }
        target.stroke = BasicStroke(paint.strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    }

    fun drawRect(l: Float, t: Float, r: Float, b: Float, p: Paint) { paint(p); target.fill(Rectangle2D.Float(l, t, r - l, b - t)) }
    fun drawCircle(x: Float, y: Float, radius: Float, p: Paint) { paint(p); target.fill(Ellipse2D.Float(x - radius, y - radius, 2 * radius, 2 * radius)) }
    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, p: Paint) { paint(p); target.draw(Line2D.Float(x0, y0, x1, y1)) }
    fun drawOval(r: RectF, p: Paint) { paint(p); target.fill(Ellipse2D.Float(r.left, r.top, r.right - r.left, r.bottom - r.top)) }
    fun drawRoundRect(r: RectF, rx: Float, ry: Float, p: Paint) {
        paint(p); target.fill(RoundRectangle2D.Float(r.left, r.top, r.right - r.left, r.bottom - r.top, 2 * rx, 2 * ry))
    }
    fun save(): Int { saved += target.transform; return saved.size }
    fun restore() {
        if (layers.isNotEmpty() && saved.size == layers.size) {
            val (layer, lg, alpha) = layers.removeAt(layers.lastIndex)
            lg.dispose()
            target = if (layers.isEmpty()) g else layers.last().second
            val before = target.composite
            val transform = target.transform
            target.transform = AffineTransform()
            target.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha / 255f)
            target.drawImage(layer, 0, 0, null)
            target.composite = before
            target.transform = transform
            saved.removeAt(saved.lastIndex)
            return
        }
        target.transform = saved.removeAt(saved.lastIndex)
    }
    fun scale(sx: Float, sy: Float, px: Float, py: Float) { target.translate(px.toDouble(), py.toDouble()); target.scale(sx.toDouble(), sy.toDouble()); target.translate(-px.toDouble(), -py.toDouble()) }
    fun saveLayerAlpha(l: Float, t: Float, r: Float, b: Float, alpha: Int): Int {
        saved += target.transform
        val layer = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        val lg = layer.createGraphics().apply { transform = target.transform; composite = AlphaComposite.SrcOver }
        layers += Triple(layer, lg, alpha)
        target = lg
        return saved.size
    }
}
