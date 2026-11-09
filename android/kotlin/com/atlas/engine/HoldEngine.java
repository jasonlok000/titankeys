package com.atlas.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * Decides if a pressed letter types itself or the symbol printed on its key. Adapted from Titan Hold Symbols.
 *
 * A letter that can wait is held back. The hold timer types its symbol at the hold time while the key is still down,
 * unless its release could still be on the way. Before that, its release, the next key's press or resolve() decide from
 * the events' own times: held for the hold time or longer gives the symbol, and shorter gives the letter. Either way it
 * is typed before the next key continues. The release and any repeats of a held-back key are always consumed, so a held
 * letter types only once.
 *
 * Shift, Alt and Sym are not tracked here. The keyboard knows their state, and says a letter is not eligible while one is on.
 */
public final class HoldEngine {
    public static final int MIN_MILLIS = 200, MAX_MILLIS = 1000, DEFAULT_MILLIS = 300;
    private static final int KEYCODE_A = 29, KEYCODE_Z = 54;
    /**
     * A key's release reaches the keyboard through the app, and only after its press is handled. This is the time that a
     * release gets to arrive, and the longest that the app can take to answer and still count as free to send it.
     */
    private static final int RELEASE_LAG_MILLIS = 100;
    /** A press that arrives this late types at once. */
    private static final int MAX_LATE_MILLIS = 2000;

    public interface Output {
        void tap(int key);

        void hold(int key);
    }

    public static final class Press {
        public final long id, downTime;
        public final int key, device;

        Press(long id, int key, int device, long downTime) {
            this.id = id;
            this.key = key;
            this.device = device;
            this.downTime = downTime;
        }

        long slot() {
            return HoldEngine.slot(device, key);
        }
    }

    private final Output output;
    /** The press time of each held-back key whose release has not arrived, by device and key. */
    private final Map<Long, Long> swallowed = new HashMap<>();
    private Press pending;
    private long nextId;
    private int threshold = DEFAULT_MILLIS;

    public HoldEngine(Output output) {
        this.output = output;
    }

    public static boolean isLetter(int key) {
        return key >= KEYCODE_A && key <= KEYCODE_Z;
    }

    public static int clamp(int millis) {
        return Math.max(MIN_MILLIS, Math.min(MAX_MILLIS, millis));
    }

    public static boolean arrivedInTime(long downTime, long now) {
        return now >= downTime && now - downTime < MAX_LATE_MILLIS;
    }

    private static long slot(int device, int key) {
        return ((long) device << 32) | (key & 0xffffffffL);
    }

    public Press pending() {
        return pending;
    }

    public int swallowing() {
        return swallowed.size();
    }

    public int threshold() {
        return threshold;
    }

    public void threshold(int millis) {
        threshold = clamp(millis);
    }

    /**
     * Returns true to consume this press. A letter held back earlier is typed before this returns.
     * Eligible is false for anything that must type at once, such as a letter while Shift is on.
     */
    public boolean down(int key, int device, long downTime, boolean repeat, boolean eligible) {
        long slot = slot(device, key);
        Long held = swallowed.get(slot);
        if (held != null) {
            if (held == downTime) return true; // The held-back key is repeating.
            swallowed.remove(slot);            // A new press, so the old release was lost.
        }
        if (repeat) return false;              // Another key repeating says nothing about the held-back letter.
        resolve(downTime);
        if (!isLetter(key) || !eligible) return false;
        pending = new Press(++nextId, key, device, downTime);
        swallowed.put(slot, downTime);
        return true;
    }

    /** Returns true to consume this release, which is only ever the release of a consumed press. */
    public boolean up(int key, int device, long eventTime) {
        long slot = slot(device, key);
        if (swallowed.remove(slot) == null) return false;
        if (pending != null && pending.slot() == slot) resolve(eventTime);
        return true;
    }

    /**
     * When to run the hold timer, on the same clock as the event times. This is normally the press time plus the hold
     * time. A press that arrives late still gives its release time to arrive, so the release can show that it was a tap.
     */
    public long timerAt(Press press, long now) {
        return Math.max(press.downTime + threshold, now + RELEASE_LAG_MILLIS);
    }

    /**
     * The hold timer: types the symbol while the key is still down. The release stays consumed.
     * appMillis is how long the app took to answer just now. A slower app can still be holding the release, so the
     * letter keeps waiting and resolve() decides from the release's own time. A busy app can't change what is typed.
     */
    public boolean holdDue(long id, long appMillis) {
        if (pending == null || pending.id != id || appMillis > RELEASE_LAG_MILLIS) return false;
        Press press = pending;
        pending = null;
        output.hold(press.key);
        return true;
    }

    /** Types the held-back key as if it was released at this time, on the same clock as the event times. */
    public void resolve(long at) {
        if (pending == null) return;
        Press press = pending;
        pending = null;
        if (at - press.downTime >= threshold) output.hold(press.key);
        else output.tap(press.key);
    }
}
