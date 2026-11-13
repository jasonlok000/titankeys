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
 */
object FixedScale {
    /** The screen's width in dp at every setting: 1076 px on the Titan 2 Elite ÷ 1.5625. */
    const val WIDTH_DP = 688.6f

    /**
     * The dpi that makes [WIDTH_DP] fill a screen this many pixels wide: 250 for the Titan 2 Elite's 1076. The shorter side
     * counts, so a turned screen keeps the same sizes.
     */
    fun densityDpi(widthPx: Int, heightPx: Int): Int {
        val shorter = if (heightPx > 0) min(widthPx, heightPx) else widthPx
        return (shorter / WIDTH_DP * DisplayMetrics.DENSITY_DEFAULT).roundToInt()
    }

    /**
     * Only what changes: font scale 1 and the fixed density. Everything else, such as night mode or locale, stays the phone's.
     * Null when the screen's size isn't known yet, so nothing is forced on a guess.
     */
    fun configuration(context: Context): Configuration? {
        val metrics = context.resources.displayMetrics
        if (metrics.widthPixels <= 0) return null
        return Configuration().apply {
            fontScale = 1f
            densityDpi = densityDpi(metrics.widthPixels, metrics.heightPixels)
        }
    }

    /** For an activity's attachBaseContext: the same context, with fixed sizes. */
    fun wrap(base: Context): Context = configuration(base)?.let(base::createConfigurationContext) ?: base

    /**
     * For a service's views: a themed context with fixed sizes whose windows and system services are still the service's own.
     * [theme] is the service's theme, so default colours and text styles stay what they were.
     */
    fun themed(base: Context, theme: Int): Context = ContextThemeWrapper(base, theme).apply {
        configuration(base)?.let(::applyOverrideConfiguration)
    }
}
