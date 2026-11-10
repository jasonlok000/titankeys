package com.atlas.connected

import java.io.File
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Instant
import java.util.Base64
import java.util.Random

/** Checks the connected features' parts that need no Android: JSON, signatures, the feed rules, sealing and the queue. */
fun main(args: Array<String>) {
    json()
    ed25519()
    feedRules()
    publishedFeed()
    seal(args.getOrNull(0))
    queue()
    example()
    println("Connected checks passed")
}

private fun must(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

private fun same(actual: Any?, expected: Any?, message: String) {
    if (actual != expected) throw AssertionError("$message\n  expected: $expected\n  actual:   $actual")
}

private fun fails(message: String, block: () -> Unit) {
    try {
        block()
    } catch (error: Exception) {
        return
    }
    throw AssertionError(message)
}

private fun hex(text: String) = ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

private fun json() {
    @Suppress("UNCHECKED_CAST")
    val value = Json.parse("""{"a":[1,2.5,-3e2,true,false,null],"b":"x\"\\\/\né😀","c":{}}""") as Map<String, Any?>
    same(value["a"], listOf(1.0, 2.5, -300.0, true, false, null), "JSON lists and numbers")
    same(value["b"], "x\"\\/\né😀", "JSON escapes")
    same(value["c"], emptyMap<String, Any?>(), "An empty object")
    same(Json.parse(Json.quote("a\"b\\c\n\u0001 ")), "a\"b\\c\n\u0001 ", "Quoting reads back")
    same(Json.number(Double.NaN), "null", "NaN has no JSON form")
    same(Json.number(3.0), "3", "Whole numbers have no point")
    for (bad in listOf("", "{", "[1,]", "{\"a\":1,}", "{\"a\":1}x", "\"\u0001\"", "{\"a\":1,\"a\":2}", "01x", "[" .repeat(40) + "]".repeat(40)))
        fails("Bad JSON must fail: $bad") { Json.parse(bad) }
}

private fun ed25519() {
    // RFC 8032, section 7.1, tests 1 and 2.
    val vectors = listOf(
        Triple("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a", "",
            "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b"),
        Triple("3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c", "72",
            "92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00"),
    )
    for ((key, message, signature) in vectors) {
        must(Ed25519.verify(hex(key), hex(message), hex(signature)), "RFC 8032 vector must verify")
        val changed = hex(signature).also { it[10] = (it[10].toInt() xor 1).toByte() }
        must(!Ed25519.verify(hex(key), hex(message), changed), "A changed signature must fail")
        must(!Ed25519.verify(hex(key), hex(message) + byteArrayOf(1), hex(signature)), "A changed message must fail")
    }
    // Signatures from Java's own Ed25519, for many keys and messages.
    val random = Random(7)
    repeat(12) { round ->
        val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val public = Ed25519.publicKeyFromSpki(pair.public.encoded)
        val message = ByteArray(round * 37).also(random::nextBytes)
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(pair.private)
        signer.update(message)
        val signature = signer.sign()
        must(Ed25519.verify(public, message, signature), "A Java signature must verify, round $round")
        val other = Ed25519.publicKeyFromSpki(KeyPairGenerator.getInstance("Ed25519").generateKeyPair().public.encoded)
        must(!Ed25519.verify(other, message, signature), "Another key must fail, round $round")
    }
    // S must be below the group order, or a signature could be changed and still pass.
    val (key, message, signature) = vectors[0]
    val high = hex(signature).also { it[63] = (it[63].toInt() or 0xF0).toByte() }
    must(!Ed25519.verify(hex(key), hex(message), high), "A signature with S too large must fail")
    must(!Ed25519.verify(ByteArray(31), ByteArray(0), hex(signature)), "A short key must fail")
}

private val FEED_KEY: ByteArray by lazy {
    val source = File("android/kotlin/com/atlas/connected/Connected.kt").readText()
    val key = Regex("FEED_KEY = \"([A-Za-z0-9+/=]+)\"").find(source)!!.groupValues[1]
    Ed25519.publicKeyFromSpki(Base64.getDecoder().decode(key))
}

private fun feed(entries: String, issued: String = "2026-11-10T12:00:00Z") = """{"version":1,"issued":"$issued","entries":[$entries]}"""

private const val ENTRY = """{"id":"v2-1","title":"TitanKeys 2.1 is out","text":"Get it on GitHub.","action":{"label":"Get it","url":"https://example.com/x"},"from":"2026-11-01T00:00:00Z","until":"2026-12-01T00:00:00Z","minVersion":"2.0.0","maxVersion":"2.0.9","builds":["github"]}"""

private fun feedRules() {
    val contents = Feed.read(feed(ENTRY))
    same(contents.issued, Instant.parse("2026-11-10T12:00:00Z").toEpochMilli(), "The issue time")
    val entry = contents.entries.single()
    same(entry.action, Feed.Action("Get it", "https://example.com/x", null), "The action")
    must(entry.notify, "Entries notify unless they say not to")
    val now = Instant.parse("2026-11-15T00:00:00Z").toEpochMilli()
    must(Feed.applies(entry, now, "2.0.0", "github"), "It applies in its range")
    must(!Feed.applies(entry, now, "2.1.0", "github"), "A newer version is outside the range")
    must(!Feed.applies(entry, now, "1.9", "github"), "An older version is outside the range")
    must(!Feed.applies(entry, now, "2.0.0", "play"), "Another build does not get it")
    must(!Feed.applies(entry, Instant.parse("2026-12-01T00:00:00Z").toEpochMilli(), "2.0.0", "github"), "It ends at its until time")
    must(!Feed.applies(entry, Instant.parse("2026-10-31T00:00:00Z").toEpochMilli(), "2.0.0", "github"), "It starts at its from time")
    same(Feed.compareVersions("2.10.0", "2.9"), 1, "Versions compare by number")
    same(Feed.compareVersions("2.1", "2.1.0"), 0, "Missing parts are zero")
    same(Feed.compareVersions("2.1.0-beta", "2.1.0"), 0, "Text after the numbers is ignored")
    val bad = listOf(
        ENTRY.replace("\"v2-1\"", "\"V2 1\""),
        ENTRY.replace("https://example.com/x", "http://example.com/x"),
        ENTRY.replace("\"url\":\"https://example.com/x\"", "\"url\":\"https://example.com/x\",\"page\":\"/settings\""),
        ENTRY.replace("[\"github\"]", "[\"store\"]"),
        ENTRY.replace("[\"github\"]", "[]"),
        ENTRY.replace("\"2.0.0\"", "\"two\""),
        ENTRY.replace("Get it on GitHub.", "x".repeat(601)),
        ENTRY.replace("2026-11-01T00:00:00Z", "soon"),
        "$ENTRY,$ENTRY",
    )
    for (text in bad) fails("A bad entry must make the feed fail: $text") { Feed.read(feed(text)) }
    fails("A feed of another version must fail") { Feed.read(feed(ENTRY).replace("\"version\":1", "\"version\":2")) }
    // An envelope signed by another key, or changed after signing, fails whole.
    val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    val text = feed(ENTRY)
    val signer = Signature.getInstance("Ed25519")
    signer.initSign(pair.private)
    signer.update(text.toByteArray())
    val signature = Base64.getEncoder().encodeToString(signer.sign())
    val envelope = """{"format":"titankeys-feed-1","feed":${Json.quote(text)},"signature":"$signature"}"""
    same(Feed.open(envelope, Ed25519.publicKeyFromSpki(pair.public.encoded)).entries.size, 1, "A good envelope opens")
    fails("The app's key must refuse a feed signed by another key") { Feed.open(envelope, FEED_KEY) }
    fails("A changed feed must fail") { Feed.open(envelope.replace("Get it on GitHub.", "Get it here."), Ed25519.publicKeyFromSpki(pair.public.encoded)) }
    fails("Another format must fail") { Feed.open(envelope.replace("titankeys-feed-1", "titankeys-feed-2"), Ed25519.publicKeyFromSpki(pair.public.encoded)) }
}

/** The feed on the website was signed by the tools with the real key, so the app's code must open it. */
private fun publishedFeed() {
    val contents = Feed.open(File("site/feed.json").readText(), FEED_KEY)
    must(contents.entries.isNotEmpty(), "The published feed has entries")
}

private fun seal(openTool: String?) {
    val pair = KeyPairGenerator.getInstance("XDH").generateKeyPair()
    val plain = Example.batch("2b7e1516-28ae-4d2a-a6d2-abf7158809cf", listOf("""{"v":1}""")).toByteArray()
    val sealed = Seal.seal(plain, pair.public.encoded)
    same(String(sealed.copyOfRange(0, 4)), "TKC1", "Sealed batches start with TKC1")
    must(!String(sealed).contains("contributor"), "A sealed batch must not show its text")
    same(String(Seal.open(sealed, pair.private.encoded, pair.public.encoded)), String(plain), "A sealed batch opens with the right key")
    val other = KeyPairGenerator.getInstance("XDH").generateKeyPair()
    fails("Another key must not open it") { Seal.open(sealed, other.private.encoded, other.public.encoded) }
    val changed = sealed.copyOf().also { it[it.size - 20] = (it[it.size - 20].toInt() xor 1).toByte() }
    fails("A changed batch must not open") { Seal.open(changed, pair.private.encoded, pair.public.encoded) }
    val changedHeader = sealed.copyOf().also { it[10] = (it[10].toInt() xor 1).toByte() }
    fails("A changed header must not open") { Seal.open(changedHeader, pair.private.encoded, pair.public.encoded) }
    must(!Seal.seal(plain, pair.public.encoded).contentEquals(sealed), "Each batch has its own key and nonce")
    // The app's real key must be an X25519 key that seals.
    val source = File("android/kotlin/com/atlas/connected/Connected.kt").readText()
    val appKey = Base64.getDecoder().decode(Regex("CONTRIBUTION_KEY = \"([A-Za-z0-9+/=]+)\"").find(source)!!.groupValues[1])
    must(Seal.seal(plain, appKey).size > Seal.HEADER_BYTES, "The app's contribution key seals")
    // Hand a batch to the owner's tool, which must open it the same way.
    if (openTool != null) {
        val folder = Files.createTempDirectory("seal").toFile().also { it.deleteOnExit() }
        File(folder, "batch.tkc").writeBytes(sealed)
        File(folder, "key.pem").writeText("-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString(pair.private.encoded) + "\n-----END PRIVATE KEY-----\n")
        File(folder, "expected.json").writeBytes(plain)
        File(openTool).writeText(folder.absolutePath)
    }
}

private fun queue() {
    val folder = Files.createTempDirectory("queue").toFile().also { it.deleteOnExit() }
    val queue = ContributionQueue(folder, 4000)
    same(queue.count(), 0, "A new queue is empty")
    same(queue.oldest(1000), emptyList<String>(), "Nothing to send")
    for (i in 0 until 10) queue.add("""{"n":$i,"pad":"${"x".repeat(80)}"}""")
    same(queue.count(), 10, "Ten examples wait")
    same(queue.oldest(250).size, 2, "A batch holds what fits")
    same(queue.oldest(10).size, 1, "A batch always holds at least one")
    queue.removeOldest(3)
    same(queue.count(), 7, "Sent examples leave the queue")
    must(queue.oldest(1000).first().startsWith("""{"n":3,"""), "The oldest left comes first")
    for (i in 10 until 60) queue.add("""{"n":$i,"pad":"${"x".repeat(80)}"}""")
    must(queue.bytes() <= 4000, "The queue never grows past its cap: ${queue.bytes()}")
    must(queue.oldest(100_000).last().startsWith("""{"n":59,"""), "The newest stays when the queue is full")
    queue.add("y".repeat(2000))
    must(queue.oldest(100_000).none { it.startsWith("yyy") }, "An example too large for the queue is dropped")
    fails("An example must be one line") { queue.add("a\nb") }
    queue.clear()
    same(queue.count(), 0, "Clear empties the queue")
    same(queue.bytes(), 0L, "Clear removes the file")
}

private fun example() {
    val rows = listOf(DoubleArray(18) { it.toDouble() }, DoubleArray(18) { if (it == 2) Double.NaN else 0.5 })
    val example = Example("kept", "hello", floatArrayOf(0.12345f, 0.5f), floatArrayOf(0.25f, 0.75f), floatArrayOf(0f, 16.4f),
        intArrayOf(3, -1), rows, 1, "2.1.0", "abc123", "Titan 2 Elite", "github")
    @Suppress("UNCHECKED_CAST")
    val read = Json.parse(example.line()) as Map<String, Any?>
    same(read.keys, setOf("v", "how", "word", "x", "y", "t", "size", "rows", "kept", "app", "models", "phone", "build"),
        "An example holds only these fields")
    same(read["x"], listOf(0.1235, 0.5), "Positions keep four decimals")
    same(read["t"], listOf(0.0, 16.0), "Times are whole milliseconds")
    same(read["size"], listOf(3.0, -1.0), "Sizes are sensor steps")
    same((read["rows"] as List<*>)[1].let { (it as List<*>)[2] }, null, "NaN numbers become null")
    same(read["kept"], 1.0, "The kept row")
    @Suppress("UNCHECKED_CAST")
    val batch = Json.parse(Example.batch("id-1", listOf(example.line(), example.line()))) as Map<String, Any?>
    same((batch["examples"] as List<*>).size, 2, "A batch holds its examples")
    same(batch["contributor"], "id-1", "A batch holds the contributor ID")
    must(Example.wordAllowed("hello", true), "A dictionary word may be sent")
    must(Example.wordAllowed("don't", true), "An apostrophe is part of a word")
    must(!Example.wordAllowed("hello", false), "A word outside the dictionary is never sent")
    for (word in listOf("", "two words", "a@b.com", "https://x", "1234", "x".repeat(33), "a\nb"))
        must(!Example.wordAllowed(word, true), "Not one word: $word")
    fails("A bad kind must fail") { Example("typed", "a", floatArrayOf(0f), floatArrayOf(0f), floatArrayOf(0f), null, emptyList(), -1, "", "", "", "").line() }
    fails("Rows must have 18 numbers") { Example("kept", "a", floatArrayOf(0f), floatArrayOf(0f), floatArrayOf(0f), null, listOf(DoubleArray(3)), 0, "", "", "", "").line() }
}
