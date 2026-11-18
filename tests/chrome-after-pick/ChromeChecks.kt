// Appends to the existing offline Composer fixture support in a temporary file.
private fun chromeAfterPick() {
    val world = Realistic()
    val uri = Field.of(0x80011, 0x12000002)
    fun typist(words: FakeWords): Typist = Typist(world, autocorrect = null, field = uri, words = words,
        composerWords = object : Words by words {
            override fun complete(before: String, cancelled: AtomicBoolean, sentenceCapitalisation: Boolean, result: (List<String>) -> Unit) {
                // A real engine moves to next words after a separator; it offers
                // the old completion only when Chrome rewrites it as a prefix.
                if (before.endsWith(" ")) result(listOf("world"))
                else words.complete(before, cancelled, sentenceCapitalisation, result)
            }
        })
    fun browser(): Typist {
        val words = FakeWords(Lists(world.words)).also { it.completions = listOf("hello", "help") }
        return typist(words).type("hel").also {
            val index = it.bar.indexOf("hello")
            must(index >= 0, "Fixture offers hello before choosing")
            it.choose(index)
            same(it.bar, listOf("world"), "A kept separator still permits next-word predictions")
        }
    }
    // An editor filter can normalize commitText before the verification read.
    for (inline in listOf(false, true)) {
        val words = FakeWords(Lists(world.words)).also { it.completions = listOf("hello", "help") }
        val t = typist(words).type("hel")
        t.editor.afterReplace = { editor ->
            editor.text = editor.text.trimEnd()
            editor.cursor = if (inline) 3 to editor.text.length else editor.text.length to editor.text.length
        }
        t.choose(t.bar.indexOf("hello"))
        must("hello" !in t.bar, "Synchronous Chrome normalization must not restore the accepted choice")
    }
    // Chrome removes the keyboard's trailing space, then reports its rewritten selection.
    browser().also {
        it.editor.text = "hello"
        it.select(5)
        must("hello" !in it.bar, "Chrome trimmed-space callback must not re-adopt the picked word")
        // Repeated/stale selection callbacks and delayed prediction work must not revive it.
        it.composer.selectionChanged(6, 6)
        it.settle()
        it.select(5)
        must("hello" !in it.bar, "Stale callback must not restore the chosen word")
        it.type("x")
        must(it.bar.isNotEmpty(), "New typing resumes completions")
    }
    browser().also {
        it.editor.text = "hello"
        it.select(3, 5)
        must("hello" !in it.bar, "Chrome inline selected suffix must not re-adopt the picked word")
        it.select(2)
        must(it.bar.isNotEmpty(), "A real cursor relocation may complete a word again")
    }
    browser().also {
        it.editor.text = "hello"
        it.editor.cursor = 5 to 5
        it.editor.fieldId++
        it.composer.start(uri, 5, 5)
        it.settle()
        must("hello" in it.bar, "The same letters in a new field are not suppressed")
    }
    browser().also {
        it.words.completions = null
        it.editor.text = "hello"
        it.select(5)
        val old = it.words.unanswered.toList()
        it.words.unanswered.clear()
        it.type("x")
        it.words.completions = listOf("hellox")
        for (reply in old) reply(listOf("hello"))
        while (it.words.unanswered.isNotEmpty()) it.words.unanswered.removeFirst()(listOf("hellox"))
        it.settle()
        must("hello" !in it.bar, "Stale async completions cannot revive a previously picked word after new typing")
    }
    browser().also {
        it.editor.text = "hello"
        it.editor.cursor = 5 to 5
        it.composer.start(uri, 5, 5)
        it.settle()
        must("hello" !in it.bar, "Same-field restart must not re-adopt the accepted word")
    }
    browser().also {
        it.editor.text = "hello world"
        it.select(5, 11)
        must("hello" !in it.bar, "Inline app completion after the accepted word leaves no old choice")
    }
    run {
        val words = FakeWords(Lists(world.words)).also { it.completions = listOf("hello", "help") }
        val t = typist(words).type("hel")
        t.editor.holdState = true
        t.choose(t.bar.indexOf("hello"))
        must("hello" !in t.bar, "Accepted choice cannot stay visible while state confirmation waits")
        t.editor.text = "hello"
        t.editor.cursor = 5 to 5
        t.editor.holdState = false
        while (t.editor.pendingStates.isNotEmpty()) t.editor.pendingStates.removeFirst()(Editor.State("hello", "", 5, 5))
        t.settle()
        must("hello" !in t.bar, "Deferred rewritten state confirmation cannot revive the choice")
    }
    browser().also {
        it.editor.text = "hello"
        it.select(5)
        it.backspace()
        must(it.bar.isNotEmpty(), "Deleting the accepted occurrence resumes suggestions")
    }
    browser().also {
        it.editor.text = "help"
        it.select(4)
        must(it.bar.isNotEmpty(), "A different app rewrite is not mistaken for the settled occurrence")
    }
    run {
        val words = FakeWords(Lists(world.words)).also { it.completions = listOf("hello", "help") }
        val t = typist(words).type("hel")
        t.editor.refuses = true
        t.choose(t.bar.indexOf("hello"))
        same(t.text, "hel", "A rejected pick must not fabricate an accepted edit")
        must(t.bar.isNotEmpty(), "A rejected pick can still offer completions")
    }
    run {
        val words = FakeWords(Lists(world.words)).also { it.completions = listOf("hello", "help") }
        val t = typist(words).type("hel")
        t.editor.holdState = true
        t.choose(t.bar.indexOf("hello"))
        t.editor.unreadable = true
        t.editor.holdState = false
        while (t.editor.pendingStates.isNotEmpty()) t.editor.pendingStates.removeFirst()(null)
        t.settle()
        must(t.bar.isEmpty(), "Failed state verification cannot show the already accepted word as a starter")
    }
    check(failures.isEmpty()) { failures.joinToString("\n") }
    println("Chrome after-pick checks passed")
}

fun main() = chromeAfterPick()
