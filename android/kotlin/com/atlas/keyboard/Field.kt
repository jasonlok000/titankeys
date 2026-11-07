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
            return Field(words = !password, learning = !password && !address && !private,
                fullStops = !password && !address, starters = starters,
                sentenceCapitalisation = !password && !address && inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0)
        }
    }
}
