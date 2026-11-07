package com.atlas.engine;

import java.util.Arrays;

/** Continuous position mapping with fixed sensor boundaries. */
public final class TouchAlignment {
    public static final TouchAlignment IDENTITY = new TouchAlignment();
    private static final int[] ROW_SIZE = {10, 10, 10, 7};
    private final float[][] sourceX;
    private final float[][] targetX;
    private final float[] sourceY;
    private final float[] targetY;

    private TouchAlignment() {
        sourceX = targetX = null;
        sourceY = targetY = null;
    }

    private TouchAlignment(float[][] sourceX, float[][] targetX, float[] sourceY, float[] targetY) {
        this.sourceX = sourceX;
        this.targetX = targetX;
        this.sourceY = sourceY;
        this.targetY = targetY;
    }

    public static TouchAlignment fit(float[][][] samples, float[][] centres) {
        float[][] sx = new float[4][], tx = new float[4][];
        float[] sy = new float[4], ty = new float[4];
        int key = 0;
        for (int row = 0; row < 4; row++) {
            float[] xs = new float[ROW_SIZE[row]], ys = new float[ROW_SIZE[row]];
            float[] targets = new float[xs.length];
            for (int col = 0; col < xs.length; col++, key++) {
                float[] x = new float[3], y = new float[3];
                for (int trial = 0; trial < 3; trial++) {
                    x[trial] = samples[key][trial][0];
                    y[trial] = samples[key][trial][1];
                    if (!inside(x[trial]) || !inside(y[trial])) throw new IllegalArgumentException("Touch outside the reported sensor range. Start again.");
                }
                Arrays.sort(x); Arrays.sort(y);
                if (x[2] - x[0] > .07f || y[2] - y[0] > .14f)
                    throw new IllegalArgumentException("Touches varied too much. Start again using light, steady contact.");
                xs[col] = x[1]; ys[col] = y[1]; targets[col] = centres[key][0];
                ty[row] = centres[key][1];
                if (col > 0 && xs[col] - xs[col - 1] < .008f)
                    throw new IllegalArgumentException("Neighbouring keys overlap in the readings. Start again; a position curve cannot separate identical readings.");
            }
            Arrays.sort(ys);
            sy[row] = ys[ys.length / 2];
            float[][] anchors = anchors(xs, targets);
            sx[row] = anchors[0]; tx[row] = anchors[1];
        }
        float[][] vertical = anchors(sy, ty);
        return new TouchAlignment(sx, tx, vertical[0], vertical[1]);
    }

    private static float[][] anchors(float[] source, float[] target) {
        float[] s = new float[source.length + 2], t = new float[s.length];
        int count = 1;
        for (int i = 0; i < source.length; i++) {
            if (i > 0 && source[i] - source[i - 1] < .008f)
                throw new IllegalArgumentException("Key positions overlap. Start again with distinct light touches.");
            // A saturated sensor endpoint still belongs to its outer key.
            if (source[i] <= .002f || source[i] >= .998f) continue;
            s[count] = source[i]; t[count++] = target[i];
        }
        s[count] = t[count] = 1;
        s = Arrays.copyOf(s, count + 1); t = Arrays.copyOf(t, count + 1);
        validate(s, t);
        return new float[][]{s, t};
    }

    private static void validate(float[] source, float[] target) {
        if (source.length != target.length || source.length < 2 || source.length > 12
                || source[0] != 0 || target[0] != 0
                || source[source.length - 1] != 1 || target[target.length - 1] != 1)
            throw new IllegalArgumentException("Invalid alignment boundaries");
        for (int i = 1; i < source.length; i++) {
            float dx = source[i] - source[i - 1], dy = target[i] - target[i - 1];
            if (!inside(source[i]) || !inside(target[i]) || dx <= 0 || dy <= 0 || dy / dx < .12f || dy / dx > 12)
                throw new IllegalArgumentException("Alignment would squeeze a key too much. Start again using the centres of the keys.");
        }
    }

    public float x(float x, float y) {
        if (sourceX == null || !inside(x) || !inside(y)) return x;
        float row = Math.max(0, Math.min(3, y(y) * 4 - .5f));
        int low = (int) row, high = Math.min(3, low + 1);
        float a = interpolate(x, sourceX[low], targetX[low]);
        return a + (interpolate(x, sourceX[high], targetX[high]) - a) * (row - low);
    }

    public float y(float y) {
        return sourceY == null || !inside(y) ? y : interpolate(y, sourceY, targetY);
    }

    private static float interpolate(float value, float[] source, float[] target) {
        for (int i = 1; i < source.length; i++) {
            if (value <= source[i]) return target[i - 1] + (target[i] - target[i - 1])
                    * (value - source[i - 1]) / (source[i] - source[i - 1]);
        }
        return 1;
    }

    private static boolean inside(float value) { return Float.isFinite(value) && value >= 0 && value <= 1; }

    public String encode() {
        if (sourceX == null) return "";
        StringBuilder text = new StringBuilder("1");
        for (int row = 0; row < 4; row++) { append(text, sourceX[row]); append(text, targetX[row]); }
        append(text, sourceY); append(text, targetY);
        return text.toString();
    }

    private static void append(StringBuilder text, float[] values) {
        text.append(';');
        for (float value : values) text.append(value).append(',');
    }

    static TouchAlignment decode(String text) {
        if (text.isEmpty()) return IDENTITY;
        String[] parts = text.split(";");
        if (parts.length != 11 || !parts[0].equals("1")) throw new IllegalArgumentException("Invalid alignment version");
        float[][] sx = new float[4][], tx = new float[4][];
        for (int row = 0; row < 4; row++) {
            sx[row] = parse(parts[1 + row * 2]); tx[row] = parse(parts[2 + row * 2]);
            validate(sx[row], tx[row]);
        }
        float[] sy = parse(parts[9]), ty = parse(parts[10]); validate(sy, ty);
        return new TouchAlignment(sx, tx, sy, ty);
    }

    private static float[] parse(String part) {
        String[] values = part.split(",");
        if (values.length > 12) throw new IllegalArgumentException("Invalid alignment size");
        float[] result = new float[values.length];
        for (int i = 0; i < result.length; i++) result[i] = Float.parseFloat(values[i]);
        return result;
    }
}
