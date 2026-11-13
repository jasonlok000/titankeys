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
 * Settings > Size multiplies the unit by [Size.factor] (owner, 2026-11-13: "bigger everything including the bar", then "the
 * bigger elements, bar size stays the same"), so every window grows alike, and Ink's screens by the same factor (appUserScale).
 * What sits over the real keys takes its places from the key calibration, in parts of the screen's width ([KeyGeometry]), so it
 * stays where the keys are and only its text grows. The bar keeps Normal's height ([normalPx]) and what's in it grows.
 */
object FixedScale {
    /** Settings > Size's factors. Named so they're easy to tune. */
    const val NORMAL = 1f
    const val BIGGER = 1.2f
    const val BIGGEST = 1.4f

    /** Settings > Size: how much bigger than Normal TitanKeys draws everything. */
    enum class Size(val id: String, val factor: Float) {
        Normal("normal", NORMAL),
        Bigger("bigger", BIGGER),
        Biggest("biggest", BIGGEST);

        companion object {
            fun of(id: String?): Size = entries.firstOrNull { it.id == id } ?: Normal
        }
    }

    private const val SIZE = "size"

    private fun preferences(context: Context) = context.getSharedPreferences("fixed_scale", Context.MODE_PRIVATE)

    fun size(context: Context): Size = Size.of(preferences(context).getString(SIZE, null))

    fun size(context: Context, size: Size) {
        preferences(context).edit().putString(SIZE, size.id).apply()
    }

    /** Settings > Reset all settings: Size goes back to its default. */
    fun resetSize(context: Context) {
        preferences(context).edit().remove(SIZE).apply()
    }

    /**
     * [dp] at Normal, in pixels, in a window of [context] at any Settings > Size: for what keeps its size while what's in it grows,
     * as the bar's height does (owner, 2026-11-13: "bar size stays the same").
     */
    fun normalPx(context: Context, dp: Float): Float = dp * context.resources.displayMetrics.density / size(context).factor

    /** The screen's width in dp at every setting: 1076 px on the Titan 2 Elite ÷ 1.5625. */
    const val WIDTH_DP = 688.6f

    /**
     * The dpi that makes [WIDTH_DP] fill a screen this many pixels wide: 250 for the Titan 2 Elite's 1076, times [factor] for
     * Settings > Size. The shorter side counts, so a turned screen keeps the same sizes.
     */
    fun densityDpi(widthPx: Int, heightPx: Int, factor: Float = NORMAL): Int {
        val shorter = if (heightPx > 0) min(widthPx, heightPx) else widthPx
        return (shorter / WIDTH_DP * DisplayMetrics.DENSITY_DEFAULT * factor).roundToInt()
    }

    /**
     * Only what changes: font scale 1 and the fixed density, at Settings > Size if [sized]. Everything else, such as night mode
     * or locale, stays the phone's. Null when the screen's size isn't known yet, so nothing is forced on a guess.
     */
    fun configuration(context: Context, sized: Boolean = true): Configuration? {
        val metrics = context.resources.displayMetrics
        if (metrics.widthPixels <= 0) return null
        return Configuration().apply {
            fontScale = 1f
            densityDpi = densityDpi(metrics.widthPixels, metrics.heightPixels, if (sized) size(context).factor else NORMAL)
        }
    }

    /**
     * For an activity's attachBaseContext: the same context, with fixed sizes. The interactive guide and the front page pass
     * [sized] false and stay at Normal: their slides are laid out to the dp. In tests/guide's drawings, 1.1 already put some
     * titles over one line even at their smallest, and from 1.2 the front page ran out of room above the keys (2026-11-13).
     */
    fun wrap(base: Context, sized: Boolean = true): Context {
        // Every window starts here, so each starts with Orange accents as it is now.
        Accent.load(base)
        return configuration(base, sized)?.let(base::createConfigurationContext) ?: base
    }

    /**
     * For a service's views: a themed context with fixed sizes whose windows and system services are still the service's own.
     * [theme] is the service's theme, so default colours and text styles stay what they were.
     */
    fun themed(base: Context, theme: Int): Context = ContextThemeWrapper(base, theme).apply {
        Accent.load(base)
        configuration(base)?.let(::applyOverrideConfiguration)
    }
}
