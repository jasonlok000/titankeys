package com.titankeys.keyboard

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.titankeys.connected.Counts
import java.io.File
import java.io.FileNotFoundException

/**
 * Puts [CrashReport]'s handler in place as TitanKeys' process starts, before the keyboard, the app or a service: Android starts
 * a process's content providers first. It also lends a report to the mail app the user sends it with, and nothing else:
 * read-only, not exported, and only the one file named in the link, which Android grants for that mail alone.
 */
class CrashKeeper : ContentProvider() {
    override fun onCreate(): Boolean {
        context?.let(CrashReport::install)
        // Sharing's daily totals count crashes and freezes, with nothing about them (#96).
        CrashReport.counted = { context, key -> Counts.crashed(context, key) }
        return true
    }

    private fun fileFor(uri: Uri): File {
        val context = context ?: throw FileNotFoundException()
        return CrashReport.lent(context, uri.lastPathSegment ?: throw FileNotFoundException()) ?: throw FileNotFoundException()
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

    override fun getType(uri: Uri) = if (uri.lastPathSegment.orEmpty().endsWith(".zip")) "application/zip" else "text/plain"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
