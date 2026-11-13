package com.atlas.keyboard

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.ContextThemeWrapper
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * TitanKeys looks the same at any display size or font size (owner, 2026-11-12). Every window it draws works in one unit: a dp is
 * the screen's width ÷ 688.6, which is 1.5625 px on the Titan 2 Elite, and text is never scaled by the system font size.
 *
 * Each activity wraps its base context in [wrap] from attachBaseContext. The keyboard service can't swap its own context, which
 * is the window Android gives it, so it makes its views from [themed] instead. Ink's screens size themselves by the surface's
 * width already; Ink's activity is wrapped too (AppNativeAdapters' appBaseContext), so its text field and touch slop agree.
 *
 * Settings > The bar > Bar text size grows only what's in the keyboard's bar (owner, 2026-11-13: "Maybe only applies to the
 * elements of the bar? (but not the height of the bar itself)"): the bar is made from [themed] at [Size.factor], and every other
 * window, Settings included, stays at Normal. The bar keeps Normal's height ([normalPx]). Until you pick a size, it follows the
 * phone's own text size ([forFontScale]), which TitanKeys otherwise ignores.
 */
object FixedScale {
    /** Bar text size's factors. Named so they're easy to tune. */
    const val NORMAL = 1f
    const val BIGGER = 1.2f
    const val BIGGEST = 1.4f

    /** Settings > The bar > Bar text size: how much bigger than Normal the bar's words, menu and chips are. */
    enum class Size(val id: String, val factor: Float) {
        Normal("normal", NORMAL),
        Bigger("bigger", BIGGER),
        Biggest("biggest", BIGGEST);

        companion object {
            /** The size [id] names, or null for none, such as before you pick one. */
            fun ofOrNull(id: String?): Size? = entries.firstOrNull { it.id == id }
            fun of(id: String?): Size = ofOrNull(id) ?: Normal
        }
    }

    /** The phone's text size up to which the bar starts at Normal, and above which at Biggest. Between, Bigger. */
    const val BIGGER_FROM = 1.15f
    const val BIGGEST_ABOVE = 1.5f

    /** The size the bar starts at, before you pick one, for the phone's own text size (owner, 2026-11-13). */
    fun forFontScale(fontScale: Float): Size = when {
        fontScale < BIGGER_FROM -> Size.Normal
        fontScale <= BIGGEST_ABOVE -> Size.Bigger
        else -> Size.Biggest
    }

    /** The pick is stored under a new name: the old Size grew every window, so a pick of it doesn't carry over. */
    private const val SIZE = "bar_text_size"
    private const val OLD_SIZE = "size"

    private fun preferences(context: Context) = context.getSharedPreferences("fixed_scale", Context.MODE_PRIVATE)

    /** Your pick, or null while the bar follows the phone's text size. */
    fun picked(context: Context): Size? = Size.ofOrNull(preferences(context).getString(SIZE, null))

    /** The bar's text size: your pick, or the one for the phone's text size until you pick one. */
    fun size(context: Context): Size = picked(context) ?: forFontScale(phoneFontScale())

    fun size(context: Context, size: Size) {
        preferences(context).edit().putString(SIZE, size.id).apply()
    }

    /**
     * The phone's own text size, as Settings > Display sets it. Every TitanKeys window forces its own font scale to 1, so this
     * reads the system's resources, which nothing overrides.
     */
    private fun phoneFontScale(): Float = Resources.getSystem().configuration.fontScale

    /** Settings > Reset most things: the bar follows the phone's text size again. */
    fun resetSize(context: Context) {
        preferences(context).edit().remove(SIZE).remove(OLD_SIZE).apply()
    }

    /**
     * [dp] at Normal, in pixels, on this screen, from a window at any Bar text size: for what keeps its size while what's in it
     * grows, as the bar's height does (owner, 2026-11-13: "size shouldn't apply to the bar height").
     */
    fun normalPx(context: Context, dp: Float): Float {
        val metrics = context.resources.displayMetrics
        return dp * densityDpi(metrics.widthPixels, metrics.heightPixels) / DisplayMetrics.DENSITY_DEFAULT
    }

    /** The screen's width in dp at every setting: 1076 px on the Titan 2 Elite ÷ 1.5625. */
    const val WIDTH_DP = 688.6f

    /**
     * The dpi that makes [WIDTH_DP] fill a screen this many pixels wide: 250 for the Titan 2 Elite's 1076, times [factor] for the
     * bar at Bar text size. The shorter side counts, so a turned screen keeps the same sizes.
     */
    fun densityDpi(widthPx: Int, heightPx: Int, factor: Float = NORMAL): Int {
        val shorter = if (heightPx > 0) min(widthPx, heightPx) else widthPx
        return (shorter / WIDTH_DP * DisplayMetrics.DENSITY_DEFAULT * factor).roundToInt()
    }

    /**
     * Only what changes: font scale 1 and the fixed density, times [factor]. Everything else, such as night mode or locale, stays
     * the phone's. Null when the screen's size isn't known yet, so nothing is forced on a guess.
     */
    fun configuration(context: Context, factor: Float = NORMAL): Configuration? {
        val metrics = context.resources.displayMetrics
        if (metrics.widthPixels <= 0) return null
        return Configuration().apply {
            fontScale = 1f
            densityDpi = densityDpi(metrics.widthPixels, metrics.heightPixels, factor)
        }
    }

    /** For an activity's attachBaseContext: the same context, with fixed sizes, at Normal whatever Bar text size says. */
    fun wrap(base: Context): Context {
        // Every window starts here, so each starts with Orange accents as it is now.
        Accent.load(base)
        return configuration(base)?.let(base::createConfigurationContext) ?: base
    }

    /**
     * For a service's views: a themed context with fixed sizes whose windows and system services are still the service's own.
     * [theme] is the service's theme, so default colours and text styles stay what they were. The bar passes Bar text size's
     * [factor]; the panels and exact typing's bar stay at Normal.
     */
    fun themed(base: Context, theme: Int, factor: Float = NORMAL): Context = ContextThemeWrapper(base, theme).apply {
        Accent.load(base)
        configuration(base, factor)?.let(::applyOverrideConfiguration)
    }
}
