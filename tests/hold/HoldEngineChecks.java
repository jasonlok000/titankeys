package com.atlas.engine;

import java.util.ArrayList;
import java.util.List;

/** Checks holding a letter for its symbol. Adapted from the tests of Titan Hold Symbols. */
public final class HoldEngineChecks {
    private static final int A = 29, H = 36, S = 47, T = 48, ALT = 57, SHIFT = 59, SPACE = 62, DEL = 67, SEVEN = 14;
    private static final List<String> log = new ArrayList<>();
    private static final HoldEngine.Output out = key -> log.add("hold:" + key);
    private static int checks;

    private static void check(boolean passed, String name) {
        if (!passed) throw new AssertionError(name);
        checks++;
    }

    private static String drain() {
        String typed = String.join(",", log);
        log.clear();
        return typed;
    }

    private static boolean down(HoldEngine e, int key, long time) { return e.down(key, 1, time, false, true); }
    private static boolean plain(HoldEngine e, int key, long time) { return e.down(key, 1, time, false, false); }
    private static boolean repeat(HoldEngine e, int key, long time) { return e.down(key, 1, time, true, true); }
    private static boolean up(HoldEngine e, int key, long time) { return e.up(key, 1, time); }

    private static HoldEngine engine(int millis) {
        HoldEngine e = new HoldEngine(out);
        e.threshold(millis);
        log.clear();
        return e;
    }

    public static void main(String[] ignored) {
        holdTime();
        tapAndHold();
        timing();
        busyApp();
        rollover();
        otherKeys();
        modifiers();
        repeats();
        leavingTheField();
        lostRelease();
        twoDevices();
        System.out.println("PASS: " + checks + " hold checks");
    }

    private static void holdTime() {
        HoldEngine e = new HoldEngine(out);
        check(e.threshold() == 300, "The hold time starts at 300 ms");
        e.threshold(199);
        check(e.threshold() == 200, "The hold time is at least 200 ms");
        e.threshold(1001);
        check(e.threshold() == 1000, "The hold time is at most 1000 ms");
        e.threshold(200);
        check(e.threshold() == 200, "200 ms is allowed");
        e.threshold(1000);
        check(e.threshold() == 1000, "1000 ms is allowed");
        check(HoldEngine.clamp(0) == HoldEngine.MIN_MILLIS && HoldEngine.clamp(5000) == HoldEngine.MAX_MILLIS, "A saved value stays in range");

        e = engine(HoldEngine.DEFAULT_MILLIS);
        down(e, S, 1000);
        up(e, S, 1299);
        check(drain().isEmpty(), "299 ms keeps the letter");
        down(e, S, 2000);
        up(e, S, 2300);
        check(drain().equals("hold:47"), "300 ms is a hold");
        e.threshold(200);
        down(e, S, 3000);
        up(e, S, 3199);
        check(drain().isEmpty(), "A shorter hold time is obeyed: letter");
        down(e, S, 4000);
        up(e, S, 4200);
        check(drain().equals("hold:47"), "A shorter hold time is obeyed: hold");
        e.threshold(1000);
        down(e, S, 5000);
        up(e, S, 5999);
        check(drain().isEmpty(), "A longer hold time is obeyed: letter");
        down(e, S, 7000);
        up(e, S, 8000);
        check(drain().equals("hold:47"), "A longer hold time is obeyed: hold");
    }

    private static void tapAndHold() {
        HoldEngine e = engine(400);
        check(!down(e, S, 1000), "The first press is not consumed, so its letter types at once");
        check(e.pending() != null, "The letter can still become its symbol");
        check(drain().isEmpty(), "Nothing changes while the press is undecided");
        check(up(e, S, 1080), "Its release is consumed");
        check(drain().isEmpty(), "A quick release keeps the letter");
        check(e.pending() == null && e.swallowing() == 0, "A tap leaves no state");

        check(!down(e, S, 2000), "A held letter types at once too");
        long id = e.pending().id;
        check(e.holdDue(id, 0), "The timer changes the held key");
        check(drain().equals("hold:47"), "The symbol replaces the letter at the hold time, before the release");
        check(!e.holdDue(id, 0), "The timer runs one time");
        check(up(e, S, 2700), "The release after the symbol is consumed");
        check(drain().isEmpty(), "The release after the symbol changes nothing");
        check(e.swallowing() == 0, "A hold leaves no state");

        down(e, S, 2900);
        id = e.pending().id;
        up(e, S, 2950);
        drain();
        check(!e.holdDue(id, 0) && drain().isEmpty(), "A timer after a quick release does nothing");
    }

