package com.titankeys.rime

import android.content.Context

/** Rime's data in the APK: assets/rime/, listed in assets/rime.sha256 (native/rime/data.sh). */
object RimeAssets {
    /** Rime's folder in the app's files: the one the keyboard starts the engine with (Languages: engines/<name>). */
    fun dataDir(context: Context) = java.io.File(context.filesDir, "engines/${RimeEngine.ENGINE}")

    /**
     * Copies the data into [RimeData]'s folder from the app's assets: the shared files, and the files of the input methods
     * that are on, only (assets/rime.schemas; docs/studies/3.0-storage.md). An input method turned on adds its files the
     * next time Rime starts, and one turned off loses them. For [RimeEngine]'s `install`: it runs in [RimeEngine.start]
     * and [RimeEngine.warm], on a background thread.
     */
    fun installer(context: Context): (RimeData) -> Boolean {
        val app = context.applicationContext
        val assets = app.assets
        return { data ->
            val manifest = assets.open(RimeData.MANIFEST).bufferedReader().use { it.readText() }
            val groups = schemaFiles(app)
            val schemas = enabledSchemas(app, groups.orEmpty())
            data.install(manifest, groups, schemas) { path -> assets.open("${RimeData.ASSETS}/$path") }
        }
    }

    /** librime can select a schema before its compiled tables are installed. Check the same groups the installer uses. */
    fun schemaReadiness(context: Context): (RimeData, String) -> Boolean {
        val app = context.applicationContext
        val groups by lazy { schemaFiles(app) }
        return { data, schema ->
            val files = groups
            // An older package without a group list installs all schemas together.
            files == null || files[schema]?.let(data::hasSchemaFiles) == true
        }
    }

    /** When a warm schema starts again, the files it no longer needs can go after closing its native session. */
    fun unusedAssets(context: Context): (RimeData) -> Boolean {
        val app = context.applicationContext
        val groups by lazy { schemaFiles(app).orEmpty() }
        return { data -> runCatching { data.hasUnusedSchemaFiles(groups, enabledSchemas(app, groups)) }.getOrDefault(false) }
    }

    private fun enabledSchemas(context: Context, groups: Map<String, Set<String>>): Set<String> =
        // If no saved Chinese methods are available yet, install every schema.
        runCatching { schemasOn(context) }.getOrDefault(emptySet()).ifEmpty { groups.keys }

    private fun schemaFiles(context: Context): Map<String, Set<String>>? = runCatching {
        context.assets.open(RimeData.SCHEMAS).bufferedReader().use { RimeData.groups(it.readText()) }
    }.getOrNull()

    /**
     * The Rime schemas of the input methods that are on (Settings > Languages): the keyboard sets this when it starts
     * (InputMethodList.rimeSchemasOn), as the input method list is the keyboard's. Empty until then: every schema.
     */
    @Volatile var schemasOn: (Context) -> Set<String> = { emptySet() }
}
