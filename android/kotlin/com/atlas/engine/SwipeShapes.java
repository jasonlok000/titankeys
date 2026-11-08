package com.atlas.engine;

import com.atlas.keyboard.Layout;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Compares a swipe with the outline of each common word. An outline keeps its proportions when a thumb stops
 * short of the keys at the edge of the touchpad, which is where the engine most often settles on another word.
 */
final class SwipeShapes {
    static final class Fit {
        final String word;
        final float score;
        /** How closely the path follows the word, leaving out how common the word is. */
        final float closeness;
        Fit(String word, float score, float closeness) { this.word = word; this.score = score; this.closeness = closeness; }
    }

    private static final int POINTS = 16;
    private static final int MIN_FREQUENCY = 100;
    /** How far, in keys, the ends of a swipe may be from the first and last letters of a word. */
    private static final float END_REACH = 3;
    private static final float PLACE_WEIGHT = .5f;
    private static final float FREQUENCY_WEIGHT = 1.5f / 255;
    /** How far a thumb tends to stop short of a corner, in keys, by the way it is travelling. */
    private static final float SHORT_RIGHT = .55f, SHORT_LEFT = .15f, SHORT_UP = .33f, SHORT_DOWN = .18f;
    /** Strokes between neighbouring keys are too short to fall short by much. */
    private static final float MIN_REACH = 1.2f;
    private static final float FULL_REACH = 4;

    private final Map<String, Float> commonFrequencies = new HashMap<>();
    private final Outlines common;
    private Map<String, Float> personalFrequencies = Map.of();
    private Outlines personal = new Outlines(personalFrequencies);

