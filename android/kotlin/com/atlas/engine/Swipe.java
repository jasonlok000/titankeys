package com.atlas.engine;

import java.util.Arrays;

/** A completed swipe in letter space: x spans the ten columns, y spans the three letter rows. */
public final class Swipe {
    public static final int MAX_POINTS = 4096;
    public static final long MAX_DURATION_MILLIS = 10_000L;
    public static final float MAX_ABS_COORDINATE = 4f;
    /** Keys a touch may land from where the last one ended and still be a brief flicker. */
    private static final float FLICKER_KEYS = 1.5f;
    private static final long MAX_BREAK_MILLIS = 400;
    private static final float TOP_ROW = 1f / 3;
    /** The sensor ends about halfway up the top row, so a finger coming back appears at this height. */
    private static final float TOP_EDGE = .267f;
    private static final float MAX_BREAK_KEYS = 3.5f;
    private static final float MOTION_MILLIS = 40;
    private static final int BREAK_CLUES = 2;
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

    /**
     * Whether another touch is this swipe carrying on, rather than a new word. Time alone cannot tell, as quick
     * words follow each other just as closely. A finger that runs off the top of the sensor comes back at that
     * edge, nearby, heading somewhere that fits the way it left. It is often still moving, while a new word lands still.
     */
    public boolean sameStroke(Swipe next, long gapMillis, long flickerMillis) {
        if (next.x.length == 0 || !couldContinue(next.x[0], next.y[0], gapMillis, flickerMillis)) return false;
        if (flicker(next.x[0], next.y[0], gapMillis, flickerMillis)) return true;
        int last = x.length - 1;
        float jumpX = (next.x[0] - x[last]) * 10, jumpY = (next.y[0] - y[last]) * 3;
        float[] leaving = motion(false), entering = next.motion(true);
        boolean carriesOn = cosine(leaving[0], leaving[1], jumpX, jumpY) >= .7f;
        boolean reverses = cosine(leaving[0], leaving[1], entering[0], entering[1]) <= -.3f;
        boolean moving = Math.sqrt(entering[0] * entering[0] + entering[1] * entering[1]) >= .002f;
        boolean quickSample = next.x.length > 1 && next.time[1] - next.time[0] <= 20;
        int clues = (carriesOn ? 1 : 0) + (reverses ? 1 : 0) + (moving ? 1 : 0) + (quickSample ? 1 : 0);
        return (carriesOn || reverses) && clues >= BREAK_CLUES;
    }

    /** Whether a touch landing here, this soon, could be this swipe carrying on. How it then moves decides. */
    public boolean couldContinue(float nextX, float nextY, long gapMillis, long flickerMillis) {
        if (gapMillis < 0 || x.length == 0) return false;
        if (flicker(nextX, nextY, gapMillis, flickerMillis)) return true;
        int last = x.length - 1;
        return gapMillis <= MAX_BREAK_MILLIS && y[last] < TOP_ROW && nextY < TOP_EDGE && keysTo(nextX, nextY) <= MAX_BREAK_KEYS;
    }

    private boolean flicker(float nextX, float nextY, long gapMillis, long flickerMillis) {
        return gapMillis <= flickerMillis && keysTo(nextX, nextY) <= FLICKER_KEYS;
    }

    private float keysTo(float nextX, float nextY) {
        int last = x.length - 1;
        float jumpX = (nextX - x[last]) * 10, jumpY = (nextY - y[last]) * 3;
        return (float) Math.sqrt(jumpX * jumpX + jumpY * jumpY);
    }

    /** The speed in keys per millisecond over the first or last part of this swipe. */
    private float[] motion(boolean opening) {
        int from = opening ? 0 : x.length - 1;
        int to = from;
        int step = opening ? 1 : -1;
        while (to + step >= 0 && to + step < x.length && Math.abs(time[to] - time[from]) < MOTION_MILLIS) to += step;
        int start = Math.min(from, to), end = Math.max(from, to);
        float elapsed = Math.max(time[end] - time[start], 1);
        return new float[] { (x[end] - x[start]) * 10 / elapsed, (y[end] - y[start]) * 3 / elapsed };
    }

    private static float cosine(float ax, float ay, float bx, float by) {
        float size = (float) Math.sqrt((ax * ax + ay * ay) * (bx * bx + by * by));
        return size > 1e-6f ? (ax * bx + ay * by) / size : 0;
    }

    /** Joins a resumed swipe after the previous one. Keeping the real gap stops the engine reading it as a jump. */
    public Swipe append(Swipe next, long gapMillis) {
        validate();
        if (next == null) throw new IllegalArgumentException("Missing swipe continuation");
        next.validate();
        int count = x.length;
        double gap = Math.max(1, gapMillis);
        double duration = (double) time[count - 1] - time[0] + gap
                + (double) next.time[next.time.length - 1] - next.time[0];
        if (count + next.x.length > MAX_POINTS || duration > MAX_DURATION_MILLIS)
            throw new IllegalArgumentException("Swipe continuation exceeds limits");
        float[] joinedX = Arrays.copyOf(x, count + next.x.length);
        float[] joinedY = Arrays.copyOf(y, count + next.y.length);
        float[] joinedTime = Arrays.copyOf(time, count + next.time.length);
        double offset = (double) time[count - 1] + gap - next.time[0];
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
