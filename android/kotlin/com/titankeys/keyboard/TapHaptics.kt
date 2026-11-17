package com.titankeys.keyboard

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * The keyboard's root, and the one place a tap on the touchscreen is felt (owner, 2026-11-17: "For tapping gives haptics, shd
 * be globally, including the normal paste menus or like sym menus etc."): every tile, button or item the keyboard draws, in
 * the bar, the switcher, the accents, the Chinese menu, the Sym panel and the clipboard. A tap is a touch one of them took,
 * lifted where it went down, before a long press. [tapped] gets the touch's down time once the tap's own handler has run, so
 * a screen that plays its own haptic for it (a word on the bar, a menu button, a pasted clip) isn't doubled. The keyboard's
 * own keys never come here.
 */
internal class TapHaptics(context: Context, private val tapped: (downTime: Long) -> Unit) : FrameLayout(context) {
    /**
     * TitanKeys Debug only: each tap on the screen, where and on what, for the phone checks (`adb logcat -s TitanKeysTap`). Never
     * any text, only the place and the view's kind.
     */
    var log: ((String) -> Unit)? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downTime = 0L
    private var downX = 0f
    private var downY = 0f
    private var taken = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val handled = super.dispatchTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTime = event.downTime
                downX = event.x
                downY = event.y
                taken = handled
            }
            MotionEvent.ACTION_MOVE -> if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) taken = false
            MotionEvent.ACTION_UP -> {
                if (taken && event.eventTime - downTime < ViewConfiguration.getLongPressTimeout()) {
                    val at = downTime
                    log?.invoke("tap at ${event.rawX.toInt()},${event.rawY.toInt()} on ${viewAt(this, event.x, event.y)?.javaClass?.simpleName}")
                    // After the view's own click, which Android posts as the touch lifts.
                    post { tapped(at) }
                } else if (event.eventTime - downTime < ViewConfiguration.getLongPressTimeout()) {
                    log?.invoke("tap at ${event.rawX.toInt()},${event.rawY.toInt()} taken by nothing")
                }
                taken = false
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> taken = false
        }
        return handled
    }

    /** The deepest view showing at ([x], [y]) in [group]'s own coordinates. */
    private fun viewAt(group: android.view.ViewGroup, x: Float, y: Float): android.view.View? {
        for (i in group.childCount - 1 downTo 0) {
            val child = group.getChildAt(i)
            if (child.visibility != VISIBLE || x < child.left || x >= child.right || y < child.top || y >= child.bottom) continue
            val inner = if (child is android.view.ViewGroup) viewAt(child, x - child.left + child.scrollX, y - child.top + child.scrollY) else null
            return inner ?: child
        }
        return null
    }
}
