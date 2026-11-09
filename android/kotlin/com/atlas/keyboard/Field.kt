package com.atlas.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo

/** What the focused text field allows. */
data class Field(
    /** Swipes and touchpad taps type words. */
    val words: Boolean,
    /** Typing here may teach the keyboard new words. */
    val learning: Boolean,
    /** Two quick spaces after a word become a full stop. */
    val fullStops: Boolean,
    /** An empty search bar shows starter words. */
    val starters: Boolean,
    /** Sentences may start with a capital letter. */
    val sentenceCapitalisation: Boolean,
    /** Typed words may be fixed when they end. A private field allows this, but nothing is learned from it. */
    val autocorrect: Boolean = false,
) {
    companion object {
        val None = Field(words = false, learning = false, fullStops = false, starters = false,
            sentenceCapitalisation = false)

        fun of(inputType: Int, imeOptions: Int): Field {
            if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return None
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            val password = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            val address = variation == InputType.TYPE_TEXT_VARIATION_URI
                || variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
            val private = imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
            // Browsers often give search bars the URL type.
            val starters = variation == InputType.TYPE_TEXT_VARIATION_URI && !private
                && inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS == 0
            val exact = variation == InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS
                || variation == InputType.TYPE_TEXT_VARIATION_PERSON_NAME
                || variation == InputType.TYPE_TEXT_VARIATION_FILTER
            return Field(words = !password, learning = !password && !address && !private,
                fullStops = !password && !address, starters = starters,
                sentenceCapitalisation = !password && !address && inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0,
                autocorrect = !password && !address && !exact)
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
