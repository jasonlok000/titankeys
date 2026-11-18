package com.titankeys.keyboard

fun main() {
    val back = SetupSetting.Accessibility
    val sym = SetupSetting.Sym
    val empty = SetupHistory()
    check(empty.observed(back, false, edited = false) == empty) { "migration does not guess a previous grant" }
    var history = empty.observed(back, true, edited = false).observed(sym, true, edited = false)
    check(history.observed(back, null, edited = false) == history) { "unreadable or unsupported is not a loss" }
    check(history.observed(back, null, edited = true) == history) { "an unreadable Settings return preserves the baseline" }
    history = history.observed(back, false, edited = false)
    check(history.confirmed == sym.bit && history.due == back.bit) { "an external loss schedules Setup" }
    history = history.observed(sym, false, edited = true)
    check(history.confirmed == 0 && history.due == back.bit) { "an intentional change leaves unrelated losses due" }
    history = SetupHistory(history.confirmed, history.due and back.bit.inv())
    check(history.observed(back, false, edited = false).due == 0) { "an unchanged loss does not reopen Setup" }
    history = history.observed(back, true, edited = false).observed(back, false, edited = false)
    check(history.due == back.bit) { "restoring the setting arms a future loss again" }
    check(history.observed(back, true, edited = false).due == 0) { "a repair before opening cancels the prompt" }
    check(history.forget(back.bit).due == 0) { "Not now acknowledges the Accessibility loss" }
    check(SetupSetting.entries.all { setting ->
        empty.observed(setting, true, edited = false).observed(setting, false, edited = false).due == setting.bit
    }) { "each setting has its own history" }
    println("Setup loss checks passed")
}
