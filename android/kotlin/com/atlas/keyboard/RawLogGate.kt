package com.atlas.keyboard

/**
 * The full typing log (Settings > Typing log, "Record my typing") exists only in TitanKeys Debug, whose package name ends
 * in ".debug". Public releases, from GitHub and Google Play, have no way to turn it on, read it or export it. Debug
 * builds are not marked debuggable, so the package name is the gate. A bug recording ([BugRecording]) is in every build.
 */
internal object RawLogGate {
    const val DEBUG_SUFFIX = ".debug"

    fun allows(packageName: String): Boolean = packageName.endsWith(DEBUG_SUFFIX)
}
