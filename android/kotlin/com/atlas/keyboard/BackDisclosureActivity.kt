package com.atlas.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Google Play's disclosure for the accessibility service ([BackService]), shown before Android's Accessibility page opens, in the
 * guide's setup and from Settings > Setup. It looks like Share swipes' ([ShareDisclosureActivity], owner, #79): a short title and
 * one line each for what the service does, then what it never does and that TitanKeys works without it. "What exactly does it
 * see?" opens the full detail in place, on the same screen. One orange pill agrees and a quiet "Not now" declines, both pinned to
 * the bottom so they show at any font size.
 *
 * Only the pill counts as agreement. Back counts as "Not now", and the screen never closes by itself. After "Not now" the guide
 * goes on without the service, and Settings > Setup shows this screen again. It's plain, with no jokes, as Play's reviewers read
 * it. The text is in strings.xml (docs/play/accessibility.md).
 */
class BackDisclosureActivity : Activity() {
    /** Every press here plays its haptic: see [onPress]. */
    private val haptics by lazy { Haptics(this) }
    /** The same sizes at any display size or font size (FixedScale). */
    override fun attachBaseContext(base: Context) = super.attachBaseContext(FixedScale.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(false)
        // The full detail, hidden until asked for, in place under the short lines.
        val detail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(text(boldLead(getString(R.string.back_disclosure_more_back)), BODY_TYPE, top = 8))
            addView(text(boldLead(getString(R.string.back_disclosure_more_keys)), BODY_TYPE, top = 12))
            for (line in listOf(R.string.back_disclosure_keys_sym, R.string.back_disclosure_keys_launcher, R.string.back_disclosure_keys_back))
                addView(bullet(getString(line)))
            addView(text(boldLead(getString(R.string.back_disclosure_more_touchpad)), BODY_TYPE, top = 12))
            addView(bullet(getString(R.string.back_disclosure_never_screen), top = 12))
            addView(bullet(getString(R.string.back_disclosure_never_keep)))
            addView(text(getString(R.string.back_disclosure_more_no), BODY_TYPE, colour = GuideColours.MUTED, top = 12))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(16))
            addView(text(getString(R.string.back_disclosure_title), TITLE_TYPE, bold = true))
            addView(text(getString(R.string.back_disclosure_subtitle), BODY_TYPE, colour = GuideColours.MUTED, top = 12))
            for (line in listOf(R.string.back_disclosure_back, R.string.back_disclosure_keys, R.string.back_disclosure_touchpad))
                addView(bullet(boldLead(getString(line)), top = if (line == R.string.back_disclosure_back) 24 else 8))
            addView(text(getString(R.string.back_disclosure_never), BODY_TYPE, bold = true, top = 24))
            addView(text(getString(R.string.back_disclosure_no), BODY_TYPE, colour = GuideColours.MUTED, top = 12))
            val more = quiet(getString(R.string.back_disclosure_more), GuideColours.CREAM, Gravity.START or Gravity.CENTER_VERTICAL) {}
            more.onPress(haptics) {
                detail.visibility = View.VISIBLE
                more.visibility = View.GONE
            }
            addView(more, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) })
            addView(detail)
        }
        val answers = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(16))
            setBackgroundColor(GuideColours.BLACK)
            // The one thing to press to agree: the orange pill, full width, labelled plainly.
            addView(TextView(context).apply {
                text = getString(R.string.back_disclosure_agree)
                typeface = GuideFonts.heavy(context)
                gravity = Gravity.CENTER
                setTextColor(GuideColours.INK)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, px(BODY_TYPE))
                minHeight = dp(56)
                background = GradientDrawable().apply {
                    setColor(Accent.colour)
                    cornerRadius = dp(28).toFloat()
                }
                onPress(haptics) { answer(true) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            // Saying no is quiet, a text button under it.
            addView(quiet(getString(R.string.back_disclosure_decline)) { answer(false) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
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

    @Deprecated("Back is the same as Not now here")
    @Suppress("DEPRECATION")
    override fun onBackPressed() = answer(false)

    private fun answer(agreed: Boolean) {
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putString(ANSWER, if (agreed) AGREED else DECLINED).apply()
        // From Settings the screen opens Accessibility itself. The guide opens it, to watch for the switch.
        if (agreed && intent.getBooleanExtra(OPEN_PAGE, false)) {
            try { PhoneSetup.open(this, SetupPart.Back, ::startActivity) } catch (_: RuntimeException) {}
        }
        setResult(if (agreed) RESULT_OK else RESULT_CANCELED)
        finish()
    }

    /** The words before the first colon, in bold, as each point's name. */
    private fun boldLead(line: String) = SpannableStringBuilder(line).apply {
        val colon = line.indexOf(':')
        if (colon > 0) setSpan(StyleSpan(Typeface.BOLD), 0, colon + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun text(value: CharSequence, size: Float, bold: Boolean = false, colour: Int = GuideColours.CREAM, top: Int = 0) =
        TextView(this).apply {
            text = value
            typeface = if (bold) GuideFonts.heavy(context) else GuideFonts.medium(context)
            setTextColor(colour)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size))
            setPadding(0, dp(top), 0, 0)
        }

    /** A bullet whose wrapped lines start under its words, not under the dot, as on Share swipes' screen. */
    private fun bullet(line: CharSequence, top: Int = 8) = text("", BODY_TYPE, top = top).apply {
        val dot = "•  "
        text = SpannableString(SpannableStringBuilder(dot).append(line)).apply {
            setSpan(LeadingMarginSpan.Standard(0, paint.measureText(dot).roundToInt()), 0, length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }
    }

    private fun quiet(label: String, colour: Int = GuideColours.MUTED, gravity: Int = Gravity.CENTER, action: () -> Unit) =
        TextView(this).apply {
            text = label
            typeface = GuideFonts.heavy(context)
            this.gravity = gravity
            setTextColor(colour)
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
        // The type scale (design v2 §1), as on Share swipes' screen.
        private const val TITLE_TYPE = 24f
        private const val BODY_TYPE = 19f

        private const val PREFERENCES = "back_disclosure"
        private const val ANSWER = "answer"
        private const val AGREED = "agreed"
        private const val DECLINED = "declined"
        private const val OPEN_PAGE = "open_page"

        fun agreed(context: Context) =
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(ANSWER, null) == AGREED

        /** The user said "Not now", so the guide's setup goes on without the service. Settings can still ask again. */
        fun declined(context: Context) =
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(ANSWER, null) == DECLINED

        /** The screen for the guide, which opens Accessibility itself after "Agree". */
        fun forGuide(context: Context) = Intent(context, BackDisclosureActivity::class.java)

        /** The screen for Settings > Setup, which opens Accessibility after "Agree". */
        fun forSettings(context: Context) = Intent(context, BackDisclosureActivity::class.java).putExtra(OPEN_PAGE, true)
    }
}
