package com.titankeys.keyboard;

import com.sun.management.HotSpotDiagnosticMXBean;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** A heap with known leaks for tests/leaks/run.sh: what tools/leak-paths.py must name, and what it must leave out. */
class Activity {
    boolean mDestroyed;
}

class GuideActivity extends Activity {
    final String typed = "SECRET-TYPED-TEXT";
    Runnable listener() {
        return () -> System.out.println(typed);
    }
}

class Registry {
    /** Destroyed, held by a listener in a static list: a leak. */
    static final List<Runnable> listeners = new ArrayList<>();
    /** Destroyed, held only weakly: not a leak. */
    static WeakReference<GuideActivity> weakly;
    /** Open: not reported without --all. */
    static GuideActivity open;
}

public class LeakFixture {
    public static void main(String[] args) throws Exception {
        // In a method of its own, so that no local variable of main's holds what it makes.
        setUp();
        ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class).dumpHeap(args[0], false);
    }

    static void setUp() {
        GuideActivity leaked = new GuideActivity();
        leaked.mDestroyed = true;
        Registry.listeners.add(leaked.listener());
        GuideActivity weak = new GuideActivity();
        weak.mDestroyed = true;
        Registry.weakly = new WeakReference<>(weak);
        weak = null;
        Registry.open = new GuideActivity();
    }
}
