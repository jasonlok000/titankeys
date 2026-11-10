package com.atlas.connected

import android.app.job.JobParameters
import android.app.job.JobService

/** Runs the daily announcement check, the contribution upload on Wi-Fi, and a waiting deletion, off the main thread. */
class ConnectedJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            val done = try {
                when (params.jobId) {
                    Connected.JOB_ANNOUNCEMENTS -> Announcements.check(this)
                    Connected.JOB_CONTRIBUTIONS -> Contributions.upload(this)
                    Connected.JOB_DELETE -> Contributions.sendDeletion(this)
                    else -> true
                }
            } catch (error: Exception) {
                false
            }
            // A periodic job runs again at its time anyway; only a waiting deletion is tried again soon.
            jobFinished(params, !done && params.jobId == Connected.JOB_DELETE)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = params.jobId == Connected.JOB_DELETE
}
