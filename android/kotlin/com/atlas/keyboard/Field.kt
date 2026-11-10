package com.atlas.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo

/** What the focused text field allows. */
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
    /** A search bar that is empty or fully selected shows starter words. */
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
            // Browsers often give search bars the URL type.
            val search = url && inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS == 0
            val exact = variation == InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS
                || variation == InputType.TYPE_TEXT_VARIATION_PERSON_NAME
                || variation == InputType.TYPE_TEXT_VARIATION_FILTER
            // A field with no suggestions types no words either: an address or a password is typed key by key.
            return Field(words = !password && !email, learning = !password && !address && !private,
                fullStops = !password && !address, starters = search,
                sentenceCapitalisation = !password && !address && inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0,
                autocorrect = !password && !address && !exact,
                // An address bar that asks for no suggestions still gets the bar: browsers search from it too.
                suggestions = !password && !email, secret = password)
        }

        /**
         * Whether the raw input log may record a field. Only fields that allow learning do, which leaves out passwords,
         * addresses and private fields. A field marked as a reference is recorded even though it asks for no
         * personalised learning. The mark overrides nothing else, so a marked password is still never recorded.
         */
        fun logged(inputType: Int, imeOptions: Int, privateImeOptions: String?): Boolean =
            of(inputType, imeOptions).learning || privateImeOptions?.contains(RAW_LOG_REFERENCE) == true
                && of(inputType, imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING.inv()).learning

        private const val RAW_LOG_REFERENCE = "com.titankeys.rawlog.reference"
    }
}
