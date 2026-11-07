package com.atlas.engine;

import java.util.Arrays;

/** A completed swipe in letter space: x spans the ten columns, y spans the three letter rows. */
public final class Swipe {
    public static final int MAX_POINTS = 4096;
    public static final long MAX_DURATION_MILLIS = 10_000L;
    public static final float MAX_ABS_COORDINATE = 4f;
    public final float[] x, y, time;

    public Swipe(float[] x, float[] y, float[] time) {
        this.x = x; this.y = y; this.time = time;
    }

    /** The final JNI boundary also checks these limits, including joined continuations. */
    public void validate() { validate(x, y, time); }

    public static void validate(float[] x, float[] y, float[] time) {
        if (x == null || y == null || time == null || x.length == 0 || x.length > MAX_POINTS
                || x.length != y.length || x.length != time.length)
            throw new IllegalArgumentException("Invalid swipe point count");
        float previous = time[0];
        for (int i = 0; i < x.length; i++) {
            if (!Float.isFinite(x[i]) || !Float.isFinite(y[i]) || Math.abs(x[i]) > MAX_ABS_COORDINATE
                    || Math.abs(y[i]) > MAX_ABS_COORDINATE || !Float.isFinite(time[i])
                    || time[i] < 0 || time[i] < previous)
                throw new IllegalArgumentException("Invalid swipe coordinates or timestamps");
            previous = time[i];
        }
        if ((double) time[time.length - 1] - time[0] > MAX_DURATION_MILLIS)
            throw new IllegalArgumentException("Swipe is too long");
    }

    /** Joins a resumed swipe after the previous one, keeping time increasing across the lift. */
    public Swipe append(Swipe next) {
        validate();
        if (next == null) throw new IllegalArgumentException("Missing swipe continuation");
        next.validate();
        int count = x.length;
        double duration = (double) time[count - 1] - time[0] + 1
                + (double) next.time[next.time.length - 1] - next.time[0];
        if (count + next.x.length > MAX_POINTS || duration > MAX_DURATION_MILLIS)
            throw new IllegalArgumentException("Swipe continuation exceeds limits");
        float[] joinedX = Arrays.copyOf(x, count + next.x.length);
        float[] joinedY = Arrays.copyOf(y, count + next.y.length);
        float[] joinedTime = Arrays.copyOf(time, count + next.time.length);
        double offset = (double) time[count - 1] + 1 - next.time[0];
        for (int i = 0; i < next.x.length; i++) {
            joinedX[count + i] = next.x[i];
            joinedY[count + i] = next.y[i];
            joinedTime[count + i] = (float) (next.time[i] + offset);
        }
        Swipe joined = new Swipe(joinedX, joinedY, joinedTime);
        joined.validate();
        return joined;
    }
}