    SwipeShapes(File dictionary) throws IOException {
        try (BufferedReader lines = Files.newBufferedReader(dictionary.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = lines.readLine()) != null) read(line);
        }
        common = new Outlines(commonFrequencies);
    }

    private void read(String line) {
        if (!line.startsWith(" word=")) return;
        int comma = line.indexOf(',');
        int end = comma < 0 ? -1 : line.indexOf(',', comma + 3);
        if (end < 0 || !line.startsWith("f=", comma + 1)) return;
        int frequency = Integer.parseInt(line.substring(comma + 3, end));
        String flags = line.substring(end + 1);
        if (frequency < MIN_FREQUENCY || flags.contains("offensive") || flags.contains("nonword")) return;
        String word = letters(line.substring(6, comma));
        if (word.length() >= 2 && outlinable(word)) commonFrequencies.merge(word, (float) frequency, Math::max);
    }

    private static String letters(String word) { return word.toLowerCase(Locale.ROOT).replace("'", "").replace("’", ""); }

    private static boolean outlinable(String letters) {
        char previous = 0;
        boolean moves = false;
        for (int i = 0; i < letters.length(); i++) {
            char letter = letters.charAt(i);
            if (letter < 'a' || letter > 'z') return false;
            if (i > 0 && letter != previous) moves = true;
            previous = letter;
        }
        return moves;
    }

    void personalWords(String[] words, float[] frequencies) {
        Map<String, Float> usable = new LinkedHashMap<>();
        for (int i = 0; i < words.length; i++) {
            String word = letters(words[i]);
            if (outlinable(word)) usable.merge(word, frequencies[i], Math::max);
        }
        personalFrequencies = usable;
        personal = new Outlines(usable);
    }

    /** A rarer word counts as the rarest common one. The engine has already weighed how likely it is. */
    private float frequency(String letters) {
        float common = commonFrequencies.getOrDefault(letters, 0f);
        return Math.max(MIN_FREQUENCY, Math.max(common, personalFrequencies.getOrDefault(letters, 0f)));
    }

    /** The words closest to this swipe, best fit first, leaving out any whose ends are too far from the swipe's. */
    Fit[] closest(float[][] path, int count) {
        if (path == null) return new Fit[0];
        Fit[] best = new Fit[count];
        int found = common.closest(path, best, 0);
        found = personal.closest(path, best, found);
        return java.util.Arrays.copyOf(best, found);
    }

    /** How well one word fits this swipe, with negative infinity for a word that has no outline. */
    Fit fit(float[][] path, String word) {
        String letters = letters(word);
        if (path == null || !outlinable(letters))
            return new Fit(word, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
        float[] place = new float[POINTS * 2];
        float[] shape = new float[POINTS * 2];
        outline(letters, place, shape, 0);
        float closeness = closeness(path, place, shape, 0);
        return new Fit(word, frequency(letters) * FREQUENCY_WEIGHT + closeness, closeness);
    }

    /** The swipe's place and proportions for comparing with words, or null when it is too short to have a shape. */
    static float[][] path(Swipe gesture) {
        int count = gesture.x.length;
        if (count < 2) return null;
        float[] x = new float[count], y = new float[count];
        for (int i = 0; i < count; i++) { x[i] = gesture.x[i] * 10; y[i] = gesture.y[i] * 3; }
        float[] place = new float[POINTS * 2];
        float[] shape = new float[POINTS * 2];
        spaced(x, y, count, place, 0);
        proportions(place, shape, 0);
        return new float[][] { place, shape };
    }

    private static void outline(String letters, float[] places, float[] shapes, int offset) {
        float[] x = new float[letters.length()], y = new float[letters.length()];
        int count = 0;
        for (int i = 0; i < letters.length(); i++) {
            if (i > 0 && letters.charAt(i) == letters.charAt(i - 1)) continue;
            int key = letters.charAt(i) - 'a';
            x[count] = Layout.KEY_X[key] * 10;
            y[count++] = Layout.KEY_Y[key] * 3;
        }
        pullCorners(x, y, count);
        spaced(x, y, count, places, offset);
        proportions(places, shapes, offset);
    }

    /**
     * Predicts the path a real swipe of a word takes. A thumb stops short of a letter it turns at, along the line
     * it was travelling and by more on a long reach, so "jason" often turns near I on its way to O.
     */
    private static void pullCorners(float[] x, float[] y, int count) {
        float[] fromX = x.clone(), fromY = y.clone();
        for (int i = 1; i < count - 1; i++) {
            float dx = fromX[i] - fromX[i - 1], dy = fromY[i] - fromY[i - 1];
            float reach = distance(dx, dy);
            if (reach < MIN_REACH) continue;
            dx /= reach;
            dy /= reach;
            float nextX = fromX[i + 1] - fromX[i], nextY = fromY[i + 1] - fromY[i];
            if ((dx * nextX + dy * nextY) / distance(nextX, nextY) >= .5f) continue;
            float pull = shortfall(dx, dy) * Math.min(1.25f, Math.max(.5f, reach / FULL_REACH));
            x[i] -= dx * pull;
            y[i] -= dy * pull;
        }
    }

    private static float shortfall(float dx, float dy) {
        return dx * dx * (dx > 0 ? SHORT_RIGHT : SHORT_LEFT) + dy * dy * (dy < 0 ? SHORT_UP : SHORT_DOWN);
    }

    /** Places {@link #POINTS} points at equal distances along a line, at this offset of the output. */
    private static void spaced(float[] x, float[] y, int count, float[] out, int offset) {
        float[] along = new float[count];
        for (int i = 1; i < count; i++) along[i] = along[i - 1] + distance(x[i] - x[i - 1], y[i] - y[i - 1]);
        int segment = 0;
        for (int point = 0; point < POINTS; point++) {
            float target = along[count - 1] * point / (POINTS - 1);
            while (segment < count - 2 && along[segment + 1] < target) segment++;
            float span = along[segment + 1] - along[segment];
            float t = span > 0 ? Math.min(1, (target - along[segment]) / span) : 0;
            out[offset + point * 2] = x[segment] + (x[segment + 1] - x[segment]) * t;
            out[offset + point * 2 + 1] = y[segment] + (y[segment + 1] - y[segment]) * t;
        }
    }

    /** Centres a line and scales it to a unit square, leaving only its proportions. */
    private static void proportions(float[] place, float[] shape, int offset) {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < POINTS * 2; i += 2) {
            minX = Math.min(minX, place[offset + i]); maxX = Math.max(maxX, place[offset + i]);
            minY = Math.min(minY, place[offset + i + 1]); maxY = Math.max(maxY, place[offset + i + 1]);
        }
        float scale = Math.max(Math.max(maxX - minX, maxY - minY), 1e-6f);
        for (int i = 0; i < POINTS * 2; i += 2) {
            shape[offset + i] = (place[offset + i] - (minX + maxX) / 2) / scale;
            shape[offset + i + 1] = (place[offset + i + 1] - (minY + maxY) / 2) / scale;
        }
    }

    private static float distance(float dx, float dy) { return (float) Math.sqrt(dx * dx + dy * dy); }

    private static float closeness(float[][] path, float[] places, float[] shapes, int offset) {
        float placeGap = 0, shapeGap = 0;
        for (int i = 0; i < POINTS * 2; i += 2) {
            placeGap += distance(places[offset + i] - path[0][i], places[offset + i + 1] - path[0][i + 1]);
            shapeGap += distance(shapes[offset + i] - path[1][i], shapes[offset + i + 1] - path[1][i + 1]);
        }
        return -(shapeGap + PLACE_WEIGHT * placeGap) / POINTS;
    }

    private static final class Outlines {
        private final String[] words;
        private final float[] frequencies, places, shapes;

        Outlines(Map<String, Float> entries) {
            words = entries.keySet().toArray(new String[0]);
            frequencies = new float[words.length];
            places = new float[words.length * POINTS * 2];
            shapes = new float[places.length];
            for (int i = 0; i < words.length; i++) {
                frequencies[i] = entries.get(words[i]);
                outline(words[i], places, shapes, i * POINTS * 2);
            }
        }

        /** Merges this set's closest words into a list sorted best first, returning the new length. */
        int closest(float[][] path, Fit[] best, int found) {
            float[] place = path[0];
            float reach = END_REACH * END_REACH;
            int last = (POINTS - 1) * 2;
            for (int i = 0; i < words.length; i++) {
                int offset = i * POINTS * 2;
                float startX = places[offset] - place[0], startY = places[offset + 1] - place[1];
                float endX = places[offset + last] - place[last], endY = places[offset + last + 1] - place[last + 1];
                if (startX * startX + startY * startY > reach || endX * endX + endY * endY > reach) continue;
                float close = closeness(path, places, shapes, offset);
                float value = frequencies[i] * FREQUENCY_WEIGHT + close;
                if (found == best.length && value <= best[found - 1].score) continue;
                int at = Math.min(found, best.length - 1);
                while (at > 0 && best[at - 1].score < value) { best[at] = best[at - 1]; at--; }
                best[at] = new Fit(words[i], value, close);
                if (found < best.length) found++;
            }
            return found;
        }
    }
}
