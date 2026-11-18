package com.titankeys.keyboard

/** Setup's settings have stable, independent bits in the saved history. */
internal enum class SetupSetting(val bit: Int) {
    Keyboard(1), DefaultKeyboard(2), Overlay(4), ScrollAssistant(8),
    Accessibility(16), Sym(32), Fn(64), Contacts(128);

    companion object {
        val mask = entries.fold(0) { bits, setting -> bits or setting.bit }
    }
}

/** A known loss moves from confirmed to due once. Unknown readings leave the saved history alone. */
internal data class SetupHistory(val confirmed: Int = 0, val due: Int = 0) {
    fun observed(setting: SetupSetting, on: Boolean?, edited: Boolean): SetupHistory {
        val bit = setting.bit
        return when (on) {
            null -> this
            true -> SetupHistory(confirmed or bit, due and bit.inv())
            false -> if (edited) forget(bit)
                else SetupHistory(confirmed and bit.inv(), due or (confirmed and bit))
        }
    }

    fun forget(bits: Int) = SetupHistory(confirmed and bits.inv(), due and bits.inv())
}
