package com.atlas.engine;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;

public final class EnglishSpelling {
    public enum Variant {
        BRITISH, AMERICAN, CANADIAN;

        /** The setting's saved value, such as "british". */
        public String value() { return name().toLowerCase(Locale.ROOT); }

        /** The variant of a saved value. Anything else is British, as it was before Canadian. */
        public static Variant of(String value) {
            if ("american".equals(value)) return AMERICAN;
            if ("canadian".equals(value)) return CANADIAN;
            return BRITISH;
        }
    }
    private static final Map<String, String> BRITISH = new HashMap<>();
    private static final Map<String, String> AMERICAN = new HashMap<>();
    /**
     * Canadian English: British -our, -re, -ce and doubled l (colour, centre, defence, travelled), with American -ize and
     * -yze (realize, analyze) and the American words that Canadians write (tire, curb, program, aluminum).
     */
    private static final Map<String, String> CANADIAN = new HashMap<>();

    static {
        family("colour", "color", "", "s", "ed", "ing", "ful", "fully", "less", "lessly", "fulness");
        family("favour", "favor", "", "s", "ed", "ing", "able", "ably", "ite", "ites", "itism");
        family("flavour", "flavor", "", "s", "ed", "ing", "ful", "less");
        family("honour", "honor", "", "s", "ed", "ing", "able", "ably");
        family("labour", "labor", "", "s", "ed", "ing", "er", "ers");
        family("neighbour", "neighbor", "", "s", "ing", "ly", "hood", "hoods");
        family("behaviour", "behavior", "", "s", "al", "ally");
        family("humour", "humor", "", "s", "ed", "ing", "less");
        family("rumour", "rumor", "", "s", "ed", "ing");
        family("odour", "odor", "", "s", "less");
        family("armour", "armor", "", "s", "ed", "ing");
        family("vigour", "vigor", "");
        family("savour", "savor", "", "s", "ed", "ing");
        family("splendour", "splendor", "", "s");
        for (String stem : ("organ recogn real apolog author categor character critic emphas final harmon "
                + "hospital immun legal local maxim minim modern normal optim personal priorit privat public "
                + "special standard summar symbol synchron util visual mobil stabil familiar custom social "
                + "capital central computer digit neutral memor theor").split(" ")) {
            americanFamily(stem + "is", stem + "iz", "e", "es", "ed", "ing");
        }
        for (String stem : ("organ author categor character harmon hospital immun legal local maxim minim "
                + "modern normal optim personal privat public special standard symbol synchron util visual "
                + "mobil stabil custom social capital central computer digit neutral").split(" ")) {
            americanFamily(stem + "isation", stem + "ization", "", "s");
        }
        for (String stem : "travel cancel label model dial fuel signal quarrel".split(" ")) {
            family(stem + "l", stem, "ed", "ing");
        }
        pairs("centre/center centres/centers centred/centered centring/centering "
                + "theatre/theater theatres/theaters fibre/fiber fibres/fibers "
                + "litre/liter litres/liters "
                + "kilometre/kilometer kilometres/kilometers centimetre/centimeter centimetres/centimeters "
                + "millimetre/millimeter millimetres/millimeters "
                + "defence/defense defences/defenses offence/offense offences/offenses "
                + "grey/gray greys/grays jewellery/jewelry "
                + "mould/mold moulds/molds moulded/molded moulding/molding mouldy/moldy "
                + "catalogue/catalog catalogues/catalogs catalogued/cataloged cataloguing/cataloging "
                + "dialogue/dialog dialogues/dialogs monologue/monolog monologues/monologs "
                + "pyjamas/pajamas "
                + "manoeuvre/maneuver manoeuvres/maneuvers manoeuvred/maneuvered manoeuvring/maneuvering "
                + "moustache/mustache moustaches/mustaches "
                + "pretence/pretense pretences/pretenses");
        // British and American differ here, and Canadians write the American word.
        americanPairs("analyse/analyze analysed/analyzed analysing/analyzing "
                + "paralyse/paralyze paralysed/paralyzed paralysing/paralyzing "
                + "ageing/aging cosy/cozy cosier/cozier cosiest/coziest "
                + "aeroplane/airplane aeroplanes/airplanes "
                + "aluminium/aluminum artefact/artifact artefacts/artifacts "
                + "sceptic/skeptic sceptics/skeptics sceptical/skeptical scepticism/skepticism "
                + "plough/plow ploughs/plows ploughed/plowed ploughing/plowing "
                + "leukaemia/leukemia anaemia/anemia anaemic/anemic oestrogen/estrogen "
                + "paediatric/pediatric paediatrics/pediatrics encyclopaedia/encyclopedia "
                + "encyclopaedias/encyclopedias diarrhoea/diarrhea");
        // These American words also have meanings that keep this spelling in British English.
        AMERICAN.put("metre", "meter");
        AMERICAN.put("metres", "meters");
        AMERICAN.put("kerb", "curb");
        AMERICAN.put("kerbs", "curbs");
        // Canadians write these the American way, but the American words are also British words, so British leaves them.
        // American leaves the British words too, as it always has.
        for (String item : "kerb/curb kerbs/curbs tyre/tire tyres/tires programme/program programmes/programs".split(" ")) {
            String[] words = item.split("/");
            CANADIAN.put(words[0], words[1]);
        }
    }

    private static void family(String british, String american, String... endings) {
        for (String ending : endings) pair(british + ending, american + ending, false);
    }

    /** A family that Canadians write the American way. */
    private static void americanFamily(String british, String american, String... endings) {
        for (String ending : endings) pair(british + ending, american + ending, true);
    }

    private static void pairs(String pairs) { pairs(pairs, false); }

    /** Pairs that Canadians write the American way. */
    private static void americanPairs(String pairs) { pairs(pairs, true); }

    private static void pairs(String pairs, boolean americanInCanada) {
        for (String item : pairs.split(" ")) {
            String[] words = item.split("/");
            pair(words[0], words[1], americanInCanada);
        }
    }

    private static void pair(String british, String american, boolean americanInCanada) {
        BRITISH.put(american, british);
        AMERICAN.put(british, american);
        if (americanInCanada) CANADIAN.put(british, american);
        else CANADIAN.put(american, british);
    }

    static String convert(String word, Variant variant) {
        String lower = word.toLowerCase(Locale.ROOT);
        String replacement = (variant == Variant.BRITISH ? BRITISH : variant == Variant.CANADIAN ? CANADIAN : AMERICAN).get(lower);
        if (replacement == null) return word;
        if (word.equals(lower)) return replacement;
        if (word.equals(word.toUpperCase(Locale.ROOT))) return replacement.toUpperCase(Locale.ROOT);
        if (word.equals(Character.toUpperCase(lower.charAt(0)) + lower.substring(1))) {
            return Character.toUpperCase(replacement.charAt(0)) + replacement.substring(1);
        }
        return word;
    }

    static String[] candidates(String[] words, Variant variant) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String word : words) result.add(convert(SwipeSpelling.restore(word), variant));
        return result.toArray(new String[0]);
    }

    private EnglishSpelling() {}
}
