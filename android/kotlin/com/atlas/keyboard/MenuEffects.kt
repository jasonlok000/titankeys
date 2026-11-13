package com.atlas.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View

/**
 * A see-through layer over the whole keyboard window, where the word menu's effects rise above the bar (ladder v4, 2026-11-13):
 * the book, the hearts, the torn page, up to [MenuScene.ABOVE] dp over the app. It takes no touch, and the keyboard's insets don't
 * count it, so a touch above the keyboard still reaches the app and one on the keyboard reaches the keyboard. The bar draws into
 * it: see [Strip.drawEffects].
 */
@SuppressLint("ViewConstructor")
class MenuEffects(context: Context, private val strip: Strip) : View(context) {
    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) = strip.drawEffects(canvas, this)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent) = false
}
