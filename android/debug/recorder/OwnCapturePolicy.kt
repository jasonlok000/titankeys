package com.titankeys.keyboard

/** Bounds shared by the shell-only own-surface receiver and its offline checks. */
internal object OwnCapturePolicy {
    const val CHUNK = 32_768
    const val MAX_BYTES = 4_194_304
    const val TTL = 120_000L
    const val TIMEOUT = 5_000L
    fun target(value: String?) = when (value) {
        "settings", "strip", "panel", "editor", "guide" -> true
        else -> false
    }
    fun id(value: String?) = value != null && Regex("[a-f0-9]{32}").matches(value)
    fun size(width: Int, height: Int) = width in 1..2048 && height in 1..2048 && width.toLong() * height <= 2_000_000L
    fun readable(offset: Int, total: Int, now: Long, expiry: Long) =
        total in 1..MAX_BYTES && offset >= 0 && offset < total && offset % CHUNK == 0 && now < expiry
}
