package com.atlas.keyboard

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Lends a finished bug recording to the app that the user picks in the share sheet, and nothing else: read-only, not
 * exported, and only for the one file named in the link, which Android grants for that share alone.
 */
class BugRecordingProvider : ContentProvider() {
    override fun onCreate() = true

    private fun fileFor(uri: Uri): File {
        val context = context ?: throw FileNotFoundException()
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        val file = File(BugRecording.folder(context), name)
        if (!NAME.matches(name) || !file.isFile) throw FileNotFoundException()
        return file
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Read only")
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?,
                       sortOrder: String?): Cursor {
        val file = fileFor(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns, 1).apply {
            addRow(columns.map { if (it == OpenableColumns.SIZE) file.length() else if (it == OpenableColumns.DISPLAY_NAME) file.name else null })
        }
    }

    override fun getType(uri: Uri) = "text/plain"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        private val NAME = Regex("titankeys-bug-[0-9-]+\\.jsonl")

        fun authority(context: Context) = "${context.packageName}.bugrecording"

        fun uri(context: Context, name: String): Uri = Uri.Builder().scheme("content").authority(authority(context)).appendPath(name).build()
    }
}
