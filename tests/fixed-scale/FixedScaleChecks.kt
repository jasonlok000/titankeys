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

    if (failures.isEmpty()) println("Fixed scale: all checks pass") else {
        failures.forEach(::println)
        throw IllegalStateException("${failures.size} fixed scale checks failed")
    }
}
