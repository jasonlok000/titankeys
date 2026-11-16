package com.titankeys.keyboard

/**
 * When a press on "Names from my contacts" (Settings > Setup, or the guide's contacts part) goes to the app's page in Android's
 * settings instead of Android's own question (#87).
 *
 * Only when Android has stopped asking: the press asked, Android showed nothing and said no by itself. Never right after a "Don't
 * allow" the user pressed: after the second one Android stops showing its reason (shouldShowRequestPermissionRationale is false),
 * which looked the same as "stopped asking" and opened App info on top of the answer. So the activity notes, before it asks,
 * whether it has asked before and whether Android would show its reason, and, as it asks, whether Android's question came up at
 * all (the activity pauses under it). A question dismissed without an answer comes up again next time, as Android does.
 */
object ContactsAsk {
    fun toSettings(granted: Boolean, questionShown: Boolean, askedBefore: Boolean, reasonBefore: Boolean) =
        !granted && !questionShown && askedBefore && !reasonBefore
}
