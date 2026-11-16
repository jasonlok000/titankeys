package com.atlas.rime

/**
 * How Chinese characters come out: one setting for every Chinese input method (docs/languages.md, owner 2026-11-16).
 * Each is one Rime option, set on every session; the schemas convert Rime's Traditional word lists with OpenCC.
 * There's no default: the user picks one before a Chinese input method turns on, so a missing setting is null.
 */
enum class ChineseVariant(val setting: String, val option: String) {
    HongKong("zh_hk", "zh_hk"),
    Taiwan("zh_tw", "zh_tw"),
    Simplified("zh_hans", "zh_hans");

    companion object {
        /** The setting's value (TypingSettings' chineseVariant), or null when none has been picked. */
        fun of(setting: String?): ChineseVariant? = entries.firstOrNull { it.setting == setting }

        /** Every option the schemas know, each on or off, so exactly [variant]'s is on. */
        fun options(variant: ChineseVariant): List<Pair<String, Boolean>> = entries.map { it.option to (it == variant) }
    }
}
