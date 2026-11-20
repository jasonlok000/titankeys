package com.titankeys.support

/** The support page's WebView rules (SupportWebProtocol.kt): only its own assets load, and every bridge message is checked. */
fun supportWebChecks() {
    val origin = SupportWebAssets.ORIGIN
    // Loads: the page's own assets, and nothing else.
    check(SupportWebAssets.assetPath("$origin/support/index.html#popup") == "support/index.html")
    check(SupportWebAssets.assetPath("$origin/support/") == "support/index.html")
    check(SupportWebAssets.assetPath("$origin/support/fonts/inter.woff2") == "support/fonts/inter.woff2")
    check(SupportWebAssets.assetPath("$origin/support/app.js?v=2") == "support/app.js")
    for (refused in listOf("https://example.com/support/index.html", "http://appassets.androidplatform.net/support/index.html",
        "file:///android_asset/support/index.html", "$origin/im/en/words.txt", "$origin/support/../connected/key.pem",
        "$origin/support/%2e%2e/x", "$origin/support/a//b", "$origin/supportx/index.html", "data:text/html,hi",
        "https://appassets.androidplatform.net.evil.example/support/index.html", "javascript:alert(1)", ""))
        check(SupportWebAssets.assetPath(refused) == null) { "should refuse $refused" }
    check(SupportWebAssets.mime("support/index.html") == "text/html")
    check(SupportWebAssets.start("popup").endsWith("#popup") && SupportWebAssets.start("anything").endsWith("#page"))

    // Messages: each op with checked arguments.
    fun parse(text: String) = SupportWebProtocol.parse(text)
    fun refused(text: String): String = try {
        parse(text)
        error("should refuse $text")
    } catch (error: SupportBadRequest) {
        error.code
    }
    check(parse("""{"id":1,"op":"state"}""") == SupportRequest(1, SupportCall.State))
    check(parse("""{"id":2,"op":"previewRank","args":{"cents":500}}""").call == SupportCall.PreviewRank(500))
    check(parse("""{"id":3,"op":"purchase","args":{"productId":"support_tip_unit","quantity":7}}""").call ==
        SupportCall.Purchase("support_tip_unit", 7))
    check(parse("""{"id":4,"op":"purchase","args":{"productId":"support_monthly_full"}}""").call ==
        SupportCall.Purchase("support_monthly_full", 1))
    check(parse("""{"id":5,"op":"joinBoard","args":{"name":"  Sunny ","message":"","anonymous":false}}""").call ==
        SupportCall.JoinBoard("Sunny", null, false))
    check(parse("""{"id":6,"op":"report","args":{"publicId":"0f8fad5b-d9cb-469f-a165-70867728950e"}}""").call ==
        SupportCall.Report("0f8fad5b-d9cb-469f-a165-70867728950e"))
    check(parse("""{"id":7,"op":"board","args":{"page":2}}""").call == SupportCall.Board(2))
    check(parse("""{"id":8,"op":"board"}""").call == SupportCall.Board(0))
    for (op in listOf("removeMe", "dismissPopup", "close")) parse("""{"id":9,"op":"$op"}""")
    check(refused("not json") == "bad_json")
    check(refused("""{"op":"state"}""") == "bad_id")
    check(refused("""{"id":1.5,"op":"state"}""") == "bad_id")
    check(refused("""{"id":1,"op":"open","args":{}}""") == "unknown_op")
    check(refused("""{"id":1,"op":"state","args":{"extra":1}}""") == "bad_args")
    check(refused("""{"id":1,"op":"previewRank","args":{"cents":-1}}""") == "bad_args")
    check(refused("""{"id":1,"op":"previewRank","args":{"cents":2.5}}""") == "bad_args")
    check(refused("""{"id":1,"op":"previewRank","args":{"cents":1000001}}""") == "bad_args")
    check(refused("""{"id":1,"op":"purchase","args":{"productId":"support_tip_3"}}""") == "bad_args")
    check(refused("""{"id":1,"op":"purchase","args":{"productId":"support_tip_full","quantity":2}}""") == "bad_args")
    check(refused("""{"id":1,"op":"purchase","args":{"productId":"support_tip_unit","quantity":101}}""") == "bad_args")
    check(refused("""{"id":1,"op":"joinBoard","args":{"name":"${"n".repeat(81)}","anonymous":false}}""") == "bad_args")
    check(refused("""{"id":1,"op":"joinBoard","args":{"message":"${"m".repeat(121)}","anonymous":false}}""") == "bad_args")
    check(refused("""{"id":1,"op":"joinBoard","args":{"name":"x"}}""") == "bad_args")
    check(refused("""{"id":1,"op":"report","args":{"publicId":"'; drop"}}""") == "bad_args")
    check(refused("""{"id":1,"op":"board","args":{"page":10001}}""") == "bad_args")
    check(refused("x".repeat(SupportWebProtocol.MAX_MESSAGE + 1)) == "too_large")

    // Answers go to the page as a string literal, never as code.
    check(SupportWebProtocol.reply(3, """{"rank":2}""") == """{"id":3,"ok":true,"result":{"rank":2}}""")
    check(SupportWebProtocol.failure(null, "bad_json") == """{"id":null,"ok":false,"error":"bad_json"}""")
    check(SupportWebProtocol.event("receipt", "{}") == """{"event":"receipt","data":{}}""")
    val delivered = SupportWebProtocol.deliver("""{"x":"</script>\"); alert(1); (\""}""")
    check(delivered.startsWith("window.titankeysSupport && window.titankeysSupport.receive(\"") && delivered.endsWith("\")"))
    // The argument is one string literal that reads back as exactly the message.
    val literal = delivered.removePrefix("window.titankeysSupport && window.titankeysSupport.receive(").removeSuffix(")")
    check(com.titankeys.connected.Json.parse(literal) == """{"x":"</script>\"); alert(1); (\""}""")
}
