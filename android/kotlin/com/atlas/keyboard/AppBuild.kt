package com.atlas.keyboard

import android.content.Context
import android.content.pm.PackageManager

/**
 * Which build of TitanKeys this is, from the manifest's `com.titankeys.build` meta-data: "play" for Google Play
 * (tools/play.sh adds it), "debug" for TitanKeys Debug, and "github" when it is absent, as in the GitHub APK.
 */
object AppBuild {
    private const val KEY = "com.titankeys.build"
    @Volatile private var known: String? = null

    fun kind(context: Context): String = known ?: read(context).also { known = it }

    /** The Google Play build, which has no typing log and only the updates that Google Play gives it. */
    fun play(context: Context) = kind(context) == "play"

    private fun read(context: Context): String = try {
        val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        info.metaData?.getString(KEY) ?: if (context.packageName.endsWith(".debug")) "debug" else "github"
    } catch (_: PackageManager.NameNotFoundException) {
        "github"
    }
}
