package com.titankeys.keyboard

fun main() {
    val original = Any()
    var hook: Any? = original
    var focused = true

    val transient = FeedFocusLease(original, { hook }, { focused })
    check(transient.checkedDispatch() === original)
    focused = false
    check(transient.checkedDispatch() == null)
    check(transient.close())
    focused = true
    check(transient.checkedDispatch() == null) { "Regaining the same field must not restart a cancelled feed" }
    check(!transient.close()) { "Cleanup must run only once" }
    check(transient.sameDispatch() === original) { "The original hook can still clear its owned holds" }

    val replaced = FeedFocusLease(original, { hook }, { focused })
    hook = Any()
    check(replaced.checkedDispatch() == null) { "A replaced hook must not receive already queued input" }
    check(replaced.close())
    check(replaced.sameDispatch() == null) { "Cleanup must not call a replacement hook" }
    hook = original
    check(replaced.checkedDispatch() == null) { "Restoring a hook must not revive the old burst" }

    val brokenEditor = FeedFocusLease(original, { hook }) { error("editor lookup failed") }
    check(brokenEditor.checkedDispatch() == null) { "An editor check failure must refuse dispatch" }
    println("Debug feed focus checks passed")
}
