package com.atlas.engine;

/**
 * Continuous position mapping through the measured key positions. Between keys the speed ramps smoothly
 * instead of changing at each key boundary, so a steady swipe stays steady.
 */
public final class TouchAlignment {
    public static final TouchAlignment IDENTITY = new TouchAlignment();
    private final Curve[] rows;
    private final Curve vertical;

    private TouchAlignment() {
        rows = null;
        vertical = null;
    }

    private TouchAlignment(float[][] sourceX, float[][] targetX, float[] sourceY, float[] targetY) {
        rows = new Curve[4];
        for (int row = 0; row < 4; row++) rows[row] = new Curve(sourceX[row], targetX[row]);
        vertical = new Curve(sourceY, targetY);
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
        if (rows == null || !Float.isFinite(x) || !Float.isFinite(y)) return x;
        // A reading just past the sensor range keeps to the edge rather than skipping the mapping.
        x = clamp(x);
        float row = Math.max(0, Math.min(3, y(y) * 4 - .5f));
        int low = Math.min(2, (int) row);
        float t = row - low;
        // Eased so height wobble while swiping along a row barely moves the path sideways.
        t = t * t * (3 - 2 * t);
        float a = rows[low].map(x);
        return a + (rows[low + 1].map(x) - a) * t;
    }

    public float y(float y) {
        return vertical == null || !Float.isFinite(y) ? y : vertical.map(clamp(y));
    }

    private static float clamp(float value) { return Math.max(0, Math.min(1, value)); }

    /** A monotone cubic through the key positions, with the speed changing gradually between keys. */
    private static final class Curve {
        private final float[] source, target, slope;

        Curve(float[] anchors, float[] targets) {
            source = anchors;
            target = targets.clone();
            int last = target.length - 1;
            if (last >= 3) {
                target[0] = edge(target[1], target[2], source[1], source[2], source[1] - source[0]);
                target[last] = edge(target[last - 1], target[last - 2], source[last - 1], source[last - 2],
                        source[last] - source[last - 1]);
            }
            float[] secant = new float[last];
            for (int i = 0; i < last; i++) secant[i] = (target[i + 1] - target[i]) / (source[i + 1] - source[i]);
            slope = new float[last + 1];
            slope[0] = secant[0];
            slope[last] = secant[last - 1];
            for (int i = 1; i < last; i++) {
                // Fritsch–Carlson weights keep the curve from overshooting a key.
                float before = source[i] - source[i - 1], after = source[i + 1] - source[i];
                float w1 = 2 * after + before, w2 = after + 2 * before;
                slope[i] = (w1 + w2) / (w1 / secant[i - 1] + w2 / secant[i]);
            }
        }

        /**
         * Where the sensor edge maps. The profile pins it to the keyboard edge, which stretches a thin strip
         * of sensor across half a key. A key that saturated the sensor is missing from the anchors, so
         * the edge becomes that key's centre; otherwise the curve continues at its outer key's speed.
         */
        private static float edge(float outer, float inner, float outerSource, float innerSource, float distance) {
            float spacing = Math.abs(outer - inner);
            float side = Math.signum(outer - inner);
            float room = side > 0 ? 1 - outer : outer;
            if (room > spacing * .75f) return outer + side * spacing;
            float speed = Math.abs((outer - inner) / (outerSource - innerSource));
            return clamp(outer + side * speed * distance);
        }

        float map(float value) {
            int i = 1;
            while (i < source.length - 1 && value > source[i]) i++;
            float h = source[i] - source[i - 1], t = (value - source[i - 1]) / h;
            float t2 = t * t, t3 = t2 * t;
            return clamp((2 * t3 - 3 * t2 + 1) * target[i - 1] + (t3 - 2 * t2 + t) * h * slope[i - 1]
                    + (-2 * t3 + 3 * t2) * target[i] + (t3 - t2) * h * slope[i]);
        }
    }

    private static boolean inside(float value) { return Float.isFinite(value) && value >= 0 && value <= 1; }

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
