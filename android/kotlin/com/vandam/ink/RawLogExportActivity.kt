package com.vandam.ink

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.view.Gravity
import android.widget.TextView
import com.atlas.keyboard.FixedScale
import com.atlas.keyboard.RawLog

/** Owns the Android file picker's result, then copies the raw input log away from the main thread. */
class RawLogExportActivity : Activity() {
    /** The same sizes at any display size or font size (FixedScale). */
    override fun attachBaseContext(base: Context) = super.attachBaseContext(FixedScale.wrap(base))

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The typing log is only in TitanKeys Debug: a public release has nothing to export.
        if (!RawLog.available(this)) {
            complete(false, "The typing log is only in TitanKeys Debug.")
            return
        }
        setContentView(TextView(this).apply {
            gravity = Gravity.CENTER
            text = "Exporting log…"
        })
        if (savedInstanceState != null) return
        val picker = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, FILE_NAME)
        try {
            startActivityForResult(picker, PICK_DOCUMENT)
        } catch (_: ActivityNotFoundException) {
            complete(false, "Couldn't open the file picker.")
        }
    }

    @Deprecated("Android activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_DOCUMENT) return
        if (resultCode != RESULT_OK) {
            complete(false)
            return
        }
        val uri = data?.data
        if (uri == null) {
            complete(false, "Couldn't open that file.")
            return
        }
        // The log can be 200 MB.
        Thread {
            val error = try {
                // Plain "w" can leave the end of a longer file that the user chose to replace.
                contentResolver.openOutputStream(uri, "wt")!!.use { RawLog.export(this, it) }
                null
            } catch (_: Exception) {
                "Couldn't save the file."
            }
            runOnUiThread { complete(error == null, error) }
        }.start()
    }

    private fun complete(exported: Boolean, error: String? = null) {
        val result = Bundle().apply { putString(ERROR, error) }
        intent.getParcelableExtra(RECEIVER, ResultReceiver::class.java)?.send(
            if (exported) RESULT_OK else RESULT_CANCELED, result,
        )
        finish()
    }

    companion object {
        private const val PICK_DOCUMENT = 1
        private const val RECEIVER = "receiver"
        private const val ERROR = "error"
        private const val FILE_NAME = "titankeys-raw-log.jsonl"

        internal fun start(activity: Activity, complete: (Boolean, String?) -> Unit) {
            val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    complete(resultCode == RESULT_OK, resultData?.getString(ERROR))
                }
            }
            activity.startActivity(Intent(activity, RawLogExportActivity::class.java).putExtra(RECEIVER, receiver))
        }
    }
}
