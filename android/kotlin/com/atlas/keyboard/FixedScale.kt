package com.atlas.keyboard

import android.content.Context
import android.content.res.Configuration
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
 * window, Settings included, stays [UNSCALED]. The bar keeps its unscaled height ([fixedPx]). Everyone starts at Normal, 1.2
 * (owner, 2026-11-13: "let's make bigger normal, normal smaller. default all use the new normal").
 */
object FixedScale {
    /** Every window but the bar: TitanKeys' fixed unit, never grown. */
    const val UNSCALED = 1f

    /** Bar text size's factors. Named so they're easy to tune. Smaller is the bar as it was designed, at [UNSCALED]. */
    const val SMALLER = 1f
    const val NORMAL = 1.2f
    const val BIGGER = 1.4f

    /**
     * Settings > The bar > Bar text size: how big the bar's words, menu and chips are. Its ids are new with these names, so no
     * stored value from the old Normal, Bigger and Biggest can mean a different size than its name: those read as Normal.
     */
    enum class Size(val id: String, val factor: Float) {
        Smaller("small", SMALLER),
        Normal("medium", NORMAL),
        Bigger("large", BIGGER);

        companion object {
            /** The size [id] names, or Normal for none, an old id or anything else. */
            fun of(id: String?): Size = entries.firstOrNull { it.id == id } ?: Normal
        }
    }

    private const val SIZE = "bar_text_size"
    /** Where the first Size, which grew every window, kept its pick. A reset clears it too. */
    private const val OLD_SIZE = "size"

    private fun preferences(context: Context) = context.getSharedPreferences("fixed_scale", Context.MODE_PRIVATE)

    /** The bar's text size: your pick, or Normal. */
    fun size(context: Context): Size = Size.of(preferences(context).getString(SIZE, null))

    fun size(context: Context, size: Size) {
        preferences(context).edit().putString(SIZE, size.id).apply()
    }

    /** Settings > Reset most things: the bar goes back to Normal. */
    fun resetSize(context: Context) {
        preferences(context).edit().remove(SIZE).remove(OLD_SIZE).apply()
    }

    /**
     * [dp] at TitanKeys' fixed unit, in pixels, on this screen, from a window at any Bar text size: for what keeps its size while
     * what's in it grows, as the bar's height does (owner, 2026-11-13: "size shouldn't apply to the bar height").
     */
    fun fixedPx(context: Context, dp: Float): Float {
        val metrics = context.resources.displayMetrics
        return dp * densityDpi(metrics.widthPixels, metrics.heightPixels) / DisplayMetrics.DENSITY_DEFAULT
    }

    /** The screen's width in dp at every setting: 1076 px on the Titan 2 Elite ÷ 1.5625. */
    const val WIDTH_DP = 688.6f

    /**
     * The dpi that makes [WIDTH_DP] fill a screen this many pixels wide: 250 for the Titan 2 Elite's 1076, times [factor] for the
     * bar at Bar text size. The shorter side counts, so a turned screen keeps the same sizes.
     */
    fun densityDpi(widthPx: Int, heightPx: Int, factor: Float = UNSCALED): Int {
        val shorter = if (heightPx > 0) min(widthPx, heightPx) else widthPx
        return (shorter / WIDTH_DP * DisplayMetrics.DENSITY_DEFAULT * factor).roundToInt()
    }

    /**
     * Only what changes: font scale 1 and the fixed density, times [factor]. Everything else, such as night mode or locale, stays
     * the phone's. Null when the screen's size isn't known yet, so nothing is forced on a guess.
     */
    fun configuration(context: Context, factor: Float = UNSCALED): Configuration? {
        val metrics = context.resources.displayMetrics
        if (metrics.widthPixels <= 0) return null
        return Configuration().apply {
            fontScale = 1f
            densityDpi = densityDpi(metrics.widthPixels, metrics.heightPixels, factor)
        }
    }

    /** For an activity's attachBaseContext: the same context, with fixed sizes, unscaled whatever Bar text size says. */
    fun wrap(base: Context): Context {
        // Every window starts here, so each starts with Orange accents as it is now.
        Accent.load(base)
        return configuration(base)?.let(base::createConfigurationContext) ?: base
    }

    /**
     * For a service's views: a themed context with fixed sizes whose windows and system services are still the service's own.
     * [theme] is the service's theme, so default colours and text styles stay what they were. The bar passes Bar text size's
     * [factor]; the panels and exact typing's bar stay unscaled.
     */
    fun themed(base: Context, theme: Int, factor: Float = UNSCALED): Context = ContextThemeWrapper(base, theme).apply {
        Accent.load(base)
        configuration(base, factor)?.let(::applyOverrideConfiguration)
    }
}
