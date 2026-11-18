private fun searchRows() {
    val world = Realistic()
    val maps = Field.of(0x80001, 0x12000003)
    val chrome = Field.of(0x80011, 0x12000002)
    for (field in listOf(maps, Typist.FIELD)) {
        for (state in listOf<Editor.State?>(null, Editor.State("", "", 0, 7), Editor.State("", "", 0, 0))) {
            val t = Typist(world, field = field)
            t.words.completions = listOf("testing", "test")
            t.type("tes")
            t.words.completions = null
            t.editor.holdState = true
            t.rows.clear()
            t.composer.choose(t.bar.indexOf("testing"))
            same(t.text, "testing ", "Post-pick fixture committed displayed word")
            must(t.rows.isEmpty(), "Pick retains old row until state returns")
            t.editor.pendingStates.removeFirst()(state)
            must(t.rows.isEmpty(), "Transient post-pick confirmation must not emit unrelated row: ${t.rows}")
            t.editor.holdState = false
            t.composer.selectionChanged(8, 8)
            t.settle()
            t.words.unanswered.removeLast()(listOf("the", "new"))
            same(t.bar, listOf("the", "new"), "Verified recovery delivers next words")
            same(t.rows.map { it.second }, listOf(listOf("the", "new")), "Next words are the first row after a pick")
        }
        val t = Typist(world, field = field)
        t.words.completions = listOf("testing")
        t.type("tes")
        t.words.completions = null
        t.editor.holdState = true
        t.rows.clear()
        t.choose(t.bar.indexOf("testing"))
        must(t.rows.isEmpty(), "The pick waits for confirmation while the cursor report comes through")
        t.editor.pendingStates.removeFirst()(null)
        t.words.unanswered.removeLast()(listOf("the", "new"))
        same(t.rows.map { it.second }, listOf(listOf("the", "new")), "A matching cursor report recovers an unavailable state query")
    }
    fun fresh(field: Field): Typist = Typist(world, field = field).also {
        it.words.completions = null
        it.rows.clear()
    }
    fresh(maps).also { t ->
        t.type("testing")
        must(t.rows.isNotEmpty(), "Maps gives a verified pending word row while models wait")
        must(t.rows.none { (_, row) -> row.any { it in t.words.starts } }, "Maps never flashes unrelated starters before testing results: ${t.rows}")
        same(t.bar.firstOrNull(), "testing", "The pending Maps row follows the verified text")
        val old = t.words.unanswered.removeLast()
        t.type("x")
        t.rows.clear()
        old(listOf("unrelated"))
        must(t.rows.isEmpty(), "Late earlier-prefix completion emits no row")
        t.words.unanswered.removeLast()(listOf("testingx", "testify"))
        must(t.rows.single().second.contains("testify"), "Current completion belongs to new prefix")
        val middle = t.bar.first()
        same(t.type(" ").text.trimEnd(), middle, "Maps keeps its normal autocorrect when Space ends the word")
    }
    for (literal in listOf("testing", "Testing", "TESTing", "teh", "TESTing21")) {
        fresh(chrome).also { t ->
            t.words.completions = listOf("testingAlternative", "the")
            for (letter in literal) {
                t.composer.type(letter.toString())
                same(t.bar.firstOrNull(), t.text, "URI updates its literal as soon as a key is typed")
                t.settle()
            }
            must(t.rows.all { (text, row) -> row.isEmpty() || row.first() == text }, "URI each-key row retains current literal/case $literal: ${t.rows}")
            t.type(" ")
            same(t.text, "$literal ", "URI each-key Space retains literal")
        }
        fresh(chrome).also { t ->
            t.type(literal)
            same(t.bar.firstOrNull(), literal, "URI pending row preserves literal casing $literal")
            t.words.unanswered.removeLast()(emptyList())
            same(t.bar.firstOrNull(), literal, "URI empty completions keep literal $literal")
            must(t.rows.all { (typed, row) -> row.isEmpty() || row.first() == typed }, "Every URI emitted row agrees with Space for $literal")
            t.type(" ")
            same(t.text, "$literal ", "URI Space retains exact literal $literal")
        }
        fresh(chrome).also { t ->
            t.words.completions = listOf("testingAlternative", literal.lowercase(), "the")
            t.type(literal)
            same(t.bar.firstOrNull(), literal, "URI literal stays middle with side offers")
            must("testingAlternative" in t.bar, "URI keeps side completion")
            if (literal != literal.lowercase()) must(literal.lowercase() in t.bar.drop(1), "URI can offer a case variant beside the literal")
            t.choose(t.bar.indexOf("testingAlternative"))
            same(t.text, "testingAlternative ", "URI side tap still replaces literal")
        }
    }
    for (pick in listOf(false, true)) {
        fresh(chrome).also { t ->
            t.tap("TESTing")
            same(t.bar.firstOrNull(), "TESTing", "URI light tap retains the literal before its own space")
            t.words.unanswered.removeLast()(listOf("testingAlternative"))
            if (pick) {
                t.choose(t.bar.indexOf("testingAlternative"))
                same(t.text, "testingAlternative ", "A side pick replaces the URI light tap and its space")
            } else {
                t.type(" ")
                same(t.text, "TESTing ", "Space keeps the URI light tap and its existing space")
            }
        }
    }
    fresh(chrome).also { t ->
        t.type("TEST")
        t.editor.text = "TESTing"
        t.select(4, 7)
        same(t.bar.firstOrNull(), "TEST", "URI inline selection leaves the typed prefix in the middle")
        same(t.type(" ").text, "TEST ", "Space replaces the URI inline selection and keeps the prefix")
    }
    for (inline in listOf(false, true)) {
        fresh(chrome).also { t ->
            t.words.completions = listOf("testing")
            t.type("tes")
            t.editor.afterReplace = { editor ->
                editor.text = if (inline) "testingAlternative" else "testing"
                editor.cursor = if (inline) 7 to editor.text.length else 7 to 7
            }
            t.rows.clear()
            t.choose(t.bar.indexOf("testing"))
            must(t.rows.all { it.second.isEmpty() }, "Chrome's post-pick rewrite never reoffers the selected word: ${t.rows}")
            same(t.bar, emptyList<String>(), "Chrome's bar clears after the app rewrites the pick")
        }
    }
    fresh(chrome).also { t ->
        t.words.completions = listOf("testing")
        t.type("tes")
        t.words.completions = null
        t.rows.clear()
        t.choose(t.bar.indexOf("testing"))
        t.words.unanswered.removeLast()(emptyList())
        must(t.rows.all { it.second.isEmpty() }, "Chrome waits on next words without publishing starters")
    }
    fresh(maps).also { t ->
        t.words.completions = listOf("testing")
        t.type("tes")
        t.editor.holdState = true
        t.composer.choose(t.bar.indexOf("testing"))
        val old = t.editor.pendingStates.removeFirst()
        t.editor.holdState = false
        t.editor.fieldId++
        t.editor.text = "new"
        t.editor.reports.clear()
        t.words.completions = null
        t.composer.start(maps, 3, 3)
        t.rows.clear()
        old(Editor.State("testing ", "", 8, 8))
        t.settle()
        must(t.rows.isEmpty(), "An old field's pick confirmation emits no row")
    }
}

fun main() {
    searchRows()
    if (failures.isNotEmpty()) error(failures.joinToString("\n"))
    println("PASS search row sequence, async stale completion/state, URI literal casing, Space and side taps")
}
