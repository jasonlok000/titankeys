package com.titankeys.keyboard

fun main() {
    check(OwnCapturePolicy.target("settings") && OwnCapturePolicy.target("strip"))
    for (target in listOf(null, "screen", "display", "../../x", "guide")) check(!OwnCapturePolicy.target(target))
    check(OwnCapturePolicy.id("a".repeat(32)))
    for (id in listOf(null, "../own-surface.png", "A".repeat(32), "a".repeat(31))) check(!OwnCapturePolicy.id(id))
    check(OwnCapturePolicy.size(1076, 1200))
    check(!OwnCapturePolicy.size(2048, 2048))
    check(!OwnCapturePolicy.size(0, 100) && !OwnCapturePolicy.size(Int.MAX_VALUE, Int.MAX_VALUE))
    check(OwnCapturePolicy.readable(0, 1, 0, 120000))
    check(OwnCapturePolicy.readable(32768, 32769, 119999, 120000))
    check(!OwnCapturePolicy.readable(32768, 32768, 1, 120000))
    check(!OwnCapturePolicy.readable(1, 50000, 1, 120000))
    check(!OwnCapturePolicy.readable(-32768, 50000, 1, 120000))
    check(!OwnCapturePolicy.readable(0, 1, 120000, 120000))
    check(!OwnCapturePolicy.readable(0, 4194305, 1, 120000))
    println("PASS fixed capture targets, IDs, dimensions, byte/chunk bounds and expiry")
}
