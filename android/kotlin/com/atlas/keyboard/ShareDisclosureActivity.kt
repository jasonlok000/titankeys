package com.atlas.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.atlas.connected.Connected
import com.atlas.connected.Contributions
import kotlin.math.roundToInt

/**
 * The short policy before Share swipes turns on, for the guide's end screen (owner: "the share swipe should go back to the end
 * screen with a clear indication on where to click accept"). It opens over the guide, so every way out lands back on the end:
 * - **Turn on Share swipes**, the one orange pill, right after the owner's "Trust me bro." on its own line, turns sharing on;
 * - **Not now**, a quiet text button, answers no, and the offer doesn't come back;
 * - Back leaves it unanswered, as it was.
 *
 * The words are the app's own policy page's (lib/connected-text.ts, sharePolicyText), word for word: tests/guide/run.sh checks
 * that they still match. Settings keeps the app's page.
 */
class ShareDisclosureActivity : Activity() {
    /** Every press here plays its haptic: see [onPress]. */
    private val haptics by lazy { Haptics(this) }
    /** The same sizes at any display size or font size (FixedScale). */
    override fun attachBaseContext(base: Context) = super.attachBaseContext(FixedScale.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(false)
        // The policy scrolls; the owner's line and the two answers stay pinned at the bottom, so the accept shows on any screen
        // (Master, 2026-11-13: at the phone's largest font it sat off screen with no sign of more).
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(16))
            addView(text(TITLE, TITLE_TYPE, bold = true))
            addView(text(SUBTITLE, BODY_TYPE, colour = GuideColours.MUTED, top = 12))
            addView(text(SENT_TITLE, LABEL_TYPE, bold = true, top = 32, label = true))
            for (line in SENT) addView(bullet(line))
            addView(text(NEVER_TITLE, LABEL_TYPE, bold = true, top = 24, label = true))
            for (line in NEVER) addView(bullet(line))
            addView(text(HOW, BODY_TYPE, colour = GuideColours.MUTED, top = 24))
            addView(quiet(POLICY) {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Connected.PRIVACY_URL))) } catch (_: RuntimeException) {}
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        }
        val answers = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(16))
            setBackgroundColor(GuideColours.BLACK)
            // The owner's line, on its own, right before the accept (owner, 2026-11-13: it moved here from the end screen).
            addView(text(TRUST, BODY_TYPE, bold = true))
            // The one thing to press to agree: the orange pill, full width, labelled plainly.
            addView(TextView(context).apply {
                text = AGREE
                typeface = GuideFonts.heavy(context)
                gravity = Gravity.CENTER
                setTextColor(GuideColours.INK)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, px(BODY_TYPE))
                minHeight = dp(56)
                background = GradientDrawable().apply {
                    setColor(GuideColours.ORANGE)
                    cornerRadius = dp(28).toFloat()
                }
                // Agreeing turns Share swipes on: it locks in, as a switch does.
                onPress(haptics, Haptic.AppSwitchOn) { answer(true) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) })
            // Saying no is quiet, a text button under it.
            addView(quiet(DECLINE) { answer(false) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(GuideColours.BLACK)
            fitsSystemWindows = true
            // The text fades out at the bottom edge while there's more of it: the sign that it scrolls.
            addView(ScrollView(context).apply {
                isVerticalFadingEdgeEnabled = true
                setFadingEdgeLength(dp(48))
                addView(content)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(answers, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
        window.statusBarColor = GuideColours.BLACK
        window.navigationBarColor = GuideColours.BLACK
    }

    /** Back leaves the offer as it was: unanswered, and the end screen still offers it. */
    @Deprecated("Back leaves the offer unanswered")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun answer(agreed: Boolean) {
        if (agreed) Contributions.enabled(this, true) else Contributions.declined(this)
        setResult(if (agreed) RESULT_AGREED else RESULT_DECLINED)
        finish()
    }

    private fun text(value: String, size: Float, bold: Boolean = false, colour: Int = GuideColours.CREAM, top: Int = 0,
                     label: Boolean = false) = TextView(this).apply {
        text = value
        typeface = if (bold) GuideFonts.heavy(context) else GuideFonts.medium(context)
        setTextColor(colour)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size))
        if (label) {
            isAllCaps = true
            letterSpacing = .08f
        }
        setPadding(0, dp(top), 0, 0)
    }

    /** A bullet whose wrapped lines start under its words, not under the dot (Master, 2026-11-13). */
    private fun bullet(line: String) = text("", BODY_TYPE, top = 8).apply {
        val dot = "•  "
        text = SpannableString(dot + line).apply {
            setSpan(LeadingMarginSpan.Standard(0, paint.measureText(dot).roundToInt()), 0, length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }
    }

    private fun quiet(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        typeface = GuideFonts.heavy(context)
        gravity = Gravity.CENTER
        setTextColor(GuideColours.MUTED)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, px(BODY_TYPE))
        minHeight = dp(48)
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        onPress(haptics) { action() }
    }

    private fun px(value: Float) = value * resources.displayMetrics.density
    private fun dp(value: Int) = Math.round(value * resources.displayMetrics.density)

    companion object {
        const val RESULT_AGREED = RESULT_OK
        const val RESULT_DECLINED = RESULT_FIRST_USER

        /** The owner's words, not the policy's: the guide's own, so the policy check leaves them out. */
        const val TRUST = "Trust me bro."

        // The type scale (design v2 §1).
        private const val TITLE_TYPE = 24f
        private const val BODY_TYPE = 19f
        private const val LABEL_TYPE = 14f

        // POLICY TEXT: lib/connected-text.ts, sharePolicyText, word for word (tests/guide/run.sh checks it).
        const val TITLE = "Before you share"
        const val SUBTITLE = "Share swipes sends us examples of your swipes, to train the swipe models. Nothing is sent until you turn it on."
        const val SENT_TITLE = "What's sent"
        val SENT = listOf(
            "How each swipe moved across the keys, with its timing and touch size.",
            "The word you kept, only if it's in the dictionary.",
            "The numbers the swipe engine used to rank the words, without the words.",
            "The app and model versions, your phone model, and a random ID.",
        )
        const val NEVER_TITLE = "What's never sent"
        val NEVER = listOf(
            "What you type, or the text around a word.",
            "Names and other words that aren't in the dictionary.",
            "Anything from password, email, web address or private fields.",
            "Your clipboard, snippets and shortcuts.",
        )
        const val HOW = "It's sent encrypted, on Wi-Fi only. You can turn it off and delete what you've sent any time in Settings > Share swipes."
        const val AGREE = "Turn on Share swipes"
        const val DECLINE = "Not now"
        const val POLICY = "Read the full privacy policy"

        fun intent(context: Context) = Intent(context, ShareDisclosureActivity::class.java)
    }
}
