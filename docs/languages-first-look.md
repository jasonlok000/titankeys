# What a new user meets first, per input method

The first look at each input method on an empty field (the Orchestrator's audit, 2026-11-17), as the files on main give it:
`android/assets/im/<id>/im.json`, its `pairs.txt`, `index.json` and the keyboard's rules. "Bar" is what the bar shows on an
empty field: the input method's starters, then its next-word table's sentence starts, the first three, with a capital where
the script has them; the best sits in the middle, the second on its left (on its right in Hebrew, which reads right
to left). "Mark at Q" is
what the mark over Q shows. "Holding a letter" is with hold for symbols on, the default; with it off, a hold types nothing
more, except Thai's and Hebrew's held letters, which are letters, not symbols.

**How-tos:** every input method's is empty, so Settings shows no how-to on any language's page (`lib/how-to.tsx` hides an
empty one). They land with F's text (`tools/im/howto.json`, then `make.py --howto`).

**Fixed in this pass** (claude/m3-first-look): the bar's sentence starts put the starters before the next-word table's, as
the table counts every sentence as it stands (datelines, page labels); Thai's starters are hand-picked, as Japanese's are;
Mandarin's and Cantonese's starters lose their particles (的 了 和 在, 嘅) and Slovenian's its datelines' "leta"; Serbian in
Latin letters marks SR, not the language's Cyrillic СР.

**Decisions kept, to confirm:** Japanese's and Thai's starters are hand-picked. Hebrew and Thai mix held letters and held
symbols on different keys. Korean, Vietnamese and Hindi open the bigger menu only with a swipe up, as their Shift types
letters. The first-use hint, "Hold a letter for its accents.", reads wrong in the Cyrillic input methods, whose
holds are letters without a key of their own (ж э, х ъ): new wording is F's.

| Input method | Mark at Q | Bar on an empty field | Holding a letter | Notes |
|---|---|---|---|---|
| Afrikaans (`af`) | AF | Die · Dit · Ek; once, the first time: "Hold a letter for its accents." | its accents on the bar (4 keys, e.g. E: é ê ë) |  |
| Azerbaijani (`az`) | AZ | Bu · O · Lakin; once, the first time: "Hold a letter for its accents." | its accents on the bar (7 keys, e.g. E: ə) |  |
| Belarusian (`be`) | БЕ | У · І · А; once, the first time: "Hold a letter for its accents." | its accents on the bar (4 keys, e.g. L: ж э) | The first-use hint says accents, but these holds are letters the layout has no key for: wording for F. |
| Bulgarian (`bg`) | БГ | В · Това · За; once, the first time: "Hold a letter for its accents." | its accents on the bar (2 keys, e.g. M: ь ю) | The first-use hint says accents, but these holds are letters the layout has no key for: wording for F. |
| Bosnian (`bs`) | BS | U · Na · Nakon; once, the first time: "Hold a letter for its accents." | its accents on the bar (4 keys, e.g. C: č ć) |  |
| Catalan (`ca`) | CA | El · La · En; once, the first time: "Hold a letter for its accents." | its accents on the bar (6 keys, e.g. E: é è) |  |
| Czech (`cs`) | CS | V · Na · A; once, the first time: "Hold a letter for its accents." | its accents on the bar (13 keys, e.g. E: é ě) |  |
| Danish (`da`) | DA | Det · Men · I; once, the first time: "Hold a letter for its accents." | its accents on the bar (4 keys, e.g. E: é) |  |
| German (`de`) | DE | Die · Der · Das; once, the first time: "Hold a letter for its accents." | its accents on the bar (5 keys, e.g. E: é) |  |
| Greek (`el`) | ΕΛ | Ο · Το · Οι; once, the first time: "Hold a letter for its accents." | its accents on the bar (7 keys, e.g. E: έ) |  |
| Esperanto (`eo`) | EO | La · En · Ĝi; once, the first time: "Hold a letter for its accents." | its accents on the bar (6 keys, e.g. C: ĉ) |  |
| Spanish (`es`) | ES | El · La · En; once, the first time: "Hold a letter for its accents." | its accents on the bar (6 keys, e.g. E: é) |  |
| Estonian (`et`) | ET | Kui · See · Ja; once, the first time: "Hold a letter for its accents." | its accents on the bar (5 keys, e.g. A: ä) |  |
| Basque (`eu`) | EU | Bere · Hala · Gaur; once, the first time: "Hold a letter for its accents." | its accents on the bar (2 keys, e.g. C: ç) |  |
| Finnish (`fi`) | FI | Hän · Se · Nyt; once, the first time: "Hold a letter for its accents." | its accents on the bar (4 keys, e.g. A: å ä) |  |
| French (`fr`) | FR | Le · La · Les; once, the first time: "Hold a letter for its accents." | its accents on the bar (7 keys, e.g. E: é è ê ë) |  |
| Irish (`ga`) | GA | Tá · Is · Bhí; once, the first time: "Hold a letter for its accents." | its accents on the bar (5 keys, e.g. E: é) |  |
| Galician (`gl`) | GL | A · O · Os; once, the first time: "Hold a letter for its accents." | its accents on the bar (6 keys, e.g. E: é) |  |
| Hebrew (`he`) | עב | הוא · לאחר · על | ף on P, ץ on M, / on Q; on the rest, its symbol | Holding mixes kinds: P, M and Q type letters (ף ץ /), the other keys symbols. Kept: the finals have no key of their own. |
| Hindi (`hi`) | हि | इस · इसके · उन्होंने | its symbol, after the syllable or word being built is written | Shift types Hindi's own sounds, so only a swipe up opens the bigger menu. |
| Croatian (`hr`) | HR | U · Na · Nakon; once, the first time: "Hold a letter for its accents." | its accents on the bar (4 keys, e.g. C: č ć) |  |
| Hungarian (`hu`) | HU | A · Az · Ez; once, the first time: "Hold a letter for its accents." | its accents on the bar (5 keys, e.g. E: é) |  |
| Indonesian (`id`) | ID | Namun · Selain · Ia | its symbol | No accents: holding gives the symbol, as in English. |
| Icelandic (`is`) | IS | Það · Í · Hann; once, the first time: "Hold a letter for its accents." | its accents on the bar (8 keys, e.g. E: é) |  |
| Italian (`it`) | IT | Il · La · In; once, the first time: "Hold a letter for its accents." | its accents on the bar (5 keys, e.g. E: é è) |  |
| Japanese (`ja`) | あ | 私 · 今日 · はい | its symbol, after the code typed so far | Starters hand-picked (Mozc's costs rank homophones): for the owner to confirm. |
| Georgian (`ka`) | ქა | ეს · როგორც · ამ | its symbol | No accents and no capitals: holding gives the symbol. |
| Kazakh (`kk`) | ҚА | Бұл · Ал · Ол; once, the first time: "Hold a letter for its accents." | its accents on the bar (12 keys, e.g. E: ү ұ) | The first-use hint says accents, but these holds are letters the layout has no key for: wording for F. |
| Korean (`ko`) | 한 | 이 · 그는 · 또 | its symbol, after the syllable or word being built is written | Shift types the double consonants, so only a swipe up opens the bigger menu. |
| Lithuanian (`lt`) | LT | Tačiau · Tai · O; once, the first time: "Hold a letter for its accents." | its accents on the bar (7 keys, e.g. E: ę ė) |  |
| Latvian (`lv`) | LV | Un · Ja · Bet; once, the first time: "Hold a letter for its accents." | its accents on the bar (11 keys, e.g. E: ē) |  |
| Macedonian (`mk`) | МК | Во · На · Тој; once, the first time: "Hold a letter for its accents." | its accents on the bar (2 keys, e.g. L: ч ќ) | The first-use hint says accents, but these holds are letters the layout has no key for: wording for F. |
| Norwegian Bokmål (`nb`) | NB | Det · I · Vi; once, the first time: "Hold a letter for its accents." | its accents on the bar (3 keys, e.g. E: é) |  |
| Dutch (`nl`) | NL | De · Het · In; once, the first time: "Hold a letter for its accents." | its accents on the bar (5 keys, e.g. E: é ë è) |  |
| Norwegian Nynorsk (`nn`) | NN | I · Han · Det; once, the first time: "Hold a letter for its accents." | its accents on the bar (3 keys, e.g. E: é è ê) |  |
| Polish (`pl`) | PL | W · Na · To; once, the first time: "Hold a letter for its accents." | its accents on the bar (8 keys, e.g. E: ę) |  |
| Portuguese (`pt`) | PT | O · A · Os; once, the first time: "Hold a letter for its accents." | its accents on the bar (6 keys, e.g. E: é ê) |  |
| Romanian (`ro`) | RO | În · De · Nu; once, the first time: "Hold a letter for its accents." | its accents on the bar (4 keys, e.g. A: ă â) |  |
| Russian (`ru`) | РУ | По · На · Это; once, the first time: "Hold a letter for its accents." | its accents on the bar (4 keys, e.g. L: ж э) | The first-use hint says accents, but these holds are letters the layout has no key for: wording for F. |
| Slovak (`sk`) | SK | Na · Podľa · Ak; once, the first time: "Hold a letter for its accents." | its accents on the bar (14 keys, e.g. E: é) | The next-word table's sentence starts ("Prečítajte", a news site's links) came before the starters: fixed, starters first. |
| Slovenian (`sl`) | SL | V · Na · Po; once, the first time: "Hold a letter for its accents." | its accents on the bar (3 keys, e.g. C: č) | "Leta" (news datelines, "Leta 2010") among the starters: fixed. |
| Serbian, Cyrillic (`sr`) | СР; СР with another Serbian input method on | У · На · Након; once, the first time: "Hold a letter for its accents." | its accents on the bar (2 keys, e.g. L: ч ћ) | The first-use hint says accents, but these holds are letters the layout has no key for: wording for F. |
| Serbian, Latin (`sr-latn`) | SR | U · Na · Nakon; once, the first time: "Hold a letter for its accents." | its accents on the bar (4 keys, e.g. C: č ć) | The mark showed the language's Cyrillic СР while typing Latin: fixed, SR. |
| Swedish (`sv`) | SV | Det · I · Men; once, the first time: "Hold a letter for its accents." | its accents on the bar (4 keys, e.g. E: é) |  |
| Thai (`th`) | ไท | สวัสดี · ขอบคุณ · ฉัน | the key's third Thai letter (21 keys); on the rest, its symbol | Holding mixes kinds: 21 keys type Thai's third-layer letters, 5 their symbols. Kept: Kedmanee needs 47 keys. Starters were particles and news datelines: fixed, hand-picked. |
| Turkish (`tr`) | TR | Bu · Ancak · Bir; once, the first time: "Hold a letter for its accents." | its accents on the bar (7 keys, e.g. A: â) |  |
| Ukrainian (`uk`) | УК | У · За · На; once, the first time: "Hold a letter for its accents." | its accents on the bar (4 keys, e.g. L: ж є) | The first-use hint says accents, but these holds are letters the layout has no key for: wording for F. |
| Vietnamese (`vi`) | VI | Trong · Theo · Tuy | its symbol, after the syllable or word being built is written | Shift types capitals, so only a swipe up opens the bigger menu. |
| Cantonese, Jyutping (`yue-jyutping`) | 中; 粵 with another Chinese input method on | 我 · 係 · 你 | its symbol, after the code typed so far | 嘅 among the starters: fixed. |
| Cantonese, Yale (`yue-yale`) | 中; 耶 with another Chinese input method on | 我 · 係 · 你 | its symbol, after the code typed so far | 嘅 (a particle) among the starters: fixed. |
| Chinese, Cangjie (`zh-cangjie`) | 中; 倉 with another Chinese input method on | 我 · 係 · 你 | its symbol, after the code typed so far | 嘅 among the starters: fixed. |
| Mandarin, Pinyin (`zh-pinyin`) | 中; 拼 with another Chinese input method on | 我 · 是 · 你 | its symbol, after the code typed so far | Starters began with the particles 的 了: fixed. |
| Chinese, Quick (`zh-quick`) | 中; 速 with another Chinese input method on | 我 · 係 · 你 | its symbol, after the code typed so far | 嘅 among the starters: fixed. |
| Mandarin, Zhuyin (Hsu layout) (`zh-zhuyin`) | 中; 注 with another Chinese input method on | 我 · 是 · 你 | its symbol, after the code typed so far | Starters began with the particles 的 了: fixed. |
