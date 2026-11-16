package com.titankeys.connected

import java.io.IOException

/** Sends a report from the form (#97) to the contributions Worker, part by part, each sealed: see [Reports]. */
internal object ReportUpload {
    /**
     * Seals and sends every part. True once the server has them all; false if any is refused or the network fails, and
     * the report then stays in the outbox. Network, so never on the main thread.
     */
    fun upload(id: String, zip: ByteArray, recipient: ByteArray = Connected.key(Connected.CONTRIBUTION_KEY)): Boolean {
        require(Regex("[0-9a-f]{32}").matches(id))
        if (Connected.CONTRIBUTIONS_URL.isEmpty()) return false
        val parts = Reports.split(zip)
        for ((index, part) in parts.withIndex()) {
            val sealed = Seal.seal(part, recipient)
            val connection = Connected.open("${Connected.CONTRIBUTIONS_URL}/v1/report", "POST")
            try {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(sealed.size)
                connection.setRequestProperty("Content-Type", "application/octet-stream")
                connection.setRequestProperty("X-Report", id)
                connection.setRequestProperty("X-Part", "$index/${parts.size}")
                connection.outputStream.use { it.write(sealed) }
                if (!Connected.ok(connection)) return false
            } catch (_: IOException) {
                return false
            } finally {
                connection.disconnect()
            }
        }
        return true
    }

}
