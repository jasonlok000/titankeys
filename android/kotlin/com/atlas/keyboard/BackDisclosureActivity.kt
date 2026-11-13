package com.atlas.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Google Play's disclosure for the accessibility service ([BackService]), shown before Android's Accessibility page opens:
 * what the service sees and does, and "No thanks" or "Agree". Only "Agree" counts as agreement. Back counts as "No thanks",
 * and the screen never closes by itself. After "No thanks" the guide goes on without the service, and Settings > Setup
 * shows this screen again. The text is in strings.xml (docs/play/accessibility.md).
 */
class BackDisclosureActivity : Activity() {
    /** The same sizes at any display size or font size (FixedScale). */
    override fun attachBaseContext(base: Context) = super.attachBaseContext(FixedScale.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(false)
        val padding = dp(24)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(text(getString(R.string.back_disclosure_title), 26f, bold = true))
            addView(text(getString(R.string.back_disclosure_intro), 17f))
            addView(text(getString(R.string.back_disclosure_does_title), 19f, bold = true, top = 20))
            addView(text(boldLead(getString(R.string.back_disclosure_back)), 17f))
            addView(text(boldLead(getString(R.string.back_disclosure_keys)), 17f))
            for (line in listOf(R.string.back_disclosure_keys_sym, R.string.back_disclosure_keys_launcher, R.string.back_disclosure_keys_back))
                addView(text("•  " + getString(line), 17f, indent = 16))
            addView(text(boldLead(getString(R.string.back_disclosure_touchpad)), 17f))
            addView(text(getString(R.string.back_disclosure_never_title), 19f, bold = true, top = 20))
            addView(text("•  " + getString(R.string.back_disclosure_never_screen), 17f))
            addView(text("•  " + getString(R.string.back_disclosure_never_keep), 17f))
            addView(text(boldLead(getString(R.string.back_disclosure_no)), 17f, top = 20))
            addView(LinearLayout(this@BackDisclosureActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, dp(24), 0, 0)
                addView(button(getString(R.string.back_disclosure_decline), filled = false) { answer(false) })
                addView(button(getString(R.string.back_disclosure_agree), filled = true) { answer(true) })
            })
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(GuideColours.BLACK)
            fitsSystemWindows = true
            addView(content)
        })
    }

    @Deprecated("Back is the same as No thanks here")
    @Suppress("DEPRECATION")
    override fun onBackPressed() = answer(false)

    private fun answer(agreed: Boolean) {
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putString(ANSWER, if (agreed) AGREED else DECLINED).apply()
        // From Settings the screen opens Accessibility itself. The guide opens it, to watch for the switch.
        if (agreed && intent.getBooleanExtra(OPEN_PAGE, false)) {
            PhoneSetup.page(this, SetupPart.Back)?.let { page ->
                try { startActivity(page) } catch (_: RuntimeException) {}
            }
        }
        setResult(if (agreed) RESULT_OK else RESULT_CANCELED)
        finish()
    }

    /** The words before the first colon, in bold, as each point's name. */
    private fun boldLead(line: String) = SpannableStringBuilder(line).apply {
        val colon = line.indexOf(':')
        if (colon > 0) setSpan(StyleSpan(Typeface.BOLD), 0, colon + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun text(value: CharSequence, size: Float, bold: Boolean = false, top: Int = 8, indent: Int = 0) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(GuideColours.CREAM)
        typeface = if (bold) GuideFonts.heavy(context) else GuideFonts.regular(context)
        setPadding(dp(indent), dp(top), 0, 0)
    }

    private fun button(label: String, filled: Boolean, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 17f
        isAllCaps = false
        setTextColor(if (filled) GuideColours.INK else GuideColours.CREAM)
        if (filled) setBackgroundColor(GuideColours.ORANGE) else setBackgroundColor(0)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { marginStart = dp(12) }
        setPadding(dp(20), dp(10), dp(20), dp(10))
        setOnClickListener { action() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val PREFERENCES = "back_disclosure"
        private const val ANSWER = "answer"
        private const val AGREED = "agreed"
        private const val DECLINED = "declined"
        private const val OPEN_PAGE = "open_page"

        fun agreed(context: Context) =
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(ANSWER, null) == AGREED

        /** The user said "No thanks", so the guide's setup goes on without the service. Settings can still ask again. */
        fun declined(context: Context) =
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(ANSWER, null) == DECLINED

        /** The screen for the guide, which opens Accessibility itself after "Agree". */
        fun forGuide(context: Context) = Intent(context, BackDisclosureActivity::class.java)

        /** The screen for Settings > Setup, which opens Accessibility after "Agree". */
        fun forSettings(context: Context) = Intent(context, BackDisclosureActivity::class.java).putExtra(OPEN_PAGE, true)
    }
}
