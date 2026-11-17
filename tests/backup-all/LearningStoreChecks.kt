package com.titankeys.keyboard

import android.content.SharedPreferences

/**
 * The other input methods' learning in Back up everything and the resets (review, 2026-11-17): a restore must merge what each
 * input method learned and kept once, as English's do, and Remove learned words must reach learning not yet written.
 */
fun learningStoreChecks(): List<String> {
    val failures = ArrayList<String>()
    fun check(message: String, ok: Boolean) { if (!ok) failures += message }

    // A word saved in Spanish after the backup was made, then the backup restored: both words stay.
    val phoneSpanish = LanguageLearning().apply { save("hola") }.encode()
    val fileSpanish = LanguageLearning().apply { save("adiós") }.encode()
    val restored = BackupFile.restored(mapOf("language_learning" to mapOf("es" to fileSpanish)),
        mapOf("language_learning" to mapOf("es" to phoneSpanish)), debug = false)
    val merged = LanguageLearning.decode(restored["language_learning"]?.get("es") as String?)
    check("restore: the backup's Spanish word comes back", merged.isSaved("adiós"))
    check("restore: the Spanish word saved since the backup stays", merged.isSaved("hola"))

    // Words typed and learned on both sides: the larger count of each, not their sum, so restoring twice changes nothing.
    val phoneLearned = LanguageLearning().apply { repeat(3) { kept("buenos", "días", known = true) } }.encode()
    val fileLearned = LanguageLearning().apply { kept("buenos", "días", known = true); kept(null, "gracias", known = true) }.encode()
    val twice = LanguageLearning.decode(BackupFile.merge(BackupFile.Merge.LEARNING, phoneLearned, fileLearned) as String)
    val again = LanguageLearning.decode(BackupFile.merge(BackupFile.Merge.LEARNING, twice.encode(), fileLearned) as String)
    check("restore: what each side learned is kept", twice.next("buenos") == listOf("días") && twice.sentenceStarts() == listOf("gracias"))
    check("restore: restoring the same backup again changes nothing", again.encode().lines().sorted() == twice.encode().lines().sorted())

    // A word kept once in Spanish since the backup stays, as English's kept-once words do.
    val keptOnce = BackupFile.restored(mapOf("autocorrect_learning" to mapOf("kept_once_words_es" to setOf("adios|20400"))),
        mapOf("autocorrect_learning" to mapOf("kept_once_words_es" to setOf("ola|20401"))), debug = false)
    check("restore: Spanish kept-once words merge", keptOnce["autocorrect_learning"]?.get("kept_once_words_es") == setOf("adios|20400", "ola|20401"))

    // Remove learned words while the keyboard holds Spanish words it hasn't written yet (it writes every few words).
    val prefs = MemoryPreferences()
    LearnedWords.clear()
    LearnedWords.of(prefs, "es").kept(null, "palabra", known = true)
    LearnedWords.forgetLearned(prefs)
    check("Remove learned words: learning not yet written goes too", LearnedWords.of(prefs, "es").ownWords().isEmpty())
    LearnedWords.save(prefs, "es")
    check("Remove learned words: it doesn't come back when the keyboard writes", LanguageLearning.decode(prefs.getString("es", null)).ownWords().isEmpty())
    LearnedWords.clear()
    return failures
}

/** SharedPreferences in memory, for the checks. */
private class MemoryPreferences : SharedPreferences {
    private val values = HashMap<String, Any?>()
    override fun getAll(): Map<String, *> = HashMap(values)
    override fun getString(key: String, default: String?) = values[key] as String? ?: default
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, default: Set<String>?) = values[key] as Set<String>? ?: default
    override fun getInt(key: String, default: Int) = values[key] as Int? ?: default
    override fun getLong(key: String, default: Long) = values[key] as Long? ?: default
    override fun getFloat(key: String, default: Float) = values[key] as Float? ?: default
    override fun getBoolean(key: String, default: Boolean) = values[key] as Boolean? ?: default
    override fun contains(key: String) = key in values
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val changes = HashMap<String, Any?>()
        private var clear = false
        override fun putString(key: String, value: String?) = apply { changes[key] = value }
        override fun putStringSet(key: String, value: Set<String>?) = apply { changes[key] = value }
        override fun putInt(key: String, value: Int) = apply { changes[key] = value }
        override fun putLong(key: String, value: Long) = apply { changes[key] = value }
        override fun putFloat(key: String, value: Float) = apply { changes[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
        override fun remove(key: String) = apply { changes[key] = null }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) values.clear()
            for ((key, value) in changes) if (value == null) values.remove(key) else values[key] = value
        }
    }
}
