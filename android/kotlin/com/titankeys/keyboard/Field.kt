package com.titankeys.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * What the focused text field allows. Ordinary text gets words even when the app supplies its own suggestions,
 * as Google Maps does. Passwords, email and URI fields keep their own rules, and a private field does not teach the keyboard.
 */
data class Field(
    /**
     * Swipes, touchpad taps, the delete flick, snippets and shortcodes type here. Where this is false, as in password, email and
     * number fields and with exact typing, the touchpad only steers the cursor and works the panel, and keys type as they are.
     */
    val words: Boolean,
    /** Typing here may teach the keyboard new words. */
    val learning: Boolean,
    /** Two quick spaces after a word become a full stop. */
    val fullStops: Boolean,
    /** A search bar that is empty or fully selected shows starter words: a browser's, or an app's own search field. */
    val starters: Boolean,
    /** Sentences may start with a capital letter. */
    val sentenceCapitalisation: Boolean,
    /** Typed words may be fixed when they end. A private field allows this, but nothing is learned from it. */
    val autocorrect: Boolean = false,
    /** Typed words get suggestions. Nothing is learned unless [learning] is also true. */
    val suggestions: Boolean = learning,
    /** Each key goes to the app as a key event, as in a terminal. See [ExactTyping]. */
    val exact: Boolean = false,
    /** A password field. Text copied from it never goes into the clipboard history. */
    val secret: Boolean = false,
    /** A typed mark gets its space when the next letter comes. Like full stops, not in passwords or addresses. */
    val markSpaces: Boolean = fullStops,
) {
    companion object {
        val None = Field(words = false, learning = false, fullStops = false, starters = false,
            sentenceCapitalisation = false)

        /** Exact typing: nothing is fixed, added, suggested or learned. */
        val Exact = None.copy(exact = true)

        fun of(inputType: Int, imeOptions: Int): Field {
            if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return None
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            val password = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            val url = variation == InputType.TYPE_TEXT_VARIATION_URI
            val email = variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
            val address = url || email
            val private = imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
            // Browsers often give search bars the URL type. Keep their distinction between search and address bars.
            val search = if (url) inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS == 0
                else !password && !email && (imeOptions and EditorInfo.IME_MASK_ACTION == EditorInfo.IME_ACTION_SEARCH ||
                    inputType and InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE != 0)
            val exact = variation == InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS
                || variation == InputType.TYPE_TEXT_VARIATION_PERSON_NAME
                || variation == InputType.TYPE_TEXT_VARIATION_FILTER
            return Field(words = !password && !email, learning = !password && !address && !private,
                fullStops = !password && !address, starters = search,
                sentenceCapitalisation = !password && !address && inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0,
                autocorrect = !password && !address && !exact,
                suggestions = !password && !email, secret = password)
        }

        /**
         * Whether an input log may record a field. Passwords, addresses and fields asking for no suggestions stay out.
         * A Debug reference mark only overrides a request for no personalised learning.
         */
        fun logged(inputType: Int, imeOptions: Int, privateImeOptions: String?): Boolean {
            if (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) return false
            return of(inputType, imeOptions).learning || privateImeOptions?.contains(RAW_LOG_REFERENCE) == true
                && of(inputType, imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING.inv()).learning
        }

        private const val RAW_LOG_REFERENCE = "com.titankeys.rawlog.reference"
    }
}
