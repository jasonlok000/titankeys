package com.titankeys.keyboard

import java.lang.ref.WeakReference

/** The first loss ends a burst permanently, even when the same editor regains focus before its next event. */
internal class FeedFocusLease<Dispatch : Any>(
    dispatch: Dispatch,
    private val currentDispatch: () -> Dispatch?,
    private val editorValid: () -> Boolean,
) {
    private val expectedDispatch = WeakReference(dispatch)
    var closed = false
        private set

    fun checkedDispatch(): Dispatch? =
        if (!closed && runCatching(editorValid).getOrDefault(false)) sameDispatch() else null

    fun sameDispatch(): Dispatch? = currentDispatch()?.takeIf { it === expectedDispatch.get() }

    fun close(): Boolean {
        if (closed) return false
        closed = true
        return true
    }
}
