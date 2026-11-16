package com.atlas.keyboard

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Feedback by email (#74): Send feedback, Record a bug and the crash offer open the phone's mail app with the address, subject
 * and text filled in, and the user sends it. TitanKeys sends nothing itself and needs no account, server or network.
 */
object Mail {
    const val ADDRESS = "titankeys@everyones-niche.com"

    /** Opens a new mail. False if the phone has no mail app. */
    fun compose(context: Context, subject: String, body: String): Boolean {
        // Mail apps read the address either from the link or from the extras, so both have it.
        val link = Uri.parse("mailto:$ADDRESS?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}")
        return open(context, Intent(Intent.ACTION_SENDTO, link)
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(ADDRESS))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body))
    }

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