    private static void timing() {
        HoldEngine e = engine(400);
        down(e, S, 5000);
        check(e.timerAt(e.pending(), 5010) == 5400, "A press that arrives on time waits for the hold time");
        check(e.timerAt(e.pending(), 5300) == 5400 && e.timerAt(e.pending(), 5301) == 5401, "The release always gets 100 ms");
        long id = e.pending().id;
        long timer = e.timerAt(e.pending(), 5600); // It arrives after the hold time, and its release 40 ms after that.
        check(timer > 5640, "A press that arrives late gives its release time to arrive");
        up(e, S, 5070);
        check(drain().isEmpty(), "A quick tap that arrives late keeps its letter");
        check(!e.holdDue(id, 0) && drain().isEmpty(), "The timer after a late tap adds nothing");

        down(e, T, 5500);
        check(e.holdDue(e.pending().id, 0) && drain().equals("hold:48"), "A real hold that arrives late still becomes its symbol");
        up(e, T, 6300);

        down(e, A, 6000);
        id = e.pending().id;
        up(e, A, 6900);
        check(drain().equals("hold:29"), "A real hold becomes its symbol even if the timer is late");
        check(!e.holdDue(id, 0) && drain().isEmpty(), "The late timer then adds nothing");

        check(HoldEngine.arrivedInTime(1000, 1000) && HoldEngine.arrivedInTime(1000, 2999), "A press less than 2 s late can become a symbol");
        check(!HoldEngine.arrivedInTime(1000, 3000), "A press 2 s late stays a letter");
        check(!HoldEngine.arrivedInTime(1000, 999), "A press from the future stays a letter");
    }

    private static void busyApp() {
        HoldEngine e = engine(400);
        down(e, S, 20000);
        long id = e.pending().id;
        check(!e.holdDue(id, 101) && drain().isEmpty() && e.pending() != null, "A busy app can still have the release, so the letter stays pending");
        check(up(e, S, 20060) && drain().isEmpty(), "The delayed release of a quick tap keeps the letter");
        check(!e.holdDue(id, 0) && drain().isEmpty(), "The timer adds nothing after that release");

        down(e, A, 21000);
        e.holdDue(e.pending().id, 900);
        check(up(e, A, 21500) && drain().equals("hold:29"), "The delayed release of a real hold replaces the letter");

        down(e, H, 22000);
        e.holdDue(e.pending().id, 900);
        check(!down(e, T, 22500) && drain().equals("hold:36"), "The next key decides a hold when the release was lost");
        up(e, T, 22550);
        drain();

        down(e, S, 23000);
        check(e.holdDue(e.pending().id, 100) && drain().equals("hold:47"), "An app that answers in time gets the symbol at the hold time");
        up(e, S, 23500);
    }

    private static void rollover() {
        HoldEngine e = engine(400);
        check(!down(e, T, 7000), "T types at once");
        check(!down(e, H, 7040) && drain().isEmpty(), "H types at once, and T stays a letter");
        check(e.pending().key == H, "H can still become its symbol");
        check(up(e, T, 7080) && drain().isEmpty(), "The release of T adds nothing");
        check(up(e, H, 7100) && drain().isEmpty(), "The release of H adds nothing");

        down(e, A, 8000);
        e.holdDue(e.pending().id, 0);
        check(drain().equals("hold:29"), "The symbol replaces the letter at the hold time");
        check(!down(e, S, 8600) && drain().isEmpty(), "The next key does not repeat the symbol");
        up(e, A, 8650);
        up(e, S, 8700);
        check(drain().isEmpty(), "The second key stays a letter");
    }

    private static void otherKeys() {
        HoldEngine e = engine(400);
        down(e, S, 9000);
        check(!plain(e, SPACE, 9050), "Space is not held back");
        check(drain().isEmpty(), "The letter stays a letter when Space follows it");
        check(!up(e, SPACE, 9090) && up(e, S, 9100), "Only the letter's release is consumed");
        check(drain().isEmpty(), "Rollover changes nothing twice");
        check(!plain(e, DEL, 9200) && !up(e, DEL, 9250), "Backspace is not held back");
        check(!down(e, SEVEN, 9300) && !up(e, SEVEN, 9350), "A key that is not a letter cannot become a symbol");
        check(e.pending() == null && e.swallowing() == 0, "Other keys leave no state");

        check(!plain(e, S, 13000) && !up(e, S, 13050), "A letter that is not eligible types at once");
        check(e.pending() == null && e.swallowing() == 0, "A letter that is not eligible leaves no state");
    }

