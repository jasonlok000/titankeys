package com.atlas.keyboard

import kotlin.math.abs

/**
 * FixedScale's sizes at every font size and display size. Android works out a window's pixels from its configuration: a dp is
 * densityDpi ÷ 160 pixels and an sp is that times fontScale. FixedScale sets both, so this models what a window gets with the
 * phone at each setting and checks that every dp and sp comes out the same. It's a model of Android's sums, not a render.
 */
fun main() {
    val failures = ArrayList<String>()
    fun check(condition: Boolean, message: String) {
        if (!condition) failures += message
    }

    // The Titan 2 Elite: 1076 pixels wide.
    val width = 1076
    val height = 1240
    val dpi = FixedScale.densityDpi(width, height)
    check(dpi == 250, "The Titan 2 Elite gets 250 dpi, so a dp is 1.5625 px: got $dpi")
    check(abs(width / (dpi / 160f) - FixedScale.WIDTH_DP) < .5f, "The screen is ${FixedScale.WIDTH_DP} dp wide: got ${width / (dpi / 160f)}")
    check(FixedScale.densityDpi(height, width) == dpi, "A turned screen keeps the same sizes")

    /** What a window's text and spacing come to, in pixels, with the phone at [systemDpi] and [systemFontScale]. */
    fun pixels(systemDpi: Int, systemFontScale: Float, fixed: Boolean): List<Float> {
        val windowDpi = if (fixed) FixedScale.densityDpi(width, height) else systemDpi
        val windowFontScale = if (fixed) 1f else systemFontScale
        val density = windowDpi / 160f
        val scaledDensity = density * windowFontScale
        // A spread of the sizes TitanKeys uses: bar words, notes, panel keys in sp, and gaps and corners in dp.
        return listOf(14f, 16f, 22f, 30f).map { it * scaledDensity } + listOf(4f, 8f, 16f, 48f).map { it * density }
    }

    val fontScales = listOf(.85f, 1f, 1.15f, 1.3f, 1.5f, 1.8f, 2f)
    val densities = listOf(200, 230, 250, 280, 320, 400)
    val reference = pixels(250, 1f, fixed = true)
    for (fontScale in fontScales) for (density in densities) {
        check(pixels(density, fontScale, fixed = true) == reference,
            "At font size $fontScale and $density dpi, sizes are the same as at 1.0 and 250 dpi")
    }
    // Without FixedScale, the owner's font size of 2.0 doubled the text, which is what this fixes.
    check(pixels(250, 2f, fixed = false)[0] == 2 * reference[0], "The model sees the old doubling at font size 2.0")
    // At the phone's default settings nothing moves: FixedScale's sizes are the ones TitanKeys was designed at.
    check(pixels(250, 1f, fixed = false) == reference, "At font size 1.0 and 250 dpi, FixedScale changes nothing")

    // Bar text size (owner, 2026-11-13: "let's make bigger normal, normal smaller. default all use the new normal"): Smaller is
    // the bar as designed, Normal 1.2 and Bigger 1.4, and everyone starts at Normal, whatever the phone's text size.
    check(FixedScale.UNSCALED == 1f, "Every window but the bar stays at 1")
    check(FixedScale.Size.entries.map { it.name to it.factor } == listOf("Smaller" to 1f, "Normal" to 1.2f, "Bigger" to 1.4f),
        "The sizes are Smaller 1.0, Normal 1.2 and Bigger 1.4: got ${FixedScale.Size.entries.map { it.name to it.factor }}")
    check(FixedScale.Size.entries.map { it.id } == listOf("small", "medium", "large"), "The sizes have fresh ids")
    // No pick, and every id the first Bar text size stored, read as Normal: none can mean a different size than its name.
    for (id in listOf(null, "", "normal", "bigger", "biggest", "size")) {
        check(FixedScale.Size.of(id) == FixedScale.Size.Normal, "\"$id\" reads as Normal: got ${FixedScale.Size.of(id)}")
    }

    // Bar text size multiplies the unit in the bar's window, and with it every dp and sp in the bar. Every other window stays at 1.
    check(FixedScale.Size.entries.zipWithNext().all { (a, b) -> b.factor > a.factor }, "Each size is bigger than the one before")
    for (size in FixedScale.Size.entries) {
        check(FixedScale.Size.of(size.id) == size, "${size.id} reads back as ${size.name}")
        val sized = FixedScale.densityDpi(width, height, size.factor)
        check(abs(sized - dpi * size.factor) <= 1f, "${size.name} makes a dp ${size.factor} times as big: got $sized dpi for $dpi")
        check(FixedScale.densityDpi(height, width, size.factor) == sized, "${size.name}: a turned screen keeps the same sizes")
        // The bar keeps its unscaled height, 56 dp, and only what's in it grows (FixedScale.fixedPx, Strip.BAR_DP).
        // fixedPx takes the screen's pixels, which a window has at any size, never the window's own dp.
        val barPx = 56f * FixedScale.densityDpi(width, height) / 160f
        check(abs(56f * (sized / 160f) / size.factor - barPx) <= 1f, "${size.name}: the bar's window would agree on its height")
        check(abs(barPx - 56f * dpi / 160f) <= 1f, "${size.name}: the bar stays ${56f * dpi / 160f} px tall: got $barPx")
        // What sits over the real keys takes its places from the key calibration, in parts of the screen's width, never from
        // the unit: the panels' columns, the word menu's places, the hover marks and the drawn keyboard stay over their keys.
        // A view gets its width in pixels: at a bigger size the screen is fewer dp wide, but just as many pixels.
        val density = sized / 160f
        val viewWidth = Math.round(width / density * density).toFloat()
        val boxes = { geometry: KeyGeometry -> geometry.boxes.map { listOf(it.left, it.top, it.right, it.bottom) } }
        check(boxes(KeyGeometry(viewWidth)) == boxes(KeyGeometry(width.toFloat())), "${size.name}: the keys' boxes don't move")
        check(boxes(KeyGeometry(viewWidth, scale = KeyGeometry.DRAWN)) == boxes(KeyGeometry(width.toFloat(), scale = KeyGeometry.DRAWN)),
            "${size.name}: the guide's drawn keyboard and its touch zone don't move")
        check((0 until KeyGeometry.COLUMNS).all { KeyGeometry(viewWidth).cell(it) == KeyGeometry(width.toFloat()).cell(it) },
            "${size.name}: the word menu's places and the panels' columns don't move")
    }

    if (failures.isEmpty()) println("Fixed scale: all checks pass") else {
        failures.forEach(::println)
        throw IllegalStateException("${failures.size} fixed scale checks failed")
    }
}
