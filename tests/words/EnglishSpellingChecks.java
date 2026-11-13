package com.atlas.engine;

import java.util.Arrays;

import static com.atlas.engine.EnglishSpelling.Variant.AMERICAN;
import static com.atlas.engine.EnglishSpelling.Variant.BRITISH;
import static com.atlas.engine.EnglishSpelling.Variant.CANADIAN;

/** Settings > English spelling: what each choice writes. */
public final class EnglishSpellingChecks {
    public static void main(String[] args) {
        // British, as before Canadian.
        check(BRITISH, "color", "colour");
        check(BRITISH, "center", "centre");
        check(BRITISH, "traveled", "travelled");
        check(BRITISH, "realize", "realise");
        check(BRITISH, "organization", "organisation");
        check(BRITISH, "analyze", "analyse");
        check(BRITISH, "aluminum", "aluminium");
        check(BRITISH, "gray", "grey");
        check(BRITISH, "aging", "ageing");
        // American words that are British words too stay.
        check(BRITISH, "curb", "curb");
        check(BRITISH, "tire", "tire");
        check(BRITISH, "program", "program");
        check(BRITISH, "meter", "meter");
        check(BRITISH, "tyre", "tyre");
        check(BRITISH, "programme", "programme");

        // American, as before Canadian.
        check(AMERICAN, "colour", "color");
        check(AMERICAN, "centre", "center");
        check(AMERICAN, "travelled", "traveled");
        check(AMERICAN, "realise", "realize");
        check(AMERICAN, "analysed", "analyzed");
        check(AMERICAN, "jewellery", "jewelry");
        check(AMERICAN, "kerb", "curb");
        check(AMERICAN, "metre", "meter");
        check(AMERICAN, "tyre", "tyre");
        check(AMERICAN, "programme", "programme");

        // Canadian: British -our, -re, -ce and doubled l.
        check(CANADIAN, "color", "colour");
        check(CANADIAN, "colour", "colour");
        check(CANADIAN, "favorite", "favourite");
        check(CANADIAN, "neighbors", "neighbours");
        check(CANADIAN, "center", "centre");
        check(CANADIAN, "centre", "centre");
        check(CANADIAN, "theater", "theatre");
        check(CANADIAN, "liters", "litres");
        check(CANADIAN, "traveled", "travelled");
        check(CANADIAN, "travelled", "travelled");
        check(CANADIAN, "canceled", "cancelled");
        check(CANADIAN, "labeling", "labelling");
        check(CANADIAN, "jewelry", "jewellery");
        check(CANADIAN, "defense", "defence");
        check(CANADIAN, "offense", "offence");
        check(CANADIAN, "gray", "grey");
        check(CANADIAN, "catalog", "catalogue");
        check(CANADIAN, "maneuver", "manoeuvre");
        check(CANADIAN, "mustache", "moustache");
        check(CANADIAN, "mold", "mould");
        check(CANADIAN, "pajamas", "pyjamas");
        // Canadian: American -ize and -yze.
        check(CANADIAN, "realise", "realize");
        check(CANADIAN, "realize", "realize");
        check(CANADIAN, "organised", "organized");
        check(CANADIAN, "organisation", "organization");
        check(CANADIAN, "apologising", "apologizing");
        check(CANADIAN, "analyse", "analyze");
        check(CANADIAN, "analyze", "analyze");
        check(CANADIAN, "paralysed", "paralyzed");
        // Canadian: the American words that Canadians write.
        check(CANADIAN, "tyre", "tire");
        check(CANADIAN, "tyres", "tires");
        check(CANADIAN, "tire", "tire");
        check(CANADIAN, "kerb", "curb");
        check(CANADIAN, "curb", "curb");
        check(CANADIAN, "programme", "program");
        check(CANADIAN, "program", "program");
        check(CANADIAN, "aluminium", "aluminum");
        check(CANADIAN, "aluminum", "aluminum");
        check(CANADIAN, "ageing", "aging");
        check(CANADIAN, "cosy", "cozy");
        check(CANADIAN, "aeroplane", "airplane");
        check(CANADIAN, "sceptical", "skeptical");
        check(CANADIAN, "plough", "plow");
        check(CANADIAN, "paediatric", "pediatric");
        check(CANADIAN, "artefact", "artifact");
        check(CANADIAN, "mom", "mom");
        check(CANADIAN, "metre", "metre");
        check(CANADIAN, "meter", "meter");
        // Capitals stay where they were.
        check(CANADIAN, "Color", "Colour");
        check(CANADIAN, "CENTER", "CENTRE");
        check(CANADIAN, "Realise", "Realize");
        check(CANADIAN, "TYRE", "TIRE");

        String[] found = EnglishSpelling.candidates(new String[] {"color", "colour", "realise", "realize"}, CANADIAN);
        if (!Arrays.equals(found, new String[] {"colour", "realize"})) {
            throw new AssertionError("Canadian suggestions gave " + Arrays.toString(found));
        }

        // The saved setting.
        for (EnglishSpelling.Variant variant : EnglishSpelling.Variant.values()) {
            same(EnglishSpelling.Variant.of(variant.value()), variant, "The saved " + variant.value() + " reads back");
        }
        same(BRITISH.value(), "british", "British saves as before");
        same(AMERICAN.value(), "american", "American saves as before");
        same(CANADIAN.value(), "canadian", "Canadian saves as canadian");
        same(EnglishSpelling.Variant.of("something else"), BRITISH, "An unknown value is British, as before");
        same(EnglishSpelling.Variant.of(null), BRITISH, "No value is British, as before");
        System.out.println("English spelling checks passed");
    }

    private static void check(EnglishSpelling.Variant variant, String word, String expected) {
        same(EnglishSpelling.convert(word, variant), expected, variant + " " + word);
    }

    private static void same(Object actual, Object expected, String what) {
        if (!actual.equals(expected)) throw new AssertionError(what + ": got " + actual + ", expected " + expected);
    }
}
