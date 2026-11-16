package com.atlas.keyboard

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Email, the report form's backup (#74, #97): when a report can't be sent, the form opens the phone's mail app with our
 * address, the subject and the report filled in, and the user sends it. TitanKeys sends nothing by mail itself.
 */
object Mail {
    const val ADDRESS = "titankeys@everyones-niche.com"

    /** Opens a new mail with [file] attached, which a read-only provider lends to the mail app. False if there's no mail app. */
    fun attach(context: Context, subject: String, body: String, file: Uri, name: String, type: String): Boolean {
        val send = Intent(Intent.ACTION_SEND)
            .setType(type)
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(ADDRESS))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body)
            .putExtra(Intent.EXTRA_STREAM, file)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(name, file)
        // Only mail apps: the share sheet's other apps stay behind Share elsewhere.
        send.selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        return open(context, send)
    }

    private fun open(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
