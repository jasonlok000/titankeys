package com.atlas.connected

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Base64
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * TitanKeys' only ways to use the internet, both strictly limited:
 *
 * - **Announcements** ([Announcements]): once a day at most, it downloads one signed file from the TitanKeys website.
 *   It sends nothing about the user: no identifier, no cookie, and a plain user agent.
 * - **Contributions** ([Contributions]): only if the user agrees, sealed swipe examples go to the project, on Wi-Fi.
 *
 * The privacy policy at [PRIVACY_URL] says the same, and must change whenever this does.
 */
object Connected {
    const val SITE = "https://jasonlok000.github.io/titankeys"
    const val FEED_URL = "$SITE/feed.json"
    const val PRIVACY_URL = "$SITE/privacy/"

    /**
     * The contributions Worker (server/contributions), on the owner's Cloudflare Free plan account. Set it to "" to
     * keep examples on the phone and send nothing.
     */
    const val CONTRIBUTIONS_URL = "https://titankeys-contributions.everyones-niche.workers.dev"

    /** The public key that checks the feed's signature. Its private key is kept off GitHub by the project owner. */
    internal const val FEED_KEY = "MCowBQYDK2VwAyEAbd0H0XoqO1qz3URQ8VPGCChg4cl9X+oCFD1A4ecwSyo="

    /** The public key that seals contributions. Only the owner's private key, kept offline, can open them. */
    internal const val CONTRIBUTION_KEY = "MCowBQYDK2VuAyEAOqhUzJadFIQ7+diFdpwLyv74vXfvMAiM9DHWbt+1PTI="

    internal const val JOB_ANNOUNCEMENTS = 42_001
    internal const val JOB_CONTRIBUTIONS = 42_002
    internal const val JOB_DELETE = 42_003

    private const val TIMEOUT_MILLIS = 20_000
    private const val USER_AGENT = "TitanKeys"

    internal fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences("connected", Context.MODE_PRIVATE)

    internal fun key(base64: String): ByteArray = Base64.decode(base64, Base64.DEFAULT)

    /** "debug" for TitanKeys Debug, "play" for the Google Play build, otherwise "github". */
    fun build(context: Context): String {
        if (context.packageName.endsWith(".debug")) return "debug"
        return try {
            val info = context.packageManager.getApplicationInfo(context.packageName, android.content.pm.PackageManager.GET_META_DATA)
            info.metaData?.getString("com.titankeys.build")?.takeIf { it in Feed.BUILDS } ?: "github"
        } catch (error: Exception) {
            "github"
        }
    }

    fun version(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""

    /** Sets the background jobs to match the settings. Safe to call often: a job that is already set stays as it is. */
    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val service = ComponentName(context, ConnectedJob::class.java)
        fun ensure(id: Int, wanted: Boolean, build: () -> JobInfo) {
            val pending = scheduler.getPendingJob(id)
            if (!wanted) {
                if (pending != null) scheduler.cancel(id)
            } else if (pending == null) {
                scheduler.schedule(build())
            }
        }
        ensure(JOB_ANNOUNCEMENTS, Announcements.enabled(context)) {
            JobInfo.Builder(JOB_ANNOUNCEMENTS, service)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(DAY_MILLIS, FLEX_MILLIS)
                .build()
        }
        ensure(JOB_CONTRIBUTIONS, Contributions.enabled(context) && CONTRIBUTIONS_URL.isNotEmpty()) {
            JobInfo.Builder(JOB_CONTRIBUTIONS, service)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setRequiresBatteryNotLow(true)
                .setPeriodic(DAY_MILLIS / 2, FLEX_MILLIS)
                .build()
        }
        ensure(JOB_DELETE, Contributions.deletionPending(context) && CONTRIBUTIONS_URL.isNotEmpty()) {
            JobInfo.Builder(JOB_DELETE, service)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(HOUR_MILLIS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build()
        }
    }

    /**
     * The keyboard calls this when its service starts, so that the jobs come back after the phone restarts, and so that
     * a job that the system dropped is set again.
     */
    fun start(context: Context) {
        val app = context.applicationContext
        this.context = app
        Thread { runCatching { schedule(app) } }.start()
        TouchSizes.recording = Contributions.enabled(app)
    }

    /** The app, once the keyboard has started. Contributions come from the keyboard, which has no other way to pass it. */
    @Volatile
    internal var context: Context? = null
        private set

    /** Whether a text field is active in the keyboard now. Announcements wait while it is, so they never get in the way. */
    @Volatile
    var typing = false
        private set

    private val main = Handler(Looper.getMainLooper())

    /** The keyboard tells when a text field starts and ends. When none is active, a waiting announcement can show. */
    fun field(context: Context, active: Boolean) {
        typing = active
        if (active) return
        val app = context.applicationContext
        main.removeCallbacksAndMessages(FIELD_TOKEN)
        main.postAtTime({ if (!typing) Thread { runCatching { Announcements.showDue(app) } }.start() }, FIELD_TOKEN,
            android.os.SystemClock.uptimeMillis() + QUIET_MILLIS)
    }

    private val FIELD_TOKEN = Any()

    /**
     * An intent that opens a page of the app through Ink's own way to open a page from a notification. The app's activity
     * is singleTask, so this brings it to the front, closes anything above it, such as the guide, and opens the page.
     */
    fun pageIntent(context: Context, page: String): android.content.Intent =
        (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: android.content.Intent())
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("com.vandam.ink.notification.HREF", page)

    /** Opens an HTTPS connection with nothing that identifies the phone: no cookies, no cache, a plain user agent. */
    internal fun open(url: String, method: String): HttpsURLConnection {
        val connection = URL(url).openConnection() as? HttpsURLConnection ?: throw IOException("Only HTTPS is allowed")
        connection.requestMethod = method
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        connection.useCaches = false
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", USER_AGENT)
        return connection
    }

    /** Reads at most [limit] bytes, so a wrong server cannot fill the phone. */
    internal fun readLimited(input: InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            out.write(buffer, 0, count)
            if (out.size() > limit) throw IOException("The answer is too large")
        }
        return out.toByteArray()
    }

    internal fun ok(connection: HttpURLConnection): Boolean = connection.responseCode in 200..299

    internal const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    internal const val HOUR_MILLIS = 60 * 60 * 1000L
    private const val FLEX_MILLIS = 4 * HOUR_MILLIS
    /** After a field ends, a short wait, so that moving from one field to the next shows nothing between them. */
    private const val QUIET_MILLIS = 3_000L
}
