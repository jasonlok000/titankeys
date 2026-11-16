package com.titankeys.keyboard

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The crash offer (#74, #97) as TitanKeys draws it: a card most of the screen wide, in the app's own type and colours, with the
 * orange "Send it" pill and a quiet "No thanks" under it, both full width. Android's stock alert was too small to read on the
 * Titan (owner, 2026-11-16: "the crash notification is too small. make it bigger."), as TitanKeys' fixed unit makes a system dp
 * about half the phone's own.
 *
 * Its host is an app screen wrapped in [FixedScale], so the sizes here are the same at any display or font size. The words are F's
 * (docs/words-m1.md). tests/crash-offer/run.sh draws it from [Look] to PNGs.
 */
internal object CrashOffer {
    /** The sizes, in TitanKeys' dp ([FixedScale]: the screen's width ÷ 688.6, 1.5625 px on the Titan 2 Elite). */
    object Look {
        /** As the guide's display type. Android's alert title was 20. */
        const val TITLE = 36f
        /** As a Settings row (theme.ts, 45 px). Android's alert message was 16. */
        const val BODY = 29f
        /** The buttons' words, as the body. Android's alert buttons were 14, in capitals. */
        const val BUTTON = 29f
        const val SEND_HEIGHT = 88
        const val NO_HEIGHT = 72
        /** The card's gap from the screen's sides, and its padding. */
        const val MARGIN = 16
        const val PADDING = 32
        const val CORNER = 28
        const val TITLE_GAP = 20
        const val BUTTONS_GAP = 36
        /** How dark the screen behind goes. */
        const val DIM = 0.7f
    }

    fun title(froze: Boolean) = if (froze) "TitanKeys froze last time." else "TitanKeys crashed last time."
    const val TEXT = "Sorry about that. Send us the report so we can fix it? It has the error and the versions, nothing you " +
        "typed. Nothing goes unless you send it."
    const val SEND = "Send it"
    const val NO = "No thanks"

    /** The offer on [host]. Back cancels it, as "No thanks" does; a touch outside it does nothing, so a stray tap can't answer. */
    fun dialog(host: Activity, froze: Boolean, send: () -> Unit, no: () -> Unit): Dialog {
        val haptics = Haptics(host)
        val density = host.resources.displayMetrics.density
        fun dp(value: Int) = Math.round(value * density)
        fun text(value: String, size: Float, bold: Boolean, colour: Int) = TextView(host).apply {
            text = value
            typeface = if (bold) GuideFonts.heavy(host) else GuideFonts.medium(host)
            setTextColor(colour)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, size * density)
        }
        val dialog = Dialog(host)
        val card = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(Look.PADDING), dp(Look.PADDING), dp(Look.PADDING), dp(Look.PADDING - 8))
            background = GradientDrawable().apply {
                setColor(GuideColours.SURFACE)
                setStroke(dp(1), GuideColours.DIVIDER)
                cornerRadius = dp(Look.CORNER).toFloat()
            }
            // The words scroll if a screen is ever too short for them, and the buttons stay in sight.
            addView(ScrollView(host).apply {
                isVerticalFadingEdgeEnabled = true
                setFadingEdgeLength(dp(32))
                addView(LinearLayout(host).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(text(title(froze), Look.TITLE, bold = true, colour = GuideColours.CREAM))
                    addView(text(TEXT, Look.BODY, bold = false, colour = GuideColours.CREAM),
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                            .apply { topMargin = dp(Look.TITLE_GAP) })
                })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            // The one to press: the orange pill, full width.
            addView(text(SEND, Look.BUTTON, bold = true, colour = GuideColours.INK).apply {
                gravity = Gravity.CENTER
                minHeight = dp(Look.SEND_HEIGHT)
                background = GradientDrawable().apply {
                    setColor(Accent.colour)
                    cornerRadius = dp(Look.SEND_HEIGHT / 2).toFloat()
                }
                isClickable = true
                onPress(haptics) {
                    dialog.dismiss()
                    send()
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(Look.BUTTONS_GAP) })
            // Saying no is quiet, full width under it.
            addView(text(NO, Look.BUTTON, bold = true, colour = GuideColours.MUTED).apply {
                gravity = Gravity.CENTER
                minHeight = dp(Look.NO_HEIGHT)
                isClickable = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                onPress(haptics) {
                    dialog.dismiss()
                    no()
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) })
        }
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(card)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnCancelListener { no() }
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // Most of the screen wide: the screen less a margin each side. Its height is its words and buttons.
            setLayout(host.resources.displayMetrics.widthPixels - 2 * dp(Look.MARGIN), ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(Look.DIM)
        }
        dialog.setOwnerActivity(host)
        return dialog
    }
}
