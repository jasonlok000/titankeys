package com.atlas.rime

import android.content.Context

/** Rime's data in the APK: assets/rime/, listed in assets/rime.sha256 (native/rime/data.sh). */
object RimeAssets {
    /**
     * Copies the data into [RimeData]'s folder, if it isn't there yet, from the app's assets. For [RimeEngine]'s
     * `install`: it runs in [RimeEngine.start], on a background thread.
     */
    fun installer(context: Context): (RimeData) -> Boolean {
        val assets = context.applicationContext.assets
        return { data ->
            val manifest = assets.open(RimeData.MANIFEST).bufferedReader().use { it.readText() }
            data.install(manifest) { path -> assets.open("${RimeData.ASSETS}/$path") }
        }
    }
}
