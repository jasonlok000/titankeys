package com.atlas.keyboard

/**
 * The keyboard's feels (docs/haptics.md): one meaning each, told apart by effect or by timing, as the Titan 2 Elite has
 * five ready-made effects and no strength control. These run without a phone.
 */
fun main() {
    eachFeelIsDistinct()
    feelsStayShort()
    pairsRiseAndFall()
    theLongBuzzIsOnlyForCant()
    swipeTicksFollowTheSetting()
    confirmationsAreSpacedButMovementIsNot()
    println("Haptic checks passed")
}

private fun check(condition: Boolean, message: () -> String) {
    if (!condition) throw AssertionError(message())
}

/** What a feel feels like on the Titan: its effect, or else its buzzes and gaps. */
private fun signature(feel: Feel): Any {
    val pattern = HapticPalette.of(feel)
    return pattern.effect ?: pattern.fallback.timings
}

private fun eachFeelIsDistinct() {
    val signatures = Feel.entries.map(::signature)
    check(signatures.toSet().size == Feel.entries.size) { "Each feel must feel different: $signatures" }
    // Phones without an effect fall back to timed buzzes, which must be distinct too.
    val fallbacks = Feel.entries.map { HapticPalette.of(it).fallback.timings }
    check(fallbacks.toSet().size == Feel.entries.size) { "Each feel's fallback must feel different: $fallbacks" }
    check(HapticPalette.of(Feel.Stays).effect == HapticPalette.EFFECT_DOUBLE_CLICK) { "Selecting or locking is a double click" }
    check(HapticPalette.of(Feel.Confirm).effect == HapticPalette.EFFECT_CLICK) { "Done is a click, as steering starting is" }
    check(HapticPalette.of(Feel.Removed).effect == HapticPalette.EFFECT_HEAVY_CLICK) { "Removing is a heavy click" }
    check(HapticPalette.of(Feel.Texture).effect == HapticPalette.EFFECT_TEXTURE_TICK) { "Movement is the lightest tick" }
}

private fun feelsStayShort() {
    for (feel in Feel.entries) {
        val millis = HapticPalette.of(feel).millis
        val limit = when (feel) {
            Feel.Texture, Feel.Tick -> 20L
            Feel.Refused -> 80L
            else -> 100L
        }
        check(millis in 1..limit) { "$feel lasts $millis ms; keep it within $limit so fast typing stays calm" }
    }
    for (feel in Feel.entries) for (strength in HapticPalette.of(feel).fallback.strengths)
        check(strength in 0..255) { "$feel's strengths stay in the vibrator's range" }
}

private fun pairsRiseAndFall() {
    val changed = HapticPalette.of(Feel.Changed).fallback.timings
    val restored = HapticPalette.of(Feel.Restored).fallback.timings
    check(changed.size == 3 && changed[0] < changed[2]) { "Changed rises: a short buzz, a gap, a longer one: $changed" }
    check(restored.size == 3 && restored[0] > restored[2]) { "Restored falls: a longer buzz, a gap, a short one: $restored" }
    check(changed == restored.reversed()) { "Restoring is changing played backwards" }
    check(HapticPalette.of(Feel.Changed).composed != null && HapticPalette.of(Feel.Restored).composed != null) {
        "Phones that can compose effects get composed pairs"
    }
}

private fun theLongBuzzIsOnlyForCant() {
    val refused = HapticPalette.of(Feel.Refused).fallback.timings
    check(refused.size == 1) { "Can't is one buzz" }
    for (feel in Feel.entries - Feel.Refused) for (buzz in HapticPalette.of(feel).fallback.timings.filterIndexed { i, _ -> i % 2 == 0 })
        check(buzz < refused[0]) { "$feel has a buzz as long as can't's" }
}

private fun swipeTicksFollowTheSetting() {
    check(HapticPalette.swipeTick(HapticPreset.Normal) == Feel.Texture) { "Normal swipe ticks are the lightest feel" }
    check(HapticPalette.swipeTick(HapticPreset.Strong) == Feel.Tick) { "Strong swipe ticks are ticks, as the Titan can't make one stronger" }
}

private fun confirmationsAreSpacedButMovementIsNot() {
    check(HapticPalette.isMotion(Feel.Texture) && HapticPalette.isMotion(Feel.Tick)) { "Movement and steps come close together" }
    for (feel in Feel.entries - Feel.Texture - Feel.Tick) check(!HapticPalette.isMotion(feel)) { "$feel is a confirmation" }
    check(HapticPalette.CONFIRM_SPACING_MILLIS in 50..150) { "Two confirmations this close play only the first" }
}