    private static void modifiers() {
        HoldEngine e = engine(400);
        check(!plain(e, ALT, 10000), "Alt is not held back");
        check(!plain(e, S, 10020), "A letter while Alt is on stays a letter");
        check(!up(e, S, 10060) && !up(e, ALT, 10080), "Their releases are not consumed");
        check(drain().isEmpty() && e.pending() == null, "Alt and a letter leave no state");
        check(!down(e, S, 10200), "After Alt a letter can become its symbol again");
        up(e, S, 10250);
        drain();

        check(!down(e, S, 11000), "The letter comes first");
        check(!plain(e, ALT, 11010), "Alt is not consumed");
        check(drain().isEmpty() && e.pending() == null, "Alt decides the letter, which stays a letter");
        check(up(e, S, 11050), "Its release is still consumed");

        check(!down(e, S, 12000), "The letter comes first");
        check(!plain(e, SHIFT, 12500), "Shift is not consumed");
        check(drain().equals("hold:47"), "A letter held long enough before Shift becomes its symbol");
        up(e, S, 12550);

        // A letter that the keyboard types while Shift is down carries Shift on its own flags, so it is never eligible.
        check(!plain(e, SHIFT, 12600), "Shift down");
        check(!e.down(A, 1, 12650, false, false) && e.pending() == null && e.swallowing() == 0, "A letter typed with Shift stays a letter");
        check(!repeat(e, A, 12650), "A letter typed with Shift repeats as usual");
    }

    private static void repeats() {
        HoldEngine e = engine(400);
        check(!down(e, S, 2000), "A held letter types at once");
        check(repeat(e, S, 2000) && drain().isEmpty(), "A repeat while it is undecided types nothing");
        e.holdDue(e.pending().id, 0);
        check(drain().equals("hold:47"), "The symbol replaces the letter one time");
        for (int i = 0; i < 5; i++) check(repeat(e, S, 2000), "Repeats after the symbol are consumed");
        check(drain().isEmpty(), "Repeats after the symbol type nothing");
        up(e, S, 3000);
        check(drain().isEmpty() && e.swallowing() == 0, "A long hold gives only the one symbol");

        check(!repeat(e, S, 4000) && e.pending() == null, "A repeat of a press that cannot become a symbol types as usual");

        down(e, T, 5000);
        check(!e.down(SPACE, 1, 4000, true, false), "Another key's repeat passes");
        check(drain().isEmpty() && e.pending() != null, "Another key's repeat does not decide the letter");
        up(e, T, 5050);
        check(drain().isEmpty(), "The letter stays a letter after a quick release");
    }

    private static void leavingTheField() {
        HoldEngine e = engine(400);
        down(e, A, 14000);
        e.cancel();
        check(drain().isEmpty() && e.pending() == null, "Leaving a field keeps the letter");
        check(up(e, A, 14700) && drain().isEmpty(), "The release afterwards adds nothing");
        check(!e.holdDue(1, 0) && drain().isEmpty(), "A timer after the field ended adds nothing");
        down(e, A, 15000);
        e.resolve(15500);
        check(drain().equals("hold:29"), "A letter decided after the hold time becomes its symbol");
        up(e, A, 15600);
        e.resolve(16000);
        check(drain().isEmpty(), "With no pending letter there is nothing to change");
    }

    private static void lostRelease() {
        HoldEngine e = engine(400);
        down(e, A, 16000);
        check(!down(e, A, 17000) && drain().equals("hold:29"), "After a lost release, the old press is decided first");
        check(up(e, A, 17050) && drain().isEmpty(), "The new press keeps its letter");
    }

    private static void twoDevices() {
        HoldEngine e = engine(400);
        check(!e.down(S, 1, 18000, false, true), "S on the first device");
        check(!e.down(S, 2, 18010, false, false), "S on the second device is separate");
        check(drain().isEmpty(), "The first device's letter stays a letter");
        check(!e.up(S, 2, 18040), "The second device's release passes");
        check(e.up(S, 1, 18060), "The first device's release is consumed");
    }
}
