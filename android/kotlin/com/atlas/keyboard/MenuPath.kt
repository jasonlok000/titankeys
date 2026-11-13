package com.atlas.keyboard

/**
 * A path for the word menu's drawing, on the icons' 24-unit grid or in dp, kept as plain commands so that both the strip (Android)
 * and the checks' renders (Java2D) draw the same shapes: see [MenuCanvas]. Made from SVG path data ([svg]) or from points.
 */
class MenuPath {
    sealed interface Op
    data class Move(val x: Float, val y: Float) : Op
    data class Line(val x: Float, val y: Float) : Op
    data class Cubic(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x: Float, val y: Float) : Op
    object Close : Op

    val ops = ArrayList<Op>()
    /** A drawing's own copy of this path, made once: see [MenuCanvas]. */
    var cache: Any? = null

    fun moveTo(x: Float, y: Float) = apply { ops += Move(x, y) }
    fun lineTo(x: Float, y: Float) = apply { ops += Line(x, y) }
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) = apply { ops += Cubic(x1, y1, x2, y2, x, y) }
    fun close() = apply { ops += Close }

    companion object {
        /** [points] joined by lines, and closed if [closed]. */
        fun poly(points: List<Pair<Float, Float>>, closed: Boolean = false) = MenuPath().apply {
            points.forEachIndexed { index, (x, y) -> if (index == 0) moveTo(x, y) else lineTo(x, y) }
            if (closed) close()
        }

        /** The commands of SVG path [data]: M, L, H, V, C, S and Z, absolute and relative, as the app's vector drawables use them. */
        fun svg(data: String): MenuPath {
            val path = MenuPath()
            val tokens = Regex("[MmLlHhVvCcSsZz]|-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?").findAll(data).map { it.value }.toList()
            var i = 0
            var command = 'M'
            var x = 0f
            var y = 0f
            var startX = 0f
            var startY = 0f
            var lastX2 = 0f
            var lastY2 = 0f
            var lastCubic = false
            fun number(): Float = tokens[i++].toFloat()
            fun more() = i < tokens.size && !tokens[i][0].isLetter()
            while (i < tokens.size) {
                if (tokens[i][0].isLetter()) command = tokens[i++][0]
                val relative = command.isLowerCase()
                val baseX = if (relative) x else 0f
                val baseY = if (relative) y else 0f
                when (command.uppercaseChar()) {
                    'M' -> {
                        x = baseX + number(); y = baseY + number()
                        path.moveTo(x, y)
                        startX = x; startY = y
                        lastCubic = false
                        // Pairs after a move are lines.
                        command = if (relative) 'l' else 'L'
                    }
                    'L' -> { x = baseX + number(); y = baseY + number(); path.lineTo(x, y); lastCubic = false }
                    'H' -> { x = baseX + number(); path.lineTo(x, y); lastCubic = false }
                    'V' -> { y = baseY + number(); path.lineTo(x, y); lastCubic = false }
                    'C' -> {
                        val x1 = baseX + number(); val y1 = baseY + number()
                        val x2 = baseX + number(); val y2 = baseY + number()
                        x = baseX + number(); y = baseY + number()
                        path.cubicTo(x1, y1, x2, y2, x, y)
                        lastX2 = x2; lastY2 = y2; lastCubic = true
                    }
                    'S' -> {
                        val x1 = if (lastCubic) 2 * x - lastX2 else x
                        val y1 = if (lastCubic) 2 * y - lastY2 else y
                        val x2 = baseX + number(); val y2 = baseY + number()
                        x = baseX + number(); y = baseY + number()
                        path.cubicTo(x1, y1, x2, y2, x, y)
                        lastX2 = x2; lastY2 = y2; lastCubic = true
                    }
                    'Z' -> { path.close(); x = startX; y = startY; lastCubic = false; if (more()) error("Numbers after Z in $data") }
                    else -> error("Unknown path command $command in $data")
                }
            }
            return path
        }
    }
}

/**
 * What the word menu's drawing needs from a canvas, in dp: the strip's Android canvas and the checks' Java2D both give it, so the
 * renders are the app's drawing. Colours are ARGB.
 */
interface MenuCanvas {
    fun save()
    fun restore()
    fun translate(x: Float, y: Float)
    fun rotate(radians: Float)
    fun scale(x: Float, y: Float)
    fun clip(path: MenuPath)
    fun fill(path: MenuPath, colour: Int)
    fun stroke(path: MenuPath, width: Float, colour: Int)
    fun circle(x: Float, y: Float, radius: Float, colour: Int)
    fun rect(left: Float, top: Float, right: Float, bottom: Float, colour: Int)
    /**
     * [text] in the menu word's font at [size] dp, solid in [colour], left out when its alpha is 0. Its left end is at [x] on the
     * baseline [y], or, when [centred], its middle is at [x], [y].
     */
    fun text(text: String, x: Float, y: Float, size: Float, colour: Int, centred: Boolean = false)
}
